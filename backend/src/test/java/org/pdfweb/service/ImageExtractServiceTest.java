package org.pdfweb.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdfweb.config.PdfWebProperties;
import org.pdfweb.testsupport.PdfFixtures;

class ImageExtractServiceTest {

    private ImageExtractService service;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        PdfWebProperties props = new PdfWebProperties(
                new PdfWebProperties.Storage(tmp.toString()), new PdfWebProperties.Limits(2000));
        service = new ImageExtractService(new ArtifactStorage(props));
    }

    @Test
    void extractsEmbeddedImageFromPdf(@TempDir Path tmp) throws Exception {
        // Critério PRD §4.5: extração recupera as imagens de um PDF.
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.pdfWithImage(120, 80));

        List<ImageExtractService.OutImage> out = service.extract(pdf, ImageExtractService.Options.defaults());

        assertThat(out).isNotEmpty();
        // O artefato extraído deve ser uma imagem legível.
        assertThat(ImageIO.read(out.get(0).file().toFile())).isNotNull();
    }

    @Test
    void pdfWithoutImagesYieldsEmptyList(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("blank.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(2));

        List<ImageExtractService.OutImage> out = service.extract(pdf, ImageExtractService.Options.defaults());

        assertThat(out).isEmpty();
    }
}
