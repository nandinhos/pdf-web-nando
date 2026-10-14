"use client";

import { useState } from "react";
import {
  extractImages,
  imagesToPdf,
  uploadFile,
  uploadImage,
  withName,
  type ImageOrientation,
  type PageSizeName,
  type Quality,
  type ResultFile,
  type SingleResult,
} from "@/lib/apiClient";
import { useI18n } from "@/lib/i18n";
import { Spinner } from "@/components/Spinner";
import { QualitySelector, FilenameField } from "@/components/ExportControls";

interface UploadedImage {
  imageId: string;
  filename: string;
}

/**
 * Aba "Imagens" (PRD §4.5): converter imagens em PDF e extrair imagens de um PDF.
 */
export function ImagesPanel() {
  const { t } = useI18n();
  // --- imagem → PDF ---
  const [images, setImages] = useState<UploadedImage[]>([]);
  const [pageSize, setPageSize] = useState<PageSizeName>("A4");
  const [orientation, setOrientation] = useState<ImageOrientation>("portrait");
  const [usePageSize, setUsePageSize] = useState(true);
  const [quality, setQuality] = useState<Quality>("medium");
  const [filename, setFilename] = useState("imagens");
  const [toPdfResult, setToPdfResult] = useState<SingleResult | null>(null);

  // --- extrair imagens ---
  const [extractResults, setExtractResults] = useState<ResultFile[]>([]);

  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onAddImages(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    setError(null);
    setBusy(true);
    try {
      for (const file of files) {
        const res = await uploadImage(file);
        setImages((prev) => [...prev, { imageId: res.imageId, filename: res.filename }]);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha no upload da imagem");
    } finally {
      setBusy(false);
    }
  }

  async function runToPdf() {
    setBusy(true);
    setError(null);
    setToPdfResult(null);
    try {
      const res = await imagesToPdf({
        imageIds: images.map((i) => i.imageId),
        pageSize,
        orientation,
        usePageSize,
        quality,
      });
      setToPdfResult(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha ao converter");
    } finally {
      setBusy(false);
    }
  }

  async function onExtract(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setBusy(true);
    setError(null);
    setExtractResults([]);
    try {
      const uploaded = await uploadFile(file);
      const res = await extractImages({ fileId: uploaded.fileId });
      setExtractResults(res.files);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha ao extrair");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="tools" aria-label="Ferramentas de imagem">
      {busy && <Spinner overlay label={t("common.processing")} />}
      <fieldset>
        <legend>Imagens → PDF</legend>
        <label className="upload">
          <span>Adicionar imagem(ns) (JPG/PNG)</span>
          <input
            type="file"
            accept="image/jpeg,image/png"
            multiple
            onChange={onAddImages}
            aria-label="Adicionar imagens"
          />
        </label>
        {images.length > 0 && (
          <ul className="results">
            {images.map((img, idx) => (
              <li key={img.imageId + idx}>{img.filename}</li>
            ))}
          </ul>
        )}
        <label>
          Tamanho da página
          <select value={pageSize} onChange={(e) => setPageSize(e.target.value as PageSizeName)}>
            <option>A4</option>
            <option>A3</option>
            <option>A5</option>
            <option>LETTER</option>
            <option>LEGAL</option>
          </select>
        </label>
        <label>
          Orientação
          <select value={orientation} onChange={(e) => setOrientation(e.target.value as ImageOrientation)}>
            <option value="portrait">Retrato</option>
            <option value="landscape">Paisagem</option>
            <option value="auto">Automática</option>
          </select>
        </label>
        <label className="inline">
          <input type="checkbox" checked={usePageSize} onChange={(e) => setUsePageSize(e.target.checked)} />
          Ajustar à página (desmarque p/ tamanho real)
        </label>

        <QualitySelector value={quality} onChange={setQuality} />
        <FilenameField value={filename} onChange={setFilename} placeholder="imagens" />

        <button type="button" className="btn-primary" onClick={runToPdf} disabled={busy || images.length === 0}>
          Gerar PDF
        </button>
        {toPdfResult && (
          <p>
            <a href={withName(toPdfResult.downloadUrl, filename)} download>
              Baixar PDF
            </a>
          </p>
        )}
      </fieldset>

      <fieldset>
        <legend>Extrair imagens de um PDF</legend>
        <label className="upload">
          <span>Selecionar PDF</span>
          <input type="file" accept="application/pdf" onChange={onExtract} aria-label="PDF para extrair imagens" />
        </label>
        {extractResults.length > 0 && (
          <ul className="results">
            {extractResults.map((r) => (
              <li key={r.resultId}>
                <a href={r.downloadUrl} download>
                  {r.name}
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
