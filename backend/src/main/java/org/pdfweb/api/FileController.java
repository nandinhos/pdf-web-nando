package org.pdfweb.api;

import java.io.IOException;
import java.nio.file.Path;

import org.pdfweb.api.dto.UploadResponse;
import org.pdfweb.api.dto.UploadResponse.FileMeta;
import org.pdfweb.api.dto.UploadResponse.PagesResponse;
import org.pdfweb.error.ApiException;
import org.pdfweb.service.FileStorageService;
import org.pdfweb.service.PdfFileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints de arquivos (PRD §7):
 * <ul>
 *   <li>{@code POST /files} → upload, retorna {@code { fileId, pages, meta }}</li>
 *   <li>{@code GET  /files/{id}/pages} → metadados por página</li>
 * </ul>
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileStorageService storage;
    private final PdfFileService pdfFiles;

    public FileController(FileStorageService storage, PdfFileService pdfFiles) {
        this.storage = storage;
        this.pdfFiles = pdfFiles;
    }

    @PostMapping
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ApiException("EMPTY_UPLOAD", "Nenhum arquivo enviado");
        }
        byte[] bytes = file.getBytes();
        // Hardening (PRD §9): valida o tipo real por magic bytes, não pela extensão.
        if (!org.pdfweb.core.FileTypeValidator.isPdf(bytes)) {
            throw new ApiException("INVALID_PDF", "O arquivo enviado não é um PDF válido");
        }
        String fileId = storage.store(new java.io.ByteArrayInputStream(bytes));
        Path path = storage.resolveExisting(fileId);
        int pages = pdfFiles.countPages(path);
        UploadResponse body = new UploadResponse(
                fileId,
                pages,
                new FileMeta(file.getOriginalFilename(), file.getSize()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{fileId}/pages")
    public PagesResponse pages(@PathVariable String fileId) {
        Path path = storage.resolveExisting(fileId);
        return new PagesResponse(fileId, pdfFiles.pageInfos(path));
    }
}
