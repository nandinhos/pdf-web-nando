package org.pdfweb.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileTypeValidatorTest {

    @Test
    void detectsPdfByMagicBytes() {
        byte[] pdf = "%PDF-1.4\n...".getBytes();
        assertThat(FileTypeValidator.isPdf(pdf)).isTrue();
        assertThat(FileTypeValidator.isPdf("not a pdf".getBytes())).isFalse();
    }

    @Test
    void detectsPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        assertThat(FileTypeValidator.isPng(png)).isTrue();
        assertThat(FileTypeValidator.isSupportedImage(png)).isTrue();
    }

    @Test
    void detectsJpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        assertThat(FileTypeValidator.isJpeg(jpeg)).isTrue();
        assertThat(FileTypeValidator.isSupportedImage(jpeg)).isTrue();
    }

    @Test
    void rejectsUnsupportedContent() {
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a'};
        assertThat(FileTypeValidator.isSupportedImage(gif)).isFalse();
        assertThat(FileTypeValidator.isPdf(gif)).isFalse();
    }

    @Test
    void handlesShortOrNullInput() {
        assertThat(FileTypeValidator.isPdf(new byte[]{'%'})).isFalse();
        assertThat(FileTypeValidator.isPdf(null)).isFalse();
    }
}
