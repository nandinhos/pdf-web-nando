package org.pdfweb.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

/**
 * Extração de imagens embutidas em um PDF, ancorada em {@code PdfImageExtract}
 * (OBSERVED). Percorre recursivamente os recursos de cada página, trata
 * {@link PDImageXObject} e desce em {@link PDFormXObject}; usa um conjunto
 * {@code seen} para não reprocessar o mesmo objeto COS.
 */
@Service
public class ImageExtractService {

    private static final List<String> JPEG_DECODE = List.of(
            COSName.DCT_DECODE.getName(), COSName.DCT_DECODE_ABBREVIATION.getName());
    private static final List<String> JPEG2000_DECODE = List.of(COSName.JPX_DECODE.getName());

    /** Opções de extração (defaults OBSERVED em PdfImageExtract). */
    public record Options(boolean passJpg, boolean passJp2, boolean jp2ToJpg) {
        public static Options defaults() {
            return new Options(true, false, true);
        }
    }

    /** Artefato extraído: caminho + nome lógico. */
    public record OutImage(Path file, String name) {
    }

    private final ArtifactStorage artifacts;

    public ImageExtractService(ArtifactStorage artifacts) {
        this.artifacts = artifacts;
    }

    public List<OutImage> extract(Path pdf, Options opts) throws IOException {
        Extraction ctx = new Extraction(opts);
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            for (PDPage page : doc.getPages()) {
                ctx.walk(page.getResources());
            }
        }
        return ctx.out;
    }

    /** Estado de uma extração (acumula saídas e objetos já vistos). */
    private final class Extraction {
        final Options opts;
        final List<OutImage> out = new ArrayList<>();
        final Set<COSObjectable> seen = new HashSet<>();

        Extraction(Options opts) {
            this.opts = opts;
        }

        void walk(PDResources resources) throws IOException {
            if (resources == null || !seen.add(resources)) {
                return;
            }
            for (COSName name : resources.getXObjectNames()) {
                PDXObject xobj = resources.getXObject(name);
                try {
                    if (xobj instanceof PDImageXObject img) {
                        extractImage(img);
                    } else if (xobj instanceof PDFormXObject form) {
                        if (seen.add(form)) {
                            walk(form.getResources());
                        }
                    }
                } catch (Exception ignored) {
                    // best-effort por XObject (espelha handleException do original)
                }
            }
        }

        void extractImage(PDImageXObject img) throws IOException {
            if (!seen.add(img)) {
                return;
            }
            String suffix = img.getSuffix();
            if (suffix == null || "jb2".equals(suffix)) {
                suffix = "png";
            } else if ("jpx".equals(suffix)) {
                suffix = "jp2";
            }
            if (img.getMask() != null || img.getSoftMask() != null) {
                suffix = "png";
            }
            String formatName = "jp2".equals(suffix) ? "jpeg2000" : suffix;

            String colorSpace = img.getColorSpace().getName();
            boolean isRgbOrGray = colorSpace.equals(PDDeviceGray.INSTANCE.getName())
                    || colorSpace.equals(PDDeviceRGB.INSTANCE.getName());

            if ("jpg".equals(suffix) && opts.passJpg() && isRgbOrGray) {
                writeRaw(img, suffix, JPEG_DECODE);
                return;
            }
            if ("jp2".equals(suffix) && opts.passJp2() && isRgbOrGray) {
                writeRaw(img, suffix, JPEG2000_DECODE);
                return;
            }
            if ("jp2".equals(suffix) && opts.jp2ToJpg()) {
                suffix = "jpg";
                formatName = "jpg";
            }
            writeRendered(img, suffix, formatName);
        }

        /** Extrai os bytes originais (sem recodificar) parando nos filtros dados. */
        void writeRaw(PDImageXObject img, String suffix, List<String> stopFilters) throws IOException {
            Path dest = artifacts.newArtifact("." + suffix);
            try (OutputStream out2 = Files.newOutputStream(dest);
                 InputStream data = img.createInputStream(stopFilters)) {
                IOUtils.copy(data, out2);
            }
            add(dest, suffix);
        }

        /** Rasteriza via ImageIO para o formato alvo. */
        void writeRendered(PDImageXObject img, String suffix, String formatName) throws IOException {
            if (img.getImage() == null) {
                return;
            }
            Path dest = artifacts.newArtifact("." + suffix);
            try (OutputStream out2 = Files.newOutputStream(dest)) {
                ImageIO.write(img.getImage(), formatName, out2);
            }
            add(dest, suffix);
        }

        void add(Path dest, String suffix) {
            out.add(new OutImage(dest, "image-" + (out.size() + 1) + "." + suffix));
        }
    }
}
