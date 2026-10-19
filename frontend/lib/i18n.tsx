"use client";

import { createContext, useContext, useEffect, useState } from "react";

export type Lang = "pt" | "en";

/** Dicionários de tradução (PRD §8 — PT-BR/EN). */
const dict: Record<Lang, Record<string, string>> = {
  pt: {
    "app.subtitle": "Páginas: reordenar, rotacionar, excluir, dividir e mesclar.",
    "launcher.title": "O que vamos fazer hoje?",
    "launcher.sub": "Escolha uma ferramenta para começar. Seus arquivos são processados com privacidade.",
    "tab.edit": "Ordenar",
    "tab.content": "Editar conteúdo",
    "tab.merge": "Mesclar",
    "tab.images": "Imagens",
    "tab.ocr": "OCR",
    "tab.signature": "Verificar Assinatura",
    "tool.edit.desc": "Ordene páginas: reordenar, girar, excluir e duplicar (não edita conteúdo).",
    "tool.content.desc": "Edite o conteúdo: caixas de texto (fonte e tamanho), inserir imagens e ocultar elementos.",
    "tool.merge.desc": "Combine vários PDFs em um só, na ordem desejada.",
    "tool.images.desc": "Converta imagens em PDF ou extraia imagens de um PDF.",
    "tool.ocr.desc": "Torne um PDF escaneado pesquisável, com texto selecionável.",
    "tool.sign.desc": "Verifique assinaturas digitais e a integridade do documento.",
    "modal.dropPdf": "Arraste um PDF aqui",
    "modal.dropImages": "Arraste imagens aqui",
    "modal.orClick": "ou clique para selecionar",
    "modal.pdfHint": "Apenas arquivos PDF",
    "modal.imgHint": "Imagens JPG ou PNG",
    "common.back": "Menu",
    "common.language": "Idioma",
    "common.theme": "Tema",
    "common.quality": "Qualidade de exportação",
    "common.filename": "Nome do arquivo",
    "common.processing": "Processando…",
    "quality.low": "Baixa",
    "quality.medium": "Média",
    "quality.high": "Alta",
    "quality.hint": "Menor qualidade = arquivo menor.",
    "tab.split": "Dividir",
    "tool.split.desc": "Divida o PDF em vários arquivos (por páginas, cortes ou metades).",
    "viewer.zoomIn": "Ampliar",
    "viewer.zoomOut": "Reduzir",
    "viewer.prev": "Página anterior",
    "viewer.next": "Próxima página",
    "viewer.open": "Visualizar página",
    "theme.light": "Claro",
    "theme.dark": "Escuro",
    "status.sending": "Enviando…",
    "content.panel.title": "Editar conteúdo",
    "content.panel.hint": "Adicione elementos sobre o PDF: caixas de texto, imagens ou tarjas para ocultar trechos. Tudo é achatado no arquivo ao exportar.",
    "content.add.text": "Caixa de texto",
    "content.add.image": "Imagem",
    "content.add.redact": "Tarja (ocultar)",
    "content.add.editText": "Editar texto",
    "content.editText.hint": "Arraste um retângulo sobre o texto que deseja alterar.",
    "content.undo": "Desfazer",
    "content.redo": "Refazer",
    "content.detect": "Detectar blocos",
    "content.detect.scanned": "Página parece escaneada (sem texto digital). Use a ferramenta OCR para torná-la editável.",
    "content.detect.empty": "Nenhum bloco detectado nesta página.",
    "content.fontSubstituted": "A fonte original não cobria o texto novo; usada uma fonte padrão (Helvetica) neste trecho.",
    "content.field.page": "Página",
    "content.field.text": "Texto",
    "content.field.font": "Fonte",
    "content.field.bold": "Negrito",
    "content.field.italic": "Itálico",
    "content.font.detected": "(detectada)",
    "content.font.embedded": "Fontes do documento",
    "content.font.standard": "Fontes padrão",
    "content.field.size": "Tamanho",
    "content.field.color": "Cor",
    "content.field.hard": "Remover conteúdo (irreversível)",
    "content.field.hardHint": "Rasteriza a página: o texto/imagem sob a tarja é destruído e deixa de ser extraível.",
    "content.field.position": "Posição (x, y)",
    "content.field.sizeWH": "Tamanho (larg., alt.)",
    "content.field.image": "Imagem",
    "content.empty": "Nenhum elemento. Adicione um acima.",
    "content.remove": "Remover elemento",
    "content.export": "Exportar PDF",
    "content.download": "Baixar resultado",
    "content.exportOcr": "Salvar + tornar pesquisável (OCR)",
    "content.ocr.status": "OCR",
    "content.error": "Falha ao editar o conteúdo",
    "content.imageHint": "Selecione uma imagem (JPG/PNG) para inserir.",
    "content.canvas.insertHint": "Clique na página para posicionar o elemento.",
    "content.canvas.selectHint": "Selecione um elemento no canvas para ajustar.",
    "content.canvas.textPlaceholder": "Texto…",
    "content.section.adjust": "Ajuste do elemento",
    "content.section.elements": "Elementos",
    "content.shortcuts": "Dica: arraste para mover · setas para ajustar (Shift = 10pt) · Delete remove · Esc cancela.",
  },
  en: {
    "app.subtitle": "Pages: reorder, rotate, delete, split and merge.",
    "launcher.title": "What are we doing today?",
    "launcher.sub": "Pick a tool to start. Your files are processed privately.",
    "tab.edit": "Arrange",
    "tab.content": "Edit content",
    "tab.merge": "Merge",
    "tab.images": "Images",
    "tab.ocr": "OCR",
    "tab.signature": "Verify Signature",
    "tool.edit.desc": "Arrange pages: reorder, rotate, delete and duplicate (no content editing).",
    "tool.content.desc": "Edit content: text boxes (font and size), insert images and hide elements.",
    "tool.merge.desc": "Combine several PDFs into one, in your order.",
    "tool.images.desc": "Convert images to PDF or extract images from a PDF.",
    "tool.ocr.desc": "Make a scanned PDF searchable, with selectable text.",
    "tool.sign.desc": "Verify digital signatures and document integrity.",
    "modal.dropPdf": "Drag a PDF here",
    "modal.dropImages": "Drag images here",
    "modal.orClick": "or click to select",
    "modal.pdfHint": "PDF files only",
    "modal.imgHint": "JPG or PNG images",
    "common.back": "Menu",
    "common.language": "Language",
    "common.theme": "Theme",
    "common.quality": "Export quality",
    "common.filename": "File name",
    "common.processing": "Processing…",
    "quality.low": "Low",
    "quality.medium": "Medium",
    "quality.high": "High",
    "quality.hint": "Lower quality = smaller file.",
    "tab.split": "Split",
    "tool.split.desc": "Split the PDF into multiple files (by pages, cut points or halves).",
    "viewer.zoomIn": "Zoom in",
    "viewer.zoomOut": "Zoom out",
    "viewer.prev": "Previous page",
    "viewer.next": "Next page",
    "viewer.open": "Preview page",
    "theme.light": "Light",
    "theme.dark": "Dark",
    "status.sending": "Uploading…",
    "content.panel.title": "Edit content",
    "content.panel.hint": "Add elements over the PDF: text boxes, images or redaction boxes to hide parts. Everything is flattened into the file on export.",
    "content.add.text": "Text box",
    "content.add.image": "Image",
    "content.add.redact": "Redaction (hide)",
    "content.add.editText": "Edit text",
    "content.editText.hint": "Drag a rectangle over the text you want to change.",
    "content.undo": "Undo",
    "content.redo": "Redo",
    "content.detect": "Detect blocks",
    "content.detect.scanned": "Page looks scanned (no digital text). Use the OCR tool to make it editable.",
    "content.detect.empty": "No blocks detected on this page.",
    "content.fontSubstituted": "The original font could not cover the new text; a standard font (Helvetica) was used here.",
    "content.field.page": "Page",
    "content.field.text": "Text",
    "content.field.font": "Font",
    "content.field.bold": "Bold",
    "content.field.italic": "Italic",
    "content.font.detected": "(detected)",
    "content.font.embedded": "Document fonts",
    "content.font.standard": "Standard fonts",
    "content.field.size": "Size",
    "content.field.color": "Color",
    "content.field.hard": "Remove content (irreversible)",
    "content.field.hardHint": "Rasterizes the page: text/image under the mark is destroyed and no longer extractable.",
    "content.field.position": "Position (x, y)",
    "content.field.sizeWH": "Size (width, height)",
    "content.field.image": "Image",
    "content.empty": "No elements. Add one above.",
    "content.remove": "Remove element",
    "content.export": "Export PDF",
    "content.download": "Download result",
    "content.exportOcr": "Save + make searchable (OCR)",
    "content.ocr.status": "OCR",
    "content.error": "Failed to edit content",
    "content.imageHint": "Pick an image (JPG/PNG) to insert.",
    "content.canvas.insertHint": "Click on the page to place the element.",
    "content.canvas.selectHint": "Select an element on the canvas to adjust.",
    "content.canvas.textPlaceholder": "Text…",
    "content.section.adjust": "Element settings",
    "content.section.elements": "Elements",
    "content.shortcuts": "Tip: drag to move · arrows to nudge (Shift = 10pt) · Delete to remove · Esc to cancel.",
  },
};

interface I18nCtx {
  lang: Lang;
  setLang: (l: Lang) => void;
  t: (key: string) => string;
}

const I18nContext = createContext<I18nCtx | null>(null);

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [lang, setLangState] = useState<Lang>("pt");

  useEffect(() => {
    const stored = (typeof window !== "undefined" && window.localStorage.getItem("lang")) as Lang | null;
    if (stored === "pt" || stored === "en") {
      setLangState(stored);
    }
  }, []);

  function setLang(l: Lang) {
    setLangState(l);
    if (typeof window !== "undefined") {
      window.localStorage.setItem("lang", l);
      document.documentElement.lang = l === "pt" ? "pt-BR" : "en";
    }
  }

  const t = (key: string) => dict[lang][key] ?? key;

  return <I18nContext.Provider value={{ lang, setLang, t }}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nCtx {
  const ctx = useContext(I18nContext);
  if (!ctx) {
    throw new Error("useI18n deve ser usado dentro de I18nProvider");
  }
  return ctx;
}
