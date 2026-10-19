package org.pdfweb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdfweb.error.ApiException;
import org.pdfweb.testsupport.PdfFixtures;

class TextExtractServiceTest {

    private TextExtractService service;

    @BeforeEach
    void setUp() {
        service = new TextExtractService();
    }

    @Test
    void extractsTextInsideArea(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        // "SEGREDO-123" em bottom-left (72, 750); "PUBLICO-ok" em (72, 400).
        Files.write(pdf, PdfFixtures.pdfWithTextAt("SEGREDO-123", 72f, 750f, "PUBLICO-ok", 72f, 400f));

        // Área (top-left) em torno do texto secreto: A4 alt=842; y_tl ≈ 842-750-18 ≈ 74.
        String text = service.extractArea(pdf, null, 1, 60f, 60f, 260f, 40f);

        assertThat(text).contains("SEGREDO-123").doesNotContain("PUBLICO-ok");
    }

    @Test
    void emptyAreaReturnsEmptyString(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.pdfWithTextAt("SEGREDO-123", 72f, 750f, "PUBLICO-ok", 72f, 400f));

        // Região sem texto (meio da página, longe de ambos).
        String text = service.extractArea(pdf, null, 1, 300f, 300f, 50f, 50f);

        assertThat(text).isEmpty();
    }

    @Test
    void invalidAreaIsRejected(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(1));

        assertThatThrownBy(() -> service.extractArea(pdf, null, 1, 10f, 10f, 0f, 20f))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void pageOutOfRangeIsRejected(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(1));

        assertThatThrownBy(() -> service.extractArea(pdf, null, 9, 10f, 10f, 20f, 20f))
                .isInstanceOf(ApiException.class);
    }
}
