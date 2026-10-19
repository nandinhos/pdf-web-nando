import { describe, it, expect } from "vitest";
import {
  applyFontChoice,
  fontSelectValue,
  toggleStyle,
} from "./fontChoice";
import type { LocalElement } from "./contentEditTypes";
import type { DocFontInfo } from "../lib/apiClient";

const textEl = (over: Partial<LocalElement> = {}): LocalElement =>
  ({ id: "t1", type: "text", page: 1, x: 0, y: 0, text: "x", font: "helvetica", ...over } as LocalElement);

const docFonts: DocFontInfo[] = [
  { resource: "F1", page: 1, family: "Times-Bold", bold: true, italic: false, embedded: true },
  { resource: "F2", page: 1, family: "LiberationSerif", bold: false, italic: false, embedded: true },
];

describe("seleção de fonte do documento (regressão do preview)", () => {
  it("escolher uma Fonte do documento define fontResource E fontFamily (para o preview refletir)", () => {
    const patch = applyFontChoice("doc:F1:1", textEl(), docFonts) as Record<string, unknown>;
    expect(patch.fontResource).toBe("F1");
    expect(patch.fontResourcePage).toBe(1);
    // A correção: fontFamily precisa vir junto, senão o canvas não muda nada.
    expect(patch.fontFamily).toBe("Times-Bold");
  });

  it("escolher outra Fonte do documento atualiza a família para a escolhida", () => {
    const patch = applyFontChoice("doc:F2:1", textEl({ fontFamily: "Times-Bold" }), docFonts) as Record<string, unknown>;
    expect(patch.fontResource).toBe("F2");
    expect(patch.fontFamily).toBe("LiberationSerif");
  });

  it("escolher Fonte padrão limpa o recurso do documento e a família detectada", () => {
    const patch = applyFontChoice("std:times-bold", textEl({ fontResource: "F1", fontFamily: "Times-Bold" }), docFonts) as Record<string, unknown>;
    expect(patch.font).toBe("times-bold");
    expect(patch.fontResource).toBeUndefined();
    expect(patch.fontFamily).toBeUndefined();
  });

  it("fontSelectValue reflete o recurso do documento quando presente", () => {
    expect(fontSelectValue(textEl({ fontResource: "F1", fontResourcePage: 1 }))).toBe("doc:F1:1");
    expect(fontSelectValue(textEl({ font: "courier" }))).toBe("std:courier");
  });

  it("toggleStyle produz apenas tokens que o backend resolve", () => {
    expect(toggleStyle("helvetica", "bold", true)).toBe("helvetica-bold");
    expect(toggleStyle("times", "italic", true)).toBe("times-italic");
    expect(toggleStyle("helvetica-bold", "bold", false)).toBe("helvetica");
  });
});
