"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { PDFDocumentProxy } from "pdfjs-dist";
import { pdfjs } from "@/lib/pdfjs";
import { useI18n } from "@/lib/i18n";
import type { LocalElement, InsertTool } from "@/components/contentEditTypes";

interface ContentCanvasProps {
  doc: PDFDocumentProxy;
  page: number;
  onPageChange: (page: number) => void;
  elements: LocalElement[];
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  onUpdate: (id: string, patch: Partial<LocalElement>) => void;
  /** Remove o elemento (usado pelo atalho Delete). */
  onRemove: (id: string) => void;
  /** Ferramenta de inserção ativa; null = apenas selecionar/mover. */
  insertTool: InsertTool;
  /** Limpa a ferramenta de inserção (usado pelo Esc). */
  onCancelInsert: () => void;
  /** Insere um elemento do tipo ativo em (xPt, yPt). */
  onInsertAt: (xPt: number, yPt: number) => void;
  /** Seleção de área (pt, top-left) para edição de texto existente. */
  onSelectArea?: (rect: { x: number; y: number; width: number; height: number }) => void;
  /** Id destacado por hover (vínculo com a mini-lista do painel). */
  hoveredId?: string | null;
  onHover?: (id: string | null) => void;
  /** Largura alvo do canvas em px (ajusta a escala de render). */
  width?: number;
}

type DragState =
  | { kind: "move"; id: string; startX: number; startY: number; origX: number; origY: number }
  | { kind: "resize"; id: string; startX: number; startY: number; origW: number; origH: number };

/**
 * Canvas de edição visual: renderiza a página (pdf.js) e sobrepõe os elementos
 * como caixas posicionáveis. Converte px<->pt usando a escala do viewport,
 * de modo que o estado permanece em pt (contrato do backend, inalterado).
 */
export function ContentCanvas({
  doc,
  page,
  onPageChange,
  elements,
  selectedId,
  onSelect,
  onUpdate,
  onRemove,
  insertTool,
  onCancelInsert,
  onInsertAt,
  onSelectArea,
  hoveredId,
  onHover,
  width = 620,
}: ContentCanvasProps) {
  const { t } = useI18n();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);
  const [size, setSize] = useState({ w: 0, h: 0 });
  const dragRef = useRef<DragState | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  // Marquee de seleção de área (px, relativo ao overlay) para "editar texto".
  const [marquee, setMarquee] = useState<{ x: number; y: number; w: number; h: number } | null>(null);
  const marqueeStart = useRef<{ x: number; y: number } | null>(null);

  // Render da página na escala derivada da largura alvo.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const pdfPage = await doc.getPage(page);
      const base = pdfPage.getViewport({ scale: 1 });
      const s = width / base.width;
      const viewport = pdfPage.getViewport({ scale: s });
      const canvas = canvasRef.current;
      if (!canvas || cancelled) return;
      const ctx = canvas.getContext("2d");
      if (!ctx) return;
      canvas.width = viewport.width;
      canvas.height = viewport.height;
      await pdfPage.render({ canvasContext: ctx, viewport }).promise;
      if (cancelled) return;
      setScale(s);
      setSize({ w: viewport.width, h: viewport.height });
    })();
    return () => {
      cancelled = true;
    };
  }, [doc, page, width]);

  const ptToPx = useCallback((pt: number) => pt * scale, [scale]);
  const pxToPt = useCallback((px: number) => px / scale, [scale]);

  // Tamanho da página em pt, para manter os elementos dentro dos limites.
  const pagePt = { w: scale ? size.w / scale : 0, h: scale ? size.h / scale : 0 };
  const clampX = useCallback(
    (x: number) => (pagePt.w ? Math.max(0, Math.min(x, Math.round(pagePt.w))) : Math.max(0, x)),
    [pagePt.w],
  );
  const clampY = useCallback(
    (y: number) => (pagePt.h ? Math.max(0, Math.min(y, Math.round(pagePt.h))) : Math.max(0, y)),
    [pagePt.h],
  );

  // Atalhos de teclado: Delete remove o selecionado; Esc cancela inserção/seleção;
  // setas movem 1pt (10pt com Shift). Ignorados enquanto edita texto num input.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const tag = (e.target as HTMLElement)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
      if (e.key === "Escape") {
        if (insertTool) onCancelInsert();
        else onSelect(null);
        return;
      }
      if (!selectedId) return;
      const el = elements.find((x) => x.id === selectedId);
      if (!el) return;
      if (e.key === "Delete" || e.key === "Backspace") {
        e.preventDefault();
        onRemove(selectedId);
        return;
      }
      const step = e.shiftKey ? 10 : 1;
      const deltas: Record<string, [number, number]> = {
        ArrowLeft: [-step, 0],
        ArrowRight: [step, 0],
        ArrowUp: [0, -step],
        ArrowDown: [0, step],
      };
      const d = deltas[e.key];
      if (d) {
        e.preventDefault();
        onUpdate(selectedId, { x: clampX(el.x + d[0]), y: clampY(el.y + d[1]) });
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [selectedId, elements, insertTool, onRemove, onSelect, onCancelInsert, onUpdate, clampX, clampY]);

  // Movimento/redimensionamento globais (mouse move/up no documento).
  useEffect(() => {
    function onMove(e: MouseEvent) {
      const d = dragRef.current;
      if (!d) return;
      const dxPt = pxToPt(e.clientX - d.startX);
      const dyPt = pxToPt(e.clientY - d.startY);
      if (d.kind === "move") {
        onUpdate(d.id, { x: clampX(Math.round(d.origX + dxPt)), y: clampY(Math.round(d.origY + dyPt)) });
      } else {
        onUpdate(d.id, {
          width: Math.max(8, Math.round(d.origW + dxPt)),
          height: Math.max(8, Math.round(d.origH + dyPt)),
        });
      }
    }
    function onUp() {
      dragRef.current = null;
    }
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    return () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
  }, [onUpdate, pxToPt, clampX, clampY]);

  function startMove(e: React.MouseEvent, el: LocalElement) {
    e.stopPropagation();
    onSelect(el.id);
    dragRef.current = {
      kind: "move",
      id: el.id,
      startX: e.clientX,
      startY: e.clientY,
      origX: el.x,
      origY: el.y,
    };
  }

  function startResize(e: React.MouseEvent, el: LocalElement) {
    e.stopPropagation();
    if (el.type === "text") return;
    onSelect(el.id);
    dragRef.current = {
      kind: "resize",
      id: el.id,
      startX: e.clientX,
      startY: e.clientY,
      origW: el.width,
      origH: el.height,
    };
  }

  function onOverlayClick(e: React.MouseEvent) {
    if (insertTool === "edit-text") return; // tratado pelo marquee (mousedown/up)
    if (!insertTool) {
      onSelect(null);
      return;
    }
    const rect = overlayRef.current?.getBoundingClientRect();
    if (!rect) return;
    const xPt = Math.round(pxToPt(e.clientX - rect.left));
    const yPt = Math.round(pxToPt(e.clientY - rect.top));
    onInsertAt(xPt, yPt);
  }

  // --- Marquee de seleção de área (ferramenta "editar texto") ---
  function onOverlayMouseDown(e: React.MouseEvent) {
    if (insertTool !== "edit-text") return;
    const rect = overlayRef.current?.getBoundingClientRect();
    if (!rect) return;
    e.preventDefault();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    marqueeStart.current = { x, y };
    setMarquee({ x, y, w: 0, h: 0 });
  }

  function onOverlayMouseMove(e: React.MouseEvent) {
    if (insertTool !== "edit-text" || !marqueeStart.current) return;
    const rect = overlayRef.current?.getBoundingClientRect();
    if (!rect) return;
    const cx = e.clientX - rect.left;
    const cy = e.clientY - rect.top;
    const s = marqueeStart.current;
    setMarquee({ x: Math.min(s.x, cx), y: Math.min(s.y, cy), w: Math.abs(cx - s.x), h: Math.abs(cy - s.y) });
  }

  function onOverlayMouseUp() {
    if (insertTool !== "edit-text" || !marquee || !marqueeStart.current) {
      marqueeStart.current = null;
      return;
    }
    const m = marquee;
    marqueeStart.current = null;
    setMarquee(null);
    if (m.w < 6 || m.h < 6) return; // ignora cliques/seleções minúsculas
    onSelectArea?.({
      x: Math.round(pxToPt(m.x)),
      y: Math.round(pxToPt(m.y)),
      width: Math.round(pxToPt(m.w)),
      height: Math.round(pxToPt(m.h)),
    });
  }

  const onPage = elements.filter((el) => el.page === page);

  return (
    <div className="cc-wrap">
      <div className="cc-toolbar">
        <button
          type="button"
          onClick={() => onPageChange(Math.max(1, page - 1))}
          disabled={page <= 1}
          aria-label={t("viewer.prev")}
        >
          ‹
        </button>
        <span>
          {page} / {doc.numPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(Math.min(doc.numPages, page + 1))}
          disabled={page >= doc.numPages}
          aria-label={t("viewer.next")}
        >
          ›
        </button>
        {insertTool && <span className="cc-hint-insert">{t("content.canvas.insertHint")}</span>}
      </div>

      <div className="cc-stage" style={{ width: size.w || width }}>
        <canvas ref={canvasRef} className="cc-canvas" aria-label={`Página ${page}`} />
        <div
          ref={overlayRef}
          className={`cc-overlay${insertTool ? " inserting" : ""}`}
          style={{ width: size.w, height: size.h }}
          onClick={onOverlayClick}
          onMouseDown={onOverlayMouseDown}
          onMouseMove={onOverlayMouseMove}
          onMouseUp={onOverlayMouseUp}
          role="presentation"
        >
          {onPage.map((el) => {
            const selected = el.id === selectedId;
            const left = ptToPx(el.x);
            const top = ptToPx(el.y);
            const boxW = el.type === "text" ? undefined : ptToPx(el.width);
            const boxH = el.type === "text" ? undefined : ptToPx(el.height);
            return (
              <div
                key={el.id}
                className={`cc-el cc-el-${el.type}${selected ? " selected" : ""}${
                  hoveredId === el.id ? " hovered" : ""
                }`}
                style={{
                  left,
                  top,
                  width: boxW,
                  height: boxH,
                  ...(el.type === "text"
                    ? {
                        fontSize: ptToPx(el.size ?? 14),
                        color: el.color ?? "#000000",
                        fontFamily: fontFamilyOf(el.fontResource ? el.fontFamily : el.font),
                        fontWeight:
                          /bold/i.test((el.fontResource ? el.fontFamily : el.font) ?? "") ? 700 : 400,
                        fontStyle:
                          /italic|oblique/i.test((el.fontResource ? el.fontFamily : el.font) ?? "")
                            ? "italic"
                            : "normal",
                      }
                    : {}),
                  ...(el.type === "redact" ? { background: el.color ?? "#ffffff" } : {}),
                }}
                onMouseDown={(e) => startMove(e, el)}
                onMouseEnter={() => onHover?.(el.id)}
                onMouseLeave={() => onHover?.(null)}
                onClick={(e) => {
                  e.stopPropagation();
                  onSelect(el.id);
                }}
                onDoubleClick={(e) => {
                  e.stopPropagation();
                  if (el.type === "text") setEditingId(el.id);
                }}
              >
                {el.type === "image" && el.previewUrl && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={el.previewUrl} alt="" className="cc-img" draggable={false} />
                )}
                {el.type === "text" &&
                  (editingId === el.id ? (
                    <input
                      className="cc-text-input"
                      autoFocus
                      value={el.text}
                      onChange={(e) => onUpdate(el.id, { text: e.target.value })}
                      onBlur={() => setEditingId(null)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") setEditingId(null);
                      }}
                      onMouseDown={(e) => e.stopPropagation()}
                      onClick={(e) => e.stopPropagation()}
                      style={{ font: "inherit", color: "inherit" }}
                    />
                  ) : (
                    <span className="cc-text">{el.text || t("content.canvas.textPlaceholder")}</span>
                  ))}

                {selected && el.type !== "text" && (
                  <span
                    className="cc-handle"
                    onMouseDown={(e) => startResize(e, el)}
                    role="presentation"
                    aria-hidden="true"
                  />
                )}
              </div>
            );
          })}
          {marquee && (
            <div
              className="cc-marquee"
              style={{ left: marquee.x, top: marquee.y, width: marquee.w, height: marquee.h }}
              aria-hidden="true"
            />
          )}
        </div>
      </div>
      <p className="cc-shortcuts">{t("content.shortcuts")}</p>
    </div>
  );
}

function fontFamilyOf(font?: string): string {
  const f = (font ?? "").toLowerCase();
  if (/times|serif|georgia|minion|garamond|roman/.test(f)) return "'Times New Roman', Times, serif";
  if (/courier|mono|consolas/.test(f)) return "'Courier New', Courier, monospace";
  return "Helvetica, Arial, sans-serif";
}
