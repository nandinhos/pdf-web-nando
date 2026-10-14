package org.pdfweb.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.pdfweb.core.PageMerger;
import org.pdfweb.core.PageRanges;
import org.pdfweb.core.PdfDocuments;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Divisão/extração de páginas, ancorada em {@code PdfSplitter} (OBSERVED).
 * Reimplementa os 5 modos reais sobre {@link PageMerger}.
 */
@Service
public class SplitService {

    public static final String MODE_PAGES_PER_PDF = "pagesPerPdf";
    public static final String MODE_CUSTOM = "custom";
    public static final String MODE_EVEN_ODD = "evenOdd";
    public static final String MODE_HALVE_PAGES = "halvePages";
    public static final String MODE_CUT_IN_HALF = "cutInHalf";

    /** Artefato de saída: arquivo gerado + nome lógico (faixa de páginas). */
    public record OutFile(Path file, String name) {
    }

    private final ArtifactStorage artifacts;

    public SplitService(ArtifactStorage artifacts) {
        this.artifacts = artifacts;
    }

    /**
     * Executa a divisão conforme o modo. Delega aos sub-métodos espelhando a
     * seleção observada em {@code PdfSplitter.split()}.
     */
    public List<OutFile> split(Path pdf, String mode, int pagesPerPdf, String splitPoints, String password)
            throws IOException {
        if (MODE_CUSTOM.equals(mode) || splitPoints != null) {
            return splitUsingSplitPoints(pdf, splitPoints, password);
        }
        if (MODE_EVEN_ODD.equals(mode)) {
            return splitUsingEvenOdd(pdf, password);
        }
        if (MODE_CUT_IN_HALF.equals(mode) || MODE_HALVE_PAGES.equals(mode)) {
            return splitUsingHalvePages(pdf, password);
        }
        return splitUsingPagesPerPdf(pdf, Math.max(1, pagesPerPdf), password);
    }

    private int pageCount(Path pdf, String password) throws IOException {
        return PdfDocuments.pageCount(pdf, password);
    }

    private List<OutFile> splitUsingPagesPerPdf(Path pdf, int pagesPerPdf, String password) throws IOException {
        int numPages = pageCount(pdf, password);
        List<OutFile> out = new ArrayList<>();
        int pageIndex = 0;
        while (pageIndex < numPages) {
            int start = pageIndex;
            try (PageMerger merger = new PageMerger()) {
                for (int i = 0; i < pagesPerPdf && pageIndex < numPages; i++, pageIndex++) {
                    merger.add(new PageMerger.PageItem(pdf, pageIndex + 1, password));
                }
                String name = makeName(start + 1, pageIndex, numPages);
                out.add(writeMerger(merger, name));
            }
        }
        return out;
    }

    private List<OutFile> splitUsingSplitPoints(Path pdf, String splitPoints, String password) throws IOException {
        int numPages = pageCount(pdf, password);
        int[] points = PageRanges.parseIntList(splitPoints);
        Arrays.sort(points);
        List<OutFile> out = new ArrayList<>();
        if (points.length == 0) {
            try (PageMerger merger = new PageMerger()) {
                for (int p = 1; p <= numPages; p++) {
                    merger.add(new PageMerger.PageItem(pdf, p, password));
                }
                out.add(writeMerger(merger, makeName(1, numPages, numPages)));
            }
            return out;
        }
        int[] ext = Arrays.copyOf(points, points.length + 1);
        ext[points.length] = numPages;
        int lastEnd = 0;
        for (int point : ext) {
            int start = lastEnd + 1;
            int end = Math.min(point, numPages);
            lastEnd = end;
            if (start > end) {
                continue;
            }
            try (PageMerger merger = new PageMerger()) {
                for (int p = start; p <= end; p++) {
                    merger.add(new PageMerger.PageItem(pdf, p, password));
                }
                out.add(writeMerger(merger, makeName(start, end, numPages)));
            }
        }
        return out;
    }

    private List<OutFile> splitUsingEvenOdd(Path pdf, String password) throws IOException {
        int numPages = pageCount(pdf, password);
        List<OutFile> out = new ArrayList<>();
        try (PageMerger odd = new PageMerger(); PageMerger even = new PageMerger()) {
            for (int p = 1; p <= numPages; p++) {
                PageMerger target = (p % 2 == 1) ? odd : even;
                target.add(new PageMerger.PageItem(pdf, p, password));
            }
            out.add(writeMerger(odd, "odd"));
            if (even.size() > 0) {
                out.add(writeMerger(even, "even"));
            }
        }
        return out;
    }

    private List<OutFile> splitUsingHalvePages(Path pdf, String password) throws IOException {
        int numPages = pageCount(pdf, password);
        List<PageMerger.PageItem> items = new ArrayList<>();
        // Lê geometria de cada página para montar os crop boxes (OBSERVED PdfSplitter.splitUsingHalvePages).
        PdfDocuments.withDocument(pdf, password, doc -> {
            for (int i = 0; i < numPages; i++) {
                PDPage page = doc.getPage(i);
                PDRectangle box = page.getMediaBox();
                int rot = PageMerger.normRot(page.getRotation());
                PageMerger.PageItem a = new PageMerger.PageItem(pdf, i + 1, password);
                PageMerger.PageItem b = new PageMerger.PageItem(pdf, i + 1, password);
                applyHalfCrops(box, rot, a, b);
                items.add(a);
                items.add(b);
            }
            return null;
        });
        try (PageMerger merger = new PageMerger()) {
            for (PageMerger.PageItem item : items) {
                merger.add(item);
            }
            return List.of(writeMerger(merger, "halves"));
        }
    }

    private void applyHalfCrops(PDRectangle box, int rot, PageMerger.PageItem first, PageMerger.PageItem second) {
        boolean portrait = box.getHeight() > box.getWidth();
        if (portrait) {
            PDRectangle top = new PDRectangle(box.getLowerLeftX(), box.getLowerLeftY() + box.getHeight() / 2f,
                    box.getWidth(), box.getHeight() / 2f);
            PDRectangle bottom = new PDRectangle(box.getLowerLeftX(), box.getLowerLeftY(),
                    box.getWidth(), box.getHeight() / 2f);
            if (rot == 0 || rot == 270) {
                first.cropBox = top;
                second.cropBox = bottom;
            } else {
                first.cropBox = bottom;
                second.cropBox = top;
            }
        } else {
            PDRectangle left = new PDRectangle(box.getLowerLeftX(), box.getLowerLeftY(),
                    box.getWidth() / 2f, box.getHeight());
            PDRectangle right = new PDRectangle(box.getLowerLeftX() + box.getWidth() / 2f, box.getLowerLeftY(),
                    box.getWidth() / 2f, box.getHeight());
            if (rot == 180 || rot == 270) {
                first.cropBox = right;
                second.cropBox = left;
            } else {
                first.cropBox = left;
                second.cropBox = right;
            }
        }
    }

    private OutFile writeMerger(PageMerger merger, String name) throws IOException {
        if (merger.size() == 0) {
            throw new ApiException("EMPTY_SPLIT", "Divisão resultou em arquivo vazio");
        }
        Path dest = artifacts.newArtifact(".pdf");
        merger.merge(dest);
        return new OutFile(dest, name);
    }

    /** Nome zero-padded por faixa, espelhando {@code PdfSplitter.makeName}. */
    static String makeName(int startPage, int endPage, int total) {
        if (startPage == endPage) {
            return pad(startPage, total);
        }
        return pad(startPage, total) + "-" + pad(endPage, total);
    }

    private static String pad(int pageNum, int total) {
        if (total < 10) {
            return String.format("%d", pageNum);
        }
        if (total < 100) {
            return String.format("%02d", pageNum);
        }
        if (total < 1000) {
            return String.format("%03d", pageNum);
        }
        if (total < 10000) {
            return String.format("%04d", pageNum);
        }
        return String.format("%d", pageNum);
    }
}
