package org.pdfweb.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.pdfweb.core.PageMerger;
import org.pdfweb.core.PageRanges;
import org.pdfweb.core.PdfDocuments;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Mesclagem de páginas, ancorada em {@code PdfPageMerger} (OBSERVED).
 * Reimplementa os 4 modos de inicialização sobre {@link PageMerger}.
 */
@Service
public class MergeService {

    public static final String MODE_CONCATENATE = "concatenate";
    public static final String MODE_ALTERNATE = "alternate";
    public static final String MODE_ODD_EVEN_REVERSE = "oddEvenReverse";

    /** Item de entrada: PDF de origem, faixas opcionais ("1-3,5") e senha. */
    public record InputItem(Path file, String ranges, String password) {
    }

    private final ArtifactStorage artifacts;

    public MergeService(ArtifactStorage artifacts) {
        this.artifacts = artifacts;
    }

    public Path merge(List<InputItem> items, String mode) throws IOException {
        if (items == null || items.isEmpty()) {
            throw new ApiException("EMPTY_MERGE", "Nenhum arquivo informado para mesclar");
        }
        try (PageMerger merger = new PageMerger()) {
            switch (mode == null ? MODE_CONCATENATE : mode) {
                case MODE_ALTERNATE -> initAlternate(merger, items);
                case MODE_ODD_EVEN_REVERSE -> initOddEvenReverse(merger, items);
                default -> initConcatenate(merger, items);
            }
            if (merger.size() == 0) {
                throw new ApiException("EMPTY_MERGE", "Mesclagem resultou em documento vazio");
            }
            Path dest = artifacts.newArtifact(".pdf");
            merger.merge(dest);
            return dest;
        }
    }

    /** Concatena todos os arquivos na ordem; respeita faixas por arquivo se dadas. */
    private void initConcatenate(PageMerger merger, List<InputItem> items) throws IOException {
        for (InputItem item : items) {
            if (item.ranges() != null && !item.ranges().isBlank()) {
                addRanges(merger, item);
            } else {
                int n = PdfDocuments.pageCount(item.file(), item.password());
                for (int p = 1; p <= n; p++) {
                    merger.add(new PageMerger.PageItem(item.file(), p, item.password()));
                }
            }
        }
    }

    /** Intercala páginas dos arquivos (frente/verso). Espelha initWithMethodAlternate. */
    private void initAlternate(PageMerger merger, List<InputItem> items) throws IOException {
        int[] counts = new int[items.size()];
        int max = 0;
        for (int i = 0; i < items.size(); i++) {
            counts[i] = PdfDocuments.pageCount(items.get(i).file(), items.get(i).password());
            max = Math.max(max, counts[i]);
        }
        for (int p = 1; p <= max; p++) {
            for (int i = 0; i < items.size(); i++) {
                if (p <= counts[i]) {
                    merger.add(new PageMerger.PageItem(items.get(i).file(), p, items.get(i).password()));
                }
            }
        }
    }

    /** Exige exatamente 2 arquivos; o 2º é lido em ordem reversa. OBSERVED. */
    private void initOddEvenReverse(PageMerger merger, List<InputItem> items) throws IOException {
        if (items.size() != 2) {
            throw new ApiException("INVALID_MERGE_ARGS",
                    "O modo oddEvenReverse exige exatamente dois arquivos (ímpares e pares em ordem reversa)");
        }
        InputItem first = items.get(0);
        InputItem second = items.get(1);
        int firstCount = PdfDocuments.pageCount(first.file(), first.password());
        int secondCount = PdfDocuments.pageCount(second.file(), second.password());
        List<PageMerger.PageItem> firstItems = new java.util.ArrayList<>();
        for (int p = 1; p <= firstCount; p++) {
            firstItems.add(new PageMerger.PageItem(first.file(), p, first.password()));
        }
        List<PageMerger.PageItem> secondItems = new java.util.ArrayList<>();
        for (int p = secondCount; p >= 1; p--) {
            secondItems.add(new PageMerger.PageItem(second.file(), p, second.password()));
        }
        int max = Math.max(firstCount, secondCount);
        for (int i = 0; i < max; i++) {
            if (i < firstItems.size()) {
                merger.add(firstItems.get(i));
            }
            if (i < secondItems.size()) {
                merger.add(secondItems.get(i));
            }
        }
    }

    private void addRanges(PageMerger merger, InputItem item) throws IOException {
        int n = PdfDocuments.pageCount(item.file(), item.password());
        for (PageRanges.Range range : PageRanges.parse(item.ranges())) {
            int end = range.endPage() <= 0 ? n : Math.min(range.endPage(), n);
            if (range.startPage() <= 0) {
                continue;
            }
            for (int p = range.startPage(); p <= end; p++) {
                merger.add(new PageMerger.PageItem(item.file(), p, item.password()));
            }
        }
    }
}
