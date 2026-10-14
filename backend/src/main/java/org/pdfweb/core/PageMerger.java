package org.pdfweb.core;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Motor de composição de páginas, ancorado em {@code PdfPageMerger} do PDF24
 * (OBSERVED). É o núcleo compartilhado por split, merge e edição: cada operação
 * monta uma lista ordenada de {@link PageItem} e chama {@link #merge(Path)}.
 *
 * <p>Comportamento reimplementado sobre PDFBox 3.x nativo
 * ({@code PDDocument.importPage}):
 * <ul>
 *   <li>preserva mediaBox, cropBox, rotation e resources da página de origem;</li>
 *   <li>aplica {@code cropBox} e {@code rotate} por página (rotação normalizada);</li>
 *   <li>remove as chaves THUMB, B e PIECE_INFO de cada página (OBSERVED);</li>
 *   <li>versão do documento resultante = máximo das versões de origem.</li>
 * </ul>
 *
 * <p>Nota de escopo (v1): o remapeamento de outlines/bookmarks e links internos
 * presente no PdfPageMerger original NÃO é replicado nesta iteração — não é
 * requisito dos critérios de aceite da Fase 1. Fica como refinamento futuro
 * (ver IMPLEMENTATION_PLAN §4.1 / N2).
 */
public final class PageMerger implements Closeable {

    /** Rotação normalizada ao intervalo [0,360). Espelha {@code PdfPageMerger.normRot}. */
    public static int normRot(int rot) {
        return (rot % 360 + 360) % 360;
    }

    /** Item de página a importar: arquivo de origem, página 1-based e transformações. */
    public static final class PageItem {
        public final Path file;
        public final String password;
        public final int pageNum;
        public int rotate;
        public PDRectangle cropBox;

        public PageItem(Path file, int pageNum) {
            this(file, pageNum, null);
        }

        public PageItem(Path file, int pageNum, String password) {
            this.file = file;
            this.pageNum = pageNum;
            this.password = password;
        }
    }

    private final List<PageItem> pageItems = new ArrayList<>();
    private final Map<Path, PDDocument> docCache = new HashMap<>();

    public void add(PageItem item) {
        pageItems.add(item);
    }

    public int size() {
        return pageItems.size();
    }

    private PDDocument docFor(Path file, String password) throws IOException {
        PDDocument doc = docCache.get(file);
        if (doc == null) {
            doc = Loader.loadPDF(file.toFile(), password == null ? "" : password);
            docCache.put(file, doc);
        }
        return doc;
    }

    /**
     * Compõe as páginas acumuladas em um novo PDF salvo em {@code dest}.
     */
    public void merge(Path dest) throws IOException {
        try (PDDocument out = new PDDocument()) {
            float highestVersion = out.getVersion();
            for (PageItem item : pageItems) {
                PDDocument src = docFor(item.file, item.password);
                highestVersion = Math.max(highestVersion, src.getVersion());

                PDPage srcPage = src.getPage(item.pageNum - 1);
                PDPage destPage = out.importPage(srcPage);
                destPage.setMediaBox(srcPage.getMediaBox());
                destPage.setCropBox(srcPage.getCropBox());
                destPage.setRotation(srcPage.getRotation());
                destPage.setResources(srcPage.getResources());

                destPage.getCOSObject().removeItem(COSName.THUMB);
                destPage.getCOSObject().removeItem(COSName.B);
                destPage.getCOSObject().removeItem(COSName.PIECE_INFO);

                if (item.cropBox != null) {
                    destPage.setCropBox(item.cropBox);
                }
                if (item.rotate != 0) {
                    destPage.setRotation(normRot(destPage.getRotation() + item.rotate));
                }
            }
            out.getDocumentCatalog().setOpenAction(null);
            out.setVersion(highestVersion);
            out.save(dest.toFile());
        }
    }

    @Override
    public void close() throws IOException {
        for (PDDocument doc : docCache.values()) {
            try {
                doc.close();
            } catch (IOException ignored) {
                // best-effort close
            }
        }
        docCache.clear();
    }
}
