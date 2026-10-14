"use client";

import { useEffect, useRef } from "react";
import { pdfjs } from "@/lib/pdfjs";
import type { PDFDocumentProxy } from "pdfjs-dist";

interface PageThumbnailProps {
  doc: PDFDocumentProxy;
  pageNumber: number;
  rotation?: number;
  width?: number;
}

/**
 * Renderiza uma miniatura de uma página do PDF já carregado (pdf.js).
 * A rotação visual reflete a edição pendente antes do export.
 */
export function PageThumbnail({ doc, pageNumber, rotation = 0, width = 120 }: PageThumbnailProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const page = await doc.getPage(pageNumber);
      const base = page.getViewport({ scale: 1 });
      const scale = width / base.width;
      const viewport = page.getViewport({ scale, rotation });
      const canvas = canvasRef.current;
      if (!canvas || cancelled) return;
      const ctx = canvas.getContext("2d");
      if (!ctx) return;
      canvas.width = viewport.width;
      canvas.height = viewport.height;
      await page.render({ canvasContext: ctx, viewport }).promise;
    })();
    return () => {
      cancelled = true;
    };
  }, [doc, pageNumber, rotation, width]);

  return <canvas ref={canvasRef} aria-label={`Página ${pageNumber}`} />;
}
