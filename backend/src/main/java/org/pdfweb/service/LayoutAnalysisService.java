package org.pdfweb.service;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Análise de layout estrutural (Fase 1): transforma o conteúdo de uma página em
 * <em>blocos</em> editáveis, lidos diretamente do PDF (sem OCR).
 *
 * <ul>
 *   <li><b>Texto</b>: cada linha (fragmento de {@link PDFTextStripper#writeString})
 *       vira um bloco com seu texto e bounding box;</li>
 *   <li><b>Imagem</b>: cada ocorrência desenhada ({@code Do} de uma imagem)
 *       vira um bloco, com a bbox derivada da CTM corrente via
 *       {@link PDFGraphicsStreamEngine}.</li>
 * </ul>
 *
 * <p>Coordenadas de saída em pontos (pt), origem no topo-esquerda (convenção do
 * projeto); a conversão a partir do espaço PDF (inferior esquerda) usa a altura
 * da {@code MediaBox}.
 *
 * <p>Se a página não tem texto extraível e é coberta por imagem, marca
 * {@code scanned=true} — sinal para o front sugerir o caminho OCR (Fase 2).
 */
@Service
public class LayoutAnalysisService {

    /** Tipo de bloco detectado. */
    public enum Kind { TEXT, IMAGE }

    /** Bloco detectado (coordenadas pt, top-left). {@code text} só para TEXT. */
    public record Block(Kind kind, float x, float y, float width, float height, String text,
                        String fontResource, String fontFamily, float fontSize) {
    }

    /** Resultado da análise de uma página. */
    public record PageLayout(List<Block> blocks, boolean scanned) {
    }

    public PageLayout analyzePage(Path pdf, String password, int page) throws IOException {
        String pwd = password == null ? "" : password;
        try (PDDocument doc = Loader.loadPDF(pdf.toFile(), pwd)) {
            int numPages = doc.getNumberOfPages();
            if (page < 1 || page > numPages) {
                throw new ApiException("INVALID_PAGE", "Página fora do intervalo: " + page);
            }
            PDPage pdPage = doc.getPage(page - 1);
            float pageHeight = pdPage.getMediaBox().getHeight();
            float pageArea = pdPage.getMediaBox().getWidth() * pageHeight;

            List<Block> textBlocks = collectTextBlocks(doc, page, pageHeight);
            List<Block> imageBlocks = collectImageBlocks(pdPage, pageHeight);

            List<Block> all = new ArrayList<>(textBlocks.size() + imageBlocks.size());
            all.addAll(textBlocks);
            all.addAll(imageBlocks);

            boolean scanned = textBlocks.isEmpty() && imageCoversPage(imageBlocks, pageArea);
            return new PageLayout(all, scanned);
        }
    }

    /** Blocos de texto por linha física, via um PDFTextStripper que captura posições. */
    private List<Block> collectTextBlocks(PDDocument doc, int page, float pageHeight) throws IOException {
        List<Block> blocks = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) throws IOException {
                if (text == null || text.isBlank() || positions.isEmpty()) {
                    return;
                }
                // Um 'writeString' pode conter várias linhas físicas (depende do PDF).
                // Agrupamos por baseline (YDirAdj) para emitir um bloco por linha,
                // respeitando as quebras — tolerância = metade da altura do glifo.
                List<List<TextPosition>> lines = new ArrayList<>();
                List<TextPosition> current = new ArrayList<>();
                float lineY = Float.NaN;
                for (TextPosition tp : positions) {
                    float y = tp.getYDirAdj();
                    float tol = Math.max(1f, tp.getHeightDir() * 0.5f);
                    if (Float.isNaN(lineY) || Math.abs(y - lineY) <= tol) {
                        if (Float.isNaN(lineY)) {
                            lineY = y;
                        }
                        current.add(tp);
                    } else {
                        lines.add(current);
                        current = new ArrayList<>();
                        current.add(tp);
                        lineY = y;
                    }
                }
                if (!current.isEmpty()) {
                    lines.add(current);
                }
                for (List<TextPosition> line : lines) {
                    emitLineBlock(doc, page, line, blocks);
                }
            }
        };
        stripper.setSortByPosition(true);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        stripper.getText(doc); // dispara writeString para a página
        return blocks;
    }

    /** Monta um bloco de texto a partir de uma linha física (lista de posições). */
    private void emitLineBlock(PDDocument doc, int page, List<TextPosition> line, List<Block> blocks) {
        StringBuilder sb = new StringBuilder(line.size());
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minYbl = Float.MAX_VALUE;
        for (TextPosition tp : line) {
            sb.append(tp.getUnicode());
            float x = tp.getXDirAdj();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x + tp.getWidthDirAdj());
            minYbl = Math.min(minYbl, tp.getYDirAdj());
        }
        String text = sb.toString().strip();
        if (text.isEmpty()) {
            return;
        }
        float width = maxX - minX;
        float height = line.get(0).getHeightDir() * 1.2f;
        float yTl = minYbl - height;
        String fontRes = resolveFontResourceName(doc, page, line.get(0).getFont());
        String fontFam = fontFamilyOf(line.get(0).getFont());
        float fontSize = line.get(0).getFontSizeInPt();
        blocks.add(new Block(Kind.TEXT, round(minX), round(Math.max(0, yTl)),
                round(width), round(height), text, fontRes, fontFam, round(fontSize)));
    }

    /** Blocos de imagem, capturando a bbox de cada drawImage via a CTM. */
    private List<Block> collectImageBlocks(PDPage pdPage, float pageHeight) throws IOException {
        List<Block> blocks = new ArrayList<>();
        PDFGraphicsStreamEngine engine = new PDFGraphicsStreamEngine(pdPage) {
            @Override
            public void drawImage(org.apache.pdfbox.pdmodel.graphics.image.PDImage pdImage) {
                Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                // A imagem é desenhada no quadrado unitário transformado pela CTM:
                // largura/altura = escala da CTM; origem = translação (bottom-left).
                float w = ctm.getScalingFactorX();
                float h = ctm.getScalingFactorY();
                float xBl = ctm.getTranslateX();
                float yBl = ctm.getTranslateY();
                float yTl = pageHeight - yBl - h;
                blocks.add(new Block(Kind.IMAGE, round(xBl), round(yTl), round(w), round(h), null, null, null, 0f));
            }

            // Métodos abstratos não usados (apenas texto/imagem nos interessam).
            @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) { }
            @Override public void clip(int windingRule) { }
            @Override public void moveTo(float x, float y) { }
            @Override public void lineTo(float x, float y) { }
            @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) { }
            @Override public Point2D getCurrentPoint() { return new Point2D.Float(0, 0); }
            @Override public void closePath() { }
            @Override public void endPath() { }
            @Override public void strokePath() { }
            @Override public void fillPath(int windingRule) { }
            @Override public void fillAndStrokePath(int windingRule) { }
            @Override public void shadingFill(org.apache.pdfbox.cos.COSName shadingName) { }
        };
        engine.processPage(pdPage);
        return blocks;
    }

    /**
     * Resolve o nome do recurso de fonte (chave no dicionário Font da página) a
     * partir do {@link PDFont} reportado pela posição de texto, por identidade.
     * Retorna {@code null} se não encontrar — o reuso de fonte então não se aplica.
     */
    private String resolveFontResourceName(PDDocument doc, int page, PDFont font) {
        if (font == null) {
            return null;
        }
        try {
            PDResources res = doc.getPage(page - 1).getResources();
            if (res == null) {
                return null;
            }
            for (COSName name : res.getFontNames()) {
                PDFont f = res.getFont(name);
                if (f == font || (f != null && f.getCOSObject() == font.getCOSObject())) {
                    return name.getName();
                }
            }
        } catch (IOException ignored) {
            // best-effort: sem recurso resolvido, cai no caminho Standard-14
        }
        return null;
    }

    private boolean imageCoversPage(List<Block> imageBlocks, float pageArea) {
        if (imageBlocks.isEmpty() || pageArea <= 0) {
            return false;
        }
        float covered = 0f;
        for (Block b : imageBlocks) {
            covered += b.width() * b.height();
        }
        return covered >= pageArea * 0.6f; // 60% coberto por imagem ⇒ provável scan
    }

    /**
     * Nome legível da família da fonte, sem o prefixo de subset (ex.:
     * {@code ABCDEF+Times-Bold} → {@code Times-Bold}). {@code null} se indisponível.
     *
     * <p>Prioriza o {@code BaseFont} <em>declarado</em> no dicionário do PDF (nome
     * fiel ao arquivo). Só cai para {@link PDFont#getName()} quando o declarado
     * está ausente — este último pode refletir uma face <em>substituta</em> que o
     * PDFBox resolveu (ex.: LiberationSerif no lugar de Times não embutida).
     */
    static String fontFamilyOf(PDFont font) {
        if (font == null) {
            return null;
        }
        String name = null;
        try {
            name = font.getCOSObject().getNameAsString(COSName.BASE_FONT);
        } catch (RuntimeException ignored) {
            // alguns tipos (Type0/Type3) podem não expor BASE_FONT diretamente
        }
        if (name == null || name.isBlank()) {
            name = font.getName();
        }
        if (name == null) {
            return null;
        }
        int plus = name.indexOf('+');
        if (plus == 6) { // prefixo de subset tem 6 letras maiúsculas + '+'
            name = name.substring(plus + 1);
        }
        return name;
    }

    private static float round(float v) {
        return Math.round(v * 10f) / 10f;
    }
}
