import { useCallback, useRef, useState } from "react";
import type { LocalElement } from "./contentEditTypes";

export interface HistoryApi {
  elements: LocalElement[];
  /** Define um novo estado criando um ponto de histórico (ação discreta). */
  commit: (next: LocalElement[] | ((prev: LocalElement[]) => LocalElement[])) => void;
  /** Atualiza coalescendo no mesmo ponto de histórico (digitação/arraste contínuo). */
  coalesce: (next: LocalElement[] | ((prev: LocalElement[]) => LocalElement[])) => void;
  undo: () => void;
  redo: () => void;
  canUndo: boolean;
  canRedo: boolean;
}

/**
 * Histórico de undo/redo para a lista de elementos do editor.
 *
 * - `commit` empilha um novo estado (ação discreta: inserir, remover, detectar,
 *   soltar após arrastar, aplicar edição) — vira um passo de undo.
 * - `coalesce` substitui o estado atual sem criar novo passo (digitação letra a
 *   letra, movimento contínuo), para o histórico não encher de micro-passos.
 * - `undo`/`redo` navegam a pilha; `redo` é descartado após um novo `commit`.
 */
export function useHistory(initial: LocalElement[] = []): HistoryApi {
  const [stack, setStack] = useState<LocalElement[][]>([initial]);
  const [index, setIndex] = useState(0);
  // Evita closures obsoletas em callbacks de eventos globais.
  const ref = useRef({ stack, index });
  ref.current = { stack, index };

  const resolve = (
    next: LocalElement[] | ((prev: LocalElement[]) => LocalElement[]),
    prev: LocalElement[],
  ): LocalElement[] => (typeof next === "function" ? (next as (p: LocalElement[]) => LocalElement[])(prev) : next);

  const commit = useCallback(
    (next: LocalElement[] | ((prev: LocalElement[]) => LocalElement[])) => {
      setStack((s) => {
        const i = ref.current.index;
        const base = s[i];
        const value = resolve(next, base);
        const trimmed = s.slice(0, i + 1); // descarta o "redo" pendente
        const newStack = [...trimmed, value];
        setIndex(newStack.length - 1);
        return newStack;
      });
    },
    [],
  );

  const coalesce = useCallback(
    (next: LocalElement[] | ((prev: LocalElement[]) => LocalElement[])) => {
      setStack((s) => {
        const i = ref.current.index;
        const value = resolve(next, s[i]);
        const copy = s.slice();
        copy[i] = value; // substitui o topo atual, sem novo passo
        return copy;
      });
    },
    [],
  );

  const undo = useCallback(() => setIndex((i) => Math.max(0, i - 1)), []);
  const redo = useCallback(
    () => setIndex((i) => Math.min(ref.current.stack.length - 1, i + 1)),
    [],
  );

  return {
    elements: stack[index],
    commit,
    coalesce,
    undo,
    redo,
    canUndo: index > 0,
    canRedo: index < stack.length - 1,
  };
}
