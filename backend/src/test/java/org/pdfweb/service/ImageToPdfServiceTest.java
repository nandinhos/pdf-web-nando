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

class ImageToPdfServiceTest {

    private ImageToPdfService service;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        PdfWebProperties props = new PdfWebProperties(
                new PdfWebProperties.Storage(tmp.toString()), new PdfWebProperties.Limits(2000));
        service = new ImageToPdfService(new ArtifactStorage(props));
    }

    @Test
    void convertsImagesToOnePagePerImage(@TempDir Path tmp) throws Exception {
        // Critério PRD §4.5: conjunto de imagens → PDF com 1 imagem por página.
        Path img1 = tmp.resolve("a.png");
        Path img2 = tmp.resolve("b.png");
        Path img3 = tmp.resolve("c.png");
        Files.write(img1, PdfFixtures.pngImage(200, 100));
        Files.write(img2, PdfFixtures.pngImage(100, 200));
        Files.write(img3, PdfFixtures.pngImage(150, 150));

        Path out = service.convert(List.of(img1, img2, img3), ImageToPdfService.Options.defaults());

        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(3);
    }

    @Test
    void realSizeModeUsesDpiGeometry(@TempDir Path tmp) throws Exception {
        Path img = tmp.resolve("a.png");
        Files.write(img, PdfFixtures.pngImage(300, 150));

        ImageToPdfService.Options opts = new ImageToPdfService.Options(
                150, org.apache.pdfbox.pdmodel.common.PDRectangle.A4,
                ImageToPdfService.ORIENTATION_PORTRAIT, 0f, false, 1f);
        Path out = service.convert(List.of(img), opts);

        // 300px @150dpi = 2in = 144pt de largura.
        float width = PdfDocuments.withDocument(out, null, d -> d.getPage(0).getMediaBox().getWidth());
        assertThat(width).isEqualTo(144f);
        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(1);
    }

    @Test
    void lowQualityProducesSmallerFileThanLossless(@TempDir Path tmp) throws Exception {
        // Detector (Fase 6): em conteúdo fotográfico/scan (alta frequência), o JPEG
        // com perdas gera arquivo bem menor que o sem perdas — a causa raiz do OCR 8MB+.
        Path img = tmp.resolve("photo.png");
        Files.write(img, PdfFixtures.noisyPngImage(800, 600));

        var hi = new ImageToPdfService.Options(
                150, org.apache.pdfbox.pdmodel.common.PDRectangle.A4,
                ImageToPdfService.ORIENTATION_PORTRAIT, 0f, true, 1f);
        var lo = new ImageToPdfService.Options(
                150, org.apache.pdfbox.pdmodel.common.PDRectangle.A4,
                ImageToPdfService.ORIENTATION_PORTRAIT, 0f, true, 0.5f);

        long sizeHi = java.nio.file.Files.size(service.convert(List.of(img), hi));
        long sizeLo = java.nio.file.Files.size(service.convert(List.of(img), lo));

        assertThat(sizeLo).isLessThan(sizeHi);
    }
}
