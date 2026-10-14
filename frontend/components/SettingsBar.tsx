"use client";

import { useI18n } from "@/lib/i18n";
import { useTheme } from "@/lib/theme";

/**
 * Barra de preferências: idioma (PT/EN) e tema (claro/escuro). A11y: controles
 * rotulados e operáveis por teclado.
 */
export function SettingsBar() {
  const { lang, setLang, t } = useI18n();
  const { theme, toggle } = useTheme();

  return (
    <div className="settings-bar">
      <label>
        {t("common.language")}
        <select value={lang} onChange={(e) => setLang(e.target.value as "pt" | "en")} aria-label={t("common.language")}>
          <option value="pt">PT-BR</option>
          <option value="en">EN</option>
        </select>
      </label>
      <button
        type="button"
        onClick={toggle}
        aria-label={t("common.theme")}
        aria-pressed={theme === "dark"}
      >
        {theme === "dark" ? `🌙 ${t("theme.dark")}` : `☀️ ${t("theme.light")}`}
      </button>
    </div>
  );
}
