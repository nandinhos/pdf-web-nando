package org.pdfweb.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Wrapper fino sobre o carregamento de documentos PDFBox 3.x.
 *
 * <p>Espelha o padrão observado nas classes de referência do PDF24
 * ({@code Loader.loadPDF(File, String)} com senha opcional), centralizando o
 * carregamento e o fechamento seguro do {@link PDDocument} para evitar vazamento
 * de recursos.
 */
public final class PdfDocuments {

    private PdfDocuments() {
    }

    /**
     * Carrega um PDF e aplica a função fornecida, garantindo o fechamento do
     * documento ao final (padrão try-with-resources).
     *
     * @param path     caminho do arquivo PDF
     * @param password senha do PDF (pode ser {@code null} ou vazia)
     * @param fn       função que extrai um resultado do documento aberto
     */
    public static <T> T withDocument(Path path, String password, Function<PDDocument, T> fn)
            throws IOException {
        String pwd = password == null ? "" : password;
        try (PDDocument doc = Loader.loadPDF(path.toFile(), pwd)) {
            return fn.apply(doc);
        }
    }

    /**
     * Retorna o número de páginas de um PDF.
     */
    public static int pageCount(Path path, String password) throws IOException {
        return withDocument(path, password, PDDocument::getNumberOfPages);
    }
}
