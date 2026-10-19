"use client";

import { useEffect, useRef, useState } from "react";
import {
  contentEdit,
  contentEditWithOcr,
  getJob,
  subscribeJob,
  withName,
  type ContentEditElement,
  type DocFontInfo,
  type JobStatus,
  type SingleResult,
} from "@/lib/apiClient";
import { useI18n } from "@/lib/i18n";
import { Spinner } from "@/components/Spinner";
import { FilenameField } from "@/components/ExportControls";
import { applyFontChoice, fontSelectValue, toggleStyle } from "@/components/fontChoice";
import type { LocalElement, InsertTool } from "@/components/contentEditTypes";

const FONTS = [
  "helvetica",
  "helvetica-bold",
  "helvetica-oblique",
  "times",
  "times-bold",
  "times-italic",
  "courier",
  "courier-bold",
];

interface ContentEditPanelProps {
  fileId: string;
  elements: LocalElement[];
  selectedId: string | null;
  insertTool: InsertTool;
  busy: boolean;
  onSetInsertTool: (tool: InsertTool) => void;
  onPickImage: (file: File) => void;
  onDetectBlocks: () => Promise<{ scanned: boolean; count: number }>;
  docFonts: DocFontInfo[];
  onUndo: () => void;
  onRedo: () => void;
  canUndo: boolean;
  canRedo: boolean;
  onSelect: (id: string | null) => void;
  onUpdate: (id: string, patch: Partial<LocalElement>) => void;
  onRemove: (id: string) => void;
  onBusy: (busy: boolean) => void;
  onHover?: (id: string | null) => void;
}

/**
 * Painel lateral de ajuste fino (estado controlado pelo pai {@code ContentEditor}).
 * O posicionamento é feito no canvas; aqui o usuário escolhe a ferramenta de
 * inserção, ajusta fonte/tamanho/cor/posição do elemento selecionado e exporta.
 */
export function ContentEditPanel({
  fileId,
  elements,
  selectedId,
  insertTool,
  busy,
  onSetInsertTool,
  onPickImage,
  onDetectBlocks,
  docFonts,
  onUndo,
  onRedo,
  canUndo,
  canRedo,
  onSelect,
  onUpdate,
  onRemove,
  onBusy,
  onHover,
}: ContentEditPanelProps) {
  const { t } = useI18n();
  const [detectNotice, setDetectNotice] = useState<string | null>(null);

  async function handleDetect() {
    setDetectNotice(null);
    try {
      const { scanned, count } = await onDetectBlocks();
      if (scanned) {
        setDetectNotice(t("content.detect.scanned"));
      } else if (count === 0) {
        setDetectNotice(t("content.detect.empty"));
      }
    } catch {
      setDetectNotice(t("content.error"));
    }
  }

  const selected = elements.find((el) => el.id === selectedId) ?? null;

  return (
    <ExportShell
      fileId={fileId}
      elements={elements}
      busy={busy}
      onBusy={onBusy}
      header={
        <>
          <p className="hint">{t("content.panel.hint")}</p>

          <div className="undo-redo-bar">
            <button
              type="button"
              className="btn-icon"
              onClick={onUndo}
              disabled={!canUndo}
              title={t("content.undo")}
              aria-label={t("content.undo")}
            >
              ⟲ {t("content.undo")}
            </button>
            <button
              type="button"
              className="btn-icon"
              onClick={onRedo}
              disabled={!canRedo}
              title={t("content.redo")}
              aria-label={t("content.redo")}
            >
              ⟳ {t("content.redo")}
            </button>
          </div>

          <button type="button" className="btn-secondary detect-btn" onClick={handleDetect} disabled={busy}>
            ⬚ {t("content.detect")}
          </button>
          {detectNotice && (
            <p className="insert-banner" role="status">
              {detectNotice}
            </p>
          )}

          <div className="content-add-bar">
            <button
              type="button"
              className={insertTool === "text" ? "active" : ""}
              aria-pressed={insertTool === "text"}
              onClick={() => onSetInsertTool(insertTool === "text" ? null : "text")}
            >
              + {t("content.add.text")}
            </button>
            <label className="btn-file">
              + {t("content.add.image")}
              <input
                type="file"
                accept="image/jpeg,image/png"
                hidden
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) onPickImage(f);
                  e.currentTarget.value = "";
                }}
              />
            </label>
            <button
              type="button"
              className={insertTool === "redact" ? "active" : ""}
              aria-pressed={insertTool === "redact"}
              onClick={() => onSetInsertTool(insertTool === "redact" ? null : "redact")}
            >
              + {t("content.add.redact")}
            </button>
            <button
              type="button"
              className={insertTool === "edit-text" ? "active" : ""}
              aria-pressed={insertTool === "edit-text"}
              onClick={() => onSetInsertTool(insertTool === "edit-text" ? null : "edit-text")}
            >
              ✎ {t("content.add.editText")}
            </button>
          </div>
          {insertTool && (
            <p className="insert-banner" role="status">
              {t(insertTool === "edit-text" ? "content.editText.hint" : "content.canvas.insertHint")}
            </p>
          )}
        </>
      }
    >
      {selected ? (
        <>
          <p className="panel-section-title">{t("content.section.adjust")}</p>
          <ElementEditor
            el={selected}
            docFonts={docFonts}
            onChange={(patch) => onUpdate(selected.id, patch)}
            onRemove={() => onRemove(selected.id)}
          />
        </>
      ) : (
        <p className="hint">{elements.length === 0 ? t("content.empty") : t("content.canvas.selectHint")}</p>
      )}

      {elements.length > 0 && (
        <>
          <p className="panel-section-title">
            {t("content.section.elements")} <span className="count-badge">{elements.length}</span>
          </p>
          <ul className="content-mini-list" aria-label={t("content.section.elements")}>
            {elements.map((el) => (
              <li key={el.id}>
                <button
                  type="button"
                  className={`content-mini${el.id === selectedId ? " active" : ""}`}
                  onClick={() => onSelect(el.id)}
                  onMouseEnter={() => onHover?.(el.id)}
                  onMouseLeave={() => onHover?.(null)}
                >
                  <span className={`mini-dot dot-${el.type}`} aria-hidden="true" />
                  <span className="mini-body">
                    <span className="mini-label">{t(`content.add.${el.type}`)}</span>
                    <span className="mini-sub">
                      {el.type === "text" && el.text
                        ? `“${el.text}”`
                        : `${t("content.field.page")} ${el.page} · ${el.x}, ${el.y}`}
                    </span>
                  </span>
                  <span
                    className="mini-remove"
                    role="button"
                    aria-label={t("content.remove")}
                    tabIndex={-1}
                    onClick={(e) => {
                      e.stopPropagation();
                      onRemove(el.id);
                    }}
                  >
                    ✕
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </ExportShell>
  );
}

/** Casca com legend, filename e botão de exportar (mantém o painel enxuto). */
function ExportShell({
  fileId,
  elements,
  busy,
  onBusy,
  header,
  children,
}: {
  fileId: string;
  elements: LocalElement[];
  busy: boolean;
  onBusy: (b: boolean) => void;
  header: React.ReactNode;
  children: React.ReactNode;
}) {
  const { t } = useI18n();
  // estado local só de export (filename/result/error) — não compartilhado.
  const [filename, setFilename] = useState<string>("documento-editado");
  const [result, setResult] = useState<SingleResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [job, setJob] = useState<JobStatus | null>(null);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    return () => esRef.current?.close();
  }, []);

  const processing = busy || (job !== null && (job.status === "PENDING" || job.status === "RUNNING"));

  function payloadOf(): ContentEditElement[] {
    return elements.map(({ id: _id, previewUrl: _p, fontFamily: _ff, ...rest }) => rest);
  }

  async function runExport() {
    onBusy(true);
    setError(null);
    setResult(null);
    setJob(null);
    try {
      setResult(await contentEdit({ fileId, elements: payloadOf() }));
    } catch (e) {
      setError(e instanceof Error ? e.message : t("content.error"));
    } finally {
      onBusy(false);
    }
  }

  async function runExportOcr() {
    onBusy(true);
    setError(null);
    setResult(null);
    setJob(null);
    try {
      const created = await contentEditWithOcr({ fileId, elements: payloadOf(), lang: "por+eng" });
      esRef.current?.close();
      esRef.current = subscribeJob(created.jobId, (data) => {
        setJob((prev) => ({
          jobId: created.jobId,
          status: data.status as JobStatus["status"],
          progress: data.progress,
          totalPages: data.totalPages,
          resultUrl: prev?.resultUrl ?? null,
          textUrl: prev?.textUrl ?? null,
          error: prev?.error ?? null,
        }));
        if (data.status === "DONE" || data.status === "ERROR") {
          esRef.current?.close();
          getJob(created.jobId).then(setJob).catch(() => {});
        }
      });
      setJob(await getJob(created.jobId));
    } catch (e) {
      setError(e instanceof Error ? e.message : t("content.error"));
    } finally {
      onBusy(false);
    }
  }

  return (
    <section className="tools" aria-label={t("content.panel.title")}>
      {processing && <Spinner overlay label={t("common.processing")} />}
      <fieldset>
        <legend>{t("content.panel.title")}</legend>
        {header}
        {children}

        <FilenameField value={filename} onChange={setFilename} placeholder="documento-editado" />
        <button
          type="button"
          className="btn-primary"
          onClick={runExport}
          disabled={processing || elements.length === 0}
        >
          {t("content.export")}
        </button>
        <button
          type="button"
          className="btn-secondary"
          onClick={runExportOcr}
          disabled={processing || elements.length === 0}
        >
          {t("content.exportOcr")}
        </button>
        {result && (
          <p>
            <a href={withName(result.downloadUrl, filename)} download>
              {t("content.download")}
            </a>
          </p>
        )}
        {result?.fontSubstituted && (
          <p className="insert-banner" role="status">
            {t("content.fontSubstituted")}
          </p>
        )}
        {job && (
          <div className="ocr-status" aria-live="polite">
            <p>
              {t("content.ocr.status")}: <strong>{job.status}</strong>
              {job.totalPages > 0 && ` — ${job.progress}% (${job.totalPages})`}
            </p>
            <progress value={job.progress} max={100} />
            {job.status === "DONE" && job.resultUrl && (
              <p>
                <a href={withName(job.resultUrl, filename)} download>
                  {t("content.download")}
                </a>
              </p>
            )}
            {job.status === "ERROR" && (
              <p role="alert" className="error">
                {job.error ?? t("content.error")}
              </p>
            )}
          </div>
        )}
      </fieldset>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
    </section>
  );
}

// Ajuste fino do elemento selecionado.
/** Ajuste fino do elemento selecionado. */
function ElementEditor({
  el,
  docFonts,
  onChange,
  onRemove,
}: {
  el: LocalElement;
  docFonts: DocFontInfo[];
  onChange: (patch: Partial<LocalElement>) => void;
  onRemove: () => void;
}) {
  const { t } = useI18n();

  const common = (
    <>
      <label className="field-sm">
        <span>{t("content.field.page")}</span>
        <input
          type="number"
          min={1}
          value={el.page}
          onChange={(e) => onChange({ page: Math.max(1, Number(e.target.value) || 1) })}
        />
      </label>
      <label className="field-sm">
        <span>{t("content.field.position")}</span>
        <span className="field-pair">
          <input type="number" value={el.x} onChange={(e) => onChange({ x: Number(e.target.value) || 0 })} aria-label="x" />
          <input type="number" value={el.y} onChange={(e) => onChange({ y: Number(e.target.value) || 0 })} aria-label="y" />
        </span>
      </label>
    </>
  );

  const sizeFields =
    el.type !== "text" ? (
      <label className="field-sm">
        <span>{t("content.field.sizeWH")}</span>
        <span className="field-pair">
          <input
            type="number"
            value={el.width}
            onChange={(e) => onChange({ width: Number(e.target.value) || 0 })}
            aria-label="width"
          />
          <input
            type="number"
            value={el.height}
            onChange={(e) => onChange({ height: Number(e.target.value) || 0 })}
            aria-label="height"
          />
        </span>
      </label>
    ) : null;

  return (
    <div className="element-grid element-selected">
      <span className={`element-tag tag-${el.type}`}>{t(`content.add.${el.type}`)}</span>
      {common}
      {el.type === "text" && (
        <>
          <label className="field-wide">
            <span>{t("content.field.text")}</span>
            <input type="text" value={el.text} onChange={(e) => onChange({ text: e.target.value })} />
          </label>
          <label className="field-sm">
            <span>{t("content.field.font")}</span>
            <select
              value={fontSelectValue(el)}
              onChange={(e) => onChange(applyFontChoice(e.target.value, el, docFonts))}
            >
              {el.fontResource && (
                <option value={`doc:${el.fontResource}`}>
                  {(el.fontFamily || el.fontResource) + " " + t("content.font.detected")}
                </option>
              )}
              {docFonts.length > 0 && (
                <optgroup label={t("content.font.embedded")}>
                  {docFonts.map((f) => (
                    <option key={`${f.page}/${f.resource}`} value={`doc:${f.resource}:${f.page}`}>
                      {f.family}
                      {f.bold ? " B" : ""}
                      {f.italic ? " I" : ""}
                    </option>
                  ))}
                </optgroup>
              )}
              <optgroup label={t("content.font.standard")}>
                {FONTS.map((f) => (
                  <option key={f} value={`std:${f}`}>
                    {f}
                  </option>
                ))}
              </optgroup>
            </select>
          </label>
          <label className="field-sm field-check">
            <input
              type="checkbox"
              checked={/bold/.test(el.font ?? "")}
              onChange={(e) => onChange({ font: toggleStyle(el.font, "bold", e.target.checked) })}
            />
            <span>{t("content.field.bold")}</span>
          </label>
          <label className="field-sm field-check">
            <input
              type="checkbox"
              checked={/italic|oblique/.test(el.font ?? "")}
              onChange={(e) => onChange({ font: toggleStyle(el.font, "italic", e.target.checked) })}
            />
            <span>{t("content.field.italic")}</span>
          </label>
          <label className="field-sm">
            <span>{t("content.field.size")}</span>
            <input
              type="number"
              min={4}
              value={el.size ?? 14}
              onChange={(e) => onChange({ size: Math.max(4, Number(e.target.value) || 14) })}
            />
          </label>
        </>
      )}
      {sizeFields}
      {(el.type === "text" || el.type === "redact") && (
        <label className="field-sm">
          <span>{t("content.field.color")}</span>
          <input
            type="color"
            value={el.color ?? (el.type === "redact" ? "#ffffff" : "#000000")}
            onChange={(e) => onChange({ color: e.target.value })}
          />
        </label>
      )}
      {el.type === "redact" && (
        <label className="field-wide field-check">
          <input
            type="checkbox"
            checked={el.hard ?? false}
            onChange={(e) => onChange({ hard: e.target.checked, color: e.target.checked ? "#000000" : el.color })}
          />
          <span>
            {t("content.field.hard")}
            <small className="field-hint">{t("content.field.hardHint")}</small>
          </span>
        </label>
      )}
      <button type="button" className="btn-remove-wide" onClick={onRemove}>
        ✕ {t("content.remove")}
      </button>
    </div>
  );
}
