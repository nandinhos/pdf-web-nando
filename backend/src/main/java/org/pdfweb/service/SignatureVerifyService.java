package org.pdfweb.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertPathBuilder;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;
import org.pdfweb.api.dto.SignatureReport;
import org.pdfweb.api.dto.SignatureReport.SignatureInfo;
import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;

/**
 * Verificação de assinaturas digitais de PDF, ancorada em
 * {@code PdfSignatureVerifier} (OBSERVED). Núcleo v1 (D10): valida a assinatura
 * CMS/PKCS#7 com BouncyCastle, calcula a cobertura do ByteRange, extrai o
 * signatário (CN) e a data, e verifica a confiança da cadeia contra as system
 * roots da JVM e certificados fornecidos.
 *
 * <p>eIDAS (EU Trust List) fica para a evolução (D9); {@code eidasQualified}
 * é reportado como "unknown".
 */
@Service
public class SignatureVerifyService {

    private final BouncyCastleProvider bc = new BouncyCastleProvider();

    public SignatureReport verify(Path pdf) throws IOException {
        byte[] bytes = Files.readAllBytes(pdf);
        List<SignatureInfo> infos = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            List<PDSignature> sigs = doc.getSignatureDictionaries();
            for (PDSignature sig : sigs) {
                infos.add(verifyOne(sig, bytes));
            }
        } catch (IOException e) {
            throw new ApiException("INVALID_PDF", "Arquivo não é um PDF válido", e);
        }

        boolean allValid = !infos.isEmpty() && infos.stream().allMatch(SignatureInfo::valid);
        return new SignatureReport(!infos.isEmpty(), infos.size(), allValid, infos);
    }

    private SignatureInfo verifyOne(PDSignature sig, byte[] fileBytes) {
        String name = sig.getName() != null ? sig.getName() : "signature";
        String kind = "ETSI.RFC3161".equals(sig.getSubFilter()) ? "documentTimestamp" : "signature";
        try {
            int[] range = sig.getByteRange();
            // Cobertura: a assinatura cobre o arquivo inteiro quando o ByteRange
            // termina no fim do arquivo (OBSERVED signedLength = r[2]+r[3]).
            long signedLength = range.length == 4 ? (long) range[2] + range[3] : 0L;
            boolean coversWholeFile = range.length == 4 && signedLength == fileBytes.length;

            byte[] contents = sig.getContents(fileBytes);       // CMS/PKCS#7
            byte[] signedContent = sig.getSignedContent(fileBytes); // bytes cobertos pelo ByteRange

            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signedContent), contents);
            Store<?> certStore = cms.getCertificates();
            SignerInformationStore signers = cms.getSignerInfos();

            boolean cmsValid = false;
            X509Certificate signerCert = null;
            String digestAlg = null;

            for (SignerInformation signer : signers.getSigners()) {
                @SuppressWarnings("unchecked")
                Collection<org.bouncycastle.cert.X509CertificateHolder> matches =
                        (Collection<org.bouncycastle.cert.X509CertificateHolder>) certStore.getMatches(signer.getSID());
                if (matches.isEmpty()) {
                    continue;
                }
                org.bouncycastle.cert.X509CertificateHolder holder = matches.iterator().next();
                X509Certificate cert = new JcaX509CertificateConverter().setProvider(bc).getCertificate(holder);
                signerCert = cert;
                digestAlg = signer.getDigestAlgOID();
                cmsValid = signer.verify(
                        new JcaSimpleSignerInfoVerifierBuilder().setProvider(bc).build(cert));
                break;
            }

            boolean chainTrusted = signerCert != null && isChainTrusted(signerCert, cms);
            String signerCN = signerCert != null ? commonName(signerCert) : null;
            String signedAt = sig.getSignDate() != null
                    ? new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(sig.getSignDate().getTime())
                    : null;

            boolean valid = cmsValid && coversWholeFile;
            return new SignatureInfo(name, kind, valid, coversWholeFile, signerCN, signedAt,
                    digestAlg, chainTrusted, "unknown", null);
        } catch (Exception e) {
            return new SignatureInfo(name, kind, false, false, null, null, null, false, "unknown",
                    e.getMessage());
        }
    }

    /** Valida a cadeia PKIX contra system roots + certificados do próprio CMS. */
    private boolean isChainTrusted(X509Certificate signerCert, CMSSignedData cms) {
        try {
            Set<TrustAnchor> anchors = systemAnchors();
            X509CertSelector target = new X509CertSelector();
            target.setCertificate(signerCert);
            PKIXBuilderParameters params = new PKIXBuilderParameters(anchors, target);
            params.setRevocationEnabled(false); // LTV/OCSP/CRL ficam para v2 (D10)

            Collection<X509Certificate> intermediates = extractCerts(cms);
            params.addCertStore(java.security.cert.CertStore.getInstance("Collection",
                    new java.security.cert.CollectionCertStoreParameters(intermediates)));

            CertPathBuilder.getInstance("PKIX").build(params);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Collection<X509Certificate> extractCerts(CMSSignedData cms) throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        JcaX509CertificateConverter conv = new JcaX509CertificateConverter().setProvider(bc);
        for (Object holder : cms.getCertificates().getMatches(null)) {
            certs.add(conv.getCertificate((org.bouncycastle.cert.X509CertificateHolder) holder));
        }
        return certs;
    }

    private Set<TrustAnchor> systemAnchors() throws Exception {
        Set<TrustAnchor> anchors = new HashSet<>();
        var tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((java.security.KeyStore) null);
        for (var tm : tmf.getTrustManagers()) {
            if (tm instanceof javax.net.ssl.X509TrustManager x509tm) {
                for (X509Certificate ca : x509tm.getAcceptedIssuers()) {
                    anchors.add(new TrustAnchor(ca, null));
                }
            }
        }
        return anchors;
    }

    /** Extrai o CN do subject (espelha {@code PdfSignatureVerifier.commonName}). */
    static String commonName(X509Certificate cert) {
        String dn = cert.getSubjectX500Principal().getName();
        for (String part : dn.split(",")) {
            String p = part.trim();
            if (p.startsWith("CN=")) {
                return p.substring(3);
            }
        }
        return dn;
    }
}
