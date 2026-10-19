# Análise aprofundada — pdf-web-nando

- **Repositório:** https://github.com/nandinhos/pdf-web-nando (`b424da7`, 3 commits)
- **Data/modo:** 2026-10-14 · `safe-run` (testes + Docker + E2E via API executados)
- **Legenda:** `OBSERVED` = lido no código ou executado · `INFERRED` = dedução com evidência parcial · `UNKNOWN` = sem evidência

## Veredito em uma frase

A v1 está **funcional e no ar** em http://localhost:8090 após corrigir um único
bloqueador de infra (imagem do MinIO descontinuada); o código é saudável e está
pronto para continuar o desenvolvimento, com dívidas conhecidas e localizadas.

## O que o projeto é

Suite web de manipulação de PDF (6 ferramentas: Ordenar, Dividir, Mesclar,
Imagens, OCR, Verificar Assinatura), reimplementada a partir de engenharia
reversa do PDF24 Creator (`docs/PRD.md` §1). Backend Java 21 + Spring Boot 3.3.5
sobre Apache PDFBox 3.0.3 + BouncyCastle; frontend Next.js 14 + pdf.js; nginx
como única porta pública. Sem banco de dados: arquivos em disco, jobs em memória.

## Arquitetura real (OBSERVED) vs declarada

```
Browser ──:8090──> nginx ──/────> Next.js standalone :3000 (1 rota, SPA)
                        └─/api/─> Spring Boot :8081 (/api/v1)
                                    api/      6 controllers, envelope de erro unico
                                    service/  Split|Merge|Edit|Image*|Ocr|Signature
                                    core/     PageMerger (god-node), PdfDocuments,
                                              PageRanges, FileTypeValidator, Filenames
                                    jobs/     JobRegistry em memoria + SSE
                                    ocr/      Tesseract via ProcessBuilder (timeout 5min)
                                  disco /tmp/pdfweb (volume pdfweb-data)
```

**Drift principal:** Redis e MinIO aparecem no PRD/compose/`.env.example`, mas
**nenhuma classe os referencia** (busca por `redis|minio|S3` em
`backend/src/main` = 0 hits). `STORAGE_*`, `REDIS_*` e `ARTIFACT_TTL_MINUTES`
são variáveis mortas na v1; não há `@Scheduled` de expurgo.

## Fluxos core (rastreados no código)

1. **Upload → operar → baixar:** `POST /files` valida magic bytes
   (`core/FileTypeValidator`), grava com UUID e conta páginas; cada ferramenta
   resolve o `fileId`, gera artefato em `service/ArtifactStorage`
   (anti path-traversal) e devolve `downloadUrl`. Split/Merge/Edit convergem
   para `core/PageMerger` (lista de `PageItem` → `importPage` preservando
   mediaBox/cropBox/rotação).
2. **OCR assíncrono:** `POST /ocr` cria `Job`, `OcrService.runAsync` (`@Async`)
   renderiza cada página em JPEG (preset de qualidade), chama `tesseract`
   (args em lista, sem shell), mescla os PDFs por página e publica progresso
   via SSE em `GET /jobs/{id}/events`.
3. **Assinatura:** `POST /signatures/verify` → `SignatureVerifyService`
   (BouncyCastle CMS/PKIX, cobertura de ByteRange, CN, confiança da cadeia
   contra roots do sistema; LOTL/eIDAS = v2 declarada).

## Verificação executada (OBSERVED, nesta sessão)

| Check | Comando | Resultado |
| --- | --- | --- |
| Backend tests | `docker run --rm -v $PWD/backend:/app maven:3.9-eclipse-temurin-21 mvn -B test` | **44 run, 0 fail, 1 skipped** (OCR real) |
| Frontend tests | `pnpm test` | **3/3 passed** |
| Typecheck/lint/build | `pnpm typecheck && pnpm lint && pnpm build` | **exit 0** |
| Stack Docker | `docker compose up --build -d` | 3 containers `Up`; health 200 `{"status":"UP"}` |
| E2E API | upload→pages→split→download→merge→verify→OCR | tudo 200; OCR `DONE` com TXT correto; não-PDF → `INVALID_PDF` |
| Dev local | `next dev :3001` + `GET /api/v1/health` | `UP` via rewrite (provado e encerrado) |

## Bloqueador encontrado e correções aplicadas (3 arquivos)

1. **`infra/docker-compose.yml`** — `docker compose up` falhava: a imagem
   `minio/minio` não existe mais publicamente (Hub: denied; Quay: 401, inclusive
   tag fixa). Como MinIO/Redis não são usados pelo código, foram para o profile
   `full` (reserva v2, exige `MINIO_IMAGE` própria), saiu o `depends_on`,
   entrou o volume `pdfweb-data` (sem ele, recreate apagava uploads) e a porta
   `8081` do backend para dev.
2. **`frontend/next.config.mjs`** — rewrite `/api/v1/*` →
   `API_PROXY_TARGET` (default `localhost:8081`). Sem isso (e sem CORS no
   backend), o fluxo documentado `pnpm dev` + backend em container **não
   alcançava a API**.
3. **`README.md`** — fluxo dev local e contagem de testes (39 → 44) corrigidos.

## Saúde técnica

| Dimensão | Nota | Evidência |
| --- | --- | --- |
| Testabilidade | Boa no backend, fraca no front | 44 testes de serviço/core com fixtures PDFBox; front só testa `apiClient` (3) |
| Segurança | Adequada p/ uso local, **não p/ expor público** | magic bytes, sanitização de nome, anti-traversal, tesseract sem shell; mas **sem autenticação, sem rate-limit**, segredo default `minioadmin` no `.env.example` |
| Robustez | Atenção a arquivos grandes | `FileController` carrega o upload inteiro em `byte[]` apesar do limite de 500 MB (risco de OOM — INFERRED) |
| Coesão | Boa | serviços finos sobre `PageMerger`; DTOs `record`; um único `apiClient.ts` |
| Dívida documentada | Controlada | outlines/links não remapeados no merge, OCR rasteriza a página (perde vetor), assinatura sem LTV/LOTL — tudo registrado como v2 nos docs |

## Onde mexer (guia rápido)

- **Nova operação de páginas:** crie um `*Service` em `backend/.../service/`
  montando `PageMerger.PageItem`s, exponha em `api/ToolsController.java`,
  adicione o DTO em `api/dto/ToolDtos.java`, a função em
  `frontend/lib/apiClient.ts` e um painel em `frontend/components/`.
- **Nova ferramenta na home:** registre o card em `components/Launcher.tsx` +
  `ToolIcons.tsx`, as chaves em `lib/i18n.tsx` e o painel em `app/page.tsx`.
- **Limites/qualidade:** `application.yml` + `config/PdfWebProperties.java`;
  presets de DPI/JPEG em `core/QualityPreset.java`.
- **Debug:** backend `docker compose -f infra/docker-compose.yml logs -f backend`;
  front com hot-reload via `pnpm dev` (proxy automático p/ :8081).

## Próximos passos sugeridos (ordem)

1. **Expurgo com TTL** de uploads/artefatos + limpeza do `JobRegistry`
   (hoje crescem sem limite em disco e memória).
2. **Upload em streaming** (sem `byte[]` inteiro) antes de prometer 500 MB.
3. **Autenticação + rate-limit** se for sair do uso local; trocar os defaults
   do `.env.example`.
4. Testes de componente no frontend (paineis) e 1 E2E de navegador no CI.
5. Decidir o destino de Redis/MinIO: implementar (jobs persistentes, S3) ou
   remover de vez do PRD/compose — o profile `full` guarda a intenção.

## Como rodar

```bash
cp .env.example .env            # ja feito nesta sessao (.env e gitignored)
docker compose -f infra/docker-compose.yml --env-file .env up --build -d
# abra http://localhost:8090 no navegador
```

> Ambiente do host (OBSERVED): Docker 28.2.2 OK · Node v22.21.1 OK ·
> pnpm via `corepack enable` (ativado) · **sem Java/Maven** — backend só via
> container, como o projeto prevê. A stack ficou **rodando** ao fim da análise.
> Nada foi commitado: 3 arquivos modificados aguardam sua revisão
> (`git diff`), e esta pasta `artifacts/` é nova (não versionada).
