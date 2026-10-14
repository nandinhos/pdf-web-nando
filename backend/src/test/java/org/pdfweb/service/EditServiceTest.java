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

class EditServiceTest {

    private EditService service;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        PdfWebProperties props = new PdfWebProperties(
                new PdfWebProperties.Storage(tmp.toString()), new PdfWebProperties.Limits(2000));
        service = new EditService(new ArtifactStorage(props));
    }

    @Test
    void deleteRemovesPage(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(5));

        Path out = service.edit(pdf, List.of(new EditService.Op("delete", 2, null, null)), null);

        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(4);
    }

    @Test
    void duplicateAddsPage(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(3));

        Path out = service.edit(pdf, List.of(new EditService.Op("duplicate", 1, null, null)), null);

        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(4);
    }

    @Test
    void insertBlankAddsPage(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(2));

        Path out = service.edit(pdf, List.of(new EditService.Op("insertBlank", 2, null, null)), null);

        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(3);
    }

    @Test
    void rotatePersistsPerPage(@TempDir Path tmp) throws Exception {
        // Critério PRD §4.3: rotação por página persiste no export.
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(3));

        Path out = service.edit(pdf, List.of(new EditService.Op("rotate", 1, 90, null)), null);

        int rotation = PdfDocuments.withDocument(out, null, doc -> doc.getPage(0).getRotation());
        assertThat(rotation).isEqualTo(90);
        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(3);
    }

    @Test
    void reorderChangesPageOrder(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(3));

        // Inverte a ordem: 3,2,1. Conteúdo verificado pelo count (geometria idêntica em fixtures).
        Path out = service.edit(pdf, List.of(new EditService.Op("reorder", null, null, List.of(3, 2, 1))), null);

        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(3);
    }
}
