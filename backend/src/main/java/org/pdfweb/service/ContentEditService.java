package org.pdfweb.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Edição de conteúdo por sobreposição (overlay) achatada no PDF.
 *
 * <p>Diferente de {@link EditService} (que manuseia a posição/rotação de
 * páginas), este serviço sobrepõe <em>elementos</em> no conteúdo de cada página
 * e os achata no documento final via {@link PDPageContentStream} em modo
 * {@code APPEND} (preserva o conteúdo original). Suporta três tipos:
 *
 * <ul>
 *   <li>{@code text}   — caixa de texto nova (fonte Standard-14, tamanho e cor);</li>
 *   <li>{@code image}  — imagem (resolvida por {@code imageId} no {@link ImageStorage});</li>
 *   <li>{@code redact} — tarja (retângulo preenchido) para ocultar/"excluir"
 *       conteúdo existente.</li>
 * </ul>
 *
 * <p><b>Sistema de coordenadas:</b> os elementos chegam em coordenadas de
 * topo-esquerda (origem no canto superior esquerdo, como telas e pdf.js). A
 * conversão para o espaço do PDF (origem inferior esquerda) é feita por página
 * usando a altura da {@code MediaBox}.
 */
@Service
public class ContentEditService {

    /** Tipo de elemento sobreposto. */
    public enum Type { TEXT, IMAGE, REDACT }

    /**
     * Elemento de overlay. Campos usados variam por {@link #type()}.
     *
     * @param type   tipo do elemento
     * @param page   página 1-based alvo
     * @param x      posição X (pt), topo-esquerda
     * @param y      posição Y (pt), topo-esquerda
     * @param width  largura (pt) — image/redact
     * @param height altura (pt) — image/redact
     * @param text   conteúdo textual — text
     * @param font   família Standard-14 (helvetica|times|courier, com bold/italic) — text
     * @param size   tamanho da fonte em pt — text
     * @param color  cor em hex {@code #RRGGBB} (texto ou preenchimento da tarja)
     * @param imageId identificador da imagem no {@link ImageStorage} — image
     * @param hard   redação REAL: quando {@code true} em um {@code redact}, a
     *               página é rasterizada e a região é destruída (não apenas
     *               coberta). Ignorado para {@code text}/{@code image}.
     * @param fontResource nome do recurso de fonte existente no documento a reusar
     *               na reescrita (ex.: detectado por {@code analyze-layout}); quando
     *               ausente ou incapaz de codificar o texto, usa-se a Standard-14 de
     *               {@code font} e sinaliza-se substituição. — text
     * @param fontResourcePage página 1-based onde o {@code fontResource} está
     *               declarado (recursos de fonte são por página). — text
     */
    public record Element(Type type, int page, float x, float y, float width, float height,
                          String text, String font, Float size, String color, String imageId,
                          boolean hard, String fontResource, Integer fontResourcePage) {
    }

    private final ArtifactStorage artifacts;
    private final ImageStorage images;

    /** DPI de rasterização para redação real (equilíbrio nitidez/tamanho). */
    private static final int REDACT_DPI = 200;

    public ContentEditService(ArtifactStorage artifacts, ImageStorage images) {
        this.artifacts = artifacts;
        this.images = images;
    }

    public Path apply(Path pdf, List<Element> elements, String password) throws IOException {
        return applyWithResult(pdf, elements, password).file();
    }

    /** Resultado da edição: artefato gerado e se alguma fonte original foi substituída. */
    public record Result(Path file, boolean fontSubstituted) {
    }

    public Result applyWithResult(Path pdf, List<Element> elements, String password) throws IOException {
        if (elements == null || elements.isEmpty()) {
            throw new ApiException("EMPTY_CONTENT_EDIT", "Nenhum elemento informado");
        }
        Path dest = artifacts.newArtifact(".pdf");
        String pwd = password == null ? "" : password;
        boolean[] fontSubstituted = {false};
        try (PDDocument doc = Loader.loadPDF(pdf.toFile(), pwd)) {
            int numPages = doc.getNumberOfPages();
            for (Element el : elements) {
                if (el.page() < 1 || el.page() > numPages) {
                    throw new ApiException("INVALID_ELEMENT", "Página fora do intervalo: " + el.page());
                }
            }

            // 1) Redação REAL: páginas com ao menos um redact 'hard' são
            //    rasterizadas e a região é destruída (os glifos/vetores somem).
            Map<Integer, List<Element>> hardByPage = new HashMap<>();
            for (Element el : elements) {
                if (el.type() == Type.REDACT && el.hard()) {
                    hardByPage.computeIfAbsent(el.page(), k -> new ArrayList<>()).add(el);
                }
            }
            if (!hardByPage.isEmpty()) {
                PDFRenderer renderer = new PDFRenderer(doc);
                for (Map.Entry<Integer, List<Element>> e : hardByPage.entrySet()) {
                    hardRedactPage(doc, renderer, e.getKey() - 1, e.getValue());
                }
            }

            // 2) Demais elementos (text/image/redact-soft) são sobrepostos por cima.
            //    Redacts 'hard' já foram aplicados destrutivamente na etapa 1.
            for (Element el : elements) {
                if (el.type() == Type.REDACT && el.hard()) {
                    continue;
                }
                PDPage page = doc.getPage(el.page() - 1);
                drawElement(doc, page, el, fontSubstituted);
            }

            doc.getDocumentCatalog().setOpenAction(null);
            doc.save(dest.toFile());
        }
        return new Result(dest, fontSubstituted[0]);
    }

    /**
     * Redação real da página {@code pageIndex}: renderiza o conteúdo para um
     * bitmap (DPI de {@link #REDACT_DPI}), pinta de preto as regiões redigidas
     * (destruindo o que havia por baixo — texto, vetores, imagens) e substitui
     * o conteúdo da página pela imagem resultante. Após isso não há mais camada
     * extraível sob a tarja: os dados viraram pixels.
     */
    private void hardRedactPage(PDDocument doc, PDFRenderer renderer, int pageIndex, List<Element> redactions)
            throws IOException {
        PDPage page = doc.getPage(pageIndex);
        PDRectangle box = page.getMediaBox();
        float pageW = box.getWidth();
        float pageH = box.getHeight();
        float scale = REDACT_DPI / 72f;

        BufferedImage raster = renderer.renderImageWithDPI(pageIndex, REDACT_DPI);

        // Pinta as regiões (coordenadas chegam em top-left, pt) em preto no bitmap.
        Graphics2D g = raster.createGraphics();
        try {
            for (Element r : redactions) {
                g.setColor(parseColor(r.color(), Color.BLACK));
                int rx = Math.round(r.x() * scale);
                int ry = Math.round(r.y() * scale);
                int rw = Math.round(r.width() * scale);
                int rh = Math.round(r.height() * scale);
                g.fillRect(rx, ry, rw, rh);
            }
        } finally {
            g.dispose();
        }

        // Substitui o conteúdo da página pela imagem achatada (sem APPEND: o
        // stream original é sobrescrito, eliminando os operadores anteriores).
        PDImageXObject img = LosslessFactory.createFromImage(doc, raster);
        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
            cs.drawImage(img, 0, 0, pageW, pageH);
        }
        // Remove anotações (ex.: campos/links poderiam reter texto redigido).
        page.setAnnotations(new ArrayList<>());
    }

    private void drawElement(PDDocument doc, PDPage page, Element el, boolean[] fontSubstituted) throws IOException {
        float pageHeight = page.getMediaBox().getHeight();
        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            switch (el.type()) {
                case TEXT -> drawText(doc, cs, el, pageHeight, fontSubstituted);
                case IMAGE -> drawImage(doc, cs, el, pageHeight);
                case REDACT -> drawRedact(cs, el, pageHeight);
            }
        }
    }

    private void drawText(PDDocument doc, PDPageContentStream cs, Element el, float pageHeight,
                          boolean[] fontSubstituted) throws IOException {
        if (el.text() == null || el.text().isBlank()) {
            throw new ApiException("INVALID_ELEMENT", "Elemento 'text' requer 'text'");
        }
        // Fase A: tenta reusar a fonte original do documento (fontResource) quando
        // ela consegue codificar o texto novo; senão, Standard-14 + sinalização.
        PDFont original = resolveDocumentFont(doc, el);
        String text = el.text();
        PDFont font;
        String drawn;
        if (original != null && canEncode(original, text)) {
            font = original;
            drawn = text; // a fonte original cobre; desenha o texto como está
        } else {
            if (original != null) {
                fontSubstituted[0] = true; // havia fonte original, mas não cobre
            }
            font = resolveFont(el.font());
            drawn = sanitizeForFont(text);
        }
        float size = el.size() == null ? 12f : el.size();
        // Baseline do texto: topo-esquerda → PDF (origem inferior esquerda).
        float baselineY = pageHeight - el.y() - size;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(parseColor(el.color(), Color.BLACK));
        cs.newLineAtOffset(el.x(), baselineY);
        cs.showText(drawn);
        cs.endText();
    }

    /**
     * Resolve a fonte original do documento a partir de {@code fontResource} /
     * {@code fontResourcePage} do elemento. Retorna {@code null} quando não há
     * recurso informado ou ele não é encontrado (cai no caminho Standard-14).
     */
    private PDFont resolveDocumentFont(PDDocument doc, Element el) {
        if (el.fontResource() == null || el.fontResource().isBlank()) {
            return null;
        }
        int pageIdx = (el.fontResourcePage() == null ? el.page() : el.fontResourcePage()) - 1;
        if (pageIdx < 0 || pageIdx >= doc.getNumberOfPages()) {
            return null;
        }
        PDResources res = doc.getPage(pageIdx).getResources();
        if (res == null) {
            return null;
        }
        try {
            return res.getFont(COSName.getPDFName(el.fontResource()));
        } catch (IOException e) {
            return null;
        }
    }

    /** True se {@code font} consegue codificar todos os caracteres de {@code text}. */
    private boolean canEncode(PDFont font, String text) {
        try {
            font.encode(text);
            return true;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }

    private void drawImage(PDDocument doc, PDPageContentStream cs, Element el, float pageHeight)
            throws IOException {
        if (el.imageId() == null || el.imageId().isBlank()) {
            throw new ApiException("INVALID_ELEMENT", "Elemento 'image' requer 'imageId'");
        }
        Path imagePath = images.resolveExisting(el.imageId());
        PDImageXObject image = PDImageXObject.createFromFile(imagePath.toString(), doc);
        float w = el.width() > 0 ? el.width() : image.getWidth();
        float h = el.height() > 0 ? el.height() : image.getHeight();
        // Canto inferior esquerdo no espaço do PDF.
        float drawY = pageHeight - el.y() - h;
        cs.drawImage(image, el.x(), drawY, w, h);
    }

    private void drawRedact(PDPageContentStream cs, Element el, float pageHeight) throws IOException {
        if (el.width() <= 0 || el.height() <= 0) {
            throw new ApiException("INVALID_ELEMENT", "Elemento 'redact' requer 'width' e 'height'");
        }
        float drawY = pageHeight - el.y() - el.height();
        cs.setNonStrokingColor(parseColor(el.color(), Color.WHITE));
        cs.addRect(el.x(), drawY, el.width(), el.height());
        cs.fill();
    }

    /** Mapeia famílias Standard-14 (sem embutir fontes). */
    private PDFont resolveFont(String name) {
        String f = name == null ? "" : name.trim().toLowerCase();
        return switch (f) {
            case "helvetica-bold", "helvetica_bold" -> new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            case "helvetica-oblique", "helvetica-italic" -> new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
            case "times", "times-roman" -> new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            case "times-bold" -> new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            case "times-italic" -> new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
            case "courier" -> new PDType1Font(Standard14Fonts.FontName.COURIER);
            case "courier-bold" -> new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
            default -> new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        };
    }

    /** Cor hex {@code #RRGGBB}; usa o default quando ausente/ inválida. */
    private Color parseColor(String hex, Color fallback) {
        if (hex == null) {
            return fallback;
        }
        String h = hex.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        if (h.length() != 6) {
            return fallback;
        }
        try {
            return new Color(Integer.parseInt(h, 16));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Remove caracteres fora do WinAnsiEncoding das fontes Standard-14, que
     * lançariam {@code IllegalArgumentException} em {@code showText}. Substitui
     * por '?' para preservar o layout sem quebrar a geração.
     */
    private String sanitizeForFont(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c >= 32 && c <= 255 ? c : '?');
        }
        return sb.toString();
    }
}
