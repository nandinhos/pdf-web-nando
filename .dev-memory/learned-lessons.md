# Learned Lessons — pdf-web-nando

Lições técnicas persistidas para evitar recorrência e para abstrair aprendizado
entre a linha web (`pdf-web-nando`) e a linha nativa (`NAN-PDF`, fork do PdfCraft).

---

## Learned Lesson — mesa-zink-sem-gpu-ruido-startup — 2026-10-18

### Contexto
**Projeto**: NAN-PDF (fork Rust) — aplicável a qualquer app wgpu/egui/EGL
**Stack**: Rust, wgpu, egui/eframe, Mesa (Linux), Ubuntu 24.04 virtualizado
**Categoria**: environment

### O Que Aconteceu (OBSERVED)
Ao abrir a GUI desktop, o stderr mostrava:
`libEGL warning: failed to get driver name for fd -1`,
`MESA: error: ZINK: failed to choose pdev`,
`libEGL warning: egl: failed to create dri2 screen`.
A app, porém, inicializava e **funcionava** (edição/salvamento OK).

### Causa Raiz Identificada
Não há GPU acessível no ambiente: `/dev/dri` ausente (VM/WSL sem passthrough).
O Mesa tenta o backend **ZINK** (Vulkan-sobre-GL, exige device físico), falha, e
**recupera** para o rasterizador de software `llvmpipe`. O ruído é a tentativa
fadada *antes* do fallback — não é bug de código da aplicação.

### Solução Aplicada
Wrapper `nan-pdf` que define `LIBGL_ALWAYS_SOFTWARE=1` e `GALLIUM_DRIVER=llvmpipe`
**apenas quando `/dev/dri` não existe**, direcionando o Mesa ao software antes da
tentativa inviável. Com GPU presente, nada é forçado (preserva aceleração).
Evidência: `eglinfo` ruído **7 → 0**; app loga `renderer: wgpu; adapter: llvmpipe`.

### Como Detectar no Futuro
Mensagens `ZINK`/`libEGL`/`dri2 screen` no stderr + `ls /dev/dri` inexistente.
Falsificar "é bug do app" rodando `glxinfo`/`eglinfo` (terceiros): se mostram o
mesmo `llvmpipe`, a origem é o ambiente/Mesa.

### Como Prevenir
Checar `/dev/dri` antes de culpar o app. Usar o wrapper em ambientes sem GPU.
**Não** silenciar o WARN `Software rasterizer` (aviso legítimo de performance).

### Teste de Regressão
`pdfcraft/scripts-test-nan-pdf.sh` — PASS (`sem_fix=7 com_fix=0`).

### Referências
- commit `eea77375` em `nandinhos/pdfcraft`

### Tags
#rust #wgpu #egui #mesa #zink #gpu #environment #linux

---

## Learned Lesson — arquitetura-motor-edicao-portavel-pdfweb — 2026-10-18

### Contexto
**Projeto**: pdf-web-nando (Java/Spring + Next.js) ← abstrai do NAN-PDF (Rust)
**Stack**: PDFBox 3.0.3 (backend), pdf.js (frontend)
**Categoria**: architecture

### O Que Aconteceu (OBSERVED)
Estudando o NAN-PDF (fork PdfCraft), observei o **modelo de edição de texto
existente** que queremos trazer para o nosso projeto web. Fluxo real confirmado
executando a engine (`pdfcraft-cli`):

1. `text_paragraphs(doc, page)` → agrupa "runs" de texto adjacentes em **parágrafos
   visuais por linha** (bbox + texto + estilo). Em PDF digital bem-comportado
   retornou `count: 2` com os textos; em PDF gerado por Chrome retornou `count: 0`.
2. `text_lines(doc, page)` → granularidade por linha.
3. `text_edit_layout(...)` → **mede** o rascunho (rewrap) sem aplicar.
4. `text_edit(...)` → **aplica**: reescreve só as linhas que mudaram, preserva
   operadores/kerning das não tocadas; reusa a fonte original quando ela cobre os
   caracteres, senão substitui por Standard-14.
5. `content_list/content_update/content_delete` → itens *adicionados* (não o conteúdo
   original); `object_list/object_move` → mover parágrafos/imagens como blocos.

### Causa Raiz (insight arquitetural)
A edição "de alto nível" não é mágica: é **(a) detecção/agrupamento** de texto em
blocos editáveis + **(b) reescrita cirúrgica do content stream** preservando o que
não mudou. O ponto frágil universal é o **(a)**: PDFs não-tagueados (Chrome,
scanners, impressão) derrotam o agrupador → `count: 0` → "não dá para editar".
O NAN-PDF admite isso (Área C = 30% ready; doc: "geometry cannot infer author
intent in every untagged PDF").

### Solução Aplicada (plano de abstração para o pdf-web-nando)
Reimplementar o **mesmo modelo de duas etapas** na nossa stack, sem portar Rust:
- **(a) Detecção** — já iniciada: `LayoutAnalysisService` (Fase 1) usa
  `PDFTextStripper.writeString` (parágrafo por linha) + `PDFGraphicsStreamEngine`
  (bbox de imagem via CTM). Espelha o `text_paragraphs`.
- **(b) Edição** — nosso caminho atual (redação real + caixa de texto reescrita)
  é o equivalente pragmático do `text_edit`. Evoluir para reusar a fonte original
  embutida quando possível (hoje caímos em Helvetica — mesmo teto que o NAN-PDF).
- **Contrato** — expor como catálogo de operações (ideia 3.1 do relatório):
  `analyze-layout` (detectar blocos) → `content-edit` (aplicar), espelhando o par
  `text_paragraphs` → `text_edit`.

### Como Detectar no Futuro (limite a vigiar)
Se `analyze-layout` retorna 0 blocos num PDF que claramente tem texto, é PDF
não-tagueado. Classes a testar sempre: (1) texto nativo, (2) Office/LibreOffice,
(3) navegador/Chrome, (4) escaneado. A cobertura varia por classe — medir com
evidência antes de prometer "edição universal".

### Como Prevenir
Nunca prometer "editar qualquer PDF": declarar a matriz de cobertura por classe.
Para escaneados, o caminho é OCR→texto invisível→editar (NAN-PDF faz isso; nós
temos Tesseract). Para fidelidade de fonte, reembutir a fonte original é o próximo
degrau (não começar por ele — YAGNI até haver demanda).

### Teste de Regressão
Backend `LayoutAnalysisServiceTest` (detecção) + teste de edição com verificação
adversarial nos bytes (redação real não deixa resíduo).

### Referências
- `docs/ANALISE_pdfcraft.md` (relatório + 171 tools)
- `docs/PLAN_layout_blocks.md` (plano Fase 1/2/3)
- NAN-PDF: `crates/content`, `crates/edit`, `crates/render`; docs
  `editing-existing-text.md`

### Tags
#pdfbox #pdfjs #edicao-texto #layout-analysis #content-stream #arquitetura #nan-pdf
