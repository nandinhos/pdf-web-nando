"use client";
import { useState } from "react";
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  arrayMove,
  rectSortingStrategy,
  sortableKeyboardCoordinates,
  useSortable,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import type { PDFDocumentProxy } from "pdfjs-dist";
import { PageThumbnail } from "@/components/PageThumbnail";
import { PdfPreviewModal, ZoomButton } from "@/components/PdfPreviewModal";

/** Representa uma página no grid de edição. id estável; origin = página 1-based original. */
export interface GridPage {
  id: string;
  origin: number;
  rotation: number;
  selected: boolean;
}

interface PageGridProps {
  doc: PDFDocumentProxy;
  pages: GridPage[];
  onChange: (pages: GridPage[]) => void;
}

function SortablePage({
  page,
  doc,
  onRotate,
  onDelete,
  onToggle,
  onZoom,
}: {
  page: GridPage;
  doc: PDFDocumentProxy;
  onRotate: () => void;
  onDelete: () => void;
  onToggle: () => void;
  onZoom: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition } = useSortable({ id: page.id });
  const style = { transform: CSS.Transform.toString(transform), transition };

  return (
    <li ref={setNodeRef} style={style} className={`page-card${page.selected ? " selected" : ""}`}>
      <div className="page-thumb" {...attributes} {...listeners} aria-label={`Reordenar página ${page.origin}`}>
        <PageThumbnail doc={doc} pageNumber={page.origin} rotation={page.rotation} />
      </div>
      <div className="page-actions">
        <label className="page-select">
          <input type="checkbox" checked={page.selected} onChange={onToggle} aria-label="Selecionar página" />
          <span>#{page.origin}</span>
        </label>
        <ZoomButton onClick={onZoom} label={`Visualizar página ${page.origin}`} />
        <button type="button" onClick={onRotate} aria-label="Rotacionar 90°">⟳</button>
        <button type="button" onClick={onDelete} aria-label="Excluir página">✕</button>
      </div>
    </li>
  );
}

/**
 * Grid interativo de páginas: arrastar para reordenar (dnd-kit), rotacionar,
 * excluir e selecionar inline (PRD §8).
 */
export function PageGrid({ doc, pages, onChange }: PageGridProps) {
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = pages.findIndex((p) => p.id === active.id);
    const newIndex = pages.findIndex((p) => p.id === over.id);
    onChange(arrayMove(pages, oldIndex, newIndex));
  }

  function update(id: string, patch: Partial<GridPage>) {
    onChange(pages.map((p) => (p.id === id ? { ...p, ...patch } : p)));
  }

  const [previewPage, setPreviewPage] = useState<number | null>(null);

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={pages.map((p) => p.id)} strategy={rectSortingStrategy}>
        <ul className="page-grid">
          {pages.map((page) => (
            <SortablePage
              key={page.id}
              page={page}
              doc={doc}
              onRotate={() => update(page.id, { rotation: (page.rotation + 90) % 360 })}
              onDelete={() => onChange(pages.filter((p) => p.id !== page.id))}
              onToggle={() => update(page.id, { selected: !page.selected })}
              onZoom={() => setPreviewPage(page.origin)}
            />
          ))}
        </ul>
      </SortableContext>
      {previewPage !== null && (
        <PdfPreviewModal doc={doc} initialPage={previewPage} onClose={() => setPreviewPage(null)} />
      )}
    </DndContext>
  );
}
