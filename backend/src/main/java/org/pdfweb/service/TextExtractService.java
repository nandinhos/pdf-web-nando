package org.pdfweb.service;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Extração de texto existente dentro de uma área retangular de uma página —
 * base da edição assistida de texto: o usuário arrasta um retângulo sobre o
 * texto que quer trocar, o sistema captura o conteúdo para pré-preencher a
 * caixa de edição (e depois a área é redigida de verdade e reescrita).
 *
 * <p>Coordenadas em pontos (pt), origem no topo-esquerda da página — mesma
 * convenção de {@link ContentEditService} e do pdf.js. {@link PDFTextStripperByArea}
 * usa {@link Rectangle2D} nesse mesmo espaço.
 */
@Service
public class TextExtractService {

    /**
     * Retorna o texto contido no retângulo {@code (x,y,w,h)} (pt, top-left) da
     * página {@code page} (1-based). String vazia se não houver texto na área.
     */
    public String extractArea(Path pdf, String password, int page, float x, float y, float w, float h)
            throws IOException {
        if (w <= 0 || h <= 0) {
            throw new ApiException("INVALID_AREA", "Área inválida: largura e altura devem ser positivas");
        }
        String pwd = password == null ? "" : password;
        try (PDDocument doc = Loader.loadPDF(pdf.toFile(), pwd)) {
            int numPages = doc.getNumberOfPages();
            if (page < 1 || page > numPages) {
                throw new ApiException("INVALID_AREA", "Página fora do intervalo: " + page);
            }
            PDPage pdPage = doc.getPage(page - 1);

            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.setSortByPosition(true);
            stripper.addRegion("area", new Rectangle2D.Float(x, y, w, h));
            stripper.extractRegions(pdPage);
            String text = stripper.getTextForRegion("area");
            return text == null ? "" : text.strip();
        }
    }
}
