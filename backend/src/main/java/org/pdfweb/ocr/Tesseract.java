package org.pdfweb.ocr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Invocação do binário {@code tesseract} via {@link ProcessBuilder}, com os
 * argumentos observados em {@code OcrEngine.recognizeText} (OBSERVED):
 * {@code tesseract <img> <base> -l <lang> --dpi <dpi> --oem 3 --psm 1 pdf txt}.
 *
 * <p>v1 (D7): sem {@code textonly_pdf} → o tesseract gera um PDF pesquisável
 * completo por página (imagem + texto invisível), evitando reimplementar o
 * overlay de camada de texto.
 */
public class Tesseract {

    private final String binary;

    public Tesseract(String binary) {
        this.binary = binary == null || binary.isBlank() ? "tesseract" : binary;
    }

    /**
     * Monta os argumentos do tesseract (sem o binário). Extraído para teste
     * determinístico independente do processo.
     */
    public static List<String> buildArgs(Path image, Path outBase, String lang, int dpi) {
        List<String> args = new ArrayList<>();
        args.add(image.toString());
        args.add(outBase.toString());
        args.add("-l");
        args.add(lang == null || lang.isBlank() ? "eng" : lang);
        args.add("--dpi");
        args.add(Integer.toString(dpi));
        args.add("--oem");
        args.add("3");
        args.add("--psm");
        args.add("1");
        args.add("pdf");
        args.add("txt");
        return args;
    }

    /**
     * Executa o OCR de uma imagem. Gera {@code <outBase>.pdf} e {@code <outBase>.txt}.
     *
     * @throws IOException se o processo falhar ou o PDF não for produzido
     */
    public void run(Path image, Path outBase, String lang, int dpi) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(binary);
        command.addAll(buildArgs(image, outBase, lang, dpi));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        // Drena a saída para não bloquear o processo em pipes cheios.
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("tesseract excedeu o tempo limite");
        }
        if (process.exitValue() != 0) {
            throw new IOException("tesseract falhou (exit " + process.exitValue() + "): " + output);
        }
    }

    /** Verifica se o binário do tesseract está disponível no ambiente. */
    public boolean isAvailable() {
        try {
            Process p = new ProcessBuilder(binary, "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
