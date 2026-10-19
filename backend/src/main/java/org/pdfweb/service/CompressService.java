package org.pdfweb.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.pdfweb.core.QualityPreset;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Serviço de compressão e otimização de PDFs.
 * Aplica recompressão de imagens rasterizadas com perdas controladas (JPEG)
 * e compressão estrutural de streams e tabelas de referência cruzada (PDFBox 3).
 */
@Service
public class CompressService {

    public record CompressResult(Path file, long originalSize, long compressedSize, int savingsPercent) {
    }

    private final ArtifactStorage artifacts;

    public CompressService(ArtifactStorage artifacts) {
        this.artifacts = artifacts;
    }

    public CompressResult compress(Path pdf, String quality, String password) throws IOException {
        long originalSize = Files.size(pdf);
        if (originalSize == 0) {
            throw new ApiException("EMPTY_FILE", "O arquivo PDF original está vazio");
        }

        QualityPreset preset = QualityPreset.from(quality);
        Path dest = artifacts.newArtifact(".pdf");

        try (PDDocument doc = password != null && !password.isEmpty()
                ? Loader.loadPDF(pdf.toFile(), password)
                : Loader.loadPDF(pdf.toFile())) {

            optimizeImages(doc, preset);

            // Salva com compressão de streams e compactação de objetos nativa do PDFBox 3
            doc.save(dest.toFile(), CompressParameters.DEFAULT_COMPRESSION);
        }

        long compressedSize = Files.size(dest);
        int savings = 0;
        if (originalSize > 0 && compressedSize < originalSize) {
            savings = (int) Math.round(((double) (originalSize - compressedSize) / originalSize) * 100.0);
        }

        return new CompressResult(dest, originalSize, compressedSize, savings);
    }

    private void optimizeImages(PDDocument doc, QualityPreset preset) throws IOException {
        Set<COSName> processed = new HashSet<>();

        for (PDPage page : doc.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }

            PDRectangle mediaBox = page.getMediaBox();
            float pageWidthPt = mediaBox != null ? mediaBox.getWidth() : 595f;
            float pageHeightPt = mediaBox != null ? mediaBox.getHeight() : 842f;

            // Limite de pixels com base no DPI do preset para a página (72 pt = 1 pol)
            int maxDimPx = Math.max(800, (int) Math.ceil((Math.max(pageWidthPt, pageHeightPt) / 72.0f) * preset.dpi));

            for (COSName name : resources.getXObjectNames()) {
                if (processed.contains(name)) {
                    continue;
                }

                PDXObject xobject;
                try {
                    xobject = resources.getXObject(name);
                } catch (Exception e) {
                    continue;
                }

                if (xobject instanceof PDImageXObject img) {
                    processed.add(name);
                    try {
                        BufferedImage bim = img.getImage();
                        if (bim == null) {
                            continue;
                        }

                        BufferedImage targetBim = bim;
                        int origW = bim.getWidth();
                        int origH = bim.getHeight();

                        // Downscale apenas se dimensões excederem o DPI alvo da página
                        if (origW > maxDimPx || origH > maxDimPx) {
                            float scale = Math.min((float) maxDimPx / origW, (float) maxDimPx / origH);
                            int newW = Math.max(1, Math.round(origW * scale));
                            int newH = Math.max(1, Math.round(origH * scale));

                            BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
                            Graphics2D g2d = scaled.createGraphics();
                            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                            g2d.drawImage(bim, 0, 0, newW, newH, null);
                            g2d.dispose();
                            targetBim = scaled;
                        }

                        PDImageXObject compressedImg = JPEGFactory.createFromImage(
                                doc,
                                targetBim,
                                Math.max(0.1f, Math.min(0.95f, preset.jpegQuality))
                        );

                        resources.put(name, compressedImg);
                    } catch (Exception ignored) {
                        // Se falhar otimização desta imagem específica, preserva a original sem quebrar o documento
                    }
                }
            }
        }
    }
}
