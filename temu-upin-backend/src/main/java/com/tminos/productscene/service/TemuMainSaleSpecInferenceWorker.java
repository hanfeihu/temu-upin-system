package com.tminos.productscene.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TemuMainSaleSpecInferenceWorker {

    private static final Logger log = LoggerFactory.getLogger(TemuMainSaleSpecInferenceWorker.class);

    private final TemuMainSaleSpecInferenceTaskClaimService claimService;
    private final TemuMainSaleSpecInferenceService inferenceService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    @Value("${temu.main-sale-spec-infer.worker.enabled:false}")
    private boolean enabled;

    @Value("${temu.main-sale-spec-infer.worker.poll-ms:300000}")
    private long pollMs;

    public TemuMainSaleSpecInferenceWorker(TemuMainSaleSpecInferenceTaskClaimService claimService,
                                          TemuMainSaleSpecInferenceService inferenceService) {
        this.claimService = claimService;
        this.inferenceService = inferenceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!enabled) {
            log.info("TemuMainSaleSpecInferenceWorker disabled (temu.main-sale-spec-infer.worker.enabled=false)");
            return;
        }
        if (running.get()) return;
        running.set(true);
        workerThread = new Thread(this::loop, "temu-main-sale-spec-infer-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("TemuMainSaleSpecInferenceWorker started (pollMs={}ms)", pollMs);
    }

    @PreDestroy
    public synchronized void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
        log.info("TemuMainSaleSpecInferenceWorker stopping");
    }

    private void loop() {
        while (running.get()) {
            try {
                boolean didWork = processOne();
                if (!didWork) {
                    sleepQuietly(pollMs);
                }
            } catch (Exception e) {
                log.warn("TemuMainSaleSpecInferenceWorker unexpected error; sleep 5000ms: {}", e.getMessage());
                sleepQuietly(5_000);
            }
        }
    }

    private boolean processOne() {
        TemuMainSaleSpecInferenceTaskClaimService.ClaimedTask c;
        try {
            c = claimService == null ? null : claimService.claimNextPending();
        } catch (Exception e) {
            log.warn("TemuMainSaleSpecInferenceWorker claim failed: {}", e.getMessage());
            return false;
        }
        if (c == null || c.id() == null) {
            return false;
        }
        Long taskId = c.id();
        try {
            inferenceService.runOnce(taskId);
        } catch (Exception e) {
            log.warn("TemuMainSaleSpecInferenceWorker runOnce failed taskId={}: {}", taskId, e.getMessage());
        }
        return true;
    }

    private void sleepQuietly(long ms) {
        try {
            if (ms <= 0) ms = 300000;
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
