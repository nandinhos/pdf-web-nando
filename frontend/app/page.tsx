"use client";

import { useState } from "react";
import { pdfjs } from "@/lib/pdfjs";
import { useI18n } from "@/lib/i18n";
import { PageGrid, type GridPage } from "@/components/PageGrid";
import { EditPanel } from "@/components/EditPanel";
import { SplitPanel } from "@/components/SplitPanel";
import { MergePanel } from "@/components/MergePanel";
import { ImagesPanel } from "@/components/ImagesPanel";
import { OcrPanel } from "@/components/OcrPanel";
import { SignaturePanel } from "@/components/SignaturePanel";
import { SettingsBar } from "@/components/SettingsBar";
import { Launcher } from "@/components/Launcher";
import { ImportModal } from "@/components/ImportModal";
import { Spinner } from "@/components/Spinner";
import { ToolIcons, type ToolId } from "@/components/ToolIcons";
import { uploadFile } from "@/lib/apiClient";
import type { PDFDocumentProxy } from "pdfjs-dist";

const META: Record<ToolId, { icon: string; titleKey: string }> = {
  edit: { icon: "edit", titleKey: "tab.edit" },
  split: { icon: "split", titleKey: "tab.split" },
  merge: { icon: "merge", titleKey: "tab.merge" },
  images: { icon: "images", titleKey: "tab.images" },
  ocr: { icon: "ocr", titleKey: "tab.ocr" },
  sign: { icon: "sign", titleKey: "tab.signature" },
};

/** Ferramentas que operam sobre 1 PDF carregado via modal + grid de páginas. */
const PDF_FIRST: ToolId[] = ["edit", "split"];

export default function HomePage() {
  const { t } = useI18n();
  const [active, setActive] = useState<ToolId | null>(null);
  const [importing, setImporting] = useState(false);
  const [loadingDoc, setLoadingDoc] = useState(false);

  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null);
  const [fileId, setFileId] = useState<string | null>(null);
  const [pages, setPages] = useState<GridPage[]>([]);
  const [error, setError] = useState<string | null>(null);

  function pick(tool: ToolId) {
    setError(null);
    setActive(tool);
    if (PDF_FIRST.includes(tool)) {
      setImporting(true);
    }
  }

  function backToMenu() {
    setActive(null);
    setImporting(false);
    setLoadingDoc(false);
    setDoc(null);
    setFileId(null);
    setPages([]);
    setError(null);
  }

  async function onPdfFiles(files: File[]) {
    const file = files[0];
    if (!file) return;
    setImporting(false);
    setLoadingDoc(true);
    try {
      const res = await uploadFile(file);
      setFileId(res.fileId);
      const data = new Uint8Array(await file.arrayBuffer());
      const loaded = await pdfjs.getDocument({ data }).promise;
      setDoc(loaded);
      setPages(
        Array.from({ length: loaded.numPages }, (_, i) => ({
          id: `p-${i + 1}`,
          origin: i + 1,
          rotation: 0,
          selected: false,
        })),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao importar");
    } finally {
      setLoadingDoc(false);
    }
  }

  const dropLabelKey = active === "images" ? "modal.dropImages" : "modal.dropPdf";

  return (
    <main className="container">
      <header className="app-header">
        <div className="brand">
          <div className="brand-logo">P</div>
          <div>
            <b>pdf-web-nando</b>
            <span>Suite de PDF</span>
          </div>
        </div>
        <SettingsBar />
      </header>

      {active === null && <Launcher onPick={pick} />}

      {importing && active !== null && PDF_FIRST.includes(active) && (
        <ImportModal
          title={t(META[active].titleKey)}
          accent={META[active].icon}
          accept="application/pdf"
          dropLabelKey={dropLabelKey}
          hintKey="modal.pdfHint"
          onClose={backToMenu}
          onFiles={onPdfFiles}
        />
      )}

      {active !== null && !importing && (
        <section>
          <div className="op-header">
            <button type="button" className="btn-back" onClick={backToMenu}>
              {ToolIcons.arrow}
              {t("common.back")}
            </button>
            <span className={`op-chip tool-ic ${META[active].icon}`} aria-hidden="true">
              {ToolIcons[META[active].icon]}
            </span>
            <h2 className="op-title">{t(META[active].titleKey)}</h2>
          </div>

          {error && (
            <p role="alert" className="error">
              {error}
            </p>
          )}

          {loadingDoc && <Spinner label={t("common.processing")} />}

          {active === "edit" && doc && fileId && (
            <div className="workspace">
              <div className="panel">
                <PageGrid doc={doc} pages={pages} onChange={setPages} />
              </div>
              <EditPanel fileId={fileId} pages={pages} />
            </div>
          )}

          {active === "split" && doc && fileId && (
            <div className="workspace">
              <div className="panel">
                <PageGrid doc={doc} pages={pages} onChange={setPages} />
              </div>
              <SplitPanel fileId={fileId} />
            </div>
          )}

          {active === "merge" && <MergePanel />}
          {active === "images" && <ImagesPanel />}
          {active === "ocr" && <OcrPanel />}
          {active === "sign" && <SignaturePanel />}
        </section>
      )}
    </main>
  );
}
