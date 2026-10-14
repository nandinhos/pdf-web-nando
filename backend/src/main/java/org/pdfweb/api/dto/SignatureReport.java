package org.pdfweb.api.dto;

import java.util.List;

/**
 * Relatório de verificação de assinatura (PRD §4.6 / §7).
 *
 * <p>Escopo v1 (D10): validade CMS, cobertura do ByteRange, signatário (CN),
 * data, algoritmos e confiança da cadeia (system roots + certificados fornecidos).
 * {@code eidasQualified} fica indefinido até a evolução (D9 / fase b).
 */
public record SignatureReport(boolean hasSignatures, int count, boolean allValid, List<SignatureInfo> signatures) {

    public record SignatureInfo(
            String name,
            String kind,
            boolean valid,
            boolean coversWholeFile,
            String signerCN,
            String signedAt,
            String digestAlgorithm,
            boolean chainTrusted,
            String eidasQualified,
            String error) {
    }
}
