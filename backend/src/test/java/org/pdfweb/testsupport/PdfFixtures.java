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

    /**
     * Cria um PDF de 1 página A4 com dois textos em posições conhecidas, no
     * espaço nativo do PDF (origem inferior esquerda). Usado para verificar
     * redação real: um texto é alvo de redação, o outro serve de controle.
     *
     * @param secret   texto "sensível" (alvo da redação)
     * @param secretX  X (pt) do texto sensível
     * @param secretY  Y (pt, bottom-left) do texto sensível
     * @param publicText texto de controle (não deve ser afetado)
     * @param publicX  X (pt) do texto de controle
     * @param publicY  Y (pt, bottom-left) do texto de controle
     */
    public static byte[] pdfWithTextAt(String secret, float secretX, float secretY,
                                       String publicText, float publicX, float publicY) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.font.PDFont font =
                    new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 18);
                cs.newLineAtOffset(secretX, secretY);
                cs.showText(secret);
                cs.endText();
                cs.beginText();
                cs.setFont(font, 18);
                cs.newLineAtOffset(publicX, publicY);
                cs.showText(publicText);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Cria um PDF de 1 página A4 com várias linhas de texto empilhadas, cada uma
     * numa baseline distinta (espaçamento {@code leading} pt). Usado para validar
     * a detecção de blocos por linha física.
     */
    public static byte[] pdfWithLines(String[] lines, float x, float topY, float leading) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.font.PDFont font =
                    new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                float y = topY;
                for (String line : lines) {
                    cs.beginText();
                    cs.setFont(font, 12);
                    cs.newLineAtOffset(x, y);
                    cs.showText(line);
                    cs.endText();
                    y -= leading;
                }
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
