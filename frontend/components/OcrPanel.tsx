"use client";

import { useEffect, useRef, useState } from "react";
import {
  getJob,
  startOcr,
  subscribeJob,
  uploadFile,
  withName,
  type JobStatus,
  type Quality,
} from "@/lib/apiClient";
import { useI18n } from "@/lib/i18n";
import { Spinner } from "@/components/Spinner";
import { QualitySelector, FilenameField } from "@/components/ExportControls";

/**
 * Aba "OCR" (PRD §4.4): envia um PDF, aplica OCR assíncrono e acompanha o
 * progresso em tempo real (SSE). Qualidade de exportação + nome do arquivo (Fase 6).
 */
export function OcrPanel() {
  const { t } = useI18n();
  const [lang, setLang] = useState("por");
  const [quality, setQuality] = useState<Quality>("medium");
  const [filename, setFilename] = useState("documento-ocr");
  const [job, setJob] = useState<JobStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    return () => esRef.current?.close();
  }, []);

  const processing = busy || (job !== null && (job.status === "PENDING" || job.status === "RUNNING"));

  async function onSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setError(null);
    setJob(null);
    setBusy(true);
    try {
      const uploaded = await uploadFile(file);
      const created = await startOcr({ fileId: uploaded.fileId, lang, quality });

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
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha ao iniciar OCR");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="tools" aria-label="OCR">
      {processing && <Spinner overlay label={t("common.processing")} />}
      <fieldset>
        <legend>OCR — PDF pesquisável</legend>
        <label>
          Idioma
          <select value={lang} onChange={(e) => setLang(e.target.value)}>
            <option value="por">Português</option>
            <option value="eng">Inglês</option>
            <option value="por+eng">Português + Inglês</option>
          </select>
        </label>

        <QualitySelector value={quality} onChange={setQuality} />
        <FilenameField value={filename} onChange={setFilename} placeholder="documento-ocr" />

        <label className="upload">
          <span>Selecionar PDF escaneado</span>
          <input type="file" accept="application/pdf" onChange={onSelect} disabled={processing} aria-label="PDF para OCR" />
        </label>

        {job && (
          <div className="ocr-status" aria-live="polite">
            <p>
              Status: <strong>{job.status}</strong>
              {job.totalPages > 0 && ` — ${job.progress}% (${job.totalPages} páginas)`}
            </p>
            <progress value={job.progress} max={100} />
            {job.status === "DONE" && (
              <ul className="results">
                {job.resultUrl && (
                  <li>
                    <a href={withName(job.resultUrl, `${filename}`)} download>
                      Baixar PDF pesquisável
                    </a>
                  </li>
                )}
                {job.textUrl && (
                  <li>
                    <a href={withName(job.textUrl, `${filename}`)} download>
                      Baixar texto (TXT)
                    </a>
                  </li>
                )}
              </ul>
            )}
            {job.status === "ERROR" && (
              <p role="alert" className="error">
                {job.error ?? "Falha no OCR"}
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
