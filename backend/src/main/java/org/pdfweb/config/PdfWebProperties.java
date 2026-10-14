package org.pdfweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de configuração do backend (prefixo {@code pdfweb}).
 */
@ConfigurationProperties(prefix = "pdfweb")
public record PdfWebProperties(Storage storage, Limits limits) {

    public record Storage(String localDir) {
    }

    public record Limits(int maxPagesPerOp) {
    }
}
