"use client";

interface SpinnerProps {
  /** Texto opcional ao lado do spinner (ex.: "Processando…"). */
  label?: string;
  /** Quando true, cobre a área com um overlay (loader de processamento). */
  overlay?: boolean;
}

/**
 * Loader reutilizável (Fase 6, item A). Dois modos: inline (ao lado de um texto)
 * ou overlay (cobre o painel enquanto processa).
 */
export function Spinner({ label, overlay = false }: SpinnerProps) {
  const content = (
    <span className="spinner-row" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      {label && <span>{label}</span>}
    </span>
  );

  if (overlay) {
    return <div className="spinner-overlay">{content}</div>;
  }
  return content;
}
