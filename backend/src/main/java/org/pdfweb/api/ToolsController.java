package org.pdfweb.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.pdfweb.api.dto.ToolDtos.EditRequest;
import org.pdfweb.api.dto.ToolDtos.MergeItem;
import org.pdfweb.api.dto.ToolDtos.MergeRequest;
import org.pdfweb.api.dto.ToolDtos.ResultFile;
import org.pdfweb.api.dto.ToolDtos.SingleResultResponse;
import org.pdfweb.api.dto.ToolDtos.SplitRequest;
import org.pdfweb.api.dto.ToolDtos.SplitResponse;
import org.pdfweb.error.ApiException;
import org.pdfweb.service.ArtifactStorage;
import org.pdfweb.service.EditService;
import org.pdfweb.service.FileStorageService;
import org.pdfweb.service.MergeService;
import org.pdfweb.service.SplitService;
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

    public ToolsController(FileStorageService files, ArtifactStorage artifacts,
                           SplitService splitService, MergeService mergeService, EditService editService) {
        this.files = files;
        this.artifacts = artifacts;
        this.splitService = splitService;
        this.mergeService = mergeService;
        this.editService = editService;
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
