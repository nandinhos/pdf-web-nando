"use client";

import { useI18n } from "@/lib/i18n";
import { ToolIcons, type ToolId } from "@/components/ToolIcons";

interface LauncherProps {
  onPick: (tool: ToolId) => void;
}

const TOOLS: { id: ToolId; icon: string; titleKey: string; descKey: string }[] = [
  { id: "edit", icon: "edit", titleKey: "tab.edit", descKey: "tool.edit.desc" },
  { id: "split", icon: "split", titleKey: "tab.split", descKey: "tool.split.desc" },
  { id: "merge", icon: "merge", titleKey: "tab.merge", descKey: "tool.merge.desc" },
  { id: "images", icon: "images", titleKey: "tab.images", descKey: "tool.images.desc" },
  { id: "ocr", icon: "ocr", titleKey: "tab.ocr", descKey: "tool.ocr.desc" },
  { id: "sign", icon: "sign", titleKey: "tab.signature", descKey: "tool.sign.desc" },
];

/**
 * Página inicial premium (direção C): grid de cards, um por ferramenta.
 * Ícone colorido à esquerda, título à direita, descrição abaixo.
 */
export function Launcher({ onPick }: LauncherProps) {
  const { t } = useI18n();

  return (
    <section aria-labelledby="launcher-h">
      <h1 id="launcher-h" className="launcher-title">
        {t("launcher.title")}
      </h1>
      <p className="launcher-sub">{t("launcher.sub")}</p>

      <ul className="card-grid">
        {TOOLS.map((tool) => (
          <li key={tool.id}>
            <button type="button" className="tool-card" onClick={() => onPick(tool.id)}>
              <span className="card-arrow" aria-hidden="true">
                {ToolIcons.arrow}
              </span>
              <span className="card-head">
                <span className={`tool-ic ${tool.icon}`} aria-hidden="true">
                  {ToolIcons[tool.icon]}
                </span>
                <h3>{t(tool.titleKey)}</h3>
              </span>
              <p>{t(tool.descKey)}</p>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
