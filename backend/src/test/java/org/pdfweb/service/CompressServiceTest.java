package org.pdfweb.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdfweb.config.PdfWebProperties;
import org.pdfweb.core.PdfDocuments;
import org.pdfweb.testsupport.PdfFixtures;

class CompressServiceTest {

    private CompressService service;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        PdfWebProperties props = new PdfWebProperties(
                new PdfWebProperties.Storage(tmp.toString()), new PdfWebProperties.Limits(2000));
        service = new CompressService(new ArtifactStorage(props));
    }

    @Test
    void compressPreservesPageCount(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(3));

        CompressService.CompressResult res = service.compress(pdf, "medium", null);

        assertThat(Files.exists(res.file())).isTrue();
        assertThat(PdfDocuments.pageCount(res.file(), null)).isEqualTo(3);
        assertThat(res.originalSize()).isGreaterThan(0);
        assertThat(res.compressedSize()).isGreaterThan(0);
    }

    @Test
    void compressReducesOrPreservesPdfWithImage(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("with-image.pdf");
        Files.write(pdf, PdfFixtures.pdfWithImage(200, 200));

        CompressService.CompressResult res = service.compress(pdf, "low", null);

        assertThat(Files.exists(res.file())).isTrue();
        assertThat(PdfDocuments.pageCount(res.file(), null)).isEqualTo(1);
        assertThat(res.compressedSize()).isGreaterThan(0);
        assertThat(res.savingsPercent()).isGreaterThanOrEqualTo(0);
    }
}
