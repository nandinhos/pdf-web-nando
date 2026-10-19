# Plano — Edição de Texto de Alto Nível (pdf-web-nando)

> Evoluir a edição de conteúdo do pdf-web-nando para o modelo de **duas etapas**
> que o NAN-PDF (fork PdfCraft) usa: **detectar blocos** → **reescrever
> cirurgicamente** preservando o que não mudou e, quando possível, **reusando a
> fonte original**.
>
> **Este documento não implementa nada.** É para análise técnica, de impacto e
> revisão adversarial antes de qualquer edição de código.
>
> Metodologia: Ponytail (menor diff funcional) + CLEARER. Risk Dial: **MEDIUM**.
> Âncora de aprendizado: `.dev-memory/learned-lessons.md` §2;
> `docs/ANALISE_pdfcraft.md`; `docs/PLAN_layout_blocks.md`.

---

## 1. Concrete Goal (C)

Permitir que o usuário **selecione um bloco de texto existente** e **altere o
conteúdo**, com o resultado:
- substituindo o texto original (sem "teatro" / sobreposição),
- preservando posição/tamanho/cor,
- **reusando a fonte original embutida quando ela cobrir os caracteres novos**,
- caindo para fonte Standard-14 **com aviso explícito** quando não for possível.

Condição de parada: fluxo `analyze-layout → content-edit` edita um parágrafo de
PDF digital; teste prova (a) texto novo presente, (b) texto antigo ausente nos
bytes, (c) fonte original reusada quando aplicável.

---

## 2. Load Context (L) — estado atual OBSERVED

| Componente | Estado hoje | Papel no plano |
|---|---|---|
| `LayoutAnalysisService` (38–162) | Fase 1: detecta blocos de texto (por linha, `PDFTextStripper.writeString`) e imagem (bbox via `PDFGraphicsStreamEngine`); flag `scanned` | **Etapa (a)** — já existe; é a base |
| `ContentEditService` (47–274) | Overlay achatado: text/image/redact; `redact hard` rasteriza e destrói região | **Etapa (b)** — hoje substitui via redação+caixa; evoluir |
| `TextExtractService` (24–52) | Extrai texto numa área (`PDFTextStripperByArea`) | Captura o texto a pré-preencher |
| Endpoints | `POST /analyze-layout`, `/content-edit`, `/text-area` | Contrato a estender |
| Frontend | `ContentEditor`/`ContentCanvas`/`ContentEditPanel`: detectar blocos, editar texto por retângulo, redação real | UX a refinar |
| PDFBox 3.0.3 | `PDType0Font`, `PDTrueTypeFont`, `PDFontDescriptor`, `FontMapper`, `PDFStreamEngine` disponíveis | Viabiliza reuso/embutir de fonte |

**Escada Ponytail (onde paramos):**
1. Precisa existir? Sim (pedido direto, alto valor).
2. Já existe na base? **Etapa (a) sim** (LayoutAnalysis), **(b) parcial** (redação+caixa).
3. Stdlib/nativo? PDFBox nativo cobre fonte embutida; sem lib externa.
4. Intervenção cirúrgica? Sim — estender serviços existentes, não reescrever.

---

## 3. Explicit Boundaries (E)

**Dentro do escopo:**
- Evoluir a etapa (b): ao editar um bloco detectado, **tentar reusar a fonte
  original embutida**; só então cair para Helvetica (com aviso).
- Melhorar o agrupamento de blocos (reduzir falhas em PDFs de navegador).
- Refinar a UX de edição (seleção de bloco → editar inline → aplicar).

**Fora do escopo (nesta leva):**
- Reembutir/subconjunto de fontes novas a partir de arquivos externos (o "Load
  font…" do NAN-PDF). Fase futura.
- Edição de CJK/RTL com reshaping completo.
- Edição de vetores/formas; tabelas; reflow multi-coluna.
- OCR→editar de escaneados (já há caminho separado; não misturar aqui).

**Blast radius:** 3 serviços backend + 3 componentes frontend existentes.
Nenhuma assinatura pública removida; só extensão aditiva.

---

## 4. Faseamento (menor diff por entrega)

### Fase A — Reuso da fonte original (fidelidade) — núcleo do valor
Hoje a edição reescreve em Helvetica. Objetivo: **reusar a fonte do bloco**.
- Em `LayoutAnalysisService`, ao detectar um bloco de texto, **capturar também o
  recurso de fonte** daquele texto (via `PDFTextStripper` → `TextPosition.getFont()`
  dá o `PDFont` da posição) e expor um identificador no bloco.
- Em `ContentEditService`, ao reescrever o texto, **tentar usar essa fonte** se ela
  puder codificar todos os caracteres do texto novo (`font.encode(...)` sem lançar);
  senão, Helvetica + flag `fontSubstituted: true` no resultado.
- **Teste:** editar um bloco cuja fonte embutida cobre o novo texto → extração
  mostra o texto novo e o PDF **mantém o mesmo recurso de fonte** (sem Helvetica).

### Fase B — Robustez do agrupamento (cobertura)
Reduzir o caso "`count: 0`" em PDFs não-tagueados.
- Ajustar o agrupamento por linha: tolerância de baseline, junção de runs
  próximos, ordenação estável. Medir cobertura por **classe de PDF**.
- Sinalizar `scanned` já funciona; adicionar `ungrouped` quando há texto mas o
  agrupador não forma blocos → o front sugere "edição por área" (fallback atual).
- **Teste:** corpus pequeno por classe (nativo, LibreOffice, Chrome, scaneado) com
  contagem esperada de blocos.

### Fase C — UX de edição inline
- Selecionar um bloco detectado → editar o texto **no lugar** (contentEditable
  sobre o canvas) → aplicar via `content-edit`.
- Mostrar aviso quando a fonte foi substituída (transparência, não esconder o teto).
- **Teste:** typecheck/lint/build + verificação manual no Docker (rebuild).

---

## 5. Enable Evidence (E) — critérios de aceite

| Critério | Verificação |
|---|---|
| Editar bloco digital: texto novo entra, antigo sai | teste backend + extração + bytes adversariais |
| Fonte original reusada quando cobre os caracteres | teste: recurso de fonte preservado no PDF de saída |
| Fonte substituída sinalizada ao usuário | campo no DTO + aviso na UX |
| Cobertura por classe de PDF medida | teste de corpus (contagem de blocos) |
| Sem regressão | suíte backend + frontend verdes |
| Verificação real | rebuild Docker + teste HTTP (padrão já estabelecido) |

---

## 6. Review & Validate (R) — REVISÃO ADVERSARIAL

### R1 — `TextPosition.getFont()` dá a fonte, mas reusá-la para TEXTO NOVO é limitado
Fontes embutidas frequentemente são **subconjuntos** (só os glifos usados). Um
caractere novo pode não existir no subset → `encode` lança. **Mitigação:** testar
`font.encode(novoTexto)` em try/catch; se falhar em qualquer caractere, cair para
Helvetica com `fontSubstituted=true`. **Não** prometer fidelidade universal (é o
mesmo teto do NAN-PDF: "subset não reconstrói glifo ausente").

### R2 — Reescrever no content stream preservando o resto é complexo
A edição precisa remover só os operadores do bloco e inserir os novos, sem afetar
vizinhos. **Mitigação (decisão):** manter a abordagem atual que já funciona —
**redação real da área do bloco + redesenho do texto** — mas com a fonte original.
Isto evita reescrita de stream token-a-token (frágil, R2 do plano de blocos) e
reusa código testado. Reescrita cirúrgica de stream fica como Fase futura se a
qualidade exigir.

### R3 — Posição/baseline do texto reescrito pode desalinhar
O bloco detectado tem bbox, mas a **baseline** exata e o avanço por glifo não.
**Mitigação:** usar a baseline derivada do `TextPosition` (já temos y); aceitar
desalinhamento sub-pixel como limite declarado; teste visual manual na Fase C.

### R4 — Agrupamento agressivo pode juntar blocos distintos (ou dividir demais)
Mudar heurística de agrupamento é risco de regressão silenciosa. **Mitigação:**
congelar o comportamento atual com testes de contagem **antes** de ajustar (Fase B
começa escrevendo os testes de corpus, depois mexe na heurística).

### R5 — PDFs de navegador podem nunca agrupar bem
Chrome desenha texto glifo-a-glifo sem estrutura. **Mitigação honesta:** declarar
que esses PDFs usam o **fallback de edição por área** (já existe) em vez de blocos.
Não vender "edição universal".

### R6 — Blast radius em serviço compartilhado
`ContentEditService` já serve redação/overlay. **Mitigação:** adicionar o caminho
de fonte sem alterar os existentes; cobrir com os testes atuais + novos.

### R7 — Licença/origem
Nada é portado do NAN-PDF (Rust). Reimplementamos o **modelo** com PDFBox. Sem
questão de licença; o aprendizado é conceitual (já registrado na lição §2).

---

## 7. Decisão recomendada (concisa)

1. **Fase A primeiro** (reuso de fonte) — maior ganho de fidelidade percebida,
   menor diff, reusa a detecção que já existe.
2. **Fase B** (robustez de agrupamento) **começando pelos testes de corpus** (R4),
   só então ajustar a heurística.
3. **Fase C** (UX inline) por último, quando o backend estiver sólido.
4. Manter a abordagem **redação real + redesenho** (R2) — não entrar em reescrita
   token-a-token de content stream agora (YAGNI + risco).
5. Rebuild Docker + teste HTTP a cada fase (padrão já adotado).

**Teto declarado (honestidade):** fidelidade total de fonte (incluindo reembutir
faces externas e CJK/RTL) **não** entra nesta evolução — é roadmap futuro, decidido
só sob demanda. Mesma fronteira que o NAN-PDF reconhece.

---

## 8. Pendências para decisão humana (antes de codar)

- [ ] Aprovar a ordem A → B → C (recomendada).
- [ ] Confirmar a decisão R2: manter **redação real + redesenho** (não reescrever
      stream token-a-token agora).
- [ ] Definir o corpus de teste por classe (quais PDFs representam nativo /
      LibreOffice / Chrome / escaneado).
- [ ] Confirmar o comportamento de fonte substituída: **avisar e prosseguir**
      (recomendado) vs. **bloquear e pedir confirmação**.
