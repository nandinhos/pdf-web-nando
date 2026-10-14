"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useI18n } from "@/lib/i18n";
import { ToolIcons } from "@/components/ToolIcons";

interface ImportModalProps {
  title: string;
  /** chave do ícone/acento da ferramenta (edit/merge/images/ocr/sign) */
  accent: string;
  /** accept do input (ex.: "application/pdf" ou "image/jpeg,image/png") */
  accept: string;
  multiple?: boolean;
  dropLabelKey: string;
  hintKey: string;
  onClose: () => void;
  onFiles: (files: File[]) => void;
}

/**
 * Modal acessível de importação (role=dialog): foco preso, ESC fecha, backdrop
 * com blur. Contém uma dropzone (arrastar-e-soltar + clique).
 */
export function ImportModal({
  title,
  accent,
  accept,
  multiple = false,
  dropLabelKey,
  hintKey,
  onClose,
  onFiles,
}: ImportModalProps) {
  const { t } = useI18n();
  const [dragging, setDragging] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    // Foco inicial no diálogo.
    dialogRef.current?.focus();
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  const handleFiles = useCallback(
    (list: FileList | null) => {
      if (!list || list.length === 0) return;
      onFiles(Array.from(list));
    },
    [onFiles],
  );

  return (
    <div
      className="modal-backdrop"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        ref={dialogRef}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-head">
          <span className={`op-chip tool-ic ${accent}`} aria-hidden="true">
            {ToolIcons[accent]}
          </span>
          <h2>{title}</h2>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Fechar">
            ×
          </button>
        </div>

        <div
          className={`dropzone${dragging ? " dragging" : ""}`}
          role="button"
          tabIndex={0}
          onClick={() => inputRef.current?.click()}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              inputRef.current?.click();
            }
          }}
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragging(false);
            handleFiles(e.dataTransfer.files);
          }}
        >
          <span className="dz-ic" aria-hidden="true">
            {ToolIcons.upload}
          </span>
          <strong>{t(dropLabelKey)}</strong>
          <small>
            {t("modal.orClick")} · {t(hintKey)}
          </small>
          <input
            ref={inputRef}
            type="file"
            accept={accept}
            multiple={multiple}
            onChange={(e) => handleFiles(e.target.files)}
          />
        </div>
      </div>
    </div>
  );
}
