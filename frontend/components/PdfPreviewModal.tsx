"use client";

import { useEffect, useRef, useState } from "react";
import type { PDFDocumentProxy } from "pdfjs-dist";
import { useI18n } from "@/lib/i18n";
import { ToolIcons } from "@/components/ToolIcons";

interface PdfPreviewModalProps {
  doc: PDFDocumentProxy;
  /** página inicial 1-based */
  initialPage: number;
  onClose: () => void;
}

/**
 * Visualizador ampliado (Fase 6, item F — lupa). Renderiza a página em tamanho
 * grande via pdf.js, com zoom +/- e navegação. Acessível (role=dialog, ESC).
 */
export function PdfPreviewModal({ doc, initialPage, onClose }: PdfPreviewModalProps) {
  const { t } = useI18n();
  const [page, setPage] = useState(initialPage);
  const [scale, setScale] = useState(1.4);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    dialogRef.current?.focus();
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const pdfPage = await doc.getPage(page);
      const viewport = pdfPage.getViewport({ scale });
      const canvas = canvasRef.current;
      if (!canvas || cancelled) return;
      const ctx = canvas.getContext("2d");
      if (!ctx) return;
      canvas.width = viewport.width;
      canvas.height = viewport.height;
      await pdfPage.render({ canvasContext: ctx, viewport }).promise;
    })();
    return () => {
      cancelled = true;
    };
  }, [doc, page, scale]);

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="viewer-modal"
        role="dialog"
        aria-modal="true"
        aria-label={t("viewer.open")}
        tabIndex={-1}
        ref={dialogRef}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="viewer-toolbar">
          <button type="button" onClick={() => setScale((s) => Math.max(0.5, s - 0.25))} aria-label={t("viewer.zoomOut")}>
            −
          </button>
          <button type="button" onClick={() => setScale((s) => Math.min(3, s + 0.25))} aria-label={t("viewer.zoomIn")}>
            +
          </button>
          <span>{Math.round(scale * 100)}%</span>
          <span className="spacer" />
          <button type="button" onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1} aria-label={t("viewer.prev")}>
            ‹
          </button>
          <span>
            {page} / {doc.numPages}
          </span>
          <button
            type="button"
            onClick={() => setPage((p) => Math.min(doc.numPages, p + 1))}
            disabled={page >= doc.numPages}
            aria-label={t("viewer.next")}
          >
            ›
          </button>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Fechar">
            ×
          </button>
        </div>
        <div className="viewer-canvas-wrap">
          <canvas ref={canvasRef} aria-label={`Página ${page}`} />
        </div>
      </div>
    </div>
  );
}

/** Botão de lupa para abrir o visualizador (ícone search). */
export function ZoomButton({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button type="button" className="page-zoom" onClick={onClick} aria-label={label}>
      <span style={{ display: "inline-block", width: 15, height: 15 }}>{ToolIcons.search}</span>
    </button>
  );
}
