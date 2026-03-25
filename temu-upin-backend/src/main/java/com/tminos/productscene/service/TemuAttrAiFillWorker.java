package com.tminos.productscene.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TemuAttrAiFillWorker {

    private static final Logger log = LoggerFactory.getLogger(TemuAttrAiFillWorker.class);

    private final TemuAttrAiFillTaskClaimService claimService;
    private final TemuAttrAiFillService service;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    @Value("${temu.attr-ai-fill.worker.enabled:false}")
    private boolean enabled;

    @Value("${temu.attr-ai-fill.worker.poll-ms:300000}")
    private long pollMs;

    public TemuAttrAiFillWorker(TemuAttrAiFillTaskClaimService claimService,
                               TemuAttrAiFillService service) {
        this.claimService = claimService;
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!enabled) {
            log.info("TemuAttrAiFillWorker disabled (temu.attr-ai-fill.worker.enabled=false)");
            return;
        }
        if (running.get()) return;
        running.set(true);
        workerThread = new Thread(this::loop, "temu-attr-ai-fill-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("TemuAttrAiFillWorker started (pollMs={}ms)", pollMs);
    }

    @PreDestroy
    public synchronized void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
        log.info("TemuAttrAiFillWorker stopping");
    }

    private void loop() {
        while (running.get()) {
            try {
                boolean didWork = processOne();
                if (!didWork) {
                    sleepQuietly(pollMs);
                }
            } catch (Exception e) {
                log.warn("TemuAttrAiFillWorker unexpected error; sleep 5000ms: {}", e.getMessage());
                sleepQuietly(5_000);
            }
        }
    }

    private boolean processOne() {
        TemuAttrAiFillTaskClaimService.ClaimedTask c;
        try {
            c = claimService == null ? null : claimService.claimNextPending();
        } catch (Exception e) {
            log.warn("TemuAttrAiFillWorker claim failed: {}", e.getMessage());
            return false;
        }
        if (c == null || c.id() == null) {
            return false;
        }
        Long taskId = c.id();
        try {
            service.runOnce(taskId);
        } catch (Exception e) {
            log.warn("TemuAttrAiFillWorker runOnce failed taskId={}: {}", taskId, e.getMessage());
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
