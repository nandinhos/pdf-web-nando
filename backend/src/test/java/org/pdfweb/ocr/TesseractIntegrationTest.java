package org.pdfweb.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integração condicional: só roda se o binário {@code tesseract} existir no
 * ambiente (determinístico — faz skip quando ausente, p.ex. no host sem OCR).
 */
class TesseractIntegrationTest {

    @Test
    void realOcrProducesSearchableText(@TempDir Path tmp) throws Exception {
        Tesseract tesseract = new Tesseract("tesseract");
        assumeTrue(tesseract.isAvailable(), "tesseract não disponível — pulando OCR real");

        // Imagem com texto conhecido.
        BufferedImage img = new BufferedImage(600, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 160);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 48));
        g.drawString("HELLO OCR", 40, 100);
        g.dispose();

        Path png = tmp.resolve("in.png");
        ImageIO.write(img, "png", png.toFile());
        Path base = tmp.resolve("out");

        tesseract.run(png, base, "eng", 300);

        Path pdf = Path.of(base + ".pdf");
        assertThat(Files.isRegularFile(pdf)).isTrue();

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(doc).toUpperCase();
            assertThat(text).contains("HELLO");
        }
    }
}
