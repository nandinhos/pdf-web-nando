# Plano de Implementação — Lapidação de Funcionalidades (Fase 6 / UX+Qualidade)

> Demanda do usuário em 2026-10-14. Backend Java 21 + PDFBox / Frontend Next.js.
> Ancorado no estado atual do código (Fases 0–5 + UI premium C). Evidências
> marcadas `OBSERVED` / `INFERRED`.

## 1. Itens da demanda

| # | Item | Tipo |
| --- | --- | --- |
| A | Loader enquanto carrega/processa (até gerar o PDF) | Frontend UX |
| B | Escolher **qualidade de exportação** (arquivos estão grandes, 8MB+) | Backend + Frontend |
| C | Escolher o **nome do arquivo** antes de gerar | Backend + Frontend |
| D | Renomear card "Assinatura" → "**Verificar Assinatura**" | Frontend (i18n) |
| E | Separar "**Editar**" de "**Dividir**" em cards distintos | Frontend |
| F | **Lupa**: modal com visualizador ampliado do documento | Frontend |

## 2. Diagnóstico do tamanho de arquivo (item B) — causa raiz

**OBSERVED:**
- PDFBox 3.0.3 já comprime object streams por padrão (`save()` usa
  `CompressParameters.DEFAULT_COMPRESSION`). Logo, o "inchaço" **não** é estrutural.
- **OCR (`OcrService`)**: cada página é renderizada em **PNG a 300 DPI** (`ImageType.GRAY`)
  e o tesseract embute essa imagem rasterizada no PDF pesquisável. Uma página A4 a
  300 DPI ≈ 2480×3508 px; PNG sem perdas → vários MB **por página**. **Esta é a
  causa principal dos 8MB+.**
- **Imagem→PDF (`ImageToPdfService`)**: usa `PDImageXObject.createFromFile`, que para
  PNG resulta em imagem **sem perdas** (grande para fotos).

**INFERRED (estratégia de correção):**
- Reduzir o **DPI de saída** do OCR (ex.: 150/200 em vez de 300) corta o tamanho
  de forma quadrática.
- **Recomprimir a imagem como JPEG** com qualidade ajustável antes de embutir
  (OCR e imagem→PDF). PDFBox: `JPEGFactory.createFromImage(doc, bufferedImage, quality)`.
- Expor um controle de **qualidade** (Baixa/Média/Alta) que mapeia para
  `{dpi, jpegQuality}`.

## 3. Decisões de escopo (D12–D15)

- **D12 (qualidade):** três presets expostos na UI, mapeados no backend:
  - `low` → dpi 120, jpeg 0.5
  - `medium` → dpi 150, jpeg 0.72 (padrão)
  - `high` → dpi 300, jpeg 0.85
  Aplica-se a **OCR** (render) e **Imagem→PDF** (recompressão). Split/merge/edit
  não rasterizam, então qualidade não se aplica (apenas nome do arquivo).
- **D13 (nome do arquivo):** o usuário informa um nome base; o backend passa a
  aceitar `filename` opcional e o download usa esse nome (`Content-Disposition`).
  Sanitização de nome (sem path traversal, extensão forçada correta).
- **D14 (Editar vs Dividir):** split vira card próprio "Dividir". "Editar" mantém
  reordenar/rotacionar/excluir/duplicar/inserir-branco (NÃO edita texto/elementos —
  deixar claro na descrição). Backend inalterado (ambos já existem); é reorganização
  da UI em dois cards + duas telas operacionais.
- **D15 (lupa/visualizador):** modal client-side (pdf.js) que amplia a página; zoom
  e navegação. Sem backend.

## 4. Mudanças por camada

### 4.1 Backend

1. **Qualidade (B / D12):**
   - `OcrService.runAsync(job, pdf, lang, dpi, jpegQuality)`: após render, recomprimir
     o PNG como **JPEG** (qualidade) antes de passar ao tesseract — ou render direto
     e gravar JPEG. Reduz a imagem embutida.
   - `ImageToPdfService`: quando a imagem for grande/foto, embutir via
     `JPEGFactory.createFromImage(doc, bim, quality)` em vez de `LosslessFactory`.
   - DTOs `OcrRequest` e `ImageToPdfRequest` ganham `quality` (`low|medium|high`).
   - Mapa de qualidade central (`QualityPreset`).

2. **Nome do arquivo (C / D13):**
   - DTOs de split/merge/edit/images-to-pdf/ocr ganham `filename?`.
   - `ArtifactStorage`: guardar/associar um "nome amigável" ao `resultId`, ou passar
     o nome no `Content-Disposition` do `GET /results/{id}` via query `?name=`.
     **Decisão:** o download aceita `?name=` e sanitiza; o front controla o nome.
   - `OcrController.result` e `ToolsController.download` honram `?name=`.

3. **Testes:** qualidade reduz tamanho (asserir `size(low) < size(high)`); nome
   sanitizado; OCR com jpeg menor que PNG.

### 4.2 Frontend

4. **Loader (A):** componente `Spinner`/overlay reutilizável; estados de "processando"
   em todas as ações assíncronas (upload, split, merge, edit export, imagem→PDF,
   verificação). Botões com estado `busy` + overlay no painel.

5. **Qualidade (B):** seletor "Qualidade de exportação" (Baixa/Média/Alta) nos
   painéis que rasterizam (OCR, Imagem→PDF). Enviar `quality`.

6. **Nome do arquivo (C):** campo "Nome do arquivo" antes de gerar; usado no link de
   download (`?name=<nome>.pdf`). Default inteligente (ex.: `documento-editado.pdf`).

7. **Cards (D/E):** Launcher passa a ter 6 cards — Editar, Dividir, Mesclar, Imagens,
   OCR, Verificar Assinatura. i18n: `tab.sign` → "Verificar Assinatura"; novas chaves
   `tab.split`, `tool.split.desc`, `tool.edit.desc` (ajustado p/ deixar claro que não
   edita texto). `ToolId` += `split`. Telas operacionais separadas (Editar usa grid de
   edição; Dividir usa só o painel de divisão).

8. **Lupa (F / D15):** botão de lupa em cada página do grid (e no painel) → abre
   `PdfPreviewModal` (pdf.js) renderizando a página em tamanho grande, com zoom +/−
   e navegação. Acessível (ESC, foco).

## 5. Critérios de aceite

- A: toda ação assíncrona mostra loader; some ao concluir/erro.
- B: preset "Baixa" gera arquivo visivelmente menor que "Alta" (teste de tamanho).
- C: o arquivo baixado tem o nome escolhido pelo usuário.
- D: card "Verificar Assinatura" aparece com esse texto.
- E: existem cards distintos "Editar" e "Dividir"; Editar sem edição de texto.
- F: clicar na lupa abre o visualizador ampliado da página.

## 6. Ordem de execução

1. Backend: qualidade (QualityPreset, OCR+Imagem→PDF JPEG) + nome no download + testes.
2. Frontend infra: Spinner + hook de busy.
3. Frontend: qualidade + nome nos painéis (OCR, Imagens, Split, Merge, Edit).
4. Frontend: separar cards Editar/Dividir + renomear Assinatura (i18n + Launcher + page).
5. Frontend: PdfPreviewModal (lupa) + botão no grid.
6. Validação: typecheck/lint/test/build + rebuild + E2E (tamanho, nome, lupa).

## 7. Riscos

- **R-Q1:** recompressão JPEG em página com texto fino pode borrar — mitigado pelo
  preset "Alta" (sem recompressão agressiva) e GRAY no OCR.
- **R-Q2:** nome de arquivo do usuário → sanitizar contra header injection e traversal.
- **R-F1:** render de página grande no modal consome memória — limitar zoom máx.

## 8. Ajuste pós-entrega — renomeação "Editar" → "Ordenar" (D16)

O card antes rotulado "Editar" realiza **ordenação/manuseio de posição de página**
(reordenar, girar, excluir, duplicar) — não edita o conteúdo. Renomeado para
**"Ordenar"** (EN: "Arrange") para refletir a função real. Mudança apenas de rótulo
i18n (`tab.edit`/`tool.edit.desc`); o `ToolId "edit"` e os componentes permanecem.

**Futuro (backlog):** um "Editar" de verdade — edição de texto/elementos do conteúdo
do PDF — será uma ferramenta separada (ex.: `ToolId "edit-content"`), fora do escopo atual.
