package org.pdfweb.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check leve conforme contrato do PRD §7 ({@code GET /api/v1/health}).
 * Complementa o endpoint do actuator.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
