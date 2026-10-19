package org.pdfweb.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.springframework.stereotype.Service;

/**
 * Lista as fontes já embutidas/declaradas num PDF, por página, para que a UI de
 * edição possa oferecê-las como opção (reuso sem reembutir — alta fidelidade e
 * sem custo de subsetting). Cada entrada traz o nome do recurso (chave no
 * dicionário Font da página), a família legível e flags negrito/itálico.
 */
@Service
public class DocumentFontsService {

    /**
     * Fonte disponível no documento.
     *
     * @param resource nome do recurso na página (ex.: {@code F1})
     * @param page     página 1-based onde o recurso está declarado
     * @param family   família legível (sem prefixo de subset)
     * @param bold     negrito (heurística: nome/flags do descritor)
     * @param italic   itálico (heurística: nome/flags do descritor)
     * @param embedded a face está embutida no arquivo
     */
    public record FontInfo(String resource, int page, String family, boolean bold, boolean italic,
                           boolean embedded) {
    }

    public List<FontInfo> listFonts(Path pdf, String password) throws IOException {
        String pwd = password == null ? "" : password;
        // Deduplica por (recurso+página) preservando ordem.
        Map<String, FontInfo> out = new LinkedHashMap<>();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile(), pwd)) {
            int pageNum = 0;
            for (PDPage page : doc.getPages()) {
                pageNum++;
                PDResources res = page.getResources();
                if (res == null) {
                    continue;
                }
                for (COSName name : res.getFontNames()) {
                    PDFont font;
                    try {
                        font = res.getFont(name);
                    } catch (IOException e) {
                        continue;
                    }
                    if (font == null) {
                        continue;
                    }
                    String family = LayoutAnalysisService.fontFamilyOf(font);
                    String key = pageNum + "/" + name.getName();
                    out.putIfAbsent(key, new FontInfo(
                            name.getName(), pageNum,
                            family == null ? "(desconhecida)" : family,
                            isBold(font, family), isItalic(font, family), font.isEmbedded()));
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private boolean isBold(PDFont font, String family) {
        if (family != null && family.toLowerCase().matches(".*(bold|black|heavy|semibold).*")) {
            return true;
        }
        PDFontDescriptor d = font.getFontDescriptor();
        return d != null && d.isForceBold();
    }

    private boolean isItalic(PDFont font, String family) {
        if (family != null && family.toLowerCase().matches(".*(italic|oblique).*")) {
            return true;
        }
        PDFontDescriptor d = font.getFontDescriptor();
        return d != null && d.isItalic();
    }
}
