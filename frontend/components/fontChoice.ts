import type { LocalElement } from "./contentEditTypes";
import type { DocFontInfo } from "../lib/apiClient";

/** Valor atual do select de fonte: recurso do doc (doc:...) ou Standard-14 (std:...). */
export function fontSelectValue(el: LocalElement): string {
  if (el.type === "text" && el.fontResource) {
    return `doc:${el.fontResource}${el.fontResourcePage ? ":" + el.fontResourcePage : ""}`;
  }
  return `std:${(el.type === "text" && el.font) || "helvetica"}`;
}

/** Converte a escolha do select em patch do elemento. */
export function applyFontChoice(value: string, el: LocalElement, docFonts: DocFontInfo[]): Partial<LocalElement> {
  if (value.startsWith("doc:")) {
    const rest = value.slice(4);
    const [resource, pageStr] = rest.split(":");
    const page = pageStr ? Number(pageStr) : undefined;
    // Resolve a família para o preview: a detectada (quando é o próprio recurso do
    // bloco) ou a da fonte do documento escolhida.
    const match = docFonts.find((f) => f.resource === resource && (page == null || f.page === page));
    const family = match?.family ?? (el.type === "text" ? el.fontFamily : undefined);
    return { fontResource: resource, fontResourcePage: page, fontFamily: family ?? undefined };
  }
  // Standard-14: limpa o recurso do doc e fixa a família.
  return { font: value.slice(4), fontResource: undefined, fontResourcePage: undefined, fontFamily: undefined };
}

/** Alterna negrito/itálico sobre o nome da família Standard-14.
 *  Mantém apenas tokens que o backend resolve (helvetica/times/courier +
 *  -bold | -oblique | -italic). Bold+itálico combinados não existem nas
 *  Standard-14 do backend → prioriza negrito (limite declarado). */
export function toggleStyle(font: string | undefined, style: "bold" | "italic", on: boolean): string {
  const raw = (font ?? "helvetica").toLowerCase();
  const fam = raw.startsWith("times") ? "times" : raw.startsWith("courier") ? "courier" : "helvetica";
  const bold = style === "bold" ? on : /bold/.test(raw);
  const italic = style === "italic" ? on : /italic|oblique/.test(raw);
  const italicToken = fam === "helvetica" ? "oblique" : "italic";
  if (bold) return `${fam}-bold`; // courier-bold existe; times-bold; helvetica-bold
  if (italic) return fam === "courier" ? "courier" : `${fam}-${italicToken}`;
  return fam;
}
