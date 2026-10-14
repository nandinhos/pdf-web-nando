package org.pdfweb.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.pdfweb.api.dto.ToolDtos.JobCreatedResponse;
import org.pdfweb.api.dto.ToolDtos.JobStatusResponse;
import org.pdfweb.api.dto.ToolDtos.OcrRequest;
import org.pdfweb.error.ApiException;
import org.pdfweb.jobs.Job;
import org.pdfweb.jobs.JobRegistry;
import org.pdfweb.service.ArtifactStorage;
import org.pdfweb.service.FileStorageService;
import org.pdfweb.service.OcrService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Endpoints de OCR e jobs assíncronos (PRD §7):
 * <ul>
 *   <li>{@code POST /ocr} → cria job, retorna jobId (202)</li>
 *   <li>{@code GET /jobs/{id}} → status/progresso</li>
 *   <li>{@code GET /jobs/{id}/events} → SSE de progresso</li>
 *   <li>{@code GET /jobs/{id}/result} → download do PDF pesquisável (ou ?type=txt)</li>
 * </ul>
 */
@RestController
public class OcrController {

    private final FileStorageService files;
    private final ArtifactStorage artifacts;
    private final OcrService ocrService;
    private final JobRegistry jobs;

    public OcrController(FileStorageService files, ArtifactStorage artifacts,
                         OcrService ocrService, JobRegistry jobs) {
        this.files = files;
        this.artifacts = artifacts;
        this.ocrService = ocrService;
        this.jobs = jobs;
    }

    @PostMapping("/ocr")
    public ResponseEntity<JobCreatedResponse> ocr(@RequestBody OcrRequest req) {
        Path pdf = files.resolveExisting(req.fileId());
        if (!ocrService.isTesseractAvailable()) {
            throw new ApiException("OCR_UNAVAILABLE", "Motor de OCR (tesseract) indisponível no servidor");
        }
        Job job = jobs.create();
        String lang = req.lang() == null || req.lang().isBlank() ? "eng" : req.lang();
        org.pdfweb.core.QualityPreset quality = org.pdfweb.core.QualityPreset.from(req.quality());
        ocrService.runAsync(job, pdf, lang, quality);
        return ResponseEntity.accepted().body(new JobCreatedResponse(job.getId()));
    }

    @GetMapping("/jobs/{jobId}")
    public JobStatusResponse status(@PathVariable String jobId) {
        Job job = jobs.get(jobId);
        return toStatus(job);
    }

    @GetMapping("/jobs/{jobId}/events")
    public SseEmitter events(@PathVariable String jobId) {
        return jobs.subscribe(jobId);
    }

    @GetMapping("/jobs/{jobId}/result")
    public ResponseEntity<Resource> result(@PathVariable String jobId,
                                            @RequestParam(name = "type", defaultValue = "pdf") String type,
                                            @RequestParam(name = "name", required = false) String name) throws IOException {
        Job job = jobs.get(jobId);
        if (job.getStatus() != Job.Status.DONE) {
            throw new ApiException("JOB_NOT_READY", "Job ainda não concluído (status " + job.getStatus() + ")");
        }
        boolean txt = "txt".equalsIgnoreCase(type);
        String resultId = txt ? job.getTextResultId() : job.getResultId();
        Path artifact = artifacts.resolveExisting(resultId);
        String ext = txt ? "txt" : "pdf";
        String downloadName = org.pdfweb.core.Filenames.safe(name, "ocr-resultado", ext);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .contentType(txt ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_PDF)
                .contentLength(Files.size(artifact))
                .body(new FileSystemResource(artifact));
    }

    private JobStatusResponse toStatus(Job job) {
        String resultUrl = job.getResultId() == null ? null : "/api/v1/jobs/" + job.getId() + "/result";
        String textUrl = job.getTextResultId() == null ? null : "/api/v1/jobs/" + job.getId() + "/result?type=txt";
        return new JobStatusResponse(job.getId(), job.getStatus().name(), job.getProgress(),
                job.getTotalPages(), resultUrl, textUrl, job.getError());
    }
}
