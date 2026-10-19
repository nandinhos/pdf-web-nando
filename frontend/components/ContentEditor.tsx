"use client";

import { useCallback, useEffect, useState } from "react";
import type { PDFDocumentProxy } from "pdfjs-dist";
import { uploadImage, extractTextArea, analyzeLayout, docFonts, type DocFontInfo } from "@/lib/apiClient";
import { ContentCanvas } from "@/components/ContentCanvas";
import { ContentEditPanel } from "@/components/ContentEditPanel";
import { useHistory } from "@/components/useHistory";
import type { LocalElement, InsertTool } from "@/components/contentEditTypes";

/**
 * Orquestrador da ferramenta "Editar": detém o estado compartilhado (lista de
 * elementos, seleção, ferramenta de inserção) e compõe o canvas visual com o
 * painel lateral de ajuste fino. O backend/contrato permanece inalterado.
 */
export function ContentEditor({ doc, fileId }: { doc: PDFDocumentProxy; fileId: string }) {
  const history = useHistory([]);
  const elements = history.elements;
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [insertTool, setInsertTool] = useState<InsertTool>(null);
  const [page, setPage] = useState(1);
  const [busy, setBusy] = useState(false);
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  // imagem pendente (upload feito ao escolher o arquivo) a inserir no próximo clique.
  const [pendingImage, setPendingImage] = useState<{ imageId: string; previewUrl: string } | null>(null);
  // fontes já embutidas no documento, para o seletor de fonte (reuso sem reembutir).
  const [docFontsList, setDocFontsList] = useState<DocFontInfo[]>([]);

  // Carrega as fontes embutidas do documento uma vez.
  useEffect(() => {
    let active = true;
    docFonts({ fileId })
      .then((r) => active && setDocFontsList(r.fonts))
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [fileId]);

  // Libera os object URLs das imagens ao desmontar.
  useEffect(() => {
    return () => {
      elements.forEach((el) => el.previewUrl && URL.revokeObjectURL(el.previewUrl));
      if (pendingImage) URL.revokeObjectURL(pendingImage.previewUrl);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Atalhos globais de undo/redo. Ignorados ao digitar num campo (o input nativo
  // trata seu próprio Ctrl+Z); fora de campos, operam no histórico de elementos.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const tag = (e.target as HTMLElement)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
      const mod = e.ctrlKey || e.metaKey;
      if (!mod || e.key.toLowerCase() !== "z") return;
      e.preventDefault();
      if (e.shiftKey) history.redo();
      else history.undo();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [history]);

  const update = useCallback((id: string, patch: Partial<LocalElement>) => {
    history.coalesce((prev) => prev.map((el) => (el.id === id ? ({ ...el, ...patch } as LocalElement) : el)));
  }, [history]);

  const remove = useCallback((id: string) => {
    history.commit((prev) => {
      const target = prev.find((el) => el.id === id);
      if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl);
      return prev.filter((el) => el.id !== id);
    });
    setSelectedId((cur) => (cur === id ? null : cur));
  }, [history]);

  const nextId = () => `el-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;

  function insertAt(xPt: number, yPt: number) {
    if (!insertTool) return;
    const id = nextId();
    if (insertTool === "text") {
      history.commit((prev) => [
        ...prev,
        { id, type: "text", page, x: xPt, y: yPt, text: "", font: "helvetica", size: 14, color: "#000000" },
      ]);
    } else if (insertTool === "redact") {
      history.commit((prev) => [
        ...prev,
        { id, type: "redact", page, x: xPt, y: yPt, width: 160, height: 20, color: "#ffffff" },
      ]);
    } else if (insertTool === "image" && pendingImage) {
      history.commit((prev) => [
        ...prev,
        {
          id,
          type: "image",
          page,
          x: xPt,
          y: yPt,
          width: 120,
          height: 120,
          imageId: pendingImage.imageId,
          previewUrl: pendingImage.previewUrl,
        },
      ]);
      setPendingImage(null);
    }
    setSelectedId(id);
    setInsertTool(null);
  }

  async function pickImage(file: File) {
    setBusy(true);
    try {
      const { imageId } = await uploadImage(file);
      const previewUrl = URL.createObjectURL(file);
      setPendingImage({ imageId, previewUrl });
      setInsertTool("image");
    } catch {
      setInsertTool(null);
    } finally {
      setBusy(false);
    }
  }

  /**
   * Detecta blocos editáveis da página atual e os injeta (R4): cada bloco de
   * texto nasce pareado com uma redação REAL da sua área (apaga o original) mais
   * uma caixa de texto pré-preenchida; cada imagem vira um bloco de remoção
   * (redação real) selecionável — basta apagá-lo da lista para manter a imagem.
   */
  async function detectBlocks(): Promise<{ scanned: boolean; count: number }> {
    setBusy(true);
    try {
      const { blocks, scanned } = await analyzeLayout({ fileId, page });
      const injected: LocalElement[] = [];
      for (const b of blocks) {
        if (b.kind === "text") {
          // Tamanho real da fonte (backend); fallback à estimativa só se ausente.
          const size =
            b.fontSize && b.fontSize > 0
              ? Math.round(b.fontSize)
              : Math.max(8, Math.min(48, Math.round(b.height * 0.8)));
          injected.push({
            id: nextId(), type: "redact", page, x: b.x, y: b.y,
            width: b.width, height: b.height, color: "#ffffff", hard: true,
          });
          injected.push({
            id: nextId(), type: "text", page, x: b.x + 1, y: b.y + 1,
            text: b.text ?? "", font: "helvetica", size, color: "#000000",
            fontResource: b.fontResource ?? undefined, fontResourcePage: page,
            fontFamily: b.fontFamily ?? undefined,
          });
        } else {
          injected.push({
            id: nextId(), type: "redact", page, x: b.x, y: b.y,
            width: b.width, height: b.height, color: "#ffffff", hard: true,
          });
        }
      }
      if (injected.length > 0) {
        history.commit((prev) => [...prev, ...injected]);
      }
      return { scanned, count: blocks.length };
    } finally {
      setBusy(false);
    }
  }

  /**
   * Edição de texto existente: captura o texto na área selecionada, aplica uma
   * redação REAL (apaga de verdade, não camufla) e cria uma caixa de texto
   * pré-preenchida no mesmo lugar para o usuário alterar.
   */
  async function editTextArea(rect: { x: number; y: number; width: number; height: number }) {
    setBusy(true);
    setInsertTool(null);
    try {
      const { text } = await extractTextArea({ fileId, page, ...rect });
      const redactId = nextId();
      const textId = nextId();
      // Tamanho de fonte aproximado pela altura da área (clamp 8–48pt).
      const size = Math.max(8, Math.min(48, Math.round(rect.height * 0.7)));
      history.commit((prev) => [
        ...prev,
        { id: redactId, type: "redact", page, x: rect.x, y: rect.y, width: rect.width, height: rect.height, color: "#ffffff", hard: true },
        { id: textId, type: "text", page, x: rect.x + 2, y: rect.y + 2, text, font: "helvetica", size, color: "#000000" },
      ]);
      setSelectedId(textId);
    } catch {
      // silencioso: usuário pode tentar outra área
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="workspace">
      <div className="panel">
        <ContentCanvas
          doc={doc}
          page={page}
          onPageChange={setPage}
          elements={elements}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onUpdate={update}
          onRemove={remove}
          insertTool={insertTool}
          onCancelInsert={() => setInsertTool(null)}
          onInsertAt={insertAt}
          onSelectArea={editTextArea}
          hoveredId={hoveredId}
          onHover={setHoveredId}
        />
      </div>
      <ContentEditPanel
        fileId={fileId}
        elements={elements}
        selectedId={selectedId}
        insertTool={insertTool}
        busy={busy}
        onSetInsertTool={setInsertTool}
        onPickImage={pickImage}
        onDetectBlocks={detectBlocks}
        docFonts={docFontsList}
        onUndo={history.undo}
        onRedo={history.redo}
        canUndo={history.canUndo}
        canRedo={history.canRedo}
        onSelect={(id) => {
          setSelectedId(id);
          if (id) {
            const el = elements.find((e) => e.id === id);
            if (el) setPage(el.page);
          }
        }}
        onUpdate={update}
        onRemove={remove}
        onBusy={setBusy}
        onHover={setHoveredId}
      />
    </div>
  );
}
