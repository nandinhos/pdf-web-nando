import type { ContentEditElement } from "@/lib/apiClient";

/**
 * Elemento local do editor: o contrato enviado ao backend
 * ({@link ContentEditElement}) acrescido de campos somente-cliente:
 * - `id`: identidade estável na lista/seleção;
 * - `previewUrl`: object URL da imagem, para renderizar no canvas (não enviado);
 * - `fontFamily`: família legível da fonte original detectada (exibição; não enviado).
 */
export type LocalElement = ContentEditElement & { id: string; previewUrl?: string; fontFamily?: string };

/** Ferramenta de inserção ativa no canvas (null = só selecionar/mover). */
export type InsertTool = "text" | "image" | "redact" | "edit-text" | null;
