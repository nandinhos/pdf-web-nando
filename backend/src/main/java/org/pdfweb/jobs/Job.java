package org.pdfweb.jobs;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Estado de um job assíncrono (ex.: OCR). Mutável e thread-safe para os campos
 * que o worker atualiza durante a execução.
 */
public class Job {

    public enum Status { PENDING, RUNNING, DONE, ERROR }

    private final String id;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);
    private final AtomicInteger progress = new AtomicInteger(0); // 0..100
    private final AtomicInteger totalPages = new AtomicInteger(0);
    private final AtomicInteger donePages = new AtomicInteger(0);
    private volatile String resultId;
    private volatile String textResultId;
    private volatile String error;

    public Job(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Status getStatus() {
        return status.get();
    }

    public void setStatus(Status s) {
        status.set(s);
    }

    public int getProgress() {
        return progress.get();
    }

    public void setTotalPages(int total) {
        totalPages.set(total);
    }

    public int getTotalPages() {
        return totalPages.get();
    }

    public void incrementDonePages() {
        int done = donePages.incrementAndGet();
        int total = totalPages.get();
        progress.set(total <= 0 ? 0 : Math.min(100, done * 100 / total));
    }

    public String getResultId() {
        return resultId;
    }

    public void setResultId(String resultId) {
        this.resultId = resultId;
    }

    public String getTextResultId() {
        return textResultId;
    }

    public void setTextResultId(String textResultId) {
        this.textResultId = textResultId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
