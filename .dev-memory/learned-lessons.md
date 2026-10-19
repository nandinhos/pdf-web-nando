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

---

## Learned Lesson — deduplicacao-cosname-e-monotonicidade-compressao-pdf — 2026-10-19

### Contexto
**Projeto**: pdf-web-nando (Backend Java / Spring Boot 3)
**Stack**: Apache PDFBox 3.0.3, Java ImageIO, PDF / Image Streams
**Categoria**: bug / logic

### O Que Aconteceu (OBSERVED)
No painel de Compressão de PDF, um documento escaneado de 43 páginas (~9.9 MB / 10.428.356 bytes):
- Modo **Forte** (LOW) reduzia 33% (para 6.7 MB / 6.987.296 bytes) porque disparava a 2ª passada (rasterização global via `PDFRenderer`).
- Modo **Recomendada** (MEDIUM) e **Leve** (HIGH) quase não alteravam o tamanho e até aumentavam ligeiramente:
  - Recomendada: 10.440.598 bytes (+12 KB / exibido como 10.0 MB).
  - Leve: 10.488.064 bytes (+59 KB / exibido como 10.0 MB).

### Causa Raiz Identificada
1. **Deduplicação Falha por Identificador Local de Página (`COSName`):** O loop de imagens usava `Set<COSName> processed = new HashSet<>()`. Em PDFs multipáginas (típicos de scanners), cada página tem seu próprio dicionário `/Resources` e costuma nomear sua imagem local como `/Im1`. Ao processar a página 1, `processed.add(/Im1)` fez com que as outras 42 páginas fossem sumariamente ignoradas (`if (processed.contains(name)) continue;`).
2. **Recompressão Inflada sem Verificação de Tamanho:** A única imagem processada era decodificada e recodificada forçando `TYPE_INT_RGB` (3 canais) com parâmetros JPEG superiores aos do scanner de origem (que era Grayscale de 1 canal), resultando em um stream de bytes maior que o original. O serviço substituía incondicionalmente (`resources.put(name, compressedImg)`), sem checar se os novos bytes eram menores que o stream original.
3. **Ausência de Invariante de Monotonicidade:** O serviço permitia que um PDF comprimido resultasse maior que a entrada original, sem salvaguarda de fallback.

### Solução Aplicada
1. **Rastreamento por Objeto Físico:** Substituição de `Set<COSName>` por `Set<COSBase> processedObjects`, rastreando `img.getCOSObject()`. Todas as 43 páginas passam a ser processadas.
2. **Preservação de Grayscale / Monocromático:** Detecção de imagens de 1 canal (`img.getColorSpace().getNumberOfComponents() == 1` ou `bim.getType() == TYPE_BYTE_GRAY`), gerando `BufferedImage.TYPE_BYTE_GRAY` para evitar o triplo de dados do RGB.
3. **Regra de Ouro (Validação de Bytes Exatos):** Codificação determinística em memória via `encodeJpeg(targetBim, jpegQuality)`. A substituição do XObject no recurso só ocorre se `origStreamLen <= 0 || jpegBytes.length < origStreamLen`.
4. **Salvaguarda de Monotonicidade no Documento:** Se `compressedSize >= originalSize` após todas as passadas, restaura o arquivo original exato (`Files.copy(pdf, dest)`), garantindo que um PDF comprimido nunca seja pior/maior que a entrada.
5. **Resultados Comprovados no Documento Real de 9.9 MB:**
   - Forte (LOW): 9.9 MB → **4.09 MB (-61%)**.
   - Recomendada (MEDIUM): 9.9 MB → **6.47 MB (-38%)**.
   - Leve (HIGH): 9.9 MB → **9.9 MB (-0%)**, salvaguarda atuou e impediu o aumento.

### Como Detectar no Futuro
- Comparar `compressedSize` com `originalSize` em testes de integração de compressão.
- Se a redução for ~1/N do esperado em documentos multipáginas escaneados, verificar se a deduplicação está usando chaves de recursos locais (`COSName`) em vez de identificadores físicos (`COSBase` / object number).

### Como Prevenir
1. **Nunca** usar `COSName` para deduplicação entre páginas em PDFBox. Use o objeto físico de stream `COSBase` (`img.getCOSObject()`).
2. **Sempre** calcular os bytes codificados antes de substituir streams existentes: `if (newBytes.length < origBytes)`.
3. Preservar o espaço de cor original (Grayscale deve permanecer Grayscale).
4. Em qualquer ferramenta de compressão, estabelecer o invariante inegociável: `assert compressedSize <= originalSize`.

### Teste de Regressão
- `CompressServiceTest.compressReducesOrPreservesPdfWithImage` (testa presets low, medium, high).
- `CompressServiceTest.compressNeverIncreasesFileSizeEvenWithAlreadyCompressedPdf` (garante monotonicidade em PDFs pequenos ou já comprimidos).

### Tags
#pdfbox #compressao #jpeg #deduplicacao #cosname #cosbase #monotonicidade #spring-boot
