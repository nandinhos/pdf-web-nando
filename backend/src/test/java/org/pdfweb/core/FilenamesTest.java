package org.pdfweb.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilenamesTest {

    @Test
    void keepsValidNameAndForcesExtension() {
        assertThat(Filenames.safe("relatorio final", "doc", "pdf")).isEqualTo("relatorio final.pdf");
    }

    @Test
    void stripsPathTraversalAndDangerousChars() {
        assertThat(Filenames.safe("../../etc/passwd", "doc", "pdf")).isEqualTo("passwd.pdf");
        assertThat(Filenames.safe("a\"b;rm -rf/c", "doc", "pdf")).doesNotContain("\"", ";", "/");
    }

    @Test
    void avoidsDoubleExtension() {
        assertThat(Filenames.safe("documento.pdf", "doc", "pdf")).isEqualTo("documento.pdf");
    }

    @Test
    void fallsBackWhenEmptyOrInvalid() {
        assertThat(Filenames.safe("   ", "resultado", "pdf")).isEqualTo("resultado.pdf");
        assertThat(Filenames.safe("////", "resultado", "txt")).isEqualTo("resultado.txt");
    }
}
