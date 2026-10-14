# Third-Party Notices

Este produto usa componentes de terceiros, cada um sob sua própria licença.
Nenhum binário, dado (ex.: `tessdata`) ou código do PDF24 Creator é
redistribuído — as funcionalidades foram reimplementadas sobre dependências
próprias.

## Backend

| Componente | Licença | Uso |
| --- | --- | --- |
| Apache PDFBox | Apache-2.0 | Núcleo de manipulação de PDF |
| Apache PDFBox Tools | Apache-2.0 | Escrita de imagens (ImageIOUtil) — imagem↔PDF (Fase 2) |
| Apache Commons Imaging | Apache-2.0 | Leitura de orientação EXIF (Fase 2) |
| BouncyCastle (bcpkix/bcprov) | MIT-like (Bouncy Castle License) | Verificação de assinatura CMS/PKIX (Fase 4) |
| Spring Boot | Apache-2.0 | Framework da API REST |
| Tesseract OCR | Apache-2.0 | OCR via processo externo, instalado na imagem (Fase 3) |

## Frontend

| Componente | Licença | Uso |
| --- | --- | --- |
| Next.js / React | MIT | Framework de UI |
| pdf.js (pdfjs-dist) | Apache-2.0 | Renderização de PDF no navegador |
| dnd-kit | MIT | Reordenação de páginas (drag-and-drop) |
| TanStack Query | MIT | Estado de servidor |

## Runtime

| Componente | Licença | Observação |
| --- | --- | --- |
| OpenJDK (Temurin) | GPLv2 + Classpath Exception | Usado para executar a aplicação; não redistribuído como instalador. |

## Componentes deliberadamente NÃO utilizados

| Componente | Licença | Motivo |
| --- | --- | --- |
| GhostScript | AGPL-3.0 | Copyleft forte incompatível com serviço web sem abrir o código. Fora da v1 (ver PRD §11 / IMPLEMENTATION_PLAN §0.3 D1). |
