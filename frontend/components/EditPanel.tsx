"use client";

import { useState } from "react";
import { editFile, withName, type EditOp, type SingleResult } from "@/lib/apiClient";
import { useI18n } from "@/lib/i18n";
import { Spinner } from "@/components/Spinner";
import { FilenameField } from "@/components/ExportControls";
import type { GridPage as GP } from "@/components/PageGrid";

interface EditPanelProps {
  fileId: string;
  pages: GP[];
  isExtractMode?: boolean;
}

export function EditPanel({ fileId, pages, isExtractMode = false }: EditPanelProps) {
  const { t } = useI18n();
  const selectedPages = pages.filter((p) => p.selected);
  const [filename, setFilename] = useState(isExtractMode ? "paginas-extraidas" : "documento-editado");
  const [result, setResult] = useState<SingleResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function runProcess(onlySelected: boolean) {
    setBusy(true);
    setError(null);
    setResult(null);

    const targetList = onlySelected ? selectedPages : pages;
    if (targetList.length === 0) {
      setError(t("extract.emptySelection"));
      setBusy(false);
      return;
    }

    try {
      const order = targetList.map((p) => p.origin);
      const ops: EditOp[] = [{ type: "reorder", order }];
      targetList.forEach((p, idx) => {
        if (p.rotation !== 0) {
          ops.push({ type: "rotate", page: idx + 1, rotate: p.rotation });
        }
      });
      setResult(await editFile({ fileId, ops }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao processar páginas");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="tools" aria-label={isExtractMode ? t("tab.extract") : t("tab.edit")}>
      {busy && <Spinner overlay label={t("common.processing")} />}
      <fieldset>
        <legend>{isExtractMode ? t("tab.extract") : t("tab.edit")}</legend>

        <p className="hint">
          {isExtractMode
            ? t("extract.panelHint")
            : t("edit.panelHint")}
        </p>

        <FilenameField
          value={filename}
          onChange={setFilename}
          placeholder={isExtractMode ? "paginas-extraidas" : "documento-editado"}
        />

        {isExtractMode ? (
          <button
            type="button"
            className="btn-primary"
            onClick={() => runProcess(true)}
            disabled={busy || selectedPages.length === 0}
          >
            {selectedPages.length === 0
              ? t("extract.selectPagesFirst")
              : `${t("extract.button")} (${selectedPages.length})`}
          </button>
        ) : (
          <div className="edit-actions-col">
            <button
              type="button"
              className="btn-primary"
              onClick={() => runProcess(false)}
              disabled={busy}
            >
              {t("edit.exportAll")}
            </button>

            {selectedPages.length > 0 && (
              <button
                type="button"
                className="btn-secondary btn-extract"
                onClick={() => runProcess(true)}
                disabled={busy}
              >
                {t("extract.button")} ({selectedPages.length})
              </button>
            )}
          </div>
        )}

        {result && (
          <p className="result-download-wrap">
            <a href={withName(result.downloadUrl, filename)} download className="btn-primary btn-download">
              {t("common.download")}
            </a>
          </p>
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
