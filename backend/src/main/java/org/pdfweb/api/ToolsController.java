package org.pdfweb.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.pdfweb.api.dto.ToolDtos.ContentEditRequest;
import org.pdfweb.api.dto.ToolDtos.ContentElement;
import org.pdfweb.api.dto.ToolDtos.EditRequest;
import org.pdfweb.api.dto.ToolDtos.MergeItem;
import org.pdfweb.api.dto.ToolDtos.MergeRequest;
import org.pdfweb.api.dto.ToolDtos.ResultFile;
import org.pdfweb.api.dto.ToolDtos.SingleResultResponse;
import org.pdfweb.api.dto.ToolDtos.SplitRequest;
import org.pdfweb.api.dto.ToolDtos.SplitResponse;
import org.pdfweb.error.ApiException;
import org.pdfweb.service.ArtifactStorage;
import org.pdfweb.service.ContentEditService;
import org.pdfweb.service.DocumentFontsService;
import org.pdfweb.service.EditService;
import org.pdfweb.service.FileStorageService;
import org.pdfweb.service.LayoutAnalysisService;
import org.pdfweb.service.MergeService;
import org.pdfweb.service.OcrService;
import org.pdfweb.service.SplitService;
import org.pdfweb.service.TextExtractService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints das ferramentas de página (PRD §7): split, merge, edit e download
 * do resultado. Fase 1 — entregas síncronas (artefato direto; jobs assíncronos
 * ficam para a etapa seguinte).
 */
@RestController
public class ToolsController {

    private final FileStorageService files;
    private final ArtifactStorage artifacts;
    private final SplitService splitService;
    private final MergeService mergeService;
    private final EditService editService;
    private final ContentEditService contentEditService;
    private final OcrService ocrService;
    private final org.pdfweb.jobs.JobRegistry jobs;
    private final TextExtractService textExtractService;
    private final LayoutAnalysisService layoutAnalysisService;
    private final DocumentFontsService documentFontsService;

    public ToolsController(FileStorageService files, ArtifactStorage artifacts,
                           SplitService splitService, MergeService mergeService, EditService editService,
                           ContentEditService contentEditService, OcrService ocrService,
                           org.pdfweb.jobs.JobRegistry jobs, TextExtractService textExtractService,
                           LayoutAnalysisService layoutAnalysisService,
                           DocumentFontsService documentFontsService) {
        this.files = files;
        this.artifacts = artifacts;
        this.splitService = splitService;
        this.mergeService = mergeService;
        this.editService = editService;
        this.contentEditService = contentEditService;
        this.ocrService = ocrService;
        this.jobs = jobs;
        this.textExtractService = textExtractService;
        this.layoutAnalysisService = layoutAnalysisService;
        this.documentFontsService = documentFontsService;
    }

    @PostMapping("/split")
    public SplitResponse split(@RequestBody SplitRequest req) throws IOException {
        Path pdf = files.resolveExisting(req.fileId());
        int pagesPerPdf = req.pagesPerPdf() == null ? 1 : req.pagesPerPdf();
        List<SplitService.OutFile> out = splitService.split(
                pdf, req.mode(), pagesPerPdf, req.splitPoints(), req.password());
        List<ResultFile> result = new ArrayList<>();
        for (SplitService.OutFile of : out) {
            String id = artifacts.idOf(of.file());
            result.add(new ResultFile(id, of.name(), "/api/v1/results/" + id));
        }
        return new SplitResponse(result);
    }

    @PostMapping("/merge")
    public SingleResultResponse merge(@RequestBody MergeRequest req) throws IOException {
        if (req.items() == null || req.items().isEmpty()) {
            throw new ApiException("EMPTY_MERGE", "Nenhum arquivo informado");
        }
        List<MergeService.InputItem> items = new ArrayList<>();
        for (MergeItem it : req.items()) {
            items.add(new MergeService.InputItem(files.resolveExisting(it.fileId()), it.ranges(), it.password()));
        }
        Path dest = mergeService.merge(items, req.mode());
        String id = artifacts.idOf(dest);
        return new SingleResultResponse(id, "/api/v1/results/" + id);
    }

    @PostMapping("/edit")
    public SingleResultResponse edit(@RequestBody EditRequest req) throws IOException {
        Path pdf = files.resolveExisting(req.fileId());
        List<EditService.Op> ops = new ArrayList<>();
        if (req.ops() != null) {
            req.ops().forEach(o -> ops.add(new EditService.Op(o.type(), o.page(), o.rotate(), o.order())));
        }
        Path dest = editService.edit(pdf, ops, req.password());
        String id = artifacts.idOf(dest);
        return new SingleResultResponse(id, "/api/v1/results/" + id);
    }

    @PostMapping("/content-edit")
    public ResponseEntity<?> contentEdit(@RequestBody ContentEditRequest req) throws IOException {
        Path pdf = files.resolveExisting(req.fileId());
        List<ContentEditService.Element> elements = new ArrayList<>();
        if (req.elements() != null) {
            for (ContentElement e : req.elements()) {
                elements.add(toElement(e));
            }
        }
        ContentEditService.Result editResult = contentEditService.applyWithResult(pdf, elements, req.password());
        Path dest = editResult.file();

        // Ponte opcional com OCR: após a redação (que já rasterizou as páginas
        // redigidas, destruindo o conteúdo), reaplica OCR para devolver um PDF
        // pesquisável — sem o conteúdo redigido, que já virou pixel.
        if (Boolean.TRUE.equals(req.ocr())) {
            if (!ocrService.isTesseractAvailable()) {
                throw new ApiException("OCR_UNAVAILABLE", "Motor de OCR (tesseract) indisponível no servidor");
            }
            org.pdfweb.jobs.Job job = jobs.create();
            String lang = req.lang() == null || req.lang().isBlank() ? "eng" : req.lang();
            org.pdfweb.core.QualityPreset quality = org.pdfweb.core.QualityPreset.from(req.quality());
            ocrService.runAsync(job, dest, lang, quality);
            return ResponseEntity.accepted()
                    .body(new org.pdfweb.api.dto.ToolDtos.JobCreatedResponse(job.getId()));
        }

        String id = artifacts.idOf(dest);
        return ResponseEntity.ok(new SingleResultResponse(id, "/api/v1/results/" + id, editResult.fontSubstituted()));
    }

    @PostMapping("/text-area")
    public org.pdfweb.api.dto.ToolDtos.TextAreaResponse textArea(
            @RequestBody org.pdfweb.api.dto.ToolDtos.TextAreaRequest req) throws IOException {
        Path pdf = files.resolveExisting(req.fileId());
        int page = req.page() == null ? 1 : req.page();
        float x = req.x() == null ? 0f : req.x();
        float y = req.y() == null ? 0f : req.y();
        float w = req.width() == null ? 0f : req.width();
        float h = req.height() == null ? 0f : req.height();
        String text = textExtractService.extractArea(pdf, req.password(), page, x, y, w, h);
        return new org.pdfweb.api.dto.ToolDtos.TextAreaResponse(text);
    }

    @PostMapping("/analyze-layout")
    public org.pdfweb.api.dto.ToolDtos.AnalyzeLayoutResponse analyzeLayout(
            @RequestBody org.pdfweb.api.dto.ToolDtos.AnalyzeLayoutRequest req) throws IOException {
        Path pdf = files.resolveExisting(req.fileId());
        int page = req.page() == null ? 1 : req.page();
        LayoutAnalysisService.PageLayout layout = layoutAnalysisService.analyzePage(pdf, req.password(), page);
        List<org.pdfweb.api.dto.ToolDtos.LayoutBlock> blocks = new ArrayList<>();
        for (LayoutAnalysisService.Block b : layout.blocks()) {
            blocks.add(new org.pdfweb.api.dto.ToolDtos.LayoutBlock(
                    b.kind().name().toLowerCase(), b.x(), b.y(), b.width(), b.height(), b.text(),
                    b.fontResource(), b.fontFamily(), b.fontSize()));
        }
        return new org.pdfweb.api.dto.ToolDtos.AnalyzeLayoutResponse(blocks, layout.scanned());
    }

    @PostMapping("/doc-fonts")
    public org.pdfweb.api.dto.ToolDtos.DocFontsResponse docFonts(
            @RequestBody org.pdfweb.api.dto.ToolDtos.DocFontsRequest req) throws IOException {
        Path pdf = files.resolveExisting(req.fileId());
        List<org.pdfweb.api.dto.ToolDtos.DocFontInfo> fonts = new ArrayList<>();
        for (DocumentFontsService.FontInfo f : documentFontsService.listFonts(pdf, req.password())) {
            fonts.add(new org.pdfweb.api.dto.ToolDtos.DocFontInfo(
                    f.resource(), f.page(), f.family(), f.bold(), f.italic(), f.embedded()));
        }
        return new org.pdfweb.api.dto.ToolDtos.DocFontsResponse(fonts);
    }

    private static ContentEditService.Element toElement(ContentElement e) {
        ContentEditService.Type type = switch (e.type() == null ? "" : e.type().toLowerCase()) {
            case "text" -> ContentEditService.Type.TEXT;
            case "image" -> ContentEditService.Type.IMAGE;
            case "redact" -> ContentEditService.Type.REDACT;
            default -> throw new ApiException("INVALID_ELEMENT", "Tipo de elemento desconhecido: " + e.type());
        };
        return new ContentEditService.Element(
                type,
                e.page() == null ? 1 : e.page(),
                e.x() == null ? 0f : e.x(),
                e.y() == null ? 0f : e.y(),
                e.width() == null ? 0f : e.width(),
                e.height() == null ? 0f : e.height(),
                e.text(), e.font(), e.size(), e.color(), e.imageId(),
                Boolean.TRUE.equals(e.hard()), e.fontResource(), e.fontResourcePage());
    }

    @GetMapping("/results/{resultId}")
    public ResponseEntity<Resource> download(@PathVariable String resultId,
                                             @RequestParam(name = "name", required = false) String name) throws IOException {
        Path artifact = artifacts.resolveExisting(resultId);
        long length = Files.size(artifact);
        // Preserva a extensão real do artefato (pdf, png, jpg...).
        String ext = "pdf";
        int dot = resultId.lastIndexOf('.');
        if (dot >= 0 && dot < resultId.length() - 1) {
            ext = resultId.substring(dot + 1).toLowerCase();
        }
        String downloadName = org.pdfweb.core.Filenames.safe(name, "resultado", ext);
        MediaType ct = switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "txt" -> MediaType.TEXT_PLAIN;
            default -> MediaType.APPLICATION_PDF;
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .contentType(ct)
                .contentLength(length)
                .body(new FileSystemResource(artifact));
    }
}
