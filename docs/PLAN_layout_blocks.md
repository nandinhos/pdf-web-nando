# Plano — Detecção de Blocos Editáveis ("Layout Blocks")

> Transformar a página do PDF em **blocos editáveis** (texto → caixas; imagens →
> blocos selecionáveis/deletáveis), detectando e classificando o conteúdo
> automaticamente. Objetivo de produto: a "sessão maravilhosamente prática" de
> personalização de página.
>
> Metodologia: **Ponytail Mode** (entender muito, construir pouco, entregar certo)
> + ciclo CLEARER. Risk Dial: **MEDIUM** (feature nova, sem tocar auth/dados).

---

## 1. Concrete Goal (C)

Dado um PDF carregado na ferramenta "Editar conteúdo", o usuário clica em
**"Detectar blocos"** e o canvas é populado automaticamente com:

- **Blocos de texto** — cada trecho vira uma caixa de texto editável, já
  preenchida com o conteúdo e posicionada na coordenada original.
- **Blocos de imagem** — cada imagem embutida vira um bloco selecionável; `Delete`
  remove (via redação real já existente).

Condição de parada: `POST /analyze-layout` retorna os blocos de 1 página; o
front os injeta como `LocalElement[]`; teste automatizado prova a detecção.

---

## 2. Load Context (L) — âncoras OBSERVED no código

| Peça existente | Papel no plano | Evidência |
|---|---|---|
| `PDFTextStripper` / `TextPosition` | posição (x,y,w,h) de cada caractere/trecho de texto | PDFBox 3.0.3 no classpath (OBSERVED) |
| `PDFTextStripperByArea` | já usado em `TextExtractService` | `TextExtractService.java` (OBSERVED) |
| `ImageExtractService` | percorre XObjects de imagem por página (recursivo em Form XObjects) | `ImageExtractService.java` (OBSERVED) — **mas hoje não captura a posição/bbox**, só os bytes |
| `Tesseract` (`--psm`, `pdf txt`) | motor OCR já instalado; suporta modos de saída com bbox (`tsv`/`hocr`) | `Tesseract.java` (OBSERVED) |
| `ContentEditService` (text/image/redact + `hard`) | consumidor final dos blocos (mover/editar/apagar) | `ContentEditService.java` (OBSERVED) |
| `ContentCanvas` / `LocalElement` | render dos blocos como elementos editáveis | `ContentCanvas.tsx` (OBSERVED) |
| Jobs + SSE | assincronismo para o caminho OCR | `OcrController`/`JobRegistry` (OBSERVED) |

**Conclusão da escada Ponytail (degraus 1–5):**
1. *Precisa existir?* Sim — pedido explícito, alto valor.
2. *Já existe na base?* Parcialmente: extração de texto por área e de imagens já
   existem; **falta** a captura de **posição** (bbox) e o **agrupamento** em blocos.
3. *Stdlib/nativo resolve?* PDFBox nativo expõe `TextPosition` (texto) e a matriz
   de cada `PDImageXObject` desenhado — não precisa de lib externa de layout.
4. *API nativa da plataforma?* Tesseract nativo tem saída `tsv` com bbox por palavra.
5. *Intervenção cirúrgica?* Sim: 1 serviço novo + 1 endpoint + 1 botão no front.
   Nenhuma refatoração dos serviços existentes.

---

## 3. Explicit Boundaries (E) — escopo e blast radius

**Dentro do escopo:**
- Novo `LayoutAnalysisService` (backend) — detecção estrutural (texto + imagem).
- Novo endpoint `POST /analyze-layout` (página única).
- Novo botão "Detectar blocos" no `ContentEditPanel` + injeção no `ContentEditor`.
- DTOs + testes.

**Fora do escopo (nesta leva):**
- Fidelidade de fonte (reembutir a fonte original) — permanece Standard-14 aprox.
- Detecção de colunas/tabelas/ordem de leitura avançada.
- Edição de vetores (linhas, formas) — tratados como parte do fundo.
- Documento inteiro de uma vez (começa por página).

**Blast radius:** aditivo. Nenhuma assinatura pública existente muda. Serviços
atuais não são tocados (só reusados por composição).

---

## 4. Arquitetura — caminhos A, B e o híbrido

### Caminho A — Estrutural (do próprio PDF) — **Fase 1**
Estende um `PDFTextStripper` para coletar `TextPosition` e agrupa por proximidade
(linha → bloco). Para imagens, replica o walk de `ImageExtractService` **capturando
a bbox** (posição/escala vêm da CTM no momento do `drawImage`, via `PDFStreamEngine`).

- Prós: preciso em PDF digital; rápido; sem dependência de OCR.
- Contras: **não funciona em PDF escaneado** (página = 1 imagem, texto = 0 blocos).

### Caminho B — Visual/OCR — **Fase 2**
Rasteriza a página, roda Tesseract em modo `tsv` (bbox por palavra), agrupa em
linhas/blocos. Para imagens, detecta regiões não-textuais.

- Prós: funciona em qualquer PDF, inclusive escaneado.
- Contras: herda erros de OCR; assíncrono (jobs/SSE).

### Híbrido (decisão): **A primeiro, B como fallback automático**
Regra: roda A; se a página tem < N caracteres de texto detectável **e** cobertura
de imagem alta → considera "escaneada" e oferece B (OCR). Foi o que o usuário
observou ("fazia OCR para diferenciar imagem de texto").

---

## 5. Faseamento (menor diff funcional por entrega)

### Fase 1 — Detecção estrutural (entrega 80% do valor)
- `LayoutAnalysisService.analyzePage(pdf, page) → List<Block>`
  - Texto: `PDFTextStripper` custom coletando `TextPosition` → agrupa por linha
    (mesma baseline ± tolerância) e por bloco (linhas próximas).
  - Imagem: `PDFStreamEngine` custom capturando a CTM de cada `Do` de
    `PDImageXObject` → bbox em pt (top-left, convenção do projeto).
- `POST /analyze-layout { fileId, page, password? } → { blocks: [...] }`
- Front: botão "Detectar blocos" → injeta `LocalElement[]` (text/image).
- **Teste (TDD):** fixture `pdfWithTextAt` (2 textos conhecidos) → retorna 2
  blocos de texto nas posições certas; fixture `pdfWithImage` → 1 bloco de imagem.

### Fase 2 — Fallback OCR (páginas escaneadas)
- `LayoutAnalysisService` detecta página "pobre em texto" → dispara Job OCR em
  modo `tsv`; parseia bbox por palavra → blocos.
- Reusa `JobRegistry`/SSE. Novo parse de TSV no `Tesseract` (modo saída).
- **Teste:** só roda com Tesseract presente (skip no host, como `TesseractIntegrationTest`).

### Fase 3 — Refino de agrupamento
- Heurística de parágrafo/coluna; merge de linhas; ordenação de leitura.
- Puramente de qualidade; sem mudança de contrato.

---

## 6. Enable Evidence (E) — critérios de aceite verificáveis

| Critério | Verificação |
|---|---|
| Texto digital vira blocos nas posições corretas | teste unit: `analyzePage` sobre `pdfWithTextAt` → asserts de bbox |
| Imagem embutida vira bloco com bbox | teste unit: `pdfWithImage` → 1 bloco imagem, área > 0 |
| Endpoint responde o contrato | teste MockMvc `POST /analyze-layout` |
| Sem regressão | suíte backend completa verde |
| Front injeta e edita | typecheck/lint/build + verificação manual no container |

---

## 7. Review & Validate (R) — REVISÃO ADVERSARIAL

> Confronto crítico do plano contra os modos de falha conhecidos de "layout
> reconstruction". Cada risco tem mitigação concreta.

### R1 — Coordenadas: PDFBox usa bottom-left; o projeto usa top-left
`TextPosition.getY()` e a CTM estão no espaço PDF (origem inferior esquerda).
**Risco:** blocos aparecem espelhados verticalmente. **Mitigação:** converter
`y_tl = pageHeight - y_bl - h` (mesma fórmula já validada em `ContentEditService`
e `TextExtractService`). Teste trava a conversão.

### R2 — `TextPosition` é por caractere/glifo, não por "parágrafo"
Agrupar glifos em blocos é heurístico; errar o agrupamento gera caixas demais
(cada letra) ou de menos (bloco gigante). **Mitigação:** começar com agrupamento
por **linha** (baseline + gap horizontal) — determinístico e testável. Parágrafo
fica para a Fase 3. YAGNI: não otimizar agrupamento antes de ver o resultado real.

### R3 — Fidelidade de fonte (teto conhecido)
Editar o texto detectado reescreve em Standard-14, não na fonte original.
**Mitigação:** honestidade de produto — o bloco detectado preserva **posição e
conteúdo**; ao editar, avisa que a fonte é aproximada. Reembutir fonte original =
escopo futuro explícito. **Não prometer o que não entrega.**

### R4 — "Apagar o texto detectado" exige redação real na origem
Se o usuário detecta um bloco de texto e o edita, o texto **original** continua no
PDF por baixo (a caixa nova é overlay). **Mitigação:** o bloco de texto detectado
deve vir **pareado com uma redação real** da sua própria área (reusa o `hard`
redact + o fluxo de edição assistida que já existe). Caso contrário, teríamos o
"teatro" que já combatemos. **Isto é um requisito, não um detalhe.**

### R5 — Imagem como bloco: bbox vem da CTM, não do XObject
A mesma imagem pode ser desenhada várias vezes, em posições/escalas diferentes.
`ImageExtractService` hoje itera XObjects (não ocorrências desenhadas) e **não** tem
bbox. **Mitigação:** usar `PDFStreamEngine.drawImage` (intercepta cada `Do` com a
CTM corrente) — captura cada **ocorrência** com sua bbox real. Não reusar o
`ImageExtractService` para posição (ele resolve outro problema).

### R6 — Performance em páginas densas
Páginas com milhares de glifos → agrupamento O(n²) ingênuo trava. **Mitigação:**
agrupar por linha em O(n log n) (ordenar por y, depois x). Limite de blocos por
página com degradação graciosa. Medir antes de otimizar além disso (Ponytail).

### R7 — PDF escaneado silenciosamente retorna 0 blocos (Fase 1)
Sem fallback, o usuário clica "Detectar" e nada aparece → percepção de bug.
**Mitigação:** Fase 1 detecta "0 blocos de texto + página coberta por imagem" e
retorna um sinal `scanned: true` → o front sugere "usar OCR" (Fase 2). Mesmo antes
da Fase 2 existir, a mensagem evita o silêncio confuso.

### R8 — Blast radius do `ResponseEntity`/novos beans
Novo serviço injetado em novo controller (ou no `ToolsController`). **Mitigação:**
seguir o padrão já usado (ver a injeção de `TextExtractService`); teste de contexto
Spring (MockMvc) pega qualquer erro de wiring, como já aconteceu antes.

### R9 — Encoding/caracteres fora do WinAnsi (acentos)
Texto detectado com caractere fora do Standard-14 quebra ao reescrever.
**Mitigação:** o `ContentEditService.sanitizeForFont` já trata isso (OBSERVED) —
o caminho de reescrita reusa esse serviço, herdando a proteção.

---

## 8. Decisão recomendada (concisa, lógica, funcional)

1. **Implementar a Fase 1** (detecção estrutural) com o pareamento texto↔redação
   real (R4) como requisito, não opção. Entrega valor imediato no PDF digital do
   usuário, reusa 90% do que existe, risco controlado.
2. **Sinalizar `scanned`** desde a Fase 1 (R7) para não confundir em escaneados.
3. **Fase 2 (OCR) só depois** de validar a Fase 1 em uso real — evita construir o
   parser TSV antes de confirmar que o agrupamento por linha satisfaz o usuário.
4. Fidelidade de fonte (R3) permanece declarada como limitação; reembutir fonte é
   roadmap futuro, decidido só se houver demanda (Ponytail: YAGNI).

**Entregável da Fase 1:** `LayoutAnalysisService` + `POST /analyze-layout` +
botão "Detectar blocos" + testes (unit de detecção + MockMvc de contrato +
regressão verde).

---

## 9. Pendências para decisão humana (antes de codar)

- [ ] Aprovar Fase 1 isolada (recomendado) vs. Fase 1+2 juntas.
- [ ] Granularidade inicial dos blocos de texto: **linha** (recomendado) vs. parágrafo.
- [ ] "Detectar blocos" **adiciona** aos elementos atuais (recomendado) vs. substitui.
- [ ] Confirmar R4 como requisito: bloco de texto detectado nasce pareado com
      redação real da própria área (para edição não ser "teatro").
