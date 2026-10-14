"use client";

import { useState } from "react";
import { uploadFile, verifySignature, type SignatureReport } from "@/lib/apiClient";

/**
 * Aba "Assinatura" (PRD §4.6): envia um PDF e exibe o relatório de verificação
 * (válida/inválida, CN, data, cobertura, cadeia confiável).
 */
export function SignaturePanel() {
  const [report, setReport] = useState<SignatureReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setError(null);
    setReport(null);
    setBusy(true);
    try {
      const uploaded = await uploadFile(file);
      setReport(await verifySignature(uploaded.fileId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha ao verificar");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="tools" aria-label="Verificação de assinatura">
      <fieldset>
        <legend>Verificar assinatura</legend>
        <label className="upload">
          <span>Selecionar PDF assinado</span>
          <input type="file" accept="application/pdf" onChange={onSelect} disabled={busy} aria-label="PDF para verificar" />
        </label>

        {busy && <p role="status">Verificando…</p>}

        {report && !report.hasSignatures && <p className="hint">Nenhuma assinatura encontrada neste PDF.</p>}

        {report && report.hasSignatures && (
          <div aria-live="polite">
            <p>
              Resultado geral:{" "}
              <strong className={report.allValid ? "ok" : "error"}>
                {report.allValid ? "Válido" : "Inválido / com ressalvas"}
              </strong>
            </p>
            <ul className="sig-list">
              {report.signatures.map((s, i) => (
                <li key={i} className="sig-item">
                  <p>
                    <strong>{s.signerCN ?? s.name}</strong>{" "}
                    <span className={s.valid ? "ok" : "error"}>{s.valid ? "✓ válida" : "✗ inválida"}</span>
                  </p>
                  <dl className="sig-detail">
                    <dt>Cobre o arquivo todo</dt>
                    <dd>{s.coversWholeFile ? "Sim" : "Não (cobertura parcial)"}</dd>
                    <dt>Cadeia confiável</dt>
                    <dd>{s.chainTrusted ? "Sim" : "Não"}</dd>
                    <dt>Data</dt>
                    <dd>{s.signedAt ?? "—"}</dd>
                    <dt>Algoritmo de digest</dt>
                    <dd>{s.digestAlgorithm ?? "—"}</dd>
                    <dt>eIDAS qualificada</dt>
                    <dd>{s.eidasQualified}</dd>
                    {s.error && (
                      <>
                        <dt>Erro</dt>
                        <dd className="error">{s.error}</dd>
                      </>
                    )}
                  </dl>
                </li>
              ))}
            </ul>
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
