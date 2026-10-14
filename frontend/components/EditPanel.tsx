"use client";

import { useState } from "react";
import { editFile, withName, type EditOp, type SingleResult } from "@/lib/apiClient";
import { useI18n } from "@/lib/i18n";
import { Spinner } from "@/components/Spinner";
import { FilenameField } from "@/components/ExportControls";
import type { GridPage as GP } from "@/components/PageGrid";

/**
 * Painel "Editar" (Fase 6, item E — separado de Dividir). Exporta o PDF com a
 * ordem e rotações do grid. NÃO edita texto/elementos do conteúdo.
 */
export function EditPanel({ fileId, pages }: { fileId: string; pages: GP[] }) {
  const { t } = useI18n();
  const [filename, setFilename] = useState("documento-editado");
  const [result, setResult] = useState<SingleResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function runExport() {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const order = pages.map((p) => p.origin);
      const ops: EditOp[] = [{ type: "reorder", order }];
      pages.forEach((p, idx) => {
        if (p.rotation !== 0) ops.push({ type: "rotate", page: idx + 1, rotate: p.rotation });
      });
      setResult(await editFile({ fileId, ops }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao exportar edição");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="tools" aria-label={t("tab.edit")}>
      {busy && <Spinner overlay label={t("common.processing")} />}
      <fieldset>
        <legend>{t("tab.edit")}</legend>
        <p className="hint">Aplica a ordem e as rotações do grid (não edita texto).</p>

        <FilenameField value={filename} onChange={setFilename} placeholder="documento-editado" />

        <button type="button" className="btn-primary" onClick={runExport} disabled={busy}>
          Exportar PDF
        </button>
        {result && (
          <p>
            <a href={withName(result.downloadUrl, filename)} download>
              Baixar resultado
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
