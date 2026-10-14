package org.pdfweb.jobs;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.pdfweb.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Registro em memória de jobs assíncronos e seus emissores SSE (Fase 3, D5).
 *
 * <p>Instância única por processo. Redis provisionado para evoluir a
 * persistência/fila multi-instância depois (não necessário para instância única).
 */
@Service
public class JobRegistry {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public Job create() {
        Job job = new Job(UUID.randomUUID().toString());
        jobs.put(job.getId(), job);
        emitters.put(job.getId(), new CopyOnWriteArrayList<>());
        return job;
    }

    public Job get(String id) {
        Job job = jobs.get(id);
        if (job == null) {
            throw new ApiException("JOB_NOT_FOUND", "jobId inexistente: " + id);
        }
        return job;
    }

    /** Cria e registra um emissor SSE para o job. */
    public SseEmitter subscribe(String id) {
        get(id); // valida existência
        SseEmitter emitter = new SseEmitter(0L); // sem timeout
        emitters.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> emitters.getOrDefault(id, List.of()).remove(emitter));
        emitter.onTimeout(() -> emitters.getOrDefault(id, List.of()).remove(emitter));
        return emitter;
    }

    /** Emite o estado atual do job para todos os assinantes. */
    public void publish(Job job) {
        List<SseEmitter> list = emitters.getOrDefault(job.getId(), List.of());
        Map<String, Object> payload = Map.of(
                "status", job.getStatus().name(),
                "progress", job.getProgress(),
                "totalPages", job.getTotalPages());
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(payload));
                if (job.getStatus() == Job.Status.DONE || job.getStatus() == Job.Status.ERROR) {
                    emitter.complete();
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }
}
