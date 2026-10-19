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
  /** true quando a fonte original não cobriu o texto e foi substituída por Standard-14. */
  fontSubstituted?: boolean;
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

// ----- Edição de conteúdo (overlay: texto/imagem/tarja achatados no PDF) -----

/**
 * Elemento sobreposto no PDF. Coordenadas em pontos (pt), origem no topo-esquerda
 * da página (mesma convenção de tela/pdf.js); o backend converte para o espaço
 * do PDF. Campos usados variam por `type`.
 */
export type ContentEditElement =
  | {
      type: "text";
      page: number;
      x: number;
      y: number;
      text: string;
      font?: string;
      size?: number;
      color?: string;
      /** Recurso de fonte original a reusar na reescrita (de analyze-layout). */
      fontResource?: string;
      fontResourcePage?: number;
    }
  | {
      type: "image";
      page: number;
      x: number;
      y: number;
      width: number;
      height: number;
      imageId: string;
    }
  | {
      type: "redact";
      page: number;
      x: number;
      y: number;
      width: number;
      height: number;
      color?: string;
      /** Redação REAL: destrói o conteúdo sob a região (rasteriza a página). */
      hard?: boolean;
    };

export async function contentEdit(req: {
  fileId: string;
  elements: ContentEditElement[];
}): Promise<SingleResult> {
  const res = await fetch(`${API_BASE}/content-edit`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}

/**
 * Variante da edição de conteúdo que, após aplicar a redação real (que já
 * rasteriza as páginas redigidas), reaplica OCR para devolver um PDF
 * pesquisável — sem o conteúdo redigido. Retorna um jobId (assíncrono/SSE).
 */
export async function contentEditWithOcr(req: {
  fileId: string;
  elements: ContentEditElement[];
  lang?: string;
  quality?: Quality;
}): Promise<JobCreated> {
  const res = await fetch(`${API_BASE}/content-edit`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...req, ocr: true }),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}

/**
 * Extrai o texto existente dentro de uma área retangular (pt, top-left) de uma
 * página — base da edição assistida: captura o texto para pré-preencher a caixa
 * de edição antes de redigir e reescrever.
 */
export async function extractTextArea(req: {
  fileId: string;
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
}): Promise<{ text: string }> {
  const res = await fetch(`${API_BASE}/text-area`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}

/** Bloco detectado pela análise de layout (coordenadas pt, top-left). */
export interface LayoutBlock {
  kind: "text" | "image";
  x: number;
  y: number;
  width: number;
  height: number;
  text: string | null;
  /** Recurso de fonte original do bloco (para reusar na reescrita). */
  fontResource?: string | null;
  /** Família legível da fonte original do bloco (ex. "Times-Bold"). */
  fontFamily?: string | null;
  /** Tamanho real da fonte do bloco em pt (de getFontSizeInPt). */
  fontSize?: number;
}

/** Fonte embutida/declarada no documento (para o seletor de fonte). */
export interface DocFontInfo {
  resource: string;
  page: number;
  family: string;
  bold: boolean;
  italic: boolean;
  embedded: boolean;
}

/** Lista as fontes já embutidas no PDF (reuso sem reembutir). */
export async function docFonts(req: { fileId: string }): Promise<{ fonts: DocFontInfo[] }> {
  const res = await fetch(`${API_BASE}/doc-fonts`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}

export interface AnalyzeLayoutResult {
  blocks: LayoutBlock[];
  scanned: boolean;
}

/**
 * Detecta blocos editáveis de uma página (texto por linha + imagens), lidos
 * estruturalmente do PDF. `scanned=true` sinaliza página escaneada (sem texto).
 */
export async function analyzeLayout(req: {
  fileId: string;
  page: number;
}): Promise<AnalyzeLayoutResult> {
  const res = await fetch(`${API_BASE}/analyze-layout`, {
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

// ----- Compressão de PDF -----

export interface CompressRequest {
  fileId: string;
  quality?: "low" | "medium" | "high";
  password?: string;
}

export interface CompressResponse {
  resultId: string;
  downloadUrl: string;
  originalSize: number;
  compressedSize: number;
  savingsPercent: number;
}

export async function compressFile(req: CompressRequest): Promise<CompressResponse> {
  const res = await fetch(`${API_BASE}/compress`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(req),
  });
  if (!res.ok) return parseError(res);
  return res.json();
}
