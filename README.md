# pdf-web-nando

Aplicação web para manipulação interativa de PDFs: dividir, mesclar, editar
páginas, converter/extrair imagens, aplicar OCR (PDF pesquisável) e verificar
assinaturas digitais. Backend Java 21 + Spring Boot (Apache PDFBox + BouncyCastle),
frontend Next.js + pdf.js.

> Produto: `docs/PRD.md`. Plano de engenharia e decisões: `docs/IMPLEMENTATION_PLAN.md`.

## Status

**v1 completa (Fases 0–6).** Todas as famílias de ferramentas do escopo foram
implementadas, com testes e verificação ponta-a-ponta via Docker, mais uma camada
de UX premium e lapidação de funcionalidades.

| Fase | Entrega |
| --- | --- |
| 0 — Fundação | Scaffolding, upload + visualizador pdf.js, infra Docker, CI |
| 1 — Páginas | Dividir (5 modos), mesclar (4 modos), ordenar (reordenar/rotacionar/excluir/duplicar/inserir), grid drag-and-drop |
| 2 — Imagens | Imagem→PDF (layout/orientação/DPI), extração de imagens embutidas |
| 3 — OCR | Tesseract (sidecar na imagem), jobs assíncronos + progresso via SSE, PDF pesquisável + TXT |
| 4 — Assinatura | Verificação CMS/PKIX (cobertura do ByteRange, CN, cadeia) |
| 5 — Polimento | Hardening de upload (magic bytes), i18n PT-BR/EN, tema claro/escuro, a11y (foco visível), log estruturado |
| 6 — UX + Lapidação | UI premium (launcher com cards + modal de importação), loader de processamento, qualidade de exportação (reduz tamanho via JPEG/DPI), nome de arquivo escolhível, visualizador ampliado (lupa) |

## Funcionalidades (launcher com 6 ferramentas)

A página inicial é um menu de cards; ao escolher uma ferramenta, abre o modal de
importação (dropzone) e, em seguida, a tela operacional.

- **Ordenar:** grid de páginas com reordenação por arrastar (dnd-kit), rotação,
  exclusão e duplicação inline; **lupa** para visualizar cada página ampliada;
  exportar o PDF (manuseia posição de página — não edita o conteúdo).
- **Dividir:** dividir em N páginas, por pontos de corte, pares/ímpares ou cortar ao meio.
- **Mesclar:** combinar vários PDFs (concatenar, intercalar, ímpares+pares reverso),
  com faixas por arquivo.
- **Imagens:** converter JPG/PNG em PDF (tamanho de página, orientação, qualidade);
  extrair imagens embutidas de um PDF.
- **OCR:** gerar PDF pesquisável + TXT a partir de PDF escaneado (idioma PT/EN),
  com progresso em tempo real e qualidade de exportação ajustável.
- **Verificar Assinatura:** verificar assinatura(s), exibindo validade, signatário
  (CN), cobertura do arquivo e confiança da cadeia.

> **Qualidade de exportação** (Baixa/Média/Alta) controla DPI + compressão JPEG das
> imagens embutidas — resolve o inchaço de PDFs de OCR/imagem. **Nome do arquivo**
> pode ser definido antes de gerar. Um **loader** cobre o painel durante o processamento.

## Estrutura

```
backend/    Spring Boot (API REST, PDFBox, BouncyCastle, Tesseract via ProcessBuilder)
frontend/   Next.js (App Router, pdf.js, dnd-kit, TanStack Query)
infra/      docker-compose, Dockerfiles, nginx
docs/       PRD e plano de implementação
```

## Pré-requisitos

- Docker + Docker Compose (modo recomendado — não requer Java/Maven no host)
- Para dev local do frontend: Node 20+ e pnpm

> O host de desenvolvimento pode não ter Java 21/Maven. O backend compila e roda
> dentro de container (Dockerfile multi-stage + Maven wrapper `mvnw`). O Tesseract
> é instalado na própria imagem do backend (idiomas `eng` e `por`).

## Subir tudo (Docker)

```bash
cp .env.example .env
docker compose -f infra/docker-compose.yml up --build
```

- Aplicação: http://localhost:8090
- API (via nginx): http://localhost:8090/api/v1/health

> A porta HTTP é configurável via `HTTP_PORT` no `.env` (padrão 8090).

## API (REST, v1 — prefixo `/api/v1`)

```
GET    /health                        health check
POST   /files                         upload de PDF (valida magic bytes) → { fileId, pages, meta }
GET    /files/{id}/pages              metadados por página
POST   /split                         { fileId, mode, pagesPerPdf?, splitPoints? } → lista de resultados
POST   /merge                         { items:[{fileId, ranges?}], mode } → resultado único
POST   /edit                          { fileId, ops:[reorder|rotate|delete|duplicate|insertBlank] } → resultado
POST   /images                        upload de imagem (valida magic bytes) → { imageId }
POST   /images/to-pdf                 { imageIds, pageSize?, orientation?, usePageSize?, quality? } → resultado
POST   /images/extract                { fileId, passJpg?, passJp2?, jp2ToJpg? } → lista de imagens
POST   /ocr                           { fileId, lang?, quality? } → { jobId }  (assíncrono)
GET    /jobs/{id}                      status/progresso do job
GET    /jobs/{id}/events               SSE de progresso
GET    /jobs/{id}/result[?type=txt][&name=] download do PDF pesquisável ou TXT
POST   /signatures/verify             { fileId } → relatório de verificação
GET    /results/{id}[?name=]           download de um artefato gerado (nome escolhível)
```

> `quality` ∈ `low|medium|high` controla DPI + compressão JPEG. `name` define o
> nome do arquivo no download (`Content-Disposition`), sanitizado no servidor.
GET    /results/{id}                  download de um artefato gerado
```

Erros seguem o envelope `{ "error": { "code", "message", "details" } }`.

## Dev local

Backend (dentro de container, sem Java no host):

```bash
docker compose -f infra/docker-compose.yml up --build backend redis minio
```

Frontend:

```bash
cd frontend
pnpm install
pnpm dev
```

## Testes

```bash
# Backend (via container) — 39 testes
docker run --rm -v "$PWD/backend":/app -w /app maven:3.9-eclipse-temurin-21 mvn test

# Frontend
cd frontend && pnpm test
```

> O teste de OCR real faz *skip* se o binário `tesseract` não estiver presente no
> ambiente de teste (é validado no container, onde o Tesseract está instalado).

## Licenciamento

Ver `THIRD_PARTY_NOTICES.md`. GhostScript (AGPL) **não** é usado na v1.
