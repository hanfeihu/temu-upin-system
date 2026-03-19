package com.tminos.productscene.service;

import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.repository.ProductCollectionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Global worker:
 * - Polls DB for pending execStatus=0 items
 * - Runs one-by-one synchronously (no overlap)
 * - When queue is empty: sleeps 5 minutes (configurable)
 *
 * This is a dedicated single thread loop (NOT a fixed-rate timer), so:
 * - After finishing one item, it immediately processes the next pending item
 * - No overlap even if a single item takes longer than the sleep interval
 */
@Service
public class PostImportAutomationWorker {

    private static final Logger log = LoggerFactory.getLogger(PostImportAutomationWorker.class);

    private final ProductCollectionRepository repo;
    private final PostImportAutomationService service;
    private final PostImportTaskClaimService claimService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    @Value("${post-import.worker.poll-ms:300000}")
    private long pollMs;

    public PostImportAutomationWorker(ProductCollectionRepository repo,
                                     PostImportAutomationService service,
                                     PostImportTaskClaimService claimService) {
        this.repo = repo;
        this.service = service;
        this.claimService = claimService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (running.get()) return;
        running.set(true);
        workerThread = new Thread(this::loop, "post-import-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("PostImportAutomationWorker started (pollMs={}ms)", pollMs);
    }

    @PreDestroy
    public synchronized void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
        log.info("PostImportAutomationWorker stopping");
    }

    private void loop() {
        while (running.get()) {
            try {
                boolean didWork = processOne();
                if (!didWork) {
                    log.debug("PostImportAutomationWorker idle; sleep {}ms", pollMs);
                    sleepQuietly(pollMs);
                }
            } catch (Exception ignored) {
                // avoid tight loop on unexpected errors
                log.warn("PostImportAutomationWorker unexpected error; sleep 5000ms");
                sleepQuietly(5_000);
            }
        }
    }

    /**
     * @return true if a pending item was found and processed
     */
    private boolean processOne() {
        Long id;
        try {
            id = claimService == null ? null : claimService.claimNextPendingId();
        } catch (Exception e) {
            log.warn("PostImportAutomationWorker claim failed: {}", e.getMessage());
            return false;
        }
        if (id == null) {
            return false;
        }
        log.info("PostImportAutomationWorker claimed spuId={}", id);
        // Already claimed (exec_status=1). Run synchronously.
        service.runForSpuClaimedSync(id);
        return true;
    }

    private void sleepQuietly(long ms) {
        try {
            if (ms <= 0) ms = 300000;
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // exit sooner on shutdown
        }
    }
}
