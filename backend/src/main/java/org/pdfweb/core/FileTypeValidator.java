package org.pdfweb.core;

/**
 * Validação de tipo de arquivo por "magic bytes" (assinatura binária), não pela
 * extensão (PRD §9 — hardening: não confiar na extensão).
 */
public final class FileTypeValidator {

    private FileTypeValidator() {
    }

    /** {@code true} se os bytes começam com a assinatura de PDF ({@code %PDF-}). */
    public static boolean isPdf(byte[] header) {
        return startsWith(header, new byte[]{'%', 'P', 'D', 'F', '-'});
    }

    /** {@code true} se os bytes são de uma imagem suportada (PNG ou JPEG). */
    public static boolean isSupportedImage(byte[] header) {
        return isPng(header) || isJpeg(header);
    }

    public static boolean isPng(byte[] header) {
        // 89 50 4E 47 0D 0A 1A 0A
        return startsWith(header, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
    }

    public static boolean isJpeg(byte[] header) {
        // FF D8 FF
        return startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
