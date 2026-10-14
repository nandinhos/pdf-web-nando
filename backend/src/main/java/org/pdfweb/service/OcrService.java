package org.pdfweb.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.pdfweb.core.QualityPreset;
import org.pdfweb.jobs.Job;
import org.pdfweb.jobs.JobRegistry;
import org.pdfweb.ocr.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * OCR de PDF, ancorado em {@code OcrEngine} (OBSERVED). Caminho v1 (D7): renderiza
 * cada página (PDFBox, GRAY, dpi), roda o tesseract para gerar um PDF pesquisável
 * por página e um TXT, e mescla as páginas com {@link PDFMergerUtility}.
 *
 * <p>Execução assíncrona ({@link Async}); o progresso é publicado por página via
 * {@link JobRegistry} (SSE).
 */
@Service
public class OcrService {

    private final ArtifactStorage artifacts;
    private final JobRegistry jobs;
    private final Tesseract tesseract;

    public OcrService(ArtifactStorage artifacts, JobRegistry jobs,
                      @Value("${pdfweb.ocr.tesseract-binary:tesseract}") String tesseractBinary) {
        this.artifacts = artifacts;
        this.jobs = jobs;
        this.tesseract = new Tesseract(tesseractBinary);
    }

    public boolean isTesseractAvailable() {
        return tesseract.isAvailable();
    }

    /**
     * Executa o OCR de forma assíncrona, atualizando o job.
     *
     * @param job  job já criado/registrado
     * @param pdf  PDF de entrada
     * @param lang idioma(s) tesseract (ex.: "por", "eng", "por+eng")
     * @param dpi  resolução de render (padrão 300)
     */
    @Async
    public void runAsync(Job job, Path pdf, String lang, QualityPreset quality) {
        job.setStatus(Job.Status.RUNNING);
        jobs.publish(job);
        List<Path> tempImgs = new ArrayList<>();
        List<Path> perPagePdfs = new ArrayList<>();
        try {
            Path textOut = artifacts.newArtifact(".txt");
            StringBuilder fullText = new StringBuilder();

            try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
                int pageCount = doc.getNumberOfPages();
                job.setTotalPages(pageCount);
                jobs.publish(job);

                PDFRenderer renderer = new PDFRenderer(doc);
                int effectiveDpi = quality.dpi;

                for (int i = 0; i < pageCount; i++) {
                    // Fix (Fase 6): grava JPEG com qualidade do preset em vez de PNG
                    // sem perdas → a imagem embutida pelo tesseract fica muito menor.
                    Path jpg = artifacts.newArtifact("_p" + i + ".jpg");
                    tempImgs.add(jpg);
                    writeJpeg(renderer.renderImageWithDPI(i, effectiveDpi, ImageType.GRAY),
                            jpg, quality.jpegQuality);

                    Path base = artifacts.newArtifact("_ocr" + i);
                    tesseract.run(jpg, base, lang, effectiveDpi);
                    perPagePdfs.add(Path.of(base + ".pdf"));

                    Path pageTxt = Path.of(base + ".txt");
                    if (Files.isRegularFile(pageTxt)) {
                        fullText.append(Files.readString(pageTxt, StandardCharsets.UTF_8)).append('\n');
                    }
                    job.incrementDonePages();
                    jobs.publish(job);
                }
            }

            // Mescla as páginas pesquisáveis.
            Path resultPdf = artifacts.newArtifact(".pdf");
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationFileName(resultPdf.toString());
            for (Path p : perPagePdfs) {
                merger.addSource(p.toFile());
            }
            merger.mergeDocuments(org.apache.pdfbox.io.IOUtils.createMemoryOnlyStreamCache());

            Files.writeString(textOut, fullText.toString(), StandardCharsets.UTF_8);

            job.setResultId(artifacts.idOf(resultPdf));
            job.setTextResultId(artifacts.idOf(textOut));
            job.setStatus(Job.Status.DONE);
            jobs.publish(job);
        } catch (Exception e) {
            job.setError(e.getMessage());
            job.setStatus(Job.Status.ERROR);
            jobs.publish(job);
        } finally {
            // Limpeza de temporários (steering: limpar mesmo em falha).
            for (Path p : tempImgs) {
                deleteQuietly(p);
            }
            for (Path p : perPagePdfs) {
                deleteQuietly(p);
                deleteQuietly(Path.of(p.toString().replace(".pdf", ".txt")));
            }
        }
    }

    /** Grava um {@link java.awt.image.BufferedImage} como JPEG com qualidade dada (0..1). */
    private void writeJpeg(java.awt.image.BufferedImage image, Path dest, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(Math.max(0.05f, Math.min(1f, quality)));
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(dest.toFile())) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
