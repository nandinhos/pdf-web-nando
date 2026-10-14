package org.pdfweb.api;

import java.io.IOException;
import java.nio.file.Path;

import org.pdfweb.api.dto.SignatureReport;
import org.pdfweb.service.FileStorageService;
import org.pdfweb.service.SignatureVerifyService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de verificação de assinatura (PRD §7): {@code POST /signatures/verify}
 * → relatório JSON (síncrono). Núcleo v1 (D10): CMS/PKIX + cobertura.
 */
@RestController
@RequestMapping("/signatures")
public class SignatureController {

    private final FileStorageService files;
    private final SignatureVerifyService verifier;

    public SignatureController(FileStorageService files, SignatureVerifyService verifier) {
        this.files = files;
        this.verifier = verifier;
    }

    public record VerifyRequest(String fileId) {
    }

    @PostMapping("/verify")
    public SignatureReport verify(@RequestBody VerifyRequest req) throws IOException {
        Path pdf = files.resolveExisting(req.fileId());
        return verifier.verify(pdf);
    }
}
