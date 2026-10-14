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
 * Storage de imagens de entrada (Fase 2). Preserva a extensão original porque o
 * algoritmo de imagem→PDF (ancorado em {@code ImageToPdf}) detecta o tipo pelo
 * nome do arquivo. Cada imagem recebe um {@code imageId} (UUID).
 */
@Service
public class ImageStorage {

    private static final String ALLOWED = "jpg,jpeg,png,tif,tiff,webp";

    private final Path baseDir;

    public ImageStorage(PdfWebProperties props) {
        this.baseDir = Path.of(props.storage().localDir()).resolve("images");
    }

    /**
     * Persiste a imagem com sua extensão original e retorna o {@code imageId}
     * (nome do arquivo = UUID + extensão).
     */
    public String store(InputStream content, String originalFilename) {
        String ext = extensionOf(originalFilename);
        if (!("," + ALLOWED + ",").contains("," + ext + ",")) {
            throw new ApiException("UNSUPPORTED_IMAGE", "Formato de imagem não suportado: " + ext);
        }
        String imageId = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(baseDir);
            Files.copy(content, baseDir.resolve(imageId));
        } catch (IOException e) {
            throw new ApiException("STORAGE_WRITE_FAILED", "Falha ao persistir a imagem", e);
        }
        return imageId;
    }

    /** Resolve uma imagem existente pelo imageId (UUID + extensão), com anti-traversal. */
    public Path resolveExisting(String imageId) {
        Path resolved = baseDir.resolve(imageId).normalize();
        if (!resolved.startsWith(baseDir.normalize())) {
            throw new ApiException("INVALID_IMAGE_ID", "Identificador de imagem inválido");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new ApiException("IMAGE_NOT_FOUND", "imageId inexistente: " + imageId);
        }
        return resolved;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
