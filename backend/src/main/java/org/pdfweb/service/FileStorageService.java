package org.pdfweb.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.pdfweb.config.PdfWebProperties;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Storage de artefatos em disco local (Fase 0).
 *
 * <p>Cada arquivo recebe um {@code fileId} (UUID) e é persistido em
 * {@code <localDir>/<fileId>.pdf}. MinIO/S3 e TTL automático entram nas fases
 * seguintes conforme o plano de implementação.
 */
@Service
public class FileStorageService {

    private final Path baseDir;

    public FileStorageService(PdfWebProperties props) {
        this.baseDir = Path.of(props.storage().localDir());
    }

    /**
     * Persiste o conteúdo e retorna o {@code fileId} gerado.
     */
    public String store(InputStream content) {
        String fileId = UUID.randomUUID().toString();
        try {
            Files.createDirectories(baseDir);
            Files.copy(content, pathFor(fileId));
        } catch (IOException e) {
            throw new ApiException("STORAGE_WRITE_FAILED", "Falha ao persistir o arquivo", e);
        }
        return fileId;
    }

    /**
     * Resolve o caminho de um {@code fileId}, validando sua existência.
     */
    public Path resolveExisting(String fileId) {
        Path path = pathFor(fileId);
        if (!Files.isRegularFile(path)) {
            throw new ApiException("FILE_NOT_FOUND", "fileId inexistente: " + fileId);
        }
        return path;
    }

    private Path pathFor(String fileId) {
        // fileId é um UUID gerado internamente — sem risco de path traversal.
        return baseDir.resolve(fileId + ".pdf");
    }
}
