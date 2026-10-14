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

    public record SingleResultResponse(String resultId, String downloadUrl) {
    }

    // ----- Edit -----
    public record EditOp(String type, Integer page, Integer rotate, List<Integer> order) {
    }

    public record EditRequest(String fileId, List<EditOp> ops, String password) {
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
}
