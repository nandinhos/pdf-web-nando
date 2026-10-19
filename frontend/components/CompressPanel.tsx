"use client";

import { useState } from "react";
import { compressFile, withName, type CompressResponse } from "@/lib/apiClient";
import { useI18n } from "@/lib/i18n";
import { Spinner } from "@/components/Spinner";
import { FilenameField } from "@/components/ExportControls";

interface CompressPanelProps {
  fileId: string;
}

type QualityLevel = "low" | "medium" | "high";

function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
}

export function CompressPanel({ fileId }: CompressPanelProps) {
  const { t } = useI18n();
  const [quality, setQuality] = useState<QualityLevel>("medium");
  const [filename, setFilename] = useState("documento-comprimido");
  const [result, setResult] = useState<CompressResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleCompress() {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const res = await compressFile({ fileId, quality });
      setResult(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao comprimir documento");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="tools" aria-label={t("tab.compress")}>
      {busy && <Spinner overlay label={t("common.processing")} />}

      <fieldset>
        <legend>{t("tab.compress")}</legend>
        <p className="hint">{t("compress.hint")}</p>

        <div className="compress-presets" role="radiogroup" aria-label={t("compress.level")}>
          <button
            type="button"
            className={`preset-btn${quality === "low" ? " active" : ""}`}
            onClick={() => setQuality("low")}
          >
            <strong>{t("compress.extreme")}</strong>
            <span>{t("compress.extremeDesc")}</span>
          </button>

          <button
            type="button"
            className={`preset-btn${quality === "medium" ? " active" : ""}`}
            onClick={() => setQuality("medium")}
          >
            <strong>{t("compress.recommended")}</strong>
            <span>{t("compress.recommendedDesc")}</span>
          </button>

          <button
            type="button"
            className={`preset-btn${quality === "high" ? " active" : ""}`}
            onClick={() => setQuality("high")}
          >
            <strong>{t("compress.light")}</strong>
            <span>{t("compress.lightDesc")}</span>
          </button>
        </div>

        <FilenameField value={filename} onChange={setFilename} placeholder="documento-comprimido" />

        <button type="button" className="btn-primary" onClick={handleCompress} disabled={busy}>
          {t("compress.action")}
        </button>

        {result && (
          <div className="compress-result-card" role="region" aria-live="polite">
            <div className="compress-stats">
              <div className="stat-item">
                <span className="stat-label">{t("compress.originalSize")}</span>
                <span className="stat-val">{formatBytes(result.originalSize)}</span>
              </div>
              <span className="stat-arrow" aria-hidden="true">→</span>
              <div className="stat-item">
                <span className="stat-label">{t("compress.newSize")}</span>
                <span className="stat-val highlight">{formatBytes(result.compressedSize)}</span>
              </div>
              {result.savingsPercent > 0 && (
                <div className="stat-badge">
                  -{result.savingsPercent}%
                </div>
              )}
            </div>

            <p className="compress-download-wrap">
              <a
                href={withName(result.downloadUrl, filename)}
                download
                className="btn-primary btn-download"
              >
                {t("common.download")} ({formatBytes(result.compressedSize)})
              </a>
            </p>
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
