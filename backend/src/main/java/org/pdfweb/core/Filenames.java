package org.pdfweb.core;

/**
 * Sanitização de nome de arquivo para download (Fase 6, D13). Impede path
 * traversal e header injection no {@code Content-Disposition}, e força a
 * extensão correta.
 */
public final class Filenames {

    private Filenames() {
    }

    /**
     * Devolve um nome seguro com a extensão dada. Se {@code requested} for vazio
     * ou inválido, usa {@code fallbackBase}.
     *
     * @param requested    nome pedido pelo usuário (sem caminho)
     * @param fallbackBase base padrão quando o pedido é inválido (sem extensão)
     * @param ext          extensão sem ponto (ex.: "pdf", "txt")
     */
    public static String safe(String requested, String fallbackBase, String ext) {
        String base = requested == null ? "" : requested.trim();
        // Remove componentes de caminho e caracteres de controle/perigosos.
        base = base.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        // Remove extensão duplicada, se o usuário já a incluiu.
        if (base.toLowerCase().endsWith("." + ext.toLowerCase())) {
            base = base.substring(0, base.length() - ext.length() - 1);
        }
        // Mantém apenas caracteres seguros.
        base = base.replaceAll("[^A-Za-z0-9 ._-]", "").trim();
        if (base.isEmpty()) {
            base = fallbackBase;
        }
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        return base + "." + ext;
    }
}
