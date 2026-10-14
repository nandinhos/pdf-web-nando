package org.pdfweb.error;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Tradutor global de exceções para o envelope de erro consistente do PRD §7:
 * {@code { "error": { "code", "message", "details" } }}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "FILE_NOT_FOUND", "RESULT_NOT_FOUND", "IMAGE_NOT_FOUND", "JOB_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_PDF", "EMPTY_UPLOAD", "UNSUPPORTED_IMAGE", "EMPTY_IMAGE_SET",
                 "EMPTY_MERGE", "INVALID_MERGE_ARGS", "INVALID_EDIT_OP", "EMPTY_SPLIT",
                 "INVALID_RESULT_ID", "INVALID_IMAGE_ID", "JOB_NOT_READY" -> HttpStatus.BAD_REQUEST;
            case "OCR_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(envelope(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(envelope("FILE_TOO_LARGE", "Arquivo excede o tamanho máximo permitido", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(envelope("INTERNAL_ERROR", "Erro interno inesperado", null));
    }

    private Map<String, Object> envelope(String code, String message, Object details) {
        Map<String, Object> error = new java.util.LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("details", details);
        return Map.of("error", error);
    }
}
