// Cliente de API tipado para o backend pdf-web.
// Trata o envelope de erro consistente do PRD §7: { error: { code, message, details } }.

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api/v1";

export interface PageInfo {
  index: number;
  width: number;
  height: number;
  rotation: number;
}

export interface UploadResponse {
  fileId: string;
  pages: number;
  meta: { filename: string; sizeBytes: number };
}

export interface PagesResponse {
  fileId: string;
  pages: PageInfo[];
}

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function parseError(res: Response): Promise<never> {
  let code = "HTTP_" + res.status;
  let message = res.statusText;
  try {
    const body = await res.json();
    if (body?.error) {
      code = body.error.code ?? code;
      message = body.error.message ?? message;
      throw new ApiError(code, message, body.error.details);
    }
  } catch (e) {
    if (e instanceof ApiError) throw e;
  }
  throw new ApiError(code, message);
}

export async function uploadFile(file: File): Promise<UploadResponse> {
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(`${API_BASE}/files`, { method: "POST", body: form });
  if (!res.ok) return parseError(res);
  return res.json();
}

export async function getPages(fileId: string): Promise<PagesResponse> {
  const res = await fetch(`${API_BASE}/files/${encodeURIComponent(fileId)}/pages`);
  if (!res.ok) return parseError(res);
  return res.json();
}

// ----- Ferramentas de página (Fase 1) -----

export interface ResultFile {
  resultId: string;
  name: string;
  downloadUrl: string;
}

export interface SplitResponse {
  files: ResultFile[];
}

export interface SingleResult {
  resultId: string;
  downloadUrl: string;
}

export type SplitMode =
  | "pagesPerPdf"
  | "custom"
  | "evenOdd"
  | "halvePages"
  | "cutInHalf";

export async function splitFile(req: {
  fileId: string;
  mode: SplitMode;
  pagesPerPdf?: number;
  splitPoints?: string;
}): Promise<SplitResponse> {
  const res = await fetch(`${API_BASE}/split`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}

export type MergeMode = "concatenate" | "alternate" | "oddEvenReverse";

export async function mergeFiles(req: {
  items: { fileId: string; ranges?: string }[];
  mode: MergeMode;
}): Promise<SingleResult> {
  const res = await fetch(`${API_BASE}/merge`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}

export type EditOp =
  | { type: "reorder"; order: number[] }
  | { type: "rotate"; page: number; rotate: number }
  | { type: "delete"; page: number }
  | { type: "duplicate"; page: number }
  | { type: "insertBlank"; page: number };

export async function editFile(req: {
  fileId: string;
  ops: EditOp[];
}): Promise<SingleResult> {
  const res = await fetch(`${API_BASE}/edit`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}

/** URL absoluta de download de um resultado (downloadUrl já vem como /api/v1/...). */
export function resultUrl(downloadUrl: string): string {
  return downloadUrl;
}

/** Qualidade de exportação (Fase 6). */
export type Quality = "low" | "medium" | "high";

/** Acrescenta ?name=<nome> à URL de download para nomear o arquivo salvo. */
export function withName(downloadUrl: string, name: string): string {
  const trimmed = name.trim();
  if (!trimmed) return downloadUrl;
  const sep = downloadUrl.includes("?") ? "&" : "?";
  return `${downloadUrl}${sep}name=${encodeURIComponent(trimmed)}`;
}

// ----- Imagens (Fase 2) -----

export interface ImageUploadResponse {
  imageId: string;
  filename: string;
}

export async function uploadImage(file: File): Promise<ImageUploadResponse> {
  const form = new FormData();
  form.append("file", file);
  const res = await fetch(`${API_BASE}/images`, { method: "POST", body: form });
  if (!res.ok) return parseError(res);
  return res.json();
}

export type PageSizeName = "A4" | "A3" | "A5" | "LETTER" | "LEGAL";
export type ImageOrientation = "portrait" | "landscape" | "auto";

export async function imagesToPdf(req: {
  imageIds: string[];
  pageSize?: PageSizeName;
  orientation?: ImageOrientation;
  dpi?: number;
  margin?: number;
  usePageSize?: boolean;
  quality?: Quality;
}): Promise<SingleResult> {
  const res = await fetch(`${API_BASE}/images/to-pdf`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}

export async function extractImages(req: {
  fileId: string;
  passJpg?: boolean;
  passJp2?: boolean;
  jp2ToJpg?: boolean;
}): Promise<SplitResponse> {
  const res = await fetch(`${API_BASE}/images/extract`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}

// ----- OCR / Jobs (Fase 3) -----

export interface JobCreated {
  jobId: string;
}

export interface JobStatus {
  jobId: string;
  status: "PENDING" | "RUNNING" | "DONE" | "ERROR";
  progress: number;
  totalPages: number;
  resultUrl: string | null;
  textUrl: string | null;
  error: string | null;
}

export async function startOcr(req: {
  fileId: string;
  lang?: string;
  dpi?: number;
  quality?: Quality;
}): Promise<JobCreated> {
  const res = await fetch(`${API_BASE}/ocr`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}

export async function getJob(jobId: string): Promise<JobStatus> {
  const res = await fetch(`${API_BASE}/jobs/${encodeURIComponent(jobId)}`);
  if (!res.ok) return parseError(res);
  return res.json();
}

/** Assina o SSE de progresso de um job. Retorna o EventSource para cancelamento. */
export function subscribeJob(jobId: string, onProgress: (data: { status: string; progress: number; totalPages: number }) => void): EventSource {
  const es = new EventSource(`${API_BASE}/jobs/${encodeURIComponent(jobId)}/events`);
  es.addEventListener("progress", (e) => {
    try {
      onProgress(JSON.parse((e as MessageEvent).data));
    } catch {
      // ignora payloads malformados
    }
  });
  return es;
}

// ----- Verificação de assinatura (Fase 4) -----

export interface SignatureInfo {
  name: string;
  kind: string;
  valid: boolean;
  coversWholeFile: boolean;
  signerCN: string | null;
  signedAt: string | null;
  digestAlgorithm: string | null;
  chainTrusted: boolean;
  eidasQualified: string;
  error: string | null;
}

export interface SignatureReport {
  hasSignatures: boolean;
  count: number;
  allValid: boolean;
  signatures: SignatureInfo[];
}

export async function verifySignature(fileId: string): Promise<SignatureReport> {
  const res = await fetch(`${API_BASE}/signatures/verify`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ fileId }),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}
