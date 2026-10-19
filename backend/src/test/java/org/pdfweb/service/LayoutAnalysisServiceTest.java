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

class LayoutAnalysisServiceTest {

    private LayoutAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new LayoutAnalysisService();
    }

    @Test
    void detectsTextBlocksFromDigitalPdf(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.pdfWithTextAt("SEGREDO-123", 72f, 750f, "PUBLICO-ok", 72f, 400f));

        LayoutAnalysisService.PageLayout layout = service.analyzePage(pdf, null, 1);

        // Dois textos → dois blocos de texto (um por linha).
        long textBlocks = layout.blocks().stream()
                .filter(b -> b.kind() == LayoutAnalysisService.Kind.TEXT).count();
        assertThat(textBlocks).isEqualTo(2);

        // Os textos detectados batem com o conteúdo conhecido.
        assertThat(layout.blocks())
                .filteredOn(b -> b.kind() == LayoutAnalysisService.Kind.TEXT)
                .anySatisfy(b -> assertThat(b.text()).contains("SEGREDO-123"))
                .anySatisfy(b -> assertThat(b.text()).contains("PUBLICO-ok"))
                .allSatisfy(b -> {
                    assertThat(b.width()).isGreaterThan(0f);
                    assertThat(b.height()).isGreaterThan(0f);
                    assertThat(b.x()).isGreaterThanOrEqualTo(0f);
                    assertThat(b.y()).isGreaterThanOrEqualTo(0f);
                });

        assertThat(layout.scanned()).isFalse();
    }

    @Test
    void splitsLinesIntoOneBlockEach(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("lines.pdf");
        // 3 linhas empilhadas, baselines distintas (leading 20pt).
        Files.write(pdf, PdfFixtures.pdfWithLines(
                new String[] {"Primeira linha", "Segunda linha", "Terceira linha"}, 72f, 700f, 20f));

        LayoutAnalysisService.PageLayout layout = service.analyzePage(pdf, null, 1);

        var textBlocks = layout.blocks().stream()
                .filter(b -> b.kind() == LayoutAnalysisService.Kind.TEXT)
                .toList();

        // Uma linha física = um bloco (respeita as quebras).
        assertThat(textBlocks).hasSize(3);
        assertThat(textBlocks).anySatisfy(b -> assertThat(b.text()).isEqualTo("Primeira linha"));
        assertThat(textBlocks).anySatisfy(b -> assertThat(b.text()).isEqualTo("Segunda linha"));
        assertThat(textBlocks).anySatisfy(b -> assertThat(b.text()).isEqualTo("Terceira linha"));

        // Nenhum bloco contém mais de uma linha (sem '\n' e sem concatenar linhas).
        assertThat(textBlocks).allSatisfy(b -> assertThat(b.text()).doesNotContain("linhaSegunda"));

        // As baselines crescem em Y (top-left) de cima para baixo.
        var ys = textBlocks.stream().map(LayoutAnalysisService.Block::y).sorted().toList();
        assertThat(ys.get(0)).isLessThan(ys.get(1));
        assertThat(ys.get(1)).isLessThan(ys.get(2));
    }

    @Test
    void detectsImageBlock(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.pdfWithImage(120, 90));

        LayoutAnalysisService.PageLayout layout = service.analyzePage(pdf, null, 1);

        assertThat(layout.blocks())
                .filteredOn(b -> b.kind() == LayoutAnalysisService.Kind.IMAGE)
                .hasSize(1)
                .allSatisfy(b -> {
                    assertThat(b.width()).isGreaterThan(0f);
                    assertThat(b.height()).isGreaterThan(0f);
                });
    }

    @Test
    void pageOutOfRangeIsRejected(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(1));

        assertThatThrownBy(() -> service.analyzePage(pdf, null, 9))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void blankPageHasNoBlocksAndIsNotScanned(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(1));

        LayoutAnalysisService.PageLayout layout = service.analyzePage(pdf, null, 1);

        assertThat(layout.blocks()).isEmpty();
        assertThat(layout.scanned()).isFalse();
    }
}
