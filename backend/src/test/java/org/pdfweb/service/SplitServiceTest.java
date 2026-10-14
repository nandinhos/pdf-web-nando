package org.pdfweb.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdfweb.config.PdfWebProperties;
import org.pdfweb.core.PdfDocuments;
import org.pdfweb.testsupport.PdfFixtures;

class SplitServiceTest {

    private SplitService service;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        PdfWebProperties props = new PdfWebProperties(
                new PdfWebProperties.Storage(tmp.toString()), new PdfWebProperties.Limits(2000));
        service = new SplitService(new ArtifactStorage(props));
    }

    @Test
    void pagesPerPdfSplitsTenByThreeIntoFourFiles(@TempDir Path tmp) throws Exception {
        // Critério de aceite PRD §4.1: 10 páginas a cada 3 → 4 arquivos (3+3+3+1).
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(10));

        List<SplitService.OutFile> out = service.split(pdf, SplitService.MODE_PAGES_PER_PDF, 3, null, null);

        assertThat(out).hasSize(4);
        assertThat(PdfDocuments.pageCount(out.get(0).file(), null)).isEqualTo(3);
        assertThat(PdfDocuments.pageCount(out.get(1).file(), null)).isEqualTo(3);
        assertThat(PdfDocuments.pageCount(out.get(2).file(), null)).isEqualTo(3);
        assertThat(PdfDocuments.pageCount(out.get(3).file(), null)).isEqualTo(1);
    }

    @Test
    void splitPointsProducesRanges(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(10));

        List<SplitService.OutFile> out = service.split(pdf, SplitService.MODE_CUSTOM, 1, "3,7", null);

        // Pontos 3 e 7 → faixas 1-3, 4-7, 8-10.
        assertThat(out).hasSize(3);
        assertThat(PdfDocuments.pageCount(out.get(0).file(), null)).isEqualTo(3);
        assertThat(PdfDocuments.pageCount(out.get(1).file(), null)).isEqualTo(4);
        assertThat(PdfDocuments.pageCount(out.get(2).file(), null)).isEqualTo(3);
    }

    @Test
    void evenOddSeparatesIntoTwoFiles(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(5));

        List<SplitService.OutFile> out = service.split(pdf, SplitService.MODE_EVEN_ODD, 1, null, null);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).name()).isEqualTo("odd");
        assertThat(PdfDocuments.pageCount(out.get(0).file(), null)).isEqualTo(3); // páginas 1,3,5
        assertThat(PdfDocuments.pageCount(out.get(1).file(), null)).isEqualTo(2); // páginas 2,4
    }

    @Test
    void cutInHalfDoublesPageCount(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(2));

        List<SplitService.OutFile> out = service.split(pdf, SplitService.MODE_CUT_IN_HALF, 1, null, null);

        assertThat(out).hasSize(1);
        // Cada página vira duas metades → 2 páginas * 2 = 4.
        assertThat(PdfDocuments.pageCount(out.get(0).file(), null)).isEqualTo(4);
    }
}
