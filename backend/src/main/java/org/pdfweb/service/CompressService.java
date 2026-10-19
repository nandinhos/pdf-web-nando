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
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.pdfweb.core.QualityPreset;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Serviço de compressão e otimização inteligente de PDFs.
 * Trata:
 * 1. Recompressão e downscale de imagens rasterizadas (JPEGFactory).
 * 2. Otimização de páginas vetoriais densas (textos convertidos em curvas Bézier
 *    sem texto digital, que inflam o arquivo com centenas de KB por página).
 * 3. Compactação estrutural de streams e tabelas de objetos (PDFBox 3).
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

        // 1ª Passada: otimização padrão de imagens e salvamento comprimido
        boolean hadHeavyVectors = false;
        try (PDDocument doc = password != null && !password.isEmpty()
                ? Loader.loadPDF(pdf.toFile(), password)
                : Loader.loadPDF(pdf.toFile())) {

            hadHeavyVectors = hasHeavyVectorPages(doc);
            optimizeImages(doc, preset);
            doc.save(dest.toFile(), CompressParameters.DEFAULT_COMPRESSION);
        }

        long compressedSize = Files.size(dest);
        double ratio = (double) compressedSize / originalSize;

        // 2ª Passada: Se a economia foi insignificante (>= 95% do tamanho original)
        // e o preset for "low" (Forte) ou "medium" (Recomendada), ou se existirem páginas
        // de vetor pesado (ex: textos convertidos em curvas em softwares de design):
        // aplicamos renderização otimizada por página (downsampling de curvas densas).
        if ((ratio >= 0.95 && (preset == QualityPreset.LOW || preset == QualityPreset.MEDIUM || hadHeavyVectors))) {
            Path rasterDest = artifacts.newArtifact(".pdf");
            try (PDDocument src = password != null && !password.isEmpty()
                    ? Loader.loadPDF(pdf.toFile(), password)
                    : Loader.loadPDF(pdf.toFile());
                 PDDocument out = new PDDocument()) {

                PDFRenderer renderer = new PDFRenderer(src);
                int targetDpi = preset == QualityPreset.LOW ? 120 : 150;
                float jpegQ = preset == QualityPreset.LOW ? 0.65f : 0.75f;

                for (int i = 0; i < src.getNumberOfPages(); i++) {
                    PDPage srcPage = src.getPage(i);
                    PDRectangle box = srcPage.getMediaBox();
                    if (box == null) {
                        box = PDRectangle.A4;
                    }

                    BufferedImage bim = renderer.renderImageWithDPI(i, targetDpi, ImageType.RGB);
                    PDPage destPage = new PDPage(box);
                    destPage.setRotation(srcPage.getRotation());
                    out.addPage(destPage);

                    PDImageXObject img = JPEGFactory.createFromImage(out, bim, jpegQ);
                    try (PDPageContentStream cs = new PDPageContentStream(out, destPage)) {
                        cs.drawImage(img, 0, 0, box.getWidth(), box.getHeight());
                    }
                }

                out.save(rasterDest.toFile(), CompressParameters.DEFAULT_COMPRESSION);
                long rasterSize = Files.size(rasterDest);
                if (rasterSize < compressedSize) {
                    Files.deleteIfExists(dest);
                    dest = rasterDest;
                    compressedSize = rasterSize;
                } else {
                    Files.deleteIfExists(rasterDest);
                }
            }
        }

        int savings = 0;
        if (originalSize > 0 && compressedSize < originalSize) {
            savings = (int) Math.round(((double) (originalSize - compressedSize) / originalSize) * 100.0);
        }

        return new CompressResult(dest, originalSize, compressedSize, savings);
    }

    private boolean hasHeavyVectorPages(PDDocument doc) {
        for (PDPage page : doc.getPages()) {
            COSBase c = page.getCOSObject().getDictionaryObject(COSName.CONTENTS);
            if (c instanceof COSStream cs) {
                // Streams maiores que 80KB de conteúdo vetorial são candidatos a otimização
                if (cs.getLength() > 80_000) {
                    return true;
                }
            }
        }
        return false;
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
                    }
                }
            }
        }
    }
}
