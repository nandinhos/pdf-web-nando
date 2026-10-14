package org.pdfweb.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.pdfweb.api.dto.UploadResponse.PageInfo;
import org.pdfweb.core.PdfDocuments;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Operações de leitura de PDFs (contagem e metadados de páginas), delegando o
 * acesso ao documento ao wrapper {@link PdfDocuments}.
 */
@Service
public class PdfFileService {

    /**
     * Conta as páginas de um PDF, traduzindo falha de parse em {@link ApiException}.
     */
    public int countPages(Path path) {
        try {
            return PdfDocuments.pageCount(path, null);
        } catch (IOException e) {
            throw new ApiException("INVALID_PDF", "Arquivo não é um PDF válido", e);
        }
    }

    /**
     * Extrai metadados por página (dimensões em pontos e rotação).
     */
    public List<PageInfo> pageInfos(Path path) {
        try {
            return PdfDocuments.withDocument(path, null, doc -> {
                List<PageInfo> infos = new ArrayList<>();
                int index = 0;
                for (PDPage page : doc.getPages()) {
                    PDRectangle box = page.getMediaBox();
                    infos.add(new PageInfo(index++, box.getWidth(), box.getHeight(), page.getRotation()));
                }
                return infos;
            });
        } catch (IOException e) {
            throw new ApiException("INVALID_PDF", "Arquivo não é um PDF válido", e);
        }
    }
}
