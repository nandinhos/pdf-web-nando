package org.pdfweb.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.pdfweb.config.PdfWebProperties;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Armazenamento de artefatos de resultado (saídas de split/merge/edit).
 *
 * <p>Fase 1: disco local em {@code <localDir>/artifacts}. Cada artefato recebe
 * um {@code resultId} (UUID) e é recuperável para download. TTL/expurgo e
 * MinIO/S3 entram nas fases seguintes (ver IMPLEMENTATION_PLAN §5.4).
 */
@Service
public class ArtifactStorage {

    private final Path baseDir;

    public ArtifactStorage(PdfWebProperties props) {
        this.baseDir = Path.of(props.storage().localDir()).resolve("artifacts");
    }

    /**
     * Cria um caminho novo e único para um artefato com o sufixo dado.
     * O arquivo ainda não existe; o chamador o escreve.
     */
    public Path newArtifact(String suffix) {
        try {
            Files.createDirectories(baseDir);
            return baseDir.resolve(UUID.randomUUID() + suffix);
        } catch (IOException e) {
            throw new ApiException("STORAGE_WRITE_FAILED", "Falha ao preparar artefato", e);
        }
    }

    /**
     * Resolve um artefato existente pelo nome do arquivo (resultId + sufixo).
     * Valida contra path traversal.
     */
    public Path resolveExisting(String fileName) {
        Path resolved = baseDir.resolve(fileName).normalize();
        if (!resolved.startsWith(baseDir.normalize())) {
            throw new ApiException("INVALID_RESULT_ID", "Identificador de resultado inválido");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new ApiException("RESULT_NOT_FOUND", "Resultado inexistente: " + fileName);
        }
        return resolved;
    }

    /** Nome do arquivo (resultId) de um artefato, para compor a URL de download. */
    public String idOf(Path artifact) {
        return artifact.getFileName().toString();
    }
}
