package org.pdfweb.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Conversão de imagens em PDF, ancorada em {@code ImageToPdf} (OBSERVED).
 * Reimplementa o algoritmo de layout de {@code calcPageValues}: modo "página
 * fixa escalada" (usePageSize) ou "tamanho real por DPI", com matriz de
 * orientação EXIF e rotação.
 *
 * <p>v1 (D6): JPG/PNG via ImageIO nativo. TIFF/WebP dependem de plugins
 * TwelveMonkeys; HEIC/SVG fora da v1.
 */
@Service
public class ImageToPdfService {

    public static final String ORIENTATION_AUTO = "auto";
    public static final String ORIENTATION_LANDSCAPE = "landscape";
    public static final String ORIENTATION_PORTRAIT = "portrait";

    /** Opções de layout (defaults OBSERVED em ImageToPdf) + qualidade de compressão. */
    public record Options(int dpi, PDRectangle pageSize, String orientation, float margin, boolean usePageSize,
                          float jpegQuality) {
        public static Options defaults() {
            return new Options(150, PDRectangle.A4, ORIENTATION_PORTRAIT, 0f, true, 0.72f);
        }
    }

    private final ArtifactStorage artifacts;

    public ImageToPdfService(ArtifactStorage artifacts) {
        this.artifacts = artifacts;
    }

    public static float mmToPt(float mm) {
        return mm * 72.0f / 25.4f;
    }

    static int normRotate(int rotate) {
        return (rotate % 360 + 360) % 360;
    }

    public Path convert(List<Path> images, Options opts) throws IOException {
        if (images == null || images.isEmpty()) {
            throw new ApiException("EMPTY_IMAGE_SET", "Nenhuma imagem informada");
        }
        Path dest = artifacts.newArtifact(".pdf");
        try (PDDocument doc = new PDDocument()) {
            for (Path image : images) {
                addImage(doc, image, opts);
            }
            doc.save(dest.toFile());
        }
        return dest;
    }

    private void addImage(PDDocument doc, Path image, Options opts) throws IOException {
        // Recompressão com perdas (JPEG) quando a qualidade permite — reduz
        // drasticamente o tamanho vs. imagem sem perdas (Fase 6, D12).
        PDImageXObject img;
        if (opts.jpegQuality() < 1f) {
            java.awt.image.BufferedImage bim = javax.imageio.ImageIO.read(image.toFile());
            if (bim == null) {
                img = PDImageXObject.createFromFile(image.toString(), doc);
            } else {
                img = org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(
                        doc, bim, Math.max(0.05f, Math.min(1f, opts.jpegQuality())));
            }
        } else {
            img = PDImageXObject.createFromFile(image.toString(), doc);
        }
        int orientation = exifOrientation(image);
        PageGeometry g = calcPageValues(img, orientation, opts);

        PDPage page = new PDPage(new PDRectangle(g.pageWidth, g.pageHeight));
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.transform(g.matrix);
            cs.drawImage(img, 0, 0, g.scaledImgWidth, g.scaledImgHeight);
        }
    }

    /** Geometria calculada para uma imagem (subconjunto de PageValues usado na v1). */
    private static final class PageGeometry {
        float pageWidth;
        float pageHeight;
        float scaledImgWidth;
        float scaledImgHeight;
        Matrix matrix = new Matrix();
    }

    /**
     * Porta direta de {@code ImageToPdf.calcPageValues} (OBSERVED), restrita ao
     * que a v1 usa (orientação EXIF, página fixa escalada ou DPI real, margem).
     */
    private PageGeometry calcPageValues(PDImageXObject img, int orientation, Options opts) {
        PageGeometry g = new PageGeometry();
        int imgWidth = img.getWidth();
        int imgHeight = img.getHeight();
        // EXIF 5–8 implicam troca de eixos.
        if (orientation >= 5 && orientation <= 8) {
            int tmp = imgWidth;
            imgWidth = imgHeight;
            imgHeight = tmp;
        }

        if (opts.usePageSize()) {
            boolean isLandscapeMode = ORIENTATION_LANDSCAPE.equals(opts.orientation());
            boolean isAutoMode = ORIENTATION_AUTO.equals(opts.orientation());
            boolean imgIsLandscape = imgWidth > imgHeight;
            float pageWidth = opts.pageSize().getWidth();
            float pageHeight = opts.pageSize().getHeight();
            boolean pageIsLandscape = pageWidth > pageHeight;

            if ((isLandscapeMode && !pageIsLandscape) || (!isLandscapeMode && !isAutoMode && pageIsLandscape)
                    || (isAutoMode && imgIsLandscape && !pageIsLandscape)
                    || (isAutoMode && !imgIsLandscape && pageIsLandscape)) {
                float tmp = pageWidth;
                pageWidth = pageHeight;
                pageHeight = tmp;
            }
            g.pageWidth = pageWidth;
            g.pageHeight = pageHeight;
            g.scaledImgWidth = pageWidth;
            g.scaledImgHeight = pageWidth * imgHeight / (float) imgWidth;

            if (opts.margin() <= 0f) {
                g.pageHeight = g.scaledImgHeight;
            } else {
                if (g.scaledImgHeight > g.pageHeight) {
                    g.scaledImgWidth *= g.pageHeight / g.scaledImgHeight;
                    g.scaledImgHeight = g.pageHeight;
                }
                float marginX = (g.pageWidth - g.scaledImgWidth) / 2f;
                float marginY = (g.pageHeight - g.scaledImgHeight) / 2f;
                g.matrix.translate(marginX, marginY);
            }
        } else {
            float dpiScale = 72.0f / opts.dpi();
            g.scaledImgWidth = imgWidth * dpiScale;
            g.scaledImgHeight = imgHeight * dpiScale;
            g.pageWidth = g.scaledImgWidth;
            g.pageHeight = g.scaledImgHeight;
            if (opts.margin() != 0f) {
                g.pageWidth += opts.margin() * 2;
                g.pageHeight += opts.margin() * 2;
                g.matrix.translate(opts.margin(), opts.margin());
            }
        }
        g.matrix.concatenate(matrixForOrientation(orientation, g.scaledImgWidth, g.scaledImgHeight));
        return g;
    }

    /** Matriz de orientação EXIF (casos 2–8), OBSERVED em ImageToPdf.matrixForOrientation. */
    static Matrix matrixForOrientation(int orientation, float w, float h) {
        Matrix m = new Matrix();
        switch (orientation) {
            case 2 -> {
                m.translate(w, 0f);
                m.scale(-1f, 1f);
            }
            case 3 -> {
                m.translate(w, h);
                m.scale(-1f, -1f);
            }
            case 4 -> {
                m.translate(0f, h);
                m.scale(1f, -1f);
            }
            case 5 -> {
                m.rotate(Math.toRadians(90));
                m.translate(0f, -w);
                m.translate(h, 0f);
                m.scale(-h / w, w / h);
            }
            case 6 -> {
                m.rotate(Math.toRadians(-90));
                m.translate(-h, 0f);
                m.scale(h / w, w / h);
            }
            case 7 -> {
                m.rotate(Math.toRadians(-90));
                m.scale(-h / w, w / h);
            }
            case 8 -> {
                m.rotate(Math.toRadians(90));
                m.translate(0f, -w);
                m.scale(h / w, w / h);
            }
            default -> {
                // orientation 0/1: identidade
            }
        }
        return m;
    }

    private int exifOrientation(Path image) {
        try {
            ImageMetadata metadata = Imaging.getMetadata(image.toFile());
            if (metadata instanceof JpegImageMetadata jpeg) {
                TiffField field = jpeg.findExifValueWithExactMatch(TiffTagConstants.TIFF_TAG_ORIENTATION);
                if (field != null) {
                    return field.getIntValue();
                }
            }
        } catch (Exception ignored) {
            // Sem metadados / formato sem EXIF → orientação padrão.
        }
        return 0;
    }
}
