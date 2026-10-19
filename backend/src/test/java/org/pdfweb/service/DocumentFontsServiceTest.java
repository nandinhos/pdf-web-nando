package org.pdfweb.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdfweb.testsupport.PdfFixtures;

class DocumentFontsServiceTest {

    private DocumentFontsService service;

    @BeforeEach
    void setUp() {
        service = new DocumentFontsService();
    }

    @Test
    void listsEmbeddedFontOfPdf(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        // Fixture escreve com HELVETICA_BOLD na página 1.
        Files.write(pdf, PdfFixtures.pdfWithTextAt("A", 72f, 700f, "B", 72f, 600f));

        List<DocumentFontsService.FontInfo> fonts = service.listFonts(pdf, null);

        assertThat(fonts).isNotEmpty();
        assertThat(fonts).anySatisfy(f -> {
            assertThat(f.resource()).isNotBlank();
            assertThat(f.page()).isEqualTo(1);
            assertThat(f.family().toLowerCase()).contains("helvetica");
            assertThat(f.bold()).isTrue(); // Helvetica-Bold
        });
    }

    @Test
    void blankPdfHasNoFonts(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("blank.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(1));

        assertThat(service.listFonts(pdf, null)).isEmpty();
    }
}
