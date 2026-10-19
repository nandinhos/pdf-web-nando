package org.pdfweb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.pdfweb.config.PdfWebProperties;
import org.pdfweb.core.PdfDocuments;
import org.pdfweb.error.ApiException;
import org.pdfweb.testsupport.PdfFixtures;

class ContentEditServiceTest {

    private ContentEditService service;
    private ImageStorage imageStorage;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        PdfWebProperties props = new PdfWebProperties(
                new PdfWebProperties.Storage(tmp.toString()), new PdfWebProperties.Limits(2000));
        imageStorage = new ImageStorage(props);
        service = new ContentEditService(new ArtifactStorage(props), imageStorage);
    }

    @Test
    void textElementIsFlattenedIntoPdf(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(1));

        ContentEditService.Element text = new ContentEditService.Element(
                ContentEditService.Type.TEXT, 1, 50f, 50f, 0f, 0f,
                "Correcao aplicada", "helvetica-bold", 14f, "#000000", null, false, null, null);

        Path out = service.apply(pdf, List.of(text), null);

        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            String extracted = new PDFTextStripper().getText(doc).trim();
            assertThat(extracted).contains("Correcao aplicada");
        }
        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(1);
    }

    @Test
    void redactElementKeepsPageCountAndSaves(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(2));

        ContentEditService.Element redact = new ContentEditService.Element(
                ContentEditService.Type.REDACT, 2, 10f, 10f, 100f, 20f,
                null, null, null, "#FFFFFF", null, false, null, null);

        Path out = service.apply(pdf, List.of(redact), null);

        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(2);
    }

    /**
     * Verificação central da redação REAL (hard redaction): o texto sob a região
     * redigida deve DESAPARECER do documento — não apenas ficar coberto.
     *
     * <p>A4 = 595 x 842 pt. O texto "SEGREDO-123" é escrito em (72, 750) no espaço
     * PDF (bottom-left). A região de redação é informada em top-left: y_tl ≈
     * 842 - 750 - 18 ≈ 74. Cobrimos uma área generosa em torno dele.
     *
     * <p>4 verificações: (1) texto redigido ausente na extração; (2) estrutura
     * preservada (pageCount); (3) adversarial — a string não existe nem nos bytes
     * crus do arquivo (pega resíduo em metadados/objetos órfãos).
     */
    @Test
    void hardRedactionRemovesUnderlyingText(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.pdfWithTextAt(
                "SEGREDO-123", 72f, 750f,
                "PUBLICO-ok", 72f, 400f));

        // Sanidade: antes da redação, ambos os textos são extraíveis.
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String before = new PDFTextStripper().getText(doc);
            assertThat(before).contains("SEGREDO-123").contains("PUBLICO-ok");
        }

        ContentEditService.Element redact = new ContentEditService.Element(
                ContentEditService.Type.REDACT, 1, 60f, 60f, 260f, 40f,
                null, null, null, "#000000", null, true, null, null);

        Path out = service.apply(pdf, List.of(redact), null);

        // (1) texto redigido sumiu da extração
        String extracted;
        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            extracted = new PDFTextStripper().getText(doc);
        }
        assertThat(extracted).doesNotContain("SEGREDO-123");

        // (2) estrutura preservada
        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(1);

        // (3) adversarial: a string não sobrou em lugar nenhum dos bytes do PDF
        String raw = new String(Files.readAllBytes(out), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(raw).doesNotContain("SEGREDO-123");
    }

    @Test
    void imageElementIsDrawn(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(1));
        String imageId = imageStorage.store(
                new java.io.ByteArrayInputStream(PdfFixtures.pngImage(40, 30)), "stamp.png");

        ContentEditService.Element image = new ContentEditService.Element(
                ContentEditService.Type.IMAGE, 1, 20f, 20f, 80f, 60f,
                null, null, null, null, imageId, false, null, null);

        Path out = service.apply(pdf, List.of(image), null);

        assertThat(PdfDocuments.pageCount(out, null)).isEqualTo(1);
        assertThat(Files.size(out)).isGreaterThan(Files.size(pdf));
    }

    @Test
    void reusesOriginalFontWhenItCoversNewText(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.pdfWithTextAt("Original", 72f, 700f, "Outro", 72f, 600f));

        // Descobre o nome do recurso de fonte da página 1 (determinístico: lê do PDF).
        String fontRes;
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            var res = doc.getPage(0).getResources();
            fontRes = res.getFontNames().iterator().next().getName();
        }
        assertThat(fontRes).isNotBlank();

        // Reescreve com texto ASCII que a fonte (Helvetica-Bold, WinAnsi) cobre.
        ContentEditService.Element text = new ContentEditService.Element(
                ContentEditService.Type.TEXT, 1, 72f, 120f, 0f, 0f,
                "Texto novo ASCII", "helvetica", 14f, "#000000", null, false, fontRes, 1);

        ContentEditService.Result r = service.applyWithResult(pdf, java.util.List.of(text), null);

        // Fonte original cobriu → sem substituição.
        assertThat(r.fontSubstituted()).isFalse();
        try (PDDocument doc = Loader.loadPDF(r.file().toFile())) {
            assertThat(new PDFTextStripper().getText(doc)).contains("Texto novo ASCII");
        }
    }

    @Test
    void fallsBackAndFlagsWhenOriginalFontCannotEncode(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.pdfWithTextAt("Original", 72f, 700f, "Outro", 72f, 600f));

        String fontRes;
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            fontRes = doc.getPage(0).getResources().getFontNames().iterator().next().getName();
        }

        // Caractere CJK que Helvetica-Bold (WinAnsi) NÃO consegue codificar → fallback.
        ContentEditService.Element text = new ContentEditService.Element(
                ContentEditService.Type.TEXT, 1, 72f, 120f, 0f, 0f,
                "Texto 中 novo", "helvetica", 14f, "#000000", null, false, fontRes, 1);

        ContentEditService.Result r = service.applyWithResult(pdf, java.util.List.of(text), null);

        // Não coube na fonte original → substituída e sinalizada.
        assertThat(r.fontSubstituted()).isTrue();
        assertThat(PdfDocuments.pageCount(r.file(), null)).isEqualTo(1);
    }

    @Test
    void emptyElementsIsRejected(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(1));

        assertThatThrownBy(() -> service.apply(pdf, List.of(), null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void pageOutOfRangeIsRejected(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("in.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(1));

        ContentEditService.Element text = new ContentEditService.Element(
                ContentEditService.Type.TEXT, 5, 10f, 10f, 0f, 0f,
                "x", "helvetica", 12f, "#000000", null, false, null, null);

        assertThatThrownBy(() -> service.apply(pdf, List.of(text), null))
                .isInstanceOf(ApiException.class);
    }
}
