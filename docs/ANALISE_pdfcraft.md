# Relatório Técnico — Análise do PdfCraft (storytold/pdfcraft)

> Análise documental (sem execução/clonagem) do repositório
> `github.com/storytold/pdfcraft`, para extrair aprendizado arquitetural e de
> produto aplicável ao **pdf-web-nando**. Fontes: `README.md`,
> `docs/target-app-parity.md` e a árvore `crates/` (OBSERVED via fetch em
> 2026-10-18).

---

## 0. Sumário executivo

| Aspecto | PdfCraft | Leitura para o nosso projeto |
|---|---|---|
| **O que é** | Leitor + editor de PDF **nativo**, escrito do zero em **Rust puro**, multiplataforma (macOS/Win/Linux/FreeBSD/WASM) | Referência de **arquitetura e escopo**, não de código reutilizável (stack diferente: Rust vs Java/Spring) |
| **Maturidade** | **Alpha**. ~67% de breadth tier-weighted vs Acrobat Pro; ~50% "pronto para trabalho real"; 152k linhas, 1.765 testes, 184 comandos, 141 tools de automação | Mostra o **teto** do domínio e a ordem de prioridade real |
| **Licença** | MIT **OR** Apache-2.0 (permissiva) | Permite estudar o código livremente; trechos poderiam ser portados citando a licença |
| **Origem** | **Clean-room** da ISO 32000 — proíbe explicitamente usar código da Adobe | **Valida nossa decisão anterior** de não reverter o binário da Adobe |
| **Renderização** | Ainda **terceirizada** ao crate `hayro` enquanto constroem o próprio | Nem eles têm renderer próprio pronto — nós usamos pdf.js, o que é legítimo |
| **Diferencial** | Camada de **automação por agentes** (CLI + MCP + canal de controle da UI) sobre **uma única tabela de tools** | **A maior ideia a abstrair** para o nosso projeto |

**Conclusão central:** o valor para nós **não é código** (é Rust, não integra no backend
Java). É a **arquitetura em camadas** (engine reutilizável + façade + frontends
plugáveis), a **tabela única de operações** exposta a GUI/CLI/agentes, e o
**modelo de salvamento incremental/atômico** com undo. São padrões, não linhas.

---

## 1. Arquitetura — como é organizado (OBSERVED: 34 crates)

O projeto é um **workspace Cargo** com crates focados, "em camadas, de modo que o
núcleo nunca dependa da UI". Esta separação é o aprendizado estrutural nº 1.

| Crate | Responsabilidade | Equivalente/consequência no nosso projeto |
|---|---|---|
| `filters` | Todos os filtros de stream PDF (Flate, LZW, ASCII85, RunLength, predictors) | PDFBox já cobre internamente |
| `crypt` | Security handler padrão: RC4, AES-128/256, revisões 2–6, permissões | Temos verificação de assinatura; **criptografia/senha é um gap nosso** |
| `cos` | Camada de objetos PDF: parsing tolerante, **reparo**, edição copy-on-write, escrita **incremental** e completa | PDFBox faz parsing; **escrita incremental/atômica é um padrão a adotar** |
| `model` | Modelo de documento | — |
| `geom` | Geometria (coordenadas, matrizes) | Nós reimplementamos conversão top-left↔bottom-left à mão |
| `organize` | Operações de página: combine/extract/split, bookmarks, page labels, doc info | **Já temos** (split/merge/edit) — eles carregam links/campos/layers junto |
| `fonts` | Métricas e encodings para appearances geradas | Relevante p/ nosso teto de fidelidade de fonte |
| `content` / `edit` | Edição de conteúdo existente (parágrafos; fonte reusada ou Helvetica substituída, **re-wrap**) | **Exatamente a área que estamos construindo** (análise de blocos) |
| `annot` | Comentários: builders + appearance streams (notas, markup, formas, tinta, caixas de texto); replies, status | Família inteira que **não temos** |
| `forms` | Forms interativos: modelo de campo, preenchimento com re-geração de appearance, Clear form | **Não temos** |
| `xfa` / `js` / `xfdf` | XFA (layout dinâmico), JavaScript sandbox (boa), import/export FDF/XFDF | Nicho/avançado |
| `render` | Renderização, inspeção e **extração de texto com ordem de leitura** | Usamos pdf.js (frontend) + PDFBox (texto); ordem de leitura é um diferencial |
| `redact` | Redação que remove glyphs, pixels, vetores, anotações e tags, **com verificação** | **Fizemos uma versão** (rasterização); a deles é cirúrgica + verificada |
| `ocr` | Imagem pesquisável (motor `ocrs`, latino sem acento) | Temos (Tesseract) — e mais línguas |
| `sign` | Assinatura digital PAdES, PKCS#12, Keychain/Windows store | Temos verificação; **assinar é gap** |
| `optimize` | Reduce File Size + Optimizer (imagens, discards, clean-up) | Temos "qualidade de exportação" (parcial) |
| `preflight` | Preflight / PDF/A (parcial) | Gap total |
| `print` | Impressão (n-up, booklet, poster, CUPS/spooler) | Fora do nosso escopo web |
| `create` / `export` | Criar de imagens/TIFF/texto; exportar PNG/JPEG/TIFF/texto/RTF/Word(parcial) | Temos imagem→PDF; export Word/Office é gap |
| `compare` | Comparar documentos (texto, visual, report) | Gap |
| `measure` | Ferramentas de medição 2D | Nicho |
| `a11y` | Accessibility Checker (32 regras) | Gap |
| `engine` | **Façade** que todo frontend usa: sessões, edits, **undo**, saving, catálogo de tools | **Padrão central a abstrair** |
| `automation` | Tabela headless de tools, `pdfcraft-cli run`, servidor MCP opt-in | **A joia da coroa para nós** |
| `ui-egui` | Interface desktop e web (egui) | Nós usamos Next.js — camada equivalente |
| `platform` / `audit` / `testkit` / `oracle` | Plataforma, auditoria de licença de assets, utilidades de teste, **oráculos externos** (qpdf/poppler/hayro) | **Verificação contra implementações independentes** é prática a imitar |

---

## 2. Catálogo de capacidades (tabela mestra)

Status conforme `docs/target-app-parity.md` (breadth medido; "ready" estimado pelo
próprio projeto). Coluna "Como é feito" resume a abordagem técnica declarada.

| # | Área | Capacidades | Como é feito (abordagem) | Status (deles) | Temos? |
|---|---|---|---|---|---|
| A | **Núcleo & fidelidade** | Parse tolerante, **reparo** de arquivos corrompidos, encriptação R2–R6, **save incremental + atômico**, undo profundo, autosave, crash recovery | Camada COS com copy-on-write; escrita que **anexa** mantendo bytes originais; swap atômico de arquivo temporário; verificação com qpdf no CI | 77% breadth / 70% ready | Parcial (PDFBox; sem incremental/atômico) |
| B | **Visualização & navegação** | Zoom em tiles, layouts (contínuo/2-up/rotação/fullscreen/Read), find-as-you-type, bookmarks, thumbnails, page labels | Render em **tiles** p/ nitidez; cada página isolada p/ sobreviver a arquivo ruim; renderer `hayro` (terceirizado) | 62% / 55% | Parcial (pdf.js) |
| C | **Edição de conteúdo** | Editar parágrafo existente (reusa fonte ou substitui Helvetica, **re-wrap**), texto/imagem adicionados editáveis, header/footer, watermark, Bates, links | Reescrita do content stream; fonte reembutida quando possível | 53% / **30%** | Em construção (nossa análise de blocos) |
| D | **Organizar páginas** | Grid, insert/extract/replace/split/combine **carregando links, campos, layers, bookmarks**; boxes; bookmarks a partir da estrutura | Reescreve destinos nomeados ao copiar páginas; mantém forms interativos | 61% / 70% | **Sim** (split/merge/edit) |
| E | **Comentários & revisão** | Todos os tipos de markup + appearance streams, replies, status, filtros, stamps, **XFDF/FDF**, summaries, flatten, medição 2D | Crate `annot` gera appearance streams por tipo | 78% / 70% | **Não** |
| F | **Forms & JavaScript** | Preencher/autorar todo tipo de campo AcroForm, funções AF na ordem de evento da Acrobat, **JS sandboxed (boa)**, XFA parcial | Modelo de campo + regeneração de appearance; motor JS isolado | 79% / 65% | **Não** |
| G | **Proteger/redigir/sanitizar** | Senha/permissões, **remover segurança**, **redação real** (glyph+pixel+vetor+anotação+tag) **com verificação**, listas de palavras, padrões, sanitize | Redação cirúrgica no content stream + verificação independente | 59% / 55% | Parcial (nossa redação por rasterização) |
| H | **Assinaturas digitais** | PAdES B-B assinar/validar, PKCS#12, Keychain/Windows, certificação, B-T/LTV parcial | Crate `sign`; validação com `pdfsig`/OpenSSL como oráculo | 62% / 45% | Parcial (só verificação) |
| I | **Scan & OCR** | Imagem pesquisável (motor `ocrs`, latino) | OCR em páginas/ranges/vários arquivos | 23% / **15%** | **Sim e melhor** (Tesseract PT/EN) |
| J | **Criar & exportar** | Criar de imagens/TIFF/texto/clipboard/vários; exportar PNG/JPEG/TIFF/texto/RTF; Word/HTML parcial | Pipelines de conversão | 40% / 25% | Parcial (imagem→PDF) |
| K | **Otimizar & padrões** | Reduce File Size, Optimizer, PDF/A-2b/3b (parcial) | Recompressão de imagem, discards, clean-up | 35% / 20% | Parcial (qualidade export) |
| L | **Imprimir** | n-up, booklet, poster, CUPS/spooler/web | Backends de impressão nativos | 40% / 35% | Fora de escopo |
| M | **Acessibilidade** | Accessibility Checker (32 regras), alt text, título, idioma | Crate `a11y` | 63% / 30% | **Não** |
| N | **Automação & misc** | **CLI + MCP + canal de controle de UI**, 141 tools, Action Wizard, compare | **Uma tabela de tools** descrita por JSON-Schema, 3 "portas": CLI/MCP/API Rust | 30% / 35% | **Não (maior oportunidade)** |

---

## 3. As 5 ideias de alto nível a abstrair (priorizadas)

> "Abstrair" aqui = **adotar o padrão arquitetural/de produto**, reimplementado no
> nosso Java/Spring + Next.js. Não é copiar Rust.

### 3.1 — Tabela única de operações exposta a GUI + CLI + Agentes (ALTO valor)
PdfCraft tem **uma** tabela de tools (open, render, extract text, rotate, delete,
redact, comment, fill form, protect, metadata, undo/redo, save, combine, split…)
descrita por **JSON-Schema**, e três front doors sobre ela: `pdfcraft-cli run`,
servidor **MCP** (para agentes de IA) e API nativa.

- **Nosso paralelo:** já temos endpoints REST isolados. O salto é consolidar num
  **catálogo declarativo** (nome, schema de entrada, handler) que alimente tanto o
  REST quanto um eventual MCP. Isso tornaria o pdf-web-nando **operável por agentes**
  — exatamente a tendência que o PdfCraft materializa.
- **Esforço:** médio. Alto retorno de posicionamento.

### 3.2 — Save incremental + atômico com undo (ALTO valor, correção de risco)
Cada save **anexa** as mudanças e **preserva os bytes originais**; escrita atômica
(temp + swap); undo sobrevive ao save; verificado com qpdf no CI.

- **Nosso paralelo:** hoje geramos um novo artefato por operação (stateless). Para
  edição real de documentos, o modelo incremental **preserva assinaturas e
  estrutura** e evita corrupção — foi literalmente a classe de bug que derrubou o
  PdfCraft no lançamento (#774). PDFBox suporta `saveIncremental`.
- **Esforço:** médio. Reduz risco em edição destrutiva.

### 3.3 — Verificação contra oráculos independentes (MÉDIO, qualidade)
Toda saída é checada contra **qpdf/poppler/hayro** no CI. É a materialização do
nosso próprio princípio (Evidence Semantics): não confiar na própria implementação.

- **Nosso paralelo:** adicionar um passo de CI que valida PDFs gerados com
  `qpdf --check` e compara extração de texto com `pdftotext`. Barato e poderoso.
- **Esforço:** baixo.

### 3.4 — Extração de texto com ordem de leitura + redação verificada (MÉDIO)
A extração respeita **ordem de leitura** (colunas, RTL, CJK), e a redação **remove
e depois verifica** que o conteúdo sumiu.

- **Nosso paralelo:** nossa redação por rasterização já é "verificável"; a lição é
  **sempre verificar após redigir** (já aplicamos isso no teste adversarial de bytes).
  A ordem de leitura melhoraria nossa detecção de blocos (Fase 3 do nosso plano).
- **Esforço:** médio-alto.

### 3.5 — Camada façade (engine) separada dos frontends (MÉDIO, manutenção)
O `engine` detém sessões/edits/undo/saving/catálogo; a UI é "uma camada
descartável por cima". Nosso backend já é a façade de fato; o aprendizado é
**manter o catálogo de operações no backend** e tratar o Next.js como cliente fino.

- **Esforço:** já estamos perto; é disciplina de design.

---

## 4. O que NÃO vale a pena perseguir agora (anti-over-engineering)

| Tentação | Por que não (Ponytail) |
|---|---|
| Renderer próprio | Nem o PdfCraft tem — usam `hayro`. pdf.js resolve para web. |
| XFA / JavaScript em forms | Nicho; 311 features "specialist" com baixo uso. |
| Assinatura digital (assinar) | Alto risco (PKI, tokens); eles mesmos estão em 45% ready. |
| PDF/A, PDF/X, Preflight | Padrões pesados; retorno baixo para o nosso público. |
| Impressão nativa | Fora do escopo de uma app web. |
| Portar código Rust | Incompatível com Java/Spring; custo > benefício. |

---

## 5. Fronteira legal e de licença (importante)

- **Licença do PdfCraft:** MIT OR Apache-2.0 — **permissiva**. Estudar e até portar
  trechos é permitido **com atribuição**. Mas é **Rust**; não há reuso direto no
  nosso backend Java.
- **Clean-room deles:** o comportamento vem da **ISO 32000** e de observação
  black-box, **nunca de código de terceiros** (regra explícita no `AGENTS.md`).
  Isso **confirma a decisão que tomamos** de não fazer engenharia reversa do
  binário da Adobe: a rota correta é a especificação pública — a mesma que
  seguimos com PDFBox.
- **Recomendação:** tratar o PdfCraft como **referência de arquitetura e de
  roadmap**, não como fonte de código. Qualquer trecho portado deve citar a
  licença MIT/Apache no nosso `THIRD_PARTY_NOTICES.md`.

---

## 6. Recomendação final (o que levar para o pdf-web-nando)

**Prioridade 1 (posicionamento):** consolidar nossos endpoints numa **tabela de
operações declarativa** e expor um **servidor MCP** opt-in. É o diferencial mais
alto do PdfCraft e o mais alinhado à direção do mercado (agentes).

**Prioridade 2 (robustez):** adotar **save incremental + atômico** no PDFBox para a
edição de conteúdo que estamos construindo, preservando bytes/assinaturas.

**Prioridade 3 (qualidade barata):** passo de CI com **qpdf --check** e comparação
de texto com **pdftotext** como oráculos independentes.

**Prioridade 4 (produto):** avaliar a família **Comentários/Markup** (`annot`) como
próxima grande feature — é 78% no PdfCraft e tem alto uso ("Reader-class work"),
e encaixa no nosso canvas de edição existente.

Nenhuma dessas exige Rust nem engenharia reversa: todas são padrões observáveis na
arquitetura do PdfCraft, reimplementáveis na nossa stack.

---

## 7. Verificação prática (fork + clone + build real) — OBSERVED 2026-10-18

O repositório foi **forkado e clonado** como segunda linha de desenvolvimento, e o
ambiente foi preparado e validado com build real.

| Item | Resultado (OBSERVED) |
|---|---|
| Fork | `github.com/nandinhos/pdfcraft` criado (via `gh repo fork`) |
| Clone | `/home/nandodev/projects/pdfcraft` (irmão do nosso web; `origin`=fork, `upstream`=original) |
| Toolchain | Rust **1.99.1** instalado via rustup (exigência do projeto: `rust-version = 1.95`) |
| Ambiente | Ubuntu 24.04 x86_64, gcc/pkg-config presentes, não-headless (DISPLAY+Wayland), 669 GB livres |
| Build CLI | `cargo build --profile low-disk -p pdfcraft-cli` → **Finished em 3m26s, exit 0** |
| Binário | `target/low-disk/pdfcraft-cli` funcional |
| **Tools de automação** | **171** (confirmadas via `pdfcraft-cli tools`) — lista salva |

**Edition/stack:** Rust edition 2024, workspace Cargo com 34 crates + `vendor/`
(hayro, lopdf, winit, egui-wgpu patchados). A GUI desktop (`-p pdfcraft`) exige
libs gráficas adicionais; a **CLI/engine** (nosso interesse) compila limpa.

### 7.1 — As 171 tools, agrupadas pelo que nos interessa

> Confirma e expande a seção 3.1: o catálogo é real e muito maior que o anunciado
> no README (171 vs "141"). Cada tool tem JSON-Schema (`pdfcraft-cli tools`).

| Grupo | Tools (amostra relevante) | Aplicação no pdf-web-nando |
|---|---|---|
| **Editar texto existente** | `text_edit`, `text_edit_layout`, `content_list`, `content_update`, `content_delete`, `text_paragraphs`, `text_lines`, `text_fonts`, `text_font_import` | Exatamente o que estamos construindo (análise de blocos). Mostra o modelo deles: listar conteúdo → atualizar/excluir |
| **Redação verificada** | `redact_mark`, `redact_apply`, `redact_clear`, `doc_audit_redactions`, `doc_hidden_info`, `doc_remove_hidden` | `doc_audit_redactions` = detectar redação falsa; alinha com nosso teste adversarial de bytes |
| **Objetos/imagens como blocos** | `object_list`, `object_move`, `image_edit`, `image_hit`, `page_images`, `page_add_image` | A "sessão prática" que você descreveu: objetos selecionáveis e movíveis |
| **OCR** | `ocr_recognize`, `ocr_recognize_files`, `ocr_status` | Temos equivalente (Tesseract) |
| **Páginas** | `page_rotate/delete/move/extract/insert_blank/insert_file/replace/duplicate/render/number` | Já temos a maioria |
| **Comentários** | `comment_add/edit/delete/reply/list/set_status/mark`, `comments_summarize` | Família a avaliar (seção 3) |
| **Forms** | `form_fill`, `form_add_field`, `form_detect_fields`, `form_set_script` | Gap nosso |
| **Automação** | `command_list/run/batch`, `action_run`, `doc_batch` + servidor **MCP** | A tabela declarativa a abstrair |
| **Proteção** | `doc_protect`, `doc_unprotect`, `sign_document`, `sign_id_create` | Senha/assinatura = gaps |

**Como puxar atualizações do upstream:** `git fetch upstream && git merge upstream/main`
(o fork mantém histórico próprio para a linha corporativa de vocês).

### 7.2 — Pendências desta linha (decisões suas)

- **Rebranding obrigatório (licença):** remover marca ArtCraft/PdfCraft e `docs/brand/`
  para a versão corporativa de vocês. Ainda **não feito** — aguarda sua decisão de nome.
- **GUI desktop:** compilar `-p pdfcraft` exige instalar libs de dev adicionais; a
  CLI já cobre análise e automação. Fazer sob demanda.
- **Modelos de OCR:** `cargo xtask models` baixa os modelos (não executado ainda).

