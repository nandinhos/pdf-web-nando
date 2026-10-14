# PRD — PDF Web (pdf-web-nando)

> **Product Requirements Document**
> Aplicação web de alto nível para manipulação interativa de PDFs, com UI/UX
> avançada. O escopo funcional é derivado de engenharia reversa estática do
> **PDF24 Creator 11.31.1**, reimplementando as ferramentas em uma stack web
> moderna e própria.

- **Projeto:** `pdf-web-nando`
- **Versão do PRD:** 1.0
- **Status:** Draft para kickoff em nova sessão
- **Autor:** definido via sessão de engenharia reversa (REA + Ghidra + CFR)
- **Data:** 2026-10-13

---

## 0. Como usar este documento

Este PRD é **autossuficiente**: contém todo o contexto necessário para iniciar o
desenvolvimento numa sessão nova, sem depender do histórico de chat anterior.
A seção [1](#1-contexto-e-origem) resume de onde vieram os requisitos; as demais
seções definem o produto. Decisões ainda em aberto estão marcadas com
**[DECISÃO PENDENTE]**.

---

## 1. Contexto e origem

### 1.1 Como os requisitos foram levantados

As funcionalidades deste produto foram mapeadas por **engenharia reversa estática**
do instalador do PDF24 Creator, realizada localmente em containers Docker isolados:

1. Análise do bootstrapper `pdf24-creator-installer.exe` (PE32 C++/MSVC) com
   **REA + Ghidra 12.1.4** → descobriu que baixa o instalador completo de
   `download.pdf24.org`.
2. Download isolado e análise do instalador completo (`pdf24-creator-latest-x64.exe`,
   372 MB, **Inno Setup 6.3.0**) com `innoextract` → inventário de 2518 arquivos.
3. Decompilação (CFR 0.152) do `pdf24-tools-commons-1.0.0.jar` → leitura da
   lógica Java real de cada ferramenta.

### 1.2 Arquitetura do PDF24 (referência — NÃO é o que vamos copiar)

O PDF24 usa: launchers **C++/MSVC** → **OpenJDK 17** embutido → aplicação **Java**
com **Apache PDFBox 3.0.8** + **BouncyCastle 1.84**; **Tesseract** (OCR) e
**GhostScript** (render/compressão) como processos externos; UI em **WebView2**.

> **Importante:** vamos **reimplementar as funcionalidades**, não redistribuir
> binários ou código do PDF24. Ver [seção 11 — Licenciamento](#11-licenciamento-e-conformidade).

### 1.3 Mapa feature → implementação de referência (classes decompiladas)

| Feature | Classe de referência (PDF24) | Base técnica observada |
| --- | --- | --- |
| Extrair/dividir páginas | `PdfSplitter` | PDFBox `PDDocument`/`PDPage` |
| Mesclar páginas | `PdfPageMerger` | PDFBox `PDFMergerUtility`/`PDFCloneUtility` |
| Editar PDF | `PdfEditEngine` | PDFBox COS/PDModel |
| OCR | `OcrEngine` | render PDFBox → Tesseract (processo) |
| Assinatura (verificação PAdES/LTV) | `signature/PdfSignatureVerifier` | PDFBox + BouncyCastle (CMS, DSS, TSP, eIDAS) |
| Imagens (extrair / imagem→PDF) | `PdfImageExtract`, `ImageToPdf`, `ImageUtils` | PDFBox + TwelveMonkeys ImageIO |

---

## 2. Visão e objetivos

### 2.1 Visão

Oferecer uma aplicação web onde o usuário manipula PDFs de forma **nativa,
interativa e visual** — pré-visualização fiel, operações diretas sobre as páginas
(arrastar, reordenar, selecionar) e um conjunto de ferramentas profissionais
(dividir, mesclar, editar, OCR, imagens, verificação de assinatura).

### 2.2 Objetivos de produto

- O1. Entregar as 6 famílias de ferramentas da [seção 4](#4-escopo-funcional) com paridade funcional ao PDF24.
- O2. UI/UX de alto nível: pré-visualização interativa, manipulação de páginas por gestos, feedback de progresso.
- O3. Processamento confiável de arquivos grandes sem travar o navegador.
- O4. Privacidade: processamento local/no servidor do próprio projeto, sem enviar PDFs a terceiros.

### 2.3 Não-objetivos (v1)

- Não é objetivo **assinar** PDFs na v1 (apenas **verificar** assinaturas); emissão de assinatura fica para fase posterior.
- Não é objetivo converter para Office (docx/xlsx/pptx) na v1.
- Não é objetivo app desktop/mobile nativo.
- Não redistribuir GhostScript/Tesseract/JRE embutidos como o PDF24 faz.

---

## 3. Personas e casos de uso

- **Usuário geral:** junta/divide PDFs, converte imagens em PDF, aplica OCR a documentos escaneados.
- **Usuário administrativo/jurídico:** verifica validade de assinaturas digitais (PAdES/LTV, eIDAS).
- **Usuário técnico/dev:** usa a API REST para automatizar operações.

Casos de uso centrais (v1):
1. Fazer upload de 1..N PDFs e visualizar as páginas.
2. Reordenar/remover/rotacionar páginas e exportar o resultado.
3. Dividir por intervalos ou a cada N páginas.
4. Mesclar vários arquivos (concatenar, intercalar frente/verso).
5. Converter imagens (JPG/PNG/TIFF/WebP) em PDF e extrair imagens de um PDF.
6. Aplicar OCR gerando PDF pesquisável.
7. Verificar a assinatura digital de um PDF e exibir o relatório de confiança.

---

## 4. Escopo funcional

Cada ferramenta abaixo tem: comportamento, parâmetros (derivados da API Java
decompilada) e critérios de aceite.

### 4.1 Páginas — Dividir / Extrair  (ref. `PdfSplitter`)

- **Modos:** (a) N páginas por arquivo; (b) pontos de corte explícitos (`"1,3,5"`); (c) extrair intervalo/página única.
- **Parâmetros:** arquivo, senha opcional, modo, `pagesPerPdf`, `splitPoints`.
- **Saída:** 1..N PDFs + metadados (nome, faixa de páginas).
- **Aceite:** dividir um PDF de 10 páginas a cada 3 gera 4 arquivos (3+3+3+1); páginas preservam conteúdo e orientação.

### 4.2 Páginas — Mesclar  (ref. `PdfPageMerger`)

- **Modos:** concatenar; intercalar (alternate, para digitalização frente/verso); par/ímpar reverso; faixas por arquivo (`addPageRange(file,"1-3,5")`).
- **Parâmetros:** lista ordenada de (arquivo, páginas), metadados do doc resultante.
- **Aceite:** concatenar A(2p)+B(3p) → 5 páginas na ordem; modo alternate intercala corretamente.

### 4.3 Editar PDF  (ref. `PdfEditEngine`)

- **v1 (essencial):** reordenar, remover, rotacionar, duplicar páginas; inserir página em branco.
- **v2:** anotações, texto, carimbo/marca d'água, recorte (crop).
- **Aceite v1:** reordenação drag-and-drop reflete no arquivo exportado; rotação por página persiste.

### 4.4 OCR  (ref. `OcrEngine`)

- **Comportamento:** renderiza páginas (DPI configurável, padrão 300) → OCR com Tesseract → gera **PDF pesquisável** (camada de texto) e/ou TXT.
- **Parâmetros:** `lang` (ex.: `por`, `eng`), `dpi`, `generatePdfA`, `skipPagesWithText`.
- **Aceite:** PDF escaneado vira PDF com texto selecionável; idioma português reconhecido.

### 4.5 Imagens  (ref. `PdfImageExtract`, `ImageToPdf`)

- **Extrair imagens:** exporta XObjects do PDF (JPG/JP2/PNG), opção JP2→JPG.
- **Imagem→PDF:** cria PDF a partir de JPG/PNG/TIFF/WebP com layout/escala e orientação.
- **Aceite:** extração recupera todas as imagens de um PDF; conjunto de imagens vira PDF com 1 imagem por página.

### 4.6 Assinatura — Verificação  (ref. `PdfSignatureVerifier`)

- **Comportamento:** detecta campos de assinatura, valida **PAdES** (`ETSI.CAdES.detached`), cadeia PKIX, **LTV** (dicionário DSS: OCSPs/CRLs embutidos), **timestamp** (TSP), confiança via **EU Trust List (eIDAS)** e/ou raízes do sistema e certificados fornecidos.
- **Saída:** relatório JSON (válida/ inválida, signatário/CN, data, cadeia, revogação, LTV, cobertura do ByteRange).
- **Aceite:** PDF assinado válido retorna "válido" com CN e timestamp; PDF alterado após assinar retorna "inválido/cobertura parcial".

---

## 5. Arquitetura técnica

### 5.1 Visão de alto nível

```
┌───────────────────────────── Frontend (SPA) ─────────────────────────────┐
│ Next.js (React) + TypeScript + Tailwind + shadcn/ui                       │
│  ├─ Visualizador PDF: pdf.js (render) + camada interativa                 │
│  ├─ Gestão de páginas: dnd-kit (drag/reorder), thumbnails virtualizados   │
│  └─ Upload resiliente (chunked), fila de jobs, progresso em tempo real    │
└───────────────▲───────────────────────────────────────────────▲──────────┘
                │ REST/JSON + SSE/WebSocket (progresso)           │
┌───────────────┴──────────── Backend (API) ─────────────────────┴──────────┐
│ Java 21 + Spring Boot (REST)                                              │
│  ├─ Serviços por ferramenta (split/merge/edit/ocr/image/signature)        │
│  ├─ Core PDF: Apache PDFBox 3.x                                           │
│  ├─ Assinatura: BouncyCastle (verificação PAdES/LTV)                      │
│  ├─ OCR: Tesseract via ProcessBuilder (binário do host/sidecar)           │
│  ├─ Compressão/render pesado: GhostScript (opcional, sidecar) [PENDENTE]  │
│  └─ Jobs assíncronos (fila) + storage temporário com expiração            │
└───────────────────────────────────────────────────────────────────────────┘
         │ artefatos temporários (TTL)         │ fila/estado
         ▼                                     ▼
   Object storage (S3/MinIO)             Redis (jobs/progresso)
```

### 5.2 Decisão de stack (justificativa)

- **Backend Java/Spring Boot:** fidelidade 1:1 ao que foi decompilado (PDFBox + BouncyCastle). Minimiza risco de reimplementação, sobretudo na **verificação de assinatura PAdES/LTV**, cuja reescrita fora do ecossistema Java é cara.
- **Frontend Next.js + pdf.js:** render fiel no navegador, SSR para performance inicial, ecossistema maduro de UI.
- **Jobs assíncronos:** operações como OCR e arquivos grandes não cabem em request síncrono.

### 5.3 Componentes externos (processos, não embutidos)

| Componente | Uso | Forma |
| --- | --- | --- |
| Tesseract OCR | OCR | binário no container backend / sidecar |
| GhostScript | compressão/rasterização avançada | **[DECISÃO PENDENTE]** incluir já na v1? (impacto de licença AGPL — ver §11) |

---

## 6. Stack e ferramentas (resumo)

- **Frontend:** Next.js (App Router), React 18+, TypeScript, Tailwind CSS, shadcn/ui, pdf.js, dnd-kit, TanStack Query.
- **Backend:** Java 21, Spring Boot 3.x, Apache PDFBox 3.x, BouncyCastle (bcpkix/bcprov) 1.8x, Tesseract (externo).
- **Infra:** Docker + docker-compose; Redis (jobs); MinIO/S3 (artefatos temporários); Nginx (reverse proxy).
- **Qualidade:** JUnit 5 + PDFBox fixtures (backend); Vitest + Playwright (frontend); ESLint/Prettier; Checkstyle/Spotless.
- **CI:** GitHub Actions (build, test, lint, imagem Docker).

---

## 7. Modelo de API (REST, v1)

Convenção: upload gera um `fileId`; operações referenciam `fileId`(s); operações
pesadas retornam `jobId` com progresso via SSE.

```
POST   /api/v1/files                      → upload (multipart/chunked) → { fileId, pages, meta }
GET    /api/v1/files/{fileId}/pages        → thumbnails/metadados das páginas
POST   /api/v1/split                       → { fileId, mode, pagesPerPdf?, splitPoints? } → jobId
POST   /api/v1/merge                       → { items:[{fileId,ranges}], mode } → jobId
POST   /api/v1/edit                        → { fileId, ops:[reorder|rotate|delete|insert] } → jobId
POST   /api/v1/ocr                         → { fileId, lang, dpi, pdfA } → jobId
POST   /api/v1/images/extract              → { fileId, jp2ToJpg? } → jobId
POST   /api/v1/images/to-pdf               → { imageIds:[...], layout } → jobId
POST   /api/v1/signatures/verify           → { fileId, useEuTrustList?, useSystemRoots? } → relatório JSON
GET    /api/v1/jobs/{jobId}                 → status/progresso
GET    /api/v1/jobs/{jobId}/result          → download do artefato
GET    /api/v1/jobs/{jobId}/events          → SSE (progresso em tempo real)
```

Erros seguem um envelope consistente `{ error: { code, message, details } }`.

---

## 8. Requisitos de UI/UX

- **Visualizador central:** render pdf.js com zoom, navegação, miniaturas laterais virtualizadas (performático para 100+ páginas).
- **Manipulação direta:** arrastar para reordenar, selecionar múltiplas páginas, rotacionar/excluir inline (dnd-kit).
- **Fluxo por ferramenta:** painel contextual com parâmetros; preview do resultado antes de confirmar.
- **Feedback:** barra de progresso por job (SSE), estados de carregamento, toasts de sucesso/erro.
- **Acessibilidade:** WCAG 2.1 AA — navegação por teclado, `aria-*`, contraste, foco visível.
- **Responsivo:** desktop-first, degradação graciosa em telas menores.
- **Internacionalização:** PT-BR e EN na v1.
- **Tema:** claro/escuro.

---

## 9. Requisitos não-funcionais

- **Desempenho:** upload resiliente (chunked); operações pesadas assíncronas; alvo de P95 < 2s para operações leves (split/merge pequenos).
- **Limites:** tamanho máx. de arquivo configurável (padrão sugerido 500 MB); limite de páginas por operação.
- **Privacidade/segurança:** artefatos temporários com **TTL** e expurgo automático; sem retenção permanente por padrão; sanitização de uploads; isolamento do Tesseract/GS; validação estrita de tipos (não confiar na extensão).
- **Confiabilidade:** jobs idempotentes quando possível; retry controlado; limpeza de temporários mesmo em falha.
- **Observabilidade:** logs estruturados, métricas por ferramenta, trace de jobs.
- **Portabilidade:** tudo containerizado; roda em WSL2/Docker (ambiente atual) e em servidor Linux.

---

## 10. Estrutura de diretórios proposta

```
pdf-web-nando/
├─ docs/                 # este PRD e documentos de design subsequentes
├─ frontend/             # Next.js app
│  ├─ app/               # rotas (App Router)
│  ├─ components/        # UI (visualizador, página-grid, painéis)
│  └─ lib/               # api client, hooks (TanStack Query), pdf.js setup
├─ backend/              # Spring Boot app
│  ├─ src/main/java/.../pdfweb/
│  │  ├─ api/            # controllers REST
│  │  ├─ service/        # split, merge, edit, ocr, image, signature
│  │  ├─ core/           # wrappers PDFBox/BouncyCastle, ProcessBuilder (tesseract/gs)
│  │  └─ jobs/           # fila, progresso, storage temporário
│  └─ src/test/          # JUnit + fixtures PDF
├─ infra/                # docker-compose, Dockerfiles, nginx, configs
└─ README.md
```

---

## 11. Licenciamento e conformidade  (CRÍTICO)

A engenharia reversa confirmou que o PDF24 depende de componentes com licenças
que impõem obrigações. Para o nosso produto:

| Componente | Licença | Implicação para o projeto |
| --- | --- | --- |
| Apache PDFBox | Apache-2.0 | Permissiva — uso livre, manter avisos. ✅ |
| BouncyCastle | MIT-like (BC) | Permissiva. ✅ |
| Tesseract | Apache-2.0 | Permissiva. ✅ |
| **GhostScript** | **AGPL-3.0** | **Copyleft forte.** Se usado como parte de um serviço web, pode exigir disponibilizar o código do serviço. **[DECISÃO PENDENTE]** — avaliar: (a) não usar GhostScript na v1; (b) licença comercial Artifex; (c) alternativas (ex.: render só com PDFBox/pdf.js, compressão via outras libs). |
| OpenJDK | GPLv2 + Classpath | OK para rodar nossa app; não embutir/redistribuir como instalador. |

- **Não** redistribuir binários, dados (`tessdata`) ou JARs do PDF24.
- Reimplementar a lógica com nossas próprias dependências e código.
- Manter um `THIRD_PARTY_NOTICES` no projeto.

---

## 12. Fases de entrega (roadmap)

- **Fase 0 — Fundação:** scaffolding (Next.js + Spring Boot), docker-compose, upload + visualizador pdf.js, pipeline CI.
- **Fase 1 — Páginas:** split, merge, edição básica (reordenar/rotacionar/remover) com drag-and-drop. (Maior valor, menor risco.)
- **Fase 2 — Imagens:** extrair imagens e imagem→PDF.
- **Fase 3 — OCR:** integração Tesseract + PDF pesquisável.
- **Fase 4 — Assinatura (verificação):** relatório PAdES/LTV/eIDAS com BouncyCastle.
- **Fase 5 — Polimento UI/UX + a11y + i18n + hardening.**
- **Fase futura:** assinar PDFs, conversões Office, edição avançada.

---

## 13. Critérios de aceite globais

- Todas as ferramentas da Fase correspondente passam seus critérios da [seção 4](#4-escopo-funcional).
- Cobertura de testes: unitários no core (PDFBox) e e2e nos fluxos principais.
- Build e testes verdes no CI; imagem Docker sobe via `docker compose up`.
- Operações pesadas rodam assíncronas com progresso visível e limpeza de temporários.
- Acessibilidade AA validada nas telas principais.
- Nenhum componente AGPL redistribuído sem decisão formal registrada (§11).

---

## 14. Riscos e decisões pendentes

| # | Item | Status |
| --- | --- | --- |
| R1 | Uso de GhostScript (AGPL) para compressão/render | **[DECISÃO PENDENTE]** §11 |
| R2 | Emissão de assinatura (assinar, não só verificar) | fora da v1; reavaliar |
| R3 | Backend Java vs. porta para Node/Python | **decidido: Java** (fidelidade PDFBox/BouncyCastle) |
| R4 | Limites de tamanho/escala e custo de infraestrutura | definir na Fase 0 |
| R5 | Fonte dos dados de confiança eIDAS (EU Trust List) | definir origem/atualização na Fase 4 |

---

## 15. Referências (artefatos da sessão de RE)

- Bootstrapper analisado: `projects/pdf-nando-toolkit/pdf24-creator-installer.exe`
- Payload analisado: `projects/pdf-nando-toolkit/payload/pdf24-creator-latest-x64.exe` (PDF24 Creator 11.31.1)
- Classes Java decompiladas: `projects/pdf-nando-toolkit/decompiled/org/pdf24/tools/commons/`
- Ambiente de RE: `projects/pdf-nando-toolkit/rea-docker/` (REA + Ghidra)
- Ferramenta de reverse de binário: REA (`rea-agents`) + Ghidra 12.1.4
