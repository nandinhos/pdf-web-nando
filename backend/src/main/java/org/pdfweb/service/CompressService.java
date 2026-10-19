package org.pdfweb.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

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
        // e o preset for "low" (Forte) ou se existirem páginas de vetor pesado:
        // aplicamos renderização otimizada por página (downsampling de curvas densas).
        if ((ratio >= 0.95 && (preset == QualityPreset.LOW || hadHeavyVectors))) {
            Path rasterDest = artifacts.newArtifact(".pdf");
            try (PDDocument src = password != null && !password.isEmpty()
                    ? Loader.loadPDF(pdf.toFile(), password)
                    : Loader.loadPDF(pdf.toFile());
                 PDDocument out = new PDDocument()) {

                PDFRenderer renderer = new PDFRenderer(src);
                int targetDpi = preset == QualityPreset.LOW ? 96 : 120;
                float jpegQ = preset == QualityPreset.LOW ? 0.50f : 0.65f;

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

                    byte[] jpegBytes = encodeJpeg(bim, jpegQ);
                    PDImageXObject img = JPEGFactory.createFromByteArray(out, jpegBytes);
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

        // Salvaguarda final: se o arquivo comprimido ficou maior ou igual ao original,
        // preservamos o original exato para nunca entregar um arquivo pior/inflado ao usuário.
        if (compressedSize >= originalSize) {
            Files.copy(pdf, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            compressedSize = originalSize;
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
                if (cs.getLength() > 80_000) {
                    return true;
                }
            }
        }
        return false;
    }

    private void optimizeImages(PDDocument doc, QualityPreset preset) throws IOException {
        Set<COSBase> processedObjects = new HashSet<>();

        // Calibração de DPI e qualidade por preset
        int targetDpi = switch (preset) {
            case LOW -> 96;
            case MEDIUM -> 120;
            case HIGH -> 150;
        };
        float jpegQuality = switch (preset) {
            case LOW -> 0.50f;
            case MEDIUM -> 0.65f;
            case HIGH -> 0.75f;
        };

        for (PDPage page : doc.getPages()) {
            PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }

            PDRectangle mediaBox = page.getMediaBox();
            float pageWidthPt = mediaBox != null ? mediaBox.getWidth() : 595f;
            float pageHeightPt = mediaBox != null ? mediaBox.getHeight() : 842f;

            int maxDimPx = Math.max(600, (int) Math.ceil((Math.max(pageWidthPt, pageHeightPt) / 72.0f) * targetDpi));

            for (COSName name : resources.getXObjectNames()) {
                PDXObject xobject;
                try {
                    xobject = resources.getXObject(name);
                } catch (Exception e) {
                    continue;
                }

                if (xobject instanceof PDImageXObject img) {
                    if (processedObjects.contains(img.getCOSObject())) {
                        continue;
                    }
                    processedObjects.add(img.getCOSObject());
                    try {
                        long origStreamLen = img.getCOSObject().getLength();

                        BufferedImage bim = img.getImage();
                        if (bim == null) {
                            continue;
                        }

                        // Preserva monocromático/grayscale se o original tiver apenas 1 componente de cor
                        boolean isGray = (img.getColorSpace() != null && img.getColorSpace().getNumberOfComponents() == 1)
                                || bim.getType() == BufferedImage.TYPE_BYTE_GRAY;

                        BufferedImage targetBim = bim;
                        int origW = bim.getWidth();
                        int origH = bim.getHeight();

                        if (origW > maxDimPx || origH > maxDimPx) {
                            float scale = Math.min((float) maxDimPx / origW, (float) maxDimPx / origH);
                            int newW = Math.max(1, Math.round(origW * scale));
                            int newH = Math.max(1, Math.round(origH * scale));

                            int imageType = isGray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB;
                            BufferedImage scaled = new BufferedImage(newW, newH, imageType);
                            Graphics2D g2d = scaled.createGraphics();
                            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                            g2d.drawImage(bim, 0, 0, newW, newH, null);
                            g2d.dispose();
                            targetBim = scaled;
                        } else if (isGray && bim.getType() != BufferedImage.TYPE_BYTE_GRAY) {
                            BufferedImage gray = new BufferedImage(origW, origH, BufferedImage.TYPE_BYTE_GRAY);
                            Graphics2D g2d = gray.createGraphics();
                            g2d.drawImage(bim, 0, 0, null);
                            g2d.dispose();
                            targetBim = gray;
                        }

                        byte[] jpegBytes = encodeJpeg(targetBim, jpegQuality);

                        // Regra de ouro da compressão: só substitui se os novos bytes forem comprovadamente MENORES
                        if (origStreamLen <= 0 || (jpegBytes.length > 0 && jpegBytes.length < origStreamLen)) {
                            PDImageXObject compressedImg = JPEGFactory.createFromByteArray(doc, jpegBytes);
                            resources.put(name, compressedImg);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG ImageWriter available");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.05f, Math.min(0.95f, quality)));
            }
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
