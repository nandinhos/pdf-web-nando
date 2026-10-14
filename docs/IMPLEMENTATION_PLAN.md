# Plano de Implementação — pdf-web-nando

> Documento de engenharia derivado do `docs/PRD.md` (v1.0) e ancorado nas classes
> Java decompiladas do PDF24 Creator 11.31.1 (`projects/pdf-nando-toolkit/decompiled/`).
> **Fonte da verdade dos contratos de serviço:** APIs reais observadas nas classes de referência.

- **Versão do plano:** 1.1
- **Data:** 2026-10-13
- **Status:** Executado — Fases 0–5 concluídas e verificadas (ver §0.5)
- **Classificação de evidências:** cada afirmação técnica é marcada `OBSERVED` (lido em arquivo/comando), `INFERRED` (dedução fundamentada) ou `UNKNOWN` (sem evidência).

---

## 0.5 Status de execução (atualizado)

Todas as 6 fases foram implementadas e validadas (testes unitários + E2E via Docker).

| Fase | Entrega | Status | Verificação |
| --- | --- | --- | --- |
| 0 — Fundação | scaffolding, upload+viewer, infra, CI | ✅ | E2E: health 200, upload 201 |
| 1 — Páginas | split (5 modos), merge (4 modos), edit | ✅ | 24 testes; E2E split/merge/edit |
| 2 — Imagens | imagem→PDF, extração | ✅ | E2E round-trip 2 imgs↔PDF |
| 3 — OCR | tesseract sidecar, jobs @Async + SSE | ✅ | E2E OCR real (texto pesquisável) |
| 4 — Assinatura | verificação CMS/PKIX | ✅ | E2E assinado=válido / alterado=inválido |
| 5 — Polimento | hardening, i18n, tema, a11y, logs | ✅ | 39 testes; E2E rejeição magic bytes |

**Decisões consolidadas:** D1 (sem GhostScript), D4 (profundidade), D5 (síncrono vs
assíncrono), D6 (imagens JPG/PNG v1), D7 (OCR mínimo), D8 (tesseract na imagem),
D9 (R5/eIDAS: v1=system roots, evolução=LOTL cache), D10 (assinatura mínima),
D11 (polimento pragmático). **Backend: 39 testes verdes (1 skip = OCR real sem
binário no host).**

**Itens v2 registrados:** LTV/DSS e EU Trust List (assinatura), overlay de OCR
sobre vetor + deskew/PDF-A, TIFF/WebP/HEIC/SVG (imagens), persistência de jobs em
Redis, métricas Prometheus/tracing.

---

## 0. Sumário executivo e processo de abstração

### 0.1 O que este plano é

O PRD define **o quê** (6 famílias de ferramentas, stack, UX). Este plano define **o como e em que ordem**, com os contratos de cada serviço backend ancorados nas assinaturas Java reais extraídas por decompilação, não em suposição.

### 0.2 Processo de abstração aplicado

O PDF24 é um app **desktop monolítico Java** (classes stateful, `File`-based, `ProcessBuilder` para binários externos). Nosso produto é um **serviço web assíncrono**. A abstração central é:

```
Classe PDF24 (stateful, File-in/File-out, síncrona)
        │  extrair algoritmo + parâmetros (OBSERVED)
        ▼
Serviço Spring (stateless por request, storage-backed, job assíncrono)
        │  expor via
        ▼
Endpoint REST (fileId/jobId, envelope de erro, SSE de progresso)
```

**Regra de fidelidade:** reimplementamos o *algoritmo* e os *parâmetros* observados (ex.: os 5 modos de split, os 4 modos de merge), mas **não** copiamos código. Reescrevemos sobre PDFBox 3.x com nossas próprias classes. Ver PRD §11 (licenciamento).

### 0.3 Decisões consolidadas nesta sessão

| # | Decisão | Status |
| --- | --- | --- |
| D1 | **GhostScript NÃO entra na v1** (risco AGPL §11/R1). Render e compressão apenas via PDFBox/pdf.js. | ✅ decidido |
| D2 | Backend em Java (fidelidade PDFBox/BouncyCastle). | ✅ PRD R3 |
| D3 | Build do backend roda **via container Docker** (host não tem Java 21 nem Maven). | ✅ ver §0.4 |
| D4 | Profundidade: Fases 0 e 1 detalhadas; Fases 2–5 em nível macro. | ✅ escopo deste doc |

### 0.4 Toolchain do ambiente — `OBSERVED` (restrição de build)

Comando `java -version && node -v && docker --version` executado em 2026-10-13:

| Ferramenta | Versão observada | Situação |
| --- | --- | --- |
| Java (host) | **1.8.0_492** | ⚠️ PRD exige **Java 21**. Host incompatível. |
| Maven / Gradle | **ausentes** | ⚠️ não instalados no host. |
| Node | v22.22.1 | ✅ adequado para Next.js |
| npm / pnpm | 11.18.0 / 11.2.2 | ✅ |
| Docker | 29.8.2 | ✅ |
| Docker Compose | v5.5.1 | ✅ |

**Implicação (`INFERRED`):** o backend Java 21 **não compila no host diretamente**. O build e os testes do backend devem rodar dentro de container (imagem `eclipse-temurin:21` + Maven wrapper `mvnw` versionado no repo, ou build multi-stage no Dockerfile). O frontend roda nativo no host. Isto é um requisito de infraestrutura da Fase 0, não um bloqueio.

---

## 1. Mapa feature → classe de referência → serviço do projeto

Correlação entre PRD §1.3, APIs **OBSERVED** nas classes decompiladas, e os serviços a implementar.

| Fase | Feature | Classe ref. (OBSERVED) | API-chave observada | Serviço do projeto |
| --- | --- | --- | --- | --- |
| 1 | Dividir/extrair | `PdfSplitter` | 5 modos; `split()→List<OutFileInfo>` | `SplitService` |
| 1 | Mesclar | `PdfPageMerger` | 4 modos; `merge(destFile)` | `MergeService` |
| 1 | Editar (básico) | `PdfPageRemover` + `PdfPageMerger` | `removePages/keepPages`; reorder via `PageItem` | `EditService` |
| 2 | Imagem→PDF | `ImageToPdf` | `setDpi/PageSize/Orientation/Margin`, `addImage(...)` | `ImageToPdfService` |
| 2 | Extrair imagens | `PdfImageExtract` | `extractImages()`, `setPassJpg/Jp2ToJpg` | `ImageExtractService` |
| 3 | OCR | `OcrEngine` | async; `setLang/Dpi/GeneratePdfA` | `OcrService` (+ Tesseract sidecar) |
| 4 | Verificar assinatura | `signature/PdfSignatureVerifier` | fluente; `verify(pdf)→JsonObject` | `SignatureVerifyService` |
| v2 | Edição avançada | `PdfEditEngine` | motor JSON `execute(in,out,spec)`, 24 defs | (fora da v1) |

> **Nota sobre `PdfEditEngine` (`OBSERVED`):** é um motor único que recebe um JSON-spec e executa até 24 operações (`SplitDef`, `WatermarkDef`, `CropDef`, `NupDef`, `SignDef`, `DrawDef`, `OrganizerDef`...). Mapeia exatamente a divisão v1/v2 do PRD §4.3. **Para a v1 NÃO replicamos esse motor** — implementamos serviços discretos e simples (`EditService`). O motor JSON fica como referência arquitetural para a edição avançada v2.

---

## 2. Arquitetura-alvo (consolidada do PRD §5)

```
Frontend (Next.js/React/TS)            Backend (Spring Boot/Java 21)
  pdf.js (render)                        api/        controllers REST + SSE
  dnd-kit (reorder)         REST/JSON    service/    split|merge|edit|image|ocr|signature
  TanStack Query  ───────── + SSE ─────▶ core/       wrappers PDFBox 3.x / BouncyCastle
  shadcn/ui                              jobs/        fila + progresso + storage TTL
                                         │
                              Redis (jobs)   MinIO/S3 (artefatos TTL)   Tesseract (sidecar, Fase 3)
```

**Padrão de request (OBSERVED PRD §7):**
1. `POST /api/v1/files` → `{ fileId, pages, meta }` (upload chunked)
2. Operação leve → resposta síncrona. Operação pesada → `{ jobId }` + progresso SSE.
3. `GET /api/v1/jobs/{jobId}/result` → download do artefato.
4. Erros: envelope `{ error: { code, message, details } }`.

---

## 3. FASE 0 — Fundação (detalhada)

**Objetivo:** scaffolding funcional ponta-a-ponta: subir backend+frontend via `docker compose up`, fazer upload de um PDF e visualizá-lo com pdf.js. CI verde.

**Risk Dial:** MEDIUM. **Blast radius:** repositório inteiro (greenfield).

### 3.1 Estrutura de diretórios (cria conforme PRD §10)

```
pdf-web-nando/
├─ docs/
├─ frontend/
│  ├─ app/            (App Router)
│  ├─ components/
│  └─ lib/            (api client, hooks, pdf.js setup)
├─ backend/
│  ├─ src/main/java/org/pdfweb/
│  │  ├─ api/         (controllers)
│  │  ├─ service/
│  │  ├─ core/        (wrappers PDFBox)
│  │  └─ jobs/
│  ├─ src/test/
│  ├─ pom.xml
│  └─ mvnw, mvnw.cmd, .mvn/   (Maven wrapper — resolve ausência de mvn no host)
├─ infra/
│  ├─ docker-compose.yml
│  ├─ backend.Dockerfile      (multi-stage: temurin:21 build → runtime)
│  ├─ frontend.Dockerfile
│  └─ nginx/
├─ .github/workflows/ci.yml
├─ THIRD_PARTY_NOTICES.md     (PRD §11)
└─ README.md
```

### 3.2 Backend — bootstrap

| Item | Decisão | Evidência |
| --- | --- | --- |
| Java | 21 (toolchain no Dockerfile) | PRD §6; host tem 1.8 → build em container (§0.4) |
| Build | Maven + wrapper `mvnw` versionado | host sem mvn (OBSERVED §0.4) |
| Framework | Spring Boot 3.x (web, actuator, validation) | PRD §6 |
| PDF core | `org.apache.pdfbox:pdfbox:3.0.x` | OBSERVED: imports `org.apache.pdfbox.Loader`, `multipdf.PDFCloneUtility` nas classes ref |
| Jobs/estado | Spring Data Redis | PRD §5.1 |
| Storage | SDK S3 (MinIO compat.) | PRD §5.1 |
| Teste | JUnit 5 + fixtures PDF geradas por PDFBox | PRD §6 |
| Lint | Spotless + Checkstyle | PRD §6 |

**Entregáveis Fase 0 backend:**
- `GET /api/v1/health` (actuator) responde 200.
- `POST /api/v1/files` aceita multipart, persiste no storage, retorna `{fileId, pages, meta}` (lê `numberOfPages` via `Loader.loadPDF`).
- `GET /api/v1/files/{fileId}/pages` retorna metadados por página (tamanho, rotação).
- Envelope de erro global (`@ControllerAdvice`).
- Classe `core/PdfDocuments` (wrapper fino de `Loader.loadPDF` + fechamento seguro — espelha o padrão `Loader.loadPDF((File)f,(String)pwd)` OBSERVED em todas as classes ref).

### 3.3 Frontend — bootstrap

- Next.js (App Router) + TS + Tailwind + shadcn/ui.
- `lib/apiClient.ts` (fetch tipado para o envelope de erro).
- Setup pdf.js (worker configurado) + componente `PdfViewer` com zoom/navegação.
- Upload chunked (`POST /files`) + render da primeira página.
- TanStack Query para estado de servidor.
- Base i18n (PT-BR/EN) e tema claro/escuro (PRD §8).

### 3.4 Infra

- `docker-compose.yml`: serviços `backend`, `frontend`, `redis`, `minio`, `nginx`.
- `backend.Dockerfile` multi-stage (`eclipse-temurin:21` build com `./mvnw package` → runtime slim).
- Nginx como reverse proxy (`/api` → backend, `/` → frontend).
- `.env.example` com limites (tamanho máx. 500MB — PRD §9) e TTL de artefatos.

### 3.5 CI (GitHub Actions)

- Jobs: `backend-build-test` (dentro de container Java 21), `frontend-build-lint-test`, `docker-build`.
- **Gate (steering 01):** push proibido sem suíte 100% verde.

### 3.6 Critérios de aceite Fase 0

1. `docker compose up` sobe todos os serviços sem erro.
2. Upload de um PDF retorna `fileId` e nº de páginas correto.
3. Frontend renderiza a primeira página do PDF enviado.
4. CI verde (build+test+lint backend e frontend).
5. `THIRD_PARTY_NOTICES.md` presente com PDFBox/BouncyCastle/Tesseract.

---

## 4. FASE 1 — Páginas (detalhada)

**Objetivo:** split, merge e edição básica (reordenar/rotacionar/remover) com drag-and-drop. É o "maior valor, menor risco" do PRD §12.

**Risk Dial:** MEDIUM. **Blast radius:** `backend/service/{split,merge,edit}`, `backend/core`, endpoints `/split /merge /edit`, UI de grid de páginas.

### 4.1 Núcleo compartilhado — `core/PageMerger` (ancorado em `PdfPageMerger`)

`PdfPageMerger` é o motor que **split e edit também usam** (OBSERVED: `PdfSplitter` instancia `PdfPageMerger` internamente). Implementar primeiro.

Comportamento a reimplementar (OBSERVED em `PdfPageMerger.merge`):
- Modelo `PageItem { file, password, pageNum, rotate, cropBox }`.
- `merge(dest)`: para cada item → `destDoc.importPage(srcPage)`, copiando **mediaBox, cropBox, rotation, resources**; aplica `cropBox` e `rotate` por página (`normRot((rot+delta)%360)`).
- Remove chaves `THUMB`, `B`, `PIECE_INFO` de cada página (OBSERVED).
- Preserva **outlines/bookmarks** e **links internos** remapeando destinos para as páginas clonadas (OBSERVED `filterAnnot`/`addOutlineItems`).
- Pós-processo: `OrphanedPagesKiller` + `PdfPageOptimizer`; `setOpenAction(null)`; versão = máx. das versões de origem.
- `implements Closeable` — fecha todos os `PDDocument` carregados.

> **Dependência (`UNKNOWN`→resolver na implementação):** `PdfPageMerger` usa helpers `PdfCloneUtil`, `OrphanedPagesKiller`, `PageRange`, `StringHelper` (também decompilados em `pdf-nando-toolkit`). Precisamos reimplementá-los ou substituir por equivalentes PDFBox 3.x nativos. Avaliar `PDFMergerUtility`/`PDFCloneUtility` nativos antes de reescrever (escada Ponytail: "existe API nativa?").

### 4.2 `SplitService` (ancorado em `PdfSplitter`)

**5 modos OBSERVED** (o PRD §4.1 cita 3; o plano usa os 5 reais):

| Modo (constante) | Comportamento OBSERVED |
| --- | --- |
| `pagesPerPdf` | N páginas por arquivo; último recebe o resto. |
| `custom` / splitPoints | pontos de corte `"1,3,5"` via parse de int-list; gera faixas. |
| `evenOdd` | separa páginas pares e ímpares em 2 arquivos. |
| `halvePages` / `cutInHalf` | divide cada página ao meio via `cropBox` (respeita rotação). |

- Entrada: `fileId`, `mode`, `pagesPerPdf?`, `splitPoints?`, `password?`.
- Saída: lista de artefatos com nome zero-padded por total (OBSERVED `makeName`).
- Operação pode ser pesada (muitos arquivos) → **assíncrona (jobId)**.
- Endpoint: `POST /api/v1/split`.

### 4.3 `MergeService` (ancorado em `PdfPageMerger`)

**4 modos OBSERVED:**

| Método OBSERVED | Comportamento |
| --- | --- |
| `initWithMethodConcatenate` | concatena todos os arquivos em ordem. |
| `initWithMethodAlternate` | intercala páginas (digitalização frente/verso). |
| `initWithMethodOddEvenReverse` | **exige exatamente 2 arquivos**; 2º é lido em ordem reversa. |
| `addPageRange(file, "1-3,5")` | faixas por arquivo via `PageRange.parse`. |

- Entrada: `{ items:[{fileId, ranges?, password?}], mode, docInfo? }`.
- Validação crítica: modo `oddEvenReverse` → exatamente 2 itens (OBSERVED lança `IllegalArgumentException`).
- Endpoint: `POST /api/v1/merge`. Assíncrono.

### 4.4 `EditService` (ancorado em `PdfPageRemover` + `PageMerger`)

v1 (PRD §4.3): reordenar, remover, rotacionar, duplicar, inserir página em branco.

| Operação | Implementação ancorada |
| --- | --- |
| remover | `PdfPageRemover.removePages(String/int[], dest)` (OBSERVED). |
| manter só | `keepPages(...)` (OBSERVED). |
| reordenar | sequência de `PageItem` na ordem desejada → `PageMerger.merge` (reusa 4.1). |
| rotacionar | `PageItem.rotate` por página (OBSERVED, `normRot`). |
| duplicar | adicionar o mesmo `PageItem` duas vezes (OBSERVED no `halvePages`). |
| inserir em branco | nova `PDPage(mediaBox)` na posição. |

- Entrada: `{ fileId, ops:[{type: reorder|rotate|delete|insert|duplicate, ...}] }` (OBSERVED PRD §7).
- Endpoint: `POST /api/v1/edit`. Pode ser síncrono para PDFs pequenos.

### 4.5 Frontend Fase 1

- Grid de páginas com thumbnails **virtualizados** (performático p/ 100+ páginas — PRD §8).
- `dnd-kit`: arrastar para reordenar; seleção múltipla; rotacionar/excluir inline.
- Painéis contextuais por ferramenta (split/merge/edit) com os parâmetros reais dos modos acima.
- Preview do resultado antes de confirmar; progresso via SSE para jobs.

### 4.6 Testes Fase 1 (fixtures determinísticas — steering 05)

Gerar PDFs sintéticos com PDFBox (sem fixtures externas, determinístico):
- **Split**: PDF 10p + `pagesPerPdf=3` → 4 arquivos (3/3/3/1); conteúdo e orientação preservados (aceite PRD §4.1).
- **Merge**: A(2p)+B(3p) concatenar → 5p em ordem; `alternate` intercala; `oddEvenReverse` com ≠2 arquivos → erro.
- **Edit**: reorder reflete no export; rotação por página persiste; delete remove a página certa.
- E2E (Playwright): upload → reorder drag-and-drop → export → download.

### 4.7 Critérios de aceite Fase 1

Todos os critérios do PRD §4.1/§4.2/§4.3 passam, com testes verdes no CI.

---

## 5. FASES 2–5 (nível macro)

### 5.1 Fase 2 — Imagens (detalhada)

**Objetivo:** converter imagens em PDF (imagem→PDF) e extrair imagens embutidas
de um PDF (PRD §4.5). **Risk Dial:** MEDIUM. Operações síncronas (D5).
**Blast radius:** `backend/service/{image}`, `backend/core`, endpoints
`/images/to-pdf` e `/images/extract`, UI (nova aba "Imagens").

#### 5.1.1 Dependências novas (backend)

- `pdfbox-tools` (ImageIOUtil — escrita de imagens): OBSERVED usado por ambas as classes.
- `commons-imaging` (EXIF orientation): OBSERVED em `ImageToPdf.getExifOrientation`.
- TwelveMonkeys ImageIO (TIFF/WebP) — PRD §1.3. **Decisão v1 (D6):** suportar
  **JPG/PNG** no núcleo (ImageIO nativo). TIFF/WebP exigem TwelveMonkeys; adicionar
  só se os plugins resolverem no runtime do container. HEIC/SVG ficam **fora da v1**
  (OBSERVED: o próprio PDF24 lança "not implemented" para esses).

#### 5.1.2 `ImageToPdfService` (ancorado em `ImageToPdf`, OBSERVED)

Reimplementa o algoritmo de layout observado em `calcPageValues`:
- Parâmetros: `dpi` (padrão 150), `pageSize` (padrão A4), `pageOrientation`
  (`auto`/`landscape`/`portrait`, padrão portrait), `margin` (pt), `usePageSize`
  (padrão true), `addBookmarks`.
- Para cada imagem: cria `PDImageXObject` (`LosslessFactory`/`createFromByteArray`),
  calcula geometria (modo página A4 escalada OU tamanho real por DPI), aplica
  matriz de orientação EXIF (casos 2–8) + rotação, desenha via `PDPageContentStream`.
- `mmToPt`/`normRotate` reimplementados.
- Entrada REST: `{ imageIds:[...], pageSize?, orientation?, dpi?, margin?, usePageSize? }`.
- Saída: 1 PDF (um `PDPage` por imagem). Endpoint `POST /images/to-pdf`.

#### 5.1.3 `ImageExtractService` (ancorado em `PdfImageExtract`, OBSERVED)

- Percorre recursivamente `PDResources` de cada página → `PDImageXObject`
  (desce em `PDFormXObject`); `seen` evita reprocessar o mesmo objeto COS.
- Opções: `passJpg` (padrão true — JPG RGB/gray extraído sem recodificar via
  `createInputStream(stopFilters)`), `passJp2` (padrão false), `jp2ToJpg` (padrão true).
- Sufixo/format por tipo; máscara/softmask → PNG. Dedup por conteúdo.
- `makeImageFile(index, suffix)` → delega ao `ArtifactStorage`.
- Entrada REST: `{ fileId, passJpg?, passJp2?, jp2ToJpg? }`.
- Saída: lista de artefatos (como no split). Endpoint `POST /images/extract`.

#### 5.1.4 Testes (fixtures determinísticas)

- **ImageToPdf**: gerar `BufferedImage` em memória (JPG/PNG via ImageIO) →
  converter → asserir nº de páginas = nº de imagens e dimensões da página.
- **ImageExtract**: gerar um PDF com uma imagem embutida (via PDFBox) → extrair →
  asserir que ≥1 arquivo de imagem é produzido e é legível por ImageIO.

#### 5.1.5 Critérios de aceite (PRD §4.5)

- Conjunto de imagens vira PDF com 1 imagem por página.
- Extração recupera as imagens de um PDF (todas as suportadas).

#### 5.1.6 Frontend

- Nova aba "Imagens": (a) upload de N imagens → imagem→PDF com opções; (b) upload
  de 1 PDF → extrair imagens, listando os downloads. Reusa upload existente.

### 5.2 Fase 3 — OCR (detalhada)

**Objetivo:** aplicar OCR a um PDF escaneado, gerando um **PDF pesquisável**
(camada de texto) e um **TXT** (PRD §4.4). **Risk Dial:** MEDIUM→HIGH (processo
externo + assíncrono). **Blast radius:** `backend/service/ocr`, `backend/jobs`,
novo container Tesseract, endpoints `/ocr` + `/jobs/*`, UI (aba "OCR").

#### 5.2.1 Algoritmo OBSERVED (`OcrEngine`)

- Fluxo real: para cada página → `PDFRenderer.renderImageWithDPI(i, dpi, GRAY)` →
  grava PNG → `tesseract <img> <base> -l <lang> -c textonly_pdf=1 --dpi <dpi>
  --oem 3 --psm 1 pdf txt` → gera `.pdf` (camada de texto) + `.txt` →
  `PdfOverlayer` sobrepõe a camada ao original (FOREGROUND).
- Defaults OBSERVED: `lang="eng"`, `dpi=300`, `skipPagesWithText=true`,
  `oem 3`, `psm 1`. `MAX_SKEW_ANGLE=15`.
- Pré-processamento opcional: `unpaper` (deskew/clean), `AutoPageRotation`,
  remove background, PDF/A via `PdfDistiller`.

#### 5.2.2 Decisão de escopo v1 (D7 — OCR mínimo robusto)

A reimplementação fiel do `PdfOverlayer` (camada de texto `textonly_pdf` sobre o
original vetorial) é complexa. **Decisão v1:**
- Rodar o tesseract **sem** `textonly_pdf` → ele produz um **PDF pesquisável
  completo** (imagem rasterizada + texto invisível) por página.
- Mesclar as páginas com `PDFMergerUtility` (nativo PDFBox) → PDF pesquisável final.
- Gerar o TXT concatenando a saída `.txt` por página.
- **Fora da v1:** overlay sobre vetor original, deskew/unpaper, auto-rotate,
  remove-background, PDF/A, skipPagesWithText. São refinamentos — não requisito do
  aceite §4.4. Registrados como v2.
- **Consequência:** páginas ficam rasterizadas a `dpi` (típico e aceitável para
  documentos escaneados, que é o caso de uso do OCR).

#### 5.2.3 Tesseract como sidecar (PRD §5.3)

- Novo serviço no compose: imagem com `tesseract-ocr` + idiomas `eng`+`por`
  (pacotes `tesseract-ocr-eng`/`-por`). **Não** redistribuímos tessdata do PDF24 (§11).
- Invocação via `ProcessBuilder` (análogo ao `CommandRunner` OBSERVED). O backend
  chama o tesseract; como ambos estão em containers, ou (a) o backend tem o binário
  instalado na sua própria imagem, ou (b) sidecar separado via exec remoto.
  **Decisão (D8):** instalar `tesseract-ocr` **na própria imagem do backend** —
  mais simples que RPC para um sidecar, e o `ProcessBuilder` do OBSERVED é local.
  Idiomas via pacotes apt. Reavaliar sidecar se o isolamento for exigido.

#### 5.2.4 Jobs assíncronos + SSE (D5 concretizado)

OCR é CPU-bound e demorado → aqui o modelo assíncrono entrega valor (PRD §7):
- `POST /ocr` → cria `jobId`, enfileira, retorna 202.
- `GET /jobs/{jobId}` → status/progresso.
- `GET /jobs/{jobId}/events` → **SSE** (progresso por página).
- `GET /jobs/{jobId}/result` → download do PDF pesquisável (TXT via query/segundo artefato).
- **Implementação v1:** executor em memória (Spring `@Async` + registro de jobs em
  mapa concorrente) + emissor SSE. Redis provisionado para evoluir a fila
  (persistência/multi-instância) depois — não necessário para instância única.

#### 5.2.5 Testes

- Unit: montagem de argumentos do tesseract; parsing de progresso; merge do PDF.
- Integração condicional: se o binário `tesseract` existir no ambiente de teste,
  rodar um OCR real sobre uma imagem com texto conhecido e asserir que o PDF
  resultante tem texto extraível (via `PDFTextStripper`). Caso contrário, **skip**
  com mensagem (teste determinístico não pode depender de binário ausente).

#### 5.2.6 Critérios de aceite (PRD §4.4)

- PDF escaneado vira PDF com texto selecionável; idioma `por` reconhecido.

### 5.3 Fase 4 — Verificação de assinatura
### 5.3 Fase 4 — Verificação de assinatura (detalhada)

**Objetivo:** verificar assinaturas digitais de um PDF e produzir um relatório
JSON de confiança (PRD §4.6). **Risk Dial:** HIGH (criptografia). Síncrono
(retorna o relatório direto — PRD §7). **Blast radius:** `backend/service/signature`,
endpoint `/signatures/verify`, UI (aba "Assinatura").

#### 5.3.1 Algoritmo OBSERVED (`PdfSignatureVerifier` + 4 classes auxiliares)

- `verify(pdf) → JsonObject` com, por assinatura: `kind` (signature/certification/
  documentTimestamp), `coversWholeFile` (cobertura do ByteRange), `algorithms`,
  `chain` (cadeia PKIX), `timestamp`+`timestampCheck` (TSP), `pades` (PAdES),
  `embeddedRevocation` (OCSP/CRL do DSS — LTV), `changes`/`violations` (alterações
  pós-assinatura).
- Trust anchors: `systemRoots` (cacerts da JVM) + `addTrustedCertificates(file)` +
  (opcional) EU Trust List. Validação CMS/PKIX via **BouncyCastle**.
- `EuTrustList` (OBSERVED): baixa a LOTL `https://ec.europa.eu/tools/lotl/eu-lotl.xml`,
  segue ponteiros para TSLs por país, valida XML-DSig e extrai CAs qualificadas
  (status `granted`/`undersupervision`/...).

#### 5.3.2 Decisão R5 / eIDAS (D9)

A obtenção da EU Trust List tem 4 caminhos (a: on-demand, b: cache agendado,
c: eIDAS opcional desligado na v1, d: lib ETSI DSS).
**Decisão v1 = (c):** verificação completa com **system roots + certificados
fornecidos** (`addTrustedCertificates`); o relatório inclui `eidasQualified` que
fica `false`/`unknown` até a Fase b. Justificativa: entrega §4.6 sem o maior risco
e **sem rede externa a cada request** (privacidade — PRD §2.2 O4). Evolução
planejada = **(b)** fetch+cache diário da LOTL como job agendado, ativando
`eidasQualified`. R5 passa de PENDENTE a **decidido (v1=c, evolução=b)**.

#### 5.3.3 Escopo v1 (D10 — verificação mínima robusta)

Reimplementar fielmente as 5 classes (~900 linhas, XML-DSig da LOTL, DSS/LTV
completo, detecção fina de alterações) é caro e de alto risco. **v1 entrega o
núcleo do aceite §4.6:**
- Detectar campos de assinatura (`PDSignature`), extrair CMS (`contents`) e o
  conteúdo assinado (ByteRange).
- Validar o **digest/assinatura CMS** com BouncyCastle (`CMSSignedData`).
- **Cobertura do ByteRange**: `coversWholeFile` (assinatura cobre o arquivo todo?).
- Extrair **signatário (CN)**, **data de assinatura**, **algoritmos**, **cadeia**
  (validação PKIX contra system roots + certificados fornecidos).
- Relatório JSON: `{ signatures:[{ name, coversWholeFile, valid, signerCN,
  signedAt, chainTrusted, eidasQualified }], valid }`.
- **Fora da v1 (v2):** LTV/DSS (OCSP/CRL embutidos), timestamp TSP detalhado,
  detecção fina de alterações por revisão, EU Trust List. Registrados como v2.

#### 5.3.4 Dependências

- `org.bouncycastle:bcpkix-jdk18on` + `bcprov-jdk18on` (CMS/PKIX) — PRD §6.

#### 5.3.5 Testes

- Gerar um par de chaves + certificado self-signed (BouncyCastle), **assinar** um
  PDF (PDFBox `addSignature`), e verificar: assinatura válida, `coversWholeFile=true`,
  `signerCN` correto.
- Alterar bytes após assinar → verificar `valid=false`/`coversWholeFile=false`.

#### 5.3.6 Critérios de aceite (PRD §4.6)

- PDF assinado válido retorna "válido" com CN e timestamp; PDF alterado após
  assinar retorna "inválido/cobertura parcial".

#### 5.3.7 Frontend

- Aba "Assinatura": upload de PDF → exibe o relatório (válida/inválida, CN, data,
  cadeia confiável, cobertura).

### 5.4 Fase 5 — Polimento

#### 5.4.1 Escopo pragmático (D11 — polimento de maior valor)

Fase 5 é transversal e ilimitada. **v1 entrega os itens que fecham os critérios
globais §13 e PRD §8/§9 com maior valor e menor risco:**

1. **Hardening de upload (PRD §9):** validar o **tipo real por magic bytes**
   (não pela extensão) — PDF (`%PDF`) e imagens (PNG/JPEG). Rejeita conteúdo que
   não corresponde ao tipo declarado. Segurança concreta.
2. **i18n PT-BR/EN (PRD §8):** dicionário + React Context + alternador. Sem lib
   pesada (volume de texto baixo — escada Ponytail).
3. **Tema claro/escuro:** alternador explícito (hoje só segue o SO via CSS).
4. **A11y (PRD §8):** foco visível, landmarks/roles, teste mínimo de a11y.
5. **Observabilidade (PRD §9):** log estruturado por operação (filtro HTTP) com
   método, rota, status e duração.

**Fora da v1 (não justificado agora):** métricas Prometheus/tracing distribuído
completos; i18n com biblioteca (next-intl). Registrados como evolução.

---

## 6. Riscos e pendências (herdados do PRD §14 + descobertos)

| # | Item | Status | Origem |
| --- | --- | --- | --- |
| R1 | GhostScript (AGPL) | **Resolvido: fora da v1 (D1)** | PRD |
| R5 | Fonte da EU Trust List (eIDAS) | **PENDENTE** — definir na Fase 4 | PRD |
| N1 | Host sem Java 21/Maven → build só em container | mitigado via Dockerfile + `mvnw` | §0.4 (OBSERVED) |
| N2 | Helpers decompilados (`PdfCloneUtil`, `OrphanedPagesKiller`, `PageRange`, `StringHelper`) precisam ser reimplementados ou substituídos por API PDFBox nativa | **Resolvido na Fase 1**: `PageRanges` reimplementado; `PageMerger` usa `importPage` nativo; outlines/links não replicados na v1 (refinamento futuro) | §4.1 |
| N3 | Compressão de PDF sem GhostScript (D1) — avaliar capacidade real do PDFBox | PENDENTE se compressão entrar no escopo | §5.1 |

### 6.1 Decisão de arquitetura — síncrono vs. assíncrono (D5)

**Decisão:** as ferramentas da **Fase 1 (split/merge/edit) são SÍNCRONAS** — o endpoint
processa e retorna o(s) artefato(s) na própria resposta, sem fila nem SSE. O
modelo de **jobs assíncronos + SSE** (PRD §5.1/§7) é **movido para a Fase 3 (OCR)**.

**Justificativa (evidência):**
- Split/merge/edit são operações de **manipulação de páginas** (clonagem de objetos
  COS via `importPage`), não de rasterização/render. O custo é I/O-bound e rápido,
  dentro do alvo P95 < 2s do PRD §9 para operações leves.
- O OCR (Fase 3, `OcrEngine`) é CPU-bound e demorado (render DPI 300 + Tesseract por
  página) — tem API assíncrona nativa (`run`/`estimateTimeToFinish`) e é onde o
  job+SSE entrega valor real (progresso visível).
- Introduzir fila/estado/SSE agora seria over-engineering (escada Ponytail): não há
  gargalo observado para justificar a complexidade na Fase 1.

**Consequência:** o contrato REST do PRD §7 (`jobId` + SSE) permanece válido e será
implementado na Fase 3; os endpoints da Fase 1 retornam o resultado direto
(`/results/{id}` para download). Redis já está provisionado na infra para quando a
fila entrar.



---

## 7. Ordem de execução recomendada

```
Fase 0 (fundação)
  └─▶ Fase 1  (core/PageMerger → Split → Merge → Edit → UI grid)   ← maior valor
        └─▶ Fase 2 (imagens)
              └─▶ Fase 3 (OCR + Tesseract sidecar)
                    └─▶ Fase 4 (verificação de assinatura)
                          └─▶ Fase 5 (polimento, a11y, i18n, hardening)
```

Dentro da Fase 1, a ordem interna é obrigatória: **`core/PageMerger` primeiro**, pois Split e Edit dependem dele (OBSERVED).

---

## 8. Rastreabilidade (claim ↔ evidência)

- Todos os contratos de serviço das §4 e §5 citam a classe de referência e método **OBSERVED** por leitura direta em `projects/pdf-nando-toolkit/decompiled/org/pdf24/tools/commons/`.
- Toolchain da §0.4 **OBSERVED** por execução de `java -version`, `node -v`, `docker --version`.
- Itens não verificados estão explicitamente marcados `UNKNOWN`/`PENDENTE` (R5, N2, N3).
- Nenhum método, classe ou endpoint foi inventado: os endpoints vêm do PRD §7; as APIs, das classes decompiladas.
