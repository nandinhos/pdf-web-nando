package org.pdfweb.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pdfweb.api.dto.SignatureReport;
import org.pdfweb.testsupport.PdfFixtures;
import org.pdfweb.testsupport.SignatureFixtures;

class SignatureVerifyServiceTest {

    private final SignatureVerifyService service = new SignatureVerifyService();

    @Test
    void validSignatureReportsValidWithCnAndCoverage(@TempDir Path tmp) throws Exception {
        byte[] signed = SignatureFixtures.signPdf(PdfFixtures.blankPdf(2), "Fulano de Tal");
        Path pdf = tmp.resolve("signed.pdf");
        Files.write(pdf, signed);

        SignatureReport report = service.verify(pdf);

        assertThat(report.hasSignatures()).isTrue();
        assertThat(report.count()).isEqualTo(1);
        SignatureReport.SignatureInfo info = report.signatures().get(0);
        assertThat(info.valid()).isTrue();
        assertThat(info.coversWholeFile()).isTrue();
        assertThat(info.signerCN()).isEqualTo("Fulano de Tal");
    }

    @Test
    void tamperedPdfReportsInvalidOrPartialCoverage(@TempDir Path tmp) throws Exception {
        byte[] signed = SignatureFixtures.signPdf(PdfFixtures.blankPdf(2), "Fulano de Tal");
        // Altera bytes após a assinatura (append) → cobertura parcial / inválido.
        byte[] tampered = new byte[signed.length + 20];
        System.arraycopy(signed, 0, tampered, 0, signed.length);
        for (int i = 0; i < 20; i++) {
            tampered[signed.length + i] = (byte) '%';
        }
        Path pdf = tmp.resolve("tampered.pdf");
        Files.write(pdf, tampered);

        SignatureReport report = service.verify(pdf);

        assertThat(report.hasSignatures()).isTrue();
        SignatureReport.SignatureInfo info = report.signatures().get(0);
        // O append quebra a cobertura do arquivo (coversWholeFile=false) → inválido.
        assertThat(info.valid()).isFalse();
        assertThat(info.coversWholeFile()).isFalse();
    }

    @Test
    void pdfWithoutSignatureReportsNoSignatures(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("blank.pdf");
        Files.write(pdf, PdfFixtures.blankPdf(1));

        SignatureReport report = service.verify(pdf);

        assertThat(report.hasSignatures()).isFalse();
        assertThat(report.count()).isZero();
    }
}
