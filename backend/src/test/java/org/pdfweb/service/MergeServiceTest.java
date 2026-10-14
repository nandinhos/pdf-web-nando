package org.pdfweb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdfweb.config.PdfWebProperties;
import org.pdfweb.core.PdfDocuments;
import org.pdfweb.error.ApiException;
import org.pdfweb.testsupport.PdfFixtures;

class MergeServiceTest {

    private MergeService service;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        PdfWebProperties props = new PdfWebProperties(
                new PdfWebProperties.Storage(tmp.toString()), new PdfWebProperties.Limits(2000));
        service = new MergeService(new ArtifactStorage(props));
    }

    @Test
    void concatenateJoinsInOrder(@TempDir Path tmp) throws Exception {
        // Critério PRD §4.2: A(2p)+B(3p) → 5 páginas.
        Path a = tmp.resolve("a.pdf");
        Path b = tmp.resolve("b.pdf");
        Files.write(a, PdfFixtures.blankPdf(2));
        Files.write(b, PdfFixtures.blankPdf(3));

        Path out = service.merge(List.of(
                new MergeService.InputItem(a, null, null),
                new MergeService.InputItem(b, null, null)), MergeService.MODE_CONCATENATE);

        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(5);
    }

    @Test
    void alternateInterleavesPages(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a.pdf");
        Path b = tmp.resolve("b.pdf");
        Files.write(a, PdfFixtures.blankPdf(3));
        Files.write(b, PdfFixtures.blankPdf(2));

        Path out = service.merge(List.of(
                new MergeService.InputItem(a, null, null),
                new MergeService.InputItem(b, null, null)), MergeService.MODE_ALTERNATE);

        // a1,b1,a2,b2,a3 → 5 páginas.
        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(5);
    }

    @Test
    void rangesSelectSubsetPerFile(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a.pdf");
        Files.write(a, PdfFixtures.blankPdf(10));

        Path out = service.merge(List.of(
                new MergeService.InputItem(a, "1-3,5", null)), MergeService.MODE_CONCATENATE);

        // 1,2,3,5 → 4 páginas.
        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(4);
    }

    @Test
    void oddEvenReverseRequiresExactlyTwoFiles(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a.pdf");
        Files.write(a, PdfFixtures.blankPdf(2));

        assertThatThrownBy(() -> service.merge(
                List.of(new MergeService.InputItem(a, null, null)), MergeService.MODE_ODD_EVEN_REVERSE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("exatamente dois");
    }
}
