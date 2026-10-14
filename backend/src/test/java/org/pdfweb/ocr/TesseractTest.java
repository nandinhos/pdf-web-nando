package org.pdfweb.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class TesseractTest {

    @Test
    void buildArgsMatchesObservedOcrEngineInvocation() {
        // OBSERVED em OcrEngine.recognizeText:
        // <img> <base> -l <lang> --dpi <dpi> --oem 3 --psm 1 pdf txt
        List<String> args = Tesseract.buildArgs(Path.of("/tmp/in.png"), Path.of("/tmp/out"), "por", 300);

        assertThat(args).containsExactly(
                "/tmp/in.png", "/tmp/out",
                "-l", "por",
                "--dpi", "300",
                "--oem", "3",
                "--psm", "1",
                "pdf", "txt");
    }

    @Test
    void buildArgsDefaultsToEngWhenLangBlank() {
        List<String> args = Tesseract.buildArgs(Path.of("a"), Path.of("b"), "  ", 150);
        assertThat(args).containsSequence("-l", "eng");
        assertThat(args).containsSequence("--dpi", "150");
    }
}
