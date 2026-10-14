package org.pdfweb.api.dto;

import java.util.List;

/**
 * Resposta do upload: {@code { fileId, pages, meta }} (PRD §7).
 */
public record UploadResponse(String fileId, int pages, FileMeta meta) {

    public record FileMeta(String filename, long sizeBytes) {
    }

    /**
     * Metadados por página retornados por {@code GET /files/{id}/pages}.
     */
    public record PageInfo(int index, float width, float height, int rotation) {
    }

    public record PagesResponse(String fileId, List<PageInfo> pages) {
    }
}
