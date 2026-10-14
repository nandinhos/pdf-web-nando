package org.pdfweb.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.pdfweb.api.dto.ToolDtos.ImageExtractRequest;
import org.pdfweb.api.dto.ToolDtos.ImageToPdfRequest;
import org.pdfweb.api.dto.ToolDtos.ImageUploadResponse;
import org.pdfweb.api.dto.ToolDtos.ResultFile;
import org.pdfweb.api.dto.ToolDtos.SingleResultResponse;
import org.pdfweb.api.dto.ToolDtos.SplitResponse;
import org.pdfweb.error.ApiException;
import org.pdfweb.service.ArtifactStorage;
import org.pdfweb.service.FileStorageService;
import org.pdfweb.service.ImageExtractService;
import org.pdfweb.service.ImageStorage;
import org.pdfweb.service.ImageToPdfService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints de imagens (PRD §4.5 / §7):
 * <ul>
 *   <li>{@code POST /images} → upload de imagem (retorna imageId)</li>
 *   <li>{@code POST /images/to-pdf} → imagens → PDF</li>
 *   <li>{@code POST /images/extract} → extrai imagens de um PDF</li>
 * </ul>
 */
@RestController
@RequestMapping("/images")
public class ImagesController {

    private final ImageStorage imageStorage;
    private final FileStorageService files;
    private final ArtifactStorage artifacts;
    private final ImageToPdfService imageToPdf;
    private final ImageExtractService imageExtract;

    public ImagesController(ImageStorage imageStorage, FileStorageService files, ArtifactStorage artifacts,
                            ImageToPdfService imageToPdf, ImageExtractService imageExtract) {
        this.imageStorage = imageStorage;
        this.files = files;
        this.artifacts = artifacts;
        this.imageToPdf = imageToPdf;
        this.imageExtract = imageExtract;
    }

    @PostMapping
    public ResponseEntity<ImageUploadResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ApiException("EMPTY_UPLOAD", "Nenhuma imagem enviada");
        }
        byte[] bytes = file.getBytes();
        // Hardening (PRD §9): valida o tipo real por magic bytes (PNG/JPEG).
        if (!org.pdfweb.core.FileTypeValidator.isSupportedImage(bytes)) {
            throw new ApiException("UNSUPPORTED_IMAGE", "O arquivo enviado não é uma imagem PNG/JPEG válida");
        }
        String imageId = imageStorage.store(new java.io.ByteArrayInputStream(bytes), file.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ImageUploadResponse(imageId, file.getOriginalFilename()));
    }

    @PostMapping("/to-pdf")
    public SingleResultResponse toPdf(@RequestBody ImageToPdfRequest req) throws IOException {
        if (req.imageIds() == null || req.imageIds().isEmpty()) {
            throw new ApiException("EMPTY_IMAGE_SET", "Nenhuma imagem informada");
        }
        List<Path> images = new ArrayList<>();
        for (String id : req.imageIds()) {
            images.add(imageStorage.resolveExisting(id));
        }
        ImageToPdfService.Options opts = optionsFrom(req);
        Path dest = imageToPdf.convert(images, opts);
        String id = artifacts.idOf(dest);
        return new SingleResultResponse(id, "/api/v1/results/" + id);
    }

    @PostMapping("/extract")
    public SplitResponse extract(@RequestBody ImageExtractRequest req) throws IOException {
        Path pdf = files.resolveExisting(req.fileId());
        ImageExtractService.Options opts = new ImageExtractService.Options(
                req.passJpg() == null ? true : req.passJpg(),
                req.passJp2() == null ? false : req.passJp2(),
                req.jp2ToJpg() == null ? true : req.jp2ToJpg());
        List<ImageExtractService.OutImage> out = imageExtract.extract(pdf, opts);
        List<ResultFile> result = new ArrayList<>();
        for (ImageExtractService.OutImage oi : out) {
            String id = artifacts.idOf(oi.file());
            result.add(new ResultFile(id, oi.name(), "/api/v1/results/" + id));
        }
        return new SplitResponse(result);
    }

    private ImageToPdfService.Options optionsFrom(ImageToPdfRequest req) {
        ImageToPdfService.Options d = ImageToPdfService.Options.defaults();
        org.pdfweb.core.QualityPreset preset = org.pdfweb.core.QualityPreset.from(req.quality());
        // Qualidade (preset) define dpi + jpegQuality; parâmetros explícitos sobrepõem.
        int dpi = req.dpi() != null ? req.dpi() : (req.quality() != null ? preset.dpi : d.dpi());
        float jpegQuality = req.quality() != null ? preset.jpegQuality : d.jpegQuality();
        return new ImageToPdfService.Options(
                dpi,
                parsePageSize(req.pageSize(), d.pageSize()),
                req.orientation() == null ? d.orientation() : req.orientation(),
                req.margin() == null ? d.margin() : req.margin(),
                req.usePageSize() == null ? d.usePageSize() : req.usePageSize(),
                jpegQuality);
    }

    /** Converte nomes de tamanho de página comuns em PDRectangle. */
    private PDRectangle parsePageSize(String name, PDRectangle fallback) {
        if (name == null) {
            return fallback;
        }
        return switch (name.toUpperCase()) {
            case "A3" -> PDRectangle.A3;
            case "A4" -> PDRectangle.A4;
            case "A5" -> PDRectangle.A5;
            case "LETTER" -> PDRectangle.LETTER;
            case "LEGAL" -> PDRectangle.LEGAL;
            default -> fallback;
        };
    }
}
