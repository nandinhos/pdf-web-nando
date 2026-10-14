package org.pdfweb.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsing de faixas e listas de páginas (1-based), reimplementando o
 * comportamento observado em {@code PageRange.parse} e
 * {@code StringHelper.parseIntList} das classes de referência do PDF24.
 *
 * <p>Formato de faixas: {@code "1-3,5,8-10"}. Uma faixa aberta à direita
 * ({@code "3-"}) vai até a última página.
 */
public final class PageRanges {

    private PageRanges() {
    }

    /** Faixa inclusiva 1-based. {@code endPage <= 0} significa "até o fim". */
    public record Range(int startPage, int endPage) {
    }

    /**
     * Converte uma lista de inteiros separada por vírgulas em {@code int[]}.
     * Entradas inválidas/vazias são ignoradas. Ex.: {@code "1,3,5" -> [1,3,5]}.
     */
    public static int[] parseIntList(String csv) {
        if (csv == null || csv.isBlank()) {
            return new int[0];
        }
        List<Integer> values = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                values.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                // Ignora tokens não numéricos (comportamento tolerante observado).
            }
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    /**
     * Parseia faixas de páginas. Ex.: {@code "1-3,5" -> [Range(1,3), Range(5,5)]}.
     * {@code "3-" -> [Range(3,0)]} (fim aberto, resolvido pelo chamador).
     */
    public static List<Range> parse(String spec) {
        List<Range> ranges = new ArrayList<>();
        if (spec == null || spec.isBlank()) {
            return ranges;
        }
        for (String part : spec.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int dash = token.indexOf('-');
            try {
                if (dash < 0) {
                    int page = Integer.parseInt(token);
                    ranges.add(new Range(page, page));
                } else {
                    String startStr = token.substring(0, dash).trim();
                    String endStr = token.substring(dash + 1).trim();
                    int start = startStr.isEmpty() ? 1 : Integer.parseInt(startStr);
                    int end = endStr.isEmpty() ? 0 : Integer.parseInt(endStr);
                    ranges.add(new Range(start, end));
                }
            } catch (NumberFormatException ignored) {
                // Ignora faixas malformadas.
            }
        }
        return ranges;
    }
}
