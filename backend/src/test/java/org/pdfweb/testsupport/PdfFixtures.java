package org.pdfweb.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Gera fixtures de PDF determinísticas em memória usando PDFBox.
 * Evita dependência de arquivos externos (testes determinísticos — steering 05).
 */
public final class PdfFixtures {

    private PdfFixtures() {
    }

    /**
     * Cria um PDF com {@code numPages} páginas A4 em branco.
     */
    public static byte[] blankPdf(int numPages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < numPages; i++) {
                doc.addPage(new PDPage(PDRectangle.A4));
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Gera uma imagem PNG simples (gradiente) em memória. */
    public static byte[] pngImage(int width, int height) throws IOException {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255) / width;
                int g = (y * 255) / height;
                img.setRGB(x, y, (r << 16) | (g << 8) | 128);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /**
     * Gera uma imagem PNG com ruído de alta frequência (determinística via seed) —
     * representa foto/scan real, onde JPEG com perdas vence o sem perdas em tamanho.
     */
    public static byte[] noisyPngImage(int width, int height) throws IOException {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.util.Random rnd = new java.util.Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Cria um PDF de 1 página com uma imagem embutida (XObject). */
    public static byte[] pdfWithImage(int imgWidth, int imgHeight) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            byte[] png = pngImage(imgWidth, imgHeight);
            org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image =
                    org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(doc, png, "img");
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.drawImage(image, 50, 50, imgWidth, imgHeight);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
