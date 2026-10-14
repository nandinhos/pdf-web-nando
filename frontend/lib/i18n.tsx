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
    "tab.merge": "Mesclar",
    "tab.images": "Imagens",
    "tab.ocr": "OCR",
    "tab.signature": "Verificar Assinatura",
    "tool.edit.desc": "Ordene páginas: reordenar, girar, excluir e duplicar (não edita conteúdo).",
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
  },
  en: {
    "app.subtitle": "Pages: reorder, rotate, delete, split and merge.",
    "launcher.title": "What are we doing today?",
    "launcher.sub": "Pick a tool to start. Your files are processed privately.",
    "tab.edit": "Arrange",
    "tab.merge": "Merge",
    "tab.images": "Images",
    "tab.ocr": "OCR",
    "tab.signature": "Verify Signature",
    "tool.edit.desc": "Arrange pages: reorder, rotate, delete and duplicate (no content editing).",
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
