package org.pdfweb.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.pdfweb.core.PageMerger;
import org.pdfweb.core.PdfDocuments;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Edição básica de páginas (v1), ancorada em {@code PdfEditEngine}/{@code PdfPageRemover}
 * (OBSERVED). Modela a edição como uma sequência final de páginas desejada,
 * expressa por operações, e a materializa com {@link PageMerger}.
 *
 * <p>Operações suportadas: reordenar, remover, rotacionar, duplicar e inserir
 * página em branco (PRD §4.3 v1).
 */
@Service
public class EditService {

    /**
     * Operação de edição.
     *
     * @param type   um de: {@code reorder}, {@code rotate}, {@code delete},
     *               {@code duplicate}, {@code insertBlank}
     * @param page   página 1-based alvo (para rotate/delete/duplicate/insertBlank)
     * @param rotate graus a aplicar (para rotate; múltiplo de 90)
     * @param order  nova ordem 1-based das páginas (para reorder)
     */
    public record Op(String type, Integer page, Integer rotate, List<Integer> order) {
    }

    private final ArtifactStorage artifacts;

    public EditService(ArtifactStorage artifacts) {
        this.artifacts = artifacts;
    }

    /** Entrada do plano de montagem: página 1-based de origem (0 = página em branco). */
    private static final class Slot {
        int sourcePage; // 1-based; 0 => blank
        int rotate;

        Slot(int sourcePage) {
            this.sourcePage = sourcePage;
        }
    }

    public Path edit(Path pdf, List<Op> ops, String password) throws IOException {
        int numPages = PdfDocuments.pageCount(pdf, password);

        // Estado inicial: ordem natural das páginas.
        List<Slot> slots = new ArrayList<>();
        for (int p = 1; p <= numPages; p++) {
            slots.add(new Slot(p));
        }

        for (Op op : ops == null ? List.<Op>of() : ops) {
            applyOp(slots, op, numPages);
        }

        if (slots.isEmpty()) {
            throw new ApiException("EMPTY_EDIT", "A edição removeu todas as páginas");
        }

        return assemble(pdf, password, slots);
    }

    private void applyOp(List<Slot> slots, Op op, int numPages) {
        String type = op.type() == null ? "" : op.type();
        switch (type) {
            case "reorder" -> {
                if (op.order() == null || op.order().isEmpty()) {
                    throw new ApiException("INVALID_EDIT_OP", "reorder requer 'order'");
                }
                List<Slot> reordered = new ArrayList<>();
                for (int oneBased : op.order()) {
                    int idx = oneBased - 1;
                    if (idx < 0 || idx >= slots.size()) {
                        throw new ApiException("INVALID_EDIT_OP", "Índice de reorder fora do intervalo: " + oneBased);
                    }
                    reordered.add(slots.get(idx));
                }
                slots.clear();
                slots.addAll(reordered);
            }
            case "delete" -> slots.remove(requireIndex(op, slots.size()));
            case "duplicate" -> {
                int idx = requireIndex(op, slots.size());
                Slot src = slots.get(idx);
                Slot copy = new Slot(src.sourcePage);
                copy.rotate = src.rotate;
                slots.add(idx + 1, copy);
            }
            case "rotate" -> {
                int idx = requireIndex(op, slots.size());
                int delta = op.rotate() == null ? 0 : op.rotate();
                slots.get(idx).rotate = PageMerger.normRot(slots.get(idx).rotate + delta);
            }
            case "insertBlank" -> {
                int pos = op.page() == null ? slots.size() + 1 : op.page();
                int idx = Math.max(0, Math.min(pos - 1, slots.size()));
                slots.add(idx, new Slot(0));
            }
            default -> throw new ApiException("INVALID_EDIT_OP", "Operação desconhecida: " + type);
        }
    }

    private int requireIndex(Op op, int size) {
        if (op.page() == null) {
            throw new ApiException("INVALID_EDIT_OP", "Operação '" + op.type() + "' requer 'page'");
        }
        int idx = op.page() - 1;
        if (idx < 0 || idx >= size) {
            throw new ApiException("INVALID_EDIT_OP", "Página fora do intervalo: " + op.page());
        }
        return idx;
    }

    private Path assemble(Path pdf, String password, List<Slot> slots) throws IOException {
        Path dest = artifacts.newArtifact(".pdf");
        boolean hasBlank = slots.stream().anyMatch(s -> s.sourcePage == 0);

        if (!hasBlank) {
            // Caminho simples: só páginas de origem → PageMerger.
            try (PageMerger merger = new PageMerger()) {
                for (Slot slot : slots) {
                    PageMerger.PageItem item = new PageMerger.PageItem(pdf, slot.sourcePage, password);
                    item.rotate = slot.rotate;
                    merger.add(item);
                }
                merger.merge(dest);
            }
            return dest;
        }

        // Caminho com páginas em branco: compõe diretamente para suportar PDPage nova.
        PDRectangle blankSize = PdfDocuments.withDocument(pdf, password,
                doc -> doc.getPage(0).getMediaBox());
        try (PDDocument src = Loader.loadPDF(pdf.toFile(), password == null ? "" : password);
             PDDocument out = new PDDocument()) {
            for (Slot slot : slots) {
                if (slot.sourcePage == 0) {
                    out.addPage(new PDPage(blankSize));
                } else {
                    PDPage srcPage = src.getPage(slot.sourcePage - 1);
                    PDPage dest2 = out.importPage(srcPage);
                    dest2.setMediaBox(srcPage.getMediaBox());
                    dest2.setCropBox(srcPage.getCropBox());
                    dest2.setResources(srcPage.getResources());
                    dest2.setRotation(PageMerger.normRot(srcPage.getRotation() + slot.rotate));
                }
            }
            out.getDocumentCatalog().setOpenAction(null);
            out.save(dest.toFile());
        }
        return dest;
    }
}
