package org.pdfweb.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdfweb.testsupport.PdfFixtures;

class PdfDocumentsTest {

    @Test
    void pageCountReturnsNumberOfPages(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("doc.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(7));

        int pages = PdfDocuments.pageCount(pdf, null);

        assertThat(pages).isEqualTo(7);
    }

    @Test
    void withDocumentClosesAndReturnsValue(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("doc.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(3));

        float firstWidth = PdfDocuments.withDocument(pdf, null,
                doc -> doc.getPage(0).getMediaBox().getWidth());

        assertThat(firstWidth).isGreaterThan(0f);
    }
}
