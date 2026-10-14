package org.pdfweb.testsupport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/**
 * Utilitário de teste: gera um certificado self-signed e assina um PDF (fluxo de
 * assinatura CMS/PAdES compatível com o verificador). Determinístico, sem rede.
 */
public final class SignatureFixtures {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private SignatureFixtures() {
    }

    private static KeyPair keyPair;
    private static X509Certificate certificate;

    private static synchronized void ensureKeys(String cn) throws Exception {
        if (keyPair != null) {
            return;
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        X500Name dn = new X500Name("CN=" + cn + ",O=pdf-web-nando,C=BR");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                dn, BigInteger.valueOf(now), new Date(now - 86400000L),
                new Date(now + 3650L * 86400000L), dn, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(keyPair.getPrivate());
        certificate = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(builder.build(signer));
    }

    /** Assina um PDF (bytes) e retorna o PDF assinado. CN embutido no certificado. */
    public static byte[] signPdf(byte[] pdfBytes, String cn) throws Exception {
        ensureKeys(cn);
        PrivateKey key = keyPair.getPrivate();
        X509Certificate cert = certificate;

        try (PDDocument doc = Loader.loadPDF(pdfBytes); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(cn);
            signature.setSignDate(java.util.Calendar.getInstance());

            SignatureInterface si = content -> cmsSign(content, key, cert);
            doc.addSignature(signature, si);
            doc.saveIncremental(out);
            return out.toByteArray();
        }
    }

    private static byte[] cmsSign(InputStream content, PrivateKey key, X509Certificate cert) throws IOException {
        try {
            byte[] data = content.readAllBytes();
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(key);
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                    .build(signer, cert));
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            gen.addCertificates(new org.bouncycastle.cert.jcajce.JcaCertStore(java.util.List.of(cert)));
            CMSTypedData typed = new org.bouncycastle.cms.CMSProcessableByteArray(data);
            CMSSignedData signed = gen.generate(typed, false);
            // mantém a referência à CertificateFactory p/ paridade de API; retorna DER
            if (cf == null) {
                throw new IllegalStateException();
            }
            return signed.getEncoded();
        } catch (Exception e) {
            throw new IOException("Falha ao assinar CMS", e);
        }
    }

    /** Lê um recurso de bytes a partir de um array (helper trivial). */
    public static InputStream stream(byte[] b) {
        return new ByteArrayInputStream(b);
    }

    public static OutputStream nullOut() {
        return OutputStream.nullOutputStream();
    }
}
