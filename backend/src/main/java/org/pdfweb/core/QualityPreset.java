package org.pdfweb.core;

/**
 * Presets de qualidade de exportação (Fase 6, D12). Mapeiam um rótulo amigável
 * para parâmetros concretos de rasterização/compressão, atacando a causa raiz do
 * tamanho de arquivo: imagens embutidas em alta resolução sem perdas.
 *
 * @param dpi         resolução de render (OCR) ou alvo de recompressão
 * @param jpegQuality qualidade JPEG (0..1) ao recomprimir imagens com perdas
 */
public enum QualityPreset {
    LOW(120, 0.5f),
    MEDIUM(150, 0.72f),
    HIGH(300, 0.85f);

    public final int dpi;
    public final float jpegQuality;

    QualityPreset(int dpi, float jpegQuality) {
        this.dpi = dpi;
        this.jpegQuality = jpegQuality;
    }

    /** Resolve o preset a partir do rótulo (low/medium/high); padrão MEDIUM. */
    public static QualityPreset from(String label) {
        if (label == null) {
            return MEDIUM;
        }
        return switch (label.trim().toLowerCase()) {
            case "low" -> LOW;
            case "high" -> HIGH;
            default -> MEDIUM;
        };
    }
}
