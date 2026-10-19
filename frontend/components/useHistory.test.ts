import { describe, it, expect } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useHistory } from "./useHistory";
import type { LocalElement } from "./contentEditTypes";

const el = (id: string, text = ""): LocalElement =>
  ({ id, type: "text", page: 1, x: 0, y: 0, text, font: "helvetica" } as LocalElement);

describe("useHistory (undo/redo)", () => {
  it("commit cria passos; undo/redo navegam", () => {
    const { result } = renderHook(() => useHistory([]));
    expect(result.current.canUndo).toBe(false);

    act(() => result.current.commit([el("a")]));
    act(() => result.current.commit((p) => [...p, el("b")]));
    expect(result.current.elements.map((e) => e.id)).toEqual(["a", "b"]);
    expect(result.current.canUndo).toBe(true);

    act(() => result.current.undo());
    expect(result.current.elements.map((e) => e.id)).toEqual(["a"]);
    expect(result.current.canRedo).toBe(true);

    act(() => result.current.redo());
    expect(result.current.elements.map((e) => e.id)).toEqual(["a", "b"]);
  });

  it("coalesce não cria novo passo (digitação contínua)", () => {
    const { result } = renderHook(() => useHistory([]));
    act(() => result.current.commit([el("a", "")]));
    act(() => result.current.coalesce((p) => p.map((e) => ({ ...e, text: "o" } as LocalElement))));
    act(() => result.current.coalesce((p) => p.map((e) => ({ ...e, text: "oi" } as LocalElement))));

    // Um único undo volta ao estado anterior ao commit, não letra a letra.
    act(() => result.current.undo());
    expect(result.current.elements).toEqual([]);
  });

  it("commit após undo descarta o redo pendente", () => {
    const { result } = renderHook(() => useHistory([]));
    act(() => result.current.commit([el("a")]));
    act(() => result.current.commit((p) => [...p, el("b")]));
    act(() => result.current.undo()); // volta a ["a"]
    act(() => result.current.commit((p) => [...p, el("c")])); // novo ramo
    expect(result.current.elements.map((e) => e.id)).toEqual(["a", "c"]);
    expect(result.current.canRedo).toBe(false);
  });
});
