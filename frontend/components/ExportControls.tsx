"use client";

import { useI18n } from "@/lib/i18n";
import type { Quality } from "@/lib/apiClient";

/** Seletor de qualidade de exportação (Baixa/Média/Alta) — Fase 6 B. */
export function QualitySelector({
  value,
  onChange,
}: {
  value: Quality;
  onChange: (q: Quality) => void;
}) {
  const { t } = useI18n();
  const opts: Quality[] = ["low", "medium", "high"];
  return (
    <div className="field-row">
      <span>{t("common.quality")}</span>
      <div className="quality-group" role="group" aria-label={t("common.quality")}>
        {opts.map((q) => (
          <button
            key={q}
            type="button"
            className={value === q ? "active" : ""}
            aria-pressed={value === q}
            onClick={() => onChange(q)}
          >
            {t(`quality.${q}`)}
          </button>
        ))}
      </div>
      <small className="hint">{t("quality.hint")}</small>
    </div>
  );
}

/** Campo de nome do arquivo de saída — Fase 6 C. */
export function FilenameField({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  const { t } = useI18n();
  return (
    <label className="field-row">
      <span>{t("common.filename")}</span>
      <input
        type="text"
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  );
}
