package org.pdfweb.api.dto;

import java.util.List;

/**
 * DTOs das ferramentas de página (split/merge/edit) e do resultado.
 */
public final class ToolDtos {

    private ToolDtos() {
    }

    // ----- Split -----
    public record SplitRequest(String fileId, String mode, Integer pagesPerPdf, String splitPoints, String password) {
    }

    public record ResultFile(String resultId, String name, String downloadUrl) {
    }

    public record SplitResponse(List<ResultFile> files) {
    }

    // ----- Merge -----
    public record MergeItem(String fileId, String ranges, String password) {
    }

    public record MergeRequest(List<MergeItem> items, String mode) {
    }

    public record SingleResultResponse(String resultId, String downloadUrl, boolean fontSubstituted) {
        /** Compat: resultados sem edição de fonte (split/merge/edit) não substituem fonte. */
        public SingleResultResponse(String resultId, String downloadUrl) {
            this(resultId, downloadUrl, false);
        }
    }

    // ----- Edit -----
    public record EditOp(String type, Integer page, Integer rotate, List<Integer> order) {
    }

    public record EditRequest(String fileId, List<EditOp> ops, String password) {
    }

    // ----- Content Edit (overlay: texto/imagem/tarja achatados no PDF) -----
    public record ContentElement(String type, Integer page, Float x, Float y, Float width, Float height,
                                 String text, String font, Float size, String color, String imageId,
                                 Boolean hard, String fontResource, Integer fontResourcePage) {
    }

    public record ContentEditRequest(String fileId, List<ContentElement> elements, String password,
                                     Boolean ocr, String lang, String quality) {
    }

    // ----- Extração de texto por área (edição assistida de texto) -----
    public record TextAreaRequest(String fileId, String password, Integer page,
                                  Float x, Float y, Float width, Float height) {
    }

    public record TextAreaResponse(String text) {
    }

    // ----- Análise de layout (detecção de blocos editáveis) -----
    public record AnalyzeLayoutRequest(String fileId, String password, Integer page) {
    }

    public record LayoutBlock(String kind, float x, float y, float width, float height, String text,
                              String fontResource, String fontFamily, float fontSize) {
    }

    public record AnalyzeLayoutResponse(java.util.List<LayoutBlock> blocks, boolean scanned) {
    }

    // ----- Fontes embutidas do documento -----
    public record DocFontsRequest(String fileId, String password) {
    }

    public record DocFontInfo(String resource, int page, String family, boolean bold, boolean italic,
                              boolean embedded) {
    }

    public record DocFontsResponse(java.util.List<DocFontInfo> fonts) {
    }

    // ----- Imagens -----
    public record ImageUploadResponse(String imageId, String filename) {
    }

    public record ImageToPdfRequest(List<String> imageIds, String pageSize, String orientation,
                                    Integer dpi, Float margin, Boolean usePageSize, String quality) {
    }

    public record ImageExtractRequest(String fileId, Boolean passJpg, Boolean passJp2, Boolean jp2ToJpg) {
    }

    // ----- OCR / Jobs -----
    public record OcrRequest(String fileId, String lang, Integer dpi, String quality) {
    }

    public record JobCreatedResponse(String jobId) {
    }

    public record JobStatusResponse(String jobId, String status, int progress, int totalPages,
                                    String resultUrl, String textUrl, String error) {
    }

    // ----- Compressão de PDF -----
    public record CompressRequest(String fileId, String quality, String password) {
    }

    public record CompressResponse(String resultId, String downloadUrl, long originalSize,
                                    long compressedSize, int savingsPercent) {
    }
}
