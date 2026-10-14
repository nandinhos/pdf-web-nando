"use client";

import { useState } from "react";
import { splitFile, withName, type ResultFile, type SplitMode } from "@/lib/apiClient";
import { useI18n } from "@/lib/i18n";
import { Spinner } from "@/components/Spinner";
import { FilenameField } from "@/components/ExportControls";

/**
 * Painel "Dividir" (Fase 6, item E — separado de Editar). Divide o PDF em vários
 * arquivos. Não rasteriza → sem controle de qualidade; apenas nome base.
 */
export function SplitPanel({ fileId }: { fileId: string }) {
  const { t } = useI18n();
  const [mode, setMode] = useState<SplitMode>("pagesPerPdf");
  const [pagesPerPdf, setPagesPerPdf] = useState(1);
  const [splitPoints, setSplitPoints] = useState("");
  const [filename, setFilename] = useState("parte");
  const [results, setResults] = useState<ResultFile[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function runSplit() {
    setBusy(true);
    setError(null);
    setResults([]);
    try {
      const res = await splitFile({
        fileId,
        mode,
        pagesPerPdf: mode === "pagesPerPdf" ? pagesPerPdf : undefined,
        splitPoints: mode === "custom" ? splitPoints : undefined,
      });
      setResults(res.files);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao dividir");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="tools" aria-label={t("tab.split")}>
      {busy && <Spinner overlay label={t("common.processing")} />}
      <fieldset>
        <legend>{t("tab.split")}</legend>
        <label>
          Modo
          <select value={mode} onChange={(e) => setMode(e.target.value as SplitMode)}>
            <option value="pagesPerPdf">N páginas por arquivo</option>
            <option value="custom">Pontos de corte</option>
            <option value="evenOdd">Pares/ímpares</option>
            <option value="cutInHalf">Cortar ao meio</option>
          </select>
        </label>
        {mode === "pagesPerPdf" && (
          <label>
            Páginas por arquivo
            <input
              type="number"
              min={1}
              value={pagesPerPdf}
              onChange={(e) => setPagesPerPdf(Math.max(1, Number(e.target.value)))}
            />
          </label>
        )}
        {mode === "custom" && (
          <label>
            Pontos de corte (ex.: 3,7)
            <input value={splitPoints} onChange={(e) => setSplitPoints(e.target.value)} />
          </label>
        )}

        <FilenameField value={filename} onChange={setFilename} placeholder="parte" />

        <button type="button" className="btn-primary" onClick={runSplit} disabled={busy}>
          {t("tab.split")}
        </button>
        {results.length > 0 && (
          <ul className="results">
            {results.map((r) => (
              <li key={r.resultId}>
                <a href={withName(r.downloadUrl, `${filename}-${r.name}`)} download>
                  {filename}-{r.name}.pdf
                </a>
              </li>
            ))}
          </ul>
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
