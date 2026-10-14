"use client";

import { useState } from "react";
import { mergeFiles, uploadFile, withName, type MergeMode, type SingleResult } from "@/lib/apiClient";
import { useI18n } from "@/lib/i18n";
import { Spinner } from "@/components/Spinner";
import { FilenameField } from "@/components/ExportControls";

interface MergeInput {
  fileId: string;
  filename: string;
  pages: number;
  ranges: string;
}

/**
 * Fluxo de mesclagem multi-arquivo (PRD §4.2): adiciona N PDFs, escolhe o modo
 * e combina. Operação síncrona — retorna o PDF resultante para download.
 */
export function MergePanel() {
  const [inputs, setInputs] = useState<MergeInput[]>([]);
  const [mode, setMode] = useState<MergeMode>("concatenate");
  const [outName, setOutName] = useState("documento-mesclado");
  const [result, setResult] = useState<SingleResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { t } = useI18n();

  async function onAdd(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    setError(null);
    setBusy(true);
    try {
      for (const file of files) {
        const res = await uploadFile(file);
        setInputs((prev) => [
          ...prev,
          { fileId: res.fileId, filename: res.meta.filename, pages: res.pages, ranges: "" },
        ]);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha no upload");
    } finally {
      setBusy(false);
    }
  }

  function updateRanges(idx: number, ranges: string) {
    setInputs((prev) => prev.map((it, i) => (i === idx ? { ...it, ranges } : it)));
  }

  function remove(idx: number) {
    setInputs((prev) => prev.filter((_, i) => i !== idx));
  }

  function move(idx: number, dir: -1 | 1) {
    setInputs((prev) => {
      const next = [...prev];
      const target = idx + dir;
      if (target < 0 || target >= next.length) return prev;
      [next[idx], next[target]] = [next[target], next[idx]];
      return next;
    });
  }

  async function runMerge() {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const res = await mergeFiles({
        items: inputs.map((it) => ({ fileId: it.fileId, ranges: it.ranges.trim() || undefined })),
        mode,
      });
      setResult(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha ao mesclar");
    } finally {
      setBusy(false);
    }
  }

  const canMerge = inputs.length >= 1 && (mode !== "oddEvenReverse" || inputs.length === 2);

  return (
    <section className="tools" aria-label="Mesclar PDFs">
      {busy && <Spinner overlay label={t("common.processing")} />}
      <h2>Mesclar</h2>

      <label className="upload">
        <span>Adicionar PDF(s)</span>
        <input type="file" accept="application/pdf" multiple onChange={onAdd} aria-label="Adicionar PDFs para mesclar" />
      </label>

      {inputs.length > 0 && (
        <ol className="merge-list">
          {inputs.map((it, idx) => (
            <li key={it.fileId + idx} className="merge-item">
              <span className="merge-name">
                {it.filename} <small>({it.pages}p)</small>
              </span>
              <input
                className="merge-ranges"
                placeholder="faixas (ex.: 1-3,5)"
                value={it.ranges}
                onChange={(e) => updateRanges(idx, e.target.value)}
                aria-label={`Faixas de ${it.filename}`}
              />
              <span className="merge-controls">
                <button type="button" onClick={() => move(idx, -1)} aria-label="Mover para cima">↑</button>
                <button type="button" onClick={() => move(idx, 1)} aria-label="Mover para baixo">↓</button>
                <button type="button" onClick={() => remove(idx)} aria-label="Remover">✕</button>
              </span>
            </li>
          ))}
        </ol>
      )}

      <label>
        Modo
        <select value={mode} onChange={(e) => setMode(e.target.value as MergeMode)}>
          <option value="concatenate">Concatenar</option>
          <option value="alternate">Intercalar (frente/verso)</option>
          <option value="oddEvenReverse">Ímpares + pares invertidos (2 arquivos)</option>
        </select>
      </label>

      {mode === "oddEvenReverse" && inputs.length !== 2 && (
        <p className="hint">Este modo exige exatamente 2 arquivos.</p>
      )}

      <FilenameField value={outName} onChange={setOutName} placeholder="documento-mesclado" />

      <button type="button" className="btn-primary" onClick={runMerge} disabled={busy || !canMerge}>
        Mesclar
      </button>

      {result && (
        <p>
          <a href={withName(result.downloadUrl, outName)} download>
            Baixar PDF mesclado
          </a>
        </p>
      )}
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
    </section>
  );
}
