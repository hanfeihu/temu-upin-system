package com.tminos.productscene.service;

import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.repository.ProductCollectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Global auto-publish worker:
 * - Polls DB for candidate items
 * - Claims one row safely (FOR UPDATE SKIP LOCKED)
 * - Re-checks all eligibility rules in Java
 * - Triggers the same TemuPublishService.publish(spuId) as the UI publish button
 */
@Service
public class TemuAutoPublishWorker {

    private static final Logger log = LoggerFactory.getLogger(TemuAutoPublishWorker.class);

    private final ProductCollectionRepository productRepo;
    private final TemuAutoPublishTaskClaimService claimService;
    private final TemuAutoPublishEligibilityService eligibilityService;
    private final TemuPublishService publishService;
    private final TemuAutoPublishLogService autoPublishLogService;
    private final TemuAutoPublishWorkerStatus status;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    @Value("${temu.auto-publish.worker.enabled:false}")
    private boolean enabled;

    @Value("${temu.auto-publish.worker.poll-ms:300000}")
    private long pollMs;

    public TemuAutoPublishWorker(ProductCollectionRepository productRepo,
                                TemuAutoPublishTaskClaimService claimService,
                                TemuAutoPublishEligibilityService eligibilityService,
                                TemuPublishService publishService,
                                TemuAutoPublishLogService autoPublishLogService,
                                TemuAutoPublishWorkerStatus status) {
        this.productRepo = productRepo;
        this.claimService = claimService;
        this.eligibilityService = eligibilityService;
        this.publishService = publishService;
        this.autoPublishLogService = autoPublishLogService;
        this.status = status;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!enabled) {
            log.info("TemuAutoPublishWorker disabled (temu.auto-publish.worker.enabled=false)");
            try {
                if (status != null) status.onDisabled(pollMs);
            } catch (Exception ignored) {
            }
            return;
        }
        if (running.get()) return;
        running.set(true);
        workerThread = new Thread(this::loop, "temu-auto-publish-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("TemuAutoPublishWorker started (pollMs={}ms)", pollMs);
        try {
            if (status != null) status.onStart(pollMs, workerThread.getName());
        } catch (Exception ignored) {
        }
    }

    @PreDestroy
    public synchronized void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
        log.info("TemuAutoPublishWorker stopping");
        try {
            if (status != null) status.onStop();
        } catch (Exception ignored) {
        }
    }

    private void loop() {
        while (running.get()) {
            try {
                try {
                    if (status != null) status.tick();
                } catch (Exception ignored) {
                }
                boolean didWork = processOne();
                if (!didWork) {
                    try {
                        if (status != null) status.onIdle();
                    } catch (Exception ignored) {
                    }
                    sleepQuietly(pollMs);
                }
            } catch (Exception e) {
                log.warn("TemuAutoPublishWorker unexpected error; sleep 5000ms: {}", e.getMessage());
                try {
                    if (status != null) status.onError(e);
                } catch (Exception ignored) {
                }
                sleepQuietly(5_000);
            }
        }
    }

    private boolean processOne() {
        TemuAutoPublishTaskClaimService.ClaimedCandidate c;
        try {
            c = claimService == null ? null : claimService.claimNextCandidate();
        } catch (Exception e) {
            log.warn("TemuAutoPublishWorker claim failed: {}", e.getMessage());
            return false;
        }
        if (c == null || c.spuId() == null) {
            // Optional: you can add a heartbeat log here if needed, but it will be too noisy by default.
            return false;
        }

        Long spuId = c.spuId();
        Integer preClaimCollectionStatus = c.preClaimCollectionStatus();

        Long autoRunId = null;
        try {
            com.tminos.productscene.entity.TemuAutoPublishRun rr = (autoPublishLogService == null ? null : autoPublishLogService.startRun(spuId));
            autoRunId = rr == null ? null : rr.getId();
            if (autoPublishLogService != null) {
                autoPublishLogService.info(autoRunId, "START", "auto publish run started");
                autoPublishLogService.data(autoRunId, "CLAIM", "claimed candidate", java.util.Map.of("spuId", spuId));
                autoPublishLogService.data(autoRunId, "CLAIM", "pre-claim publish status snapshot", java.util.Map.of(
                        "preClaimCollectionStatus", preClaimCollectionStatus
                ));
            }
            try {
                if (status != null) status.onClaim(spuId, autoRunId);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }

        ProductCollection pc = productRepo.findById(spuId).orElse(null);
        if (pc == null) {
            try {
                if (autoPublishLogService != null) {
                    autoPublishLogService.warn(autoRunId, "LOAD", "product not found; skipping");
                    autoPublishLogService.finishSkipped(autoRunId, null, "product not found");
                }
            } catch (Exception ignored) {
            }
            return false;
        }

        try {
            if (autoPublishLogService != null) {
                autoPublishLogService.data(autoRunId, "LOAD", "loaded product snapshot", java.util.Map.of(
                        "spuId", pc.getId(),
                        "productId", pc.getProductId(),
                        "productName", pc.getProductName(),
                        "execStatus", pc.getExecStatus(),
                        "ocrStatus", pc.getOcrStatus(),
                        "temuCatid", pc.getTemuCatid(),
                        "hasTemuAttributes", (pc.getTemuAttributes() != null && !pc.getTemuAttributes().isBlank())
                ));
            }
        } catch (Exception ignored) {
        }

        TemuAutoPublishEligibilityService.EligibilityResult r = eligibilityService.check(pc, preClaimCollectionStatus);
        if (!r.eligible()) {
            // We already set collection_status=1 when claiming; revert to "未发布" so manual/auto can retry.
            try {
                pc.setCollectionStatus(0);
                productRepo.save(pc);
            } catch (Exception ignored) {
            }

            try {
                if (autoPublishLogService != null) {
                    autoPublishLogService.data(autoRunId, "ELIGIBILITY", "not eligible", java.util.Map.of(
                            "eligible", false,
                            "reasons", r.reasons(),
                            "checks", r.checks(),
                            "debug", r.debug()
                    ));
                    String eligibilityJson = autoPublishLogService.toJsonSafe(java.util.Map.of(
                            "eligible", false,
                            "reasons", r.reasons(),
                            "checks", r.checks(),
                            "debug", r.debug()
                    ));
                    autoPublishLogService.finishSkipped(autoRunId, eligibilityJson, autoPublishLogService.buildEligibilityDisplaySummary(r));
                    autoPublishLogService.info(autoRunId, "END", "auto publish run finished (skipped)");
                }
            } catch (Exception ignored) {
            }
            log.debug("TemuAutoPublishWorker skip spuId={} reasons={} debug={}", spuId, r.reasons(), r.debug());
            try {
                if (status != null) status.onWorkDone();
            } catch (Exception ignored) {
            }
            return true;
        }

        log.info("TemuAutoPublishWorker publishing spuId={} debug={}", spuId, r.debug());
        try {
            if (autoPublishLogService != null) {
                autoPublishLogService.data(autoRunId, "ELIGIBILITY", "eligible", java.util.Map.of(
                        "eligible", true,
                        "checks", r.checks(),
                        "debug", r.debug()
                ));
            }
        } catch (Exception ignored) {
        }

        String eligibilityJson = null;
        try {
            if (autoPublishLogService != null) {
                eligibilityJson = autoPublishLogService.toJsonSafe(java.util.Map.of(
                        "eligible", true,
                        "checks", r.checks(),
                        "debug", r.debug()
                ));
            }
        } catch (Exception ignored) {
        }

        Long publishRunId = null;
        try {
            var resp = publishService.publish(spuId);
            publishRunId = resp == null ? null : resp.getRunId();
            boolean success = resp != null && Boolean.TRUE.equals(resp.getSuccess());
            try {
                if (autoPublishLogService != null) {
                    autoPublishLogService.data(autoRunId, "PUBLISH", "publish completed", java.util.Map.of(
                            "success", success,
                            "publishRunId", publishRunId,
                            "message", (resp == null ? null : resp.getMessage()),
                            "goodsId", (resp == null ? null : resp.getGoodsId()),
                            "warnings", (resp == null ? null : resp.getWarnings())
                    ));
                }
            } catch (Exception ignored) {
            }

            if (autoPublishLogService != null) {
                if (success) {
                    autoPublishLogService.finishSucceeded(autoRunId, publishRunId, eligibilityJson, "publish ok (publishRunId=" + publishRunId + ")");
                    autoPublishLogService.info(autoRunId, "END", "auto publish run finished (succeeded)");
                } else {
                    String err = resp == null ? "publish response null" : (resp.getMessage() == null ? "publish failed" : resp.getMessage());
                    autoPublishLogService.finishFailed(autoRunId, publishRunId, eligibilityJson, err);
                    autoPublishLogService.info(autoRunId, "END", "auto publish run finished (failed)");
                }
            }
        } catch (Exception e) {
            log.warn("TemuAutoPublishWorker publish threw spuId={}: {}", spuId, e.getMessage());
            try {
                if (autoPublishLogService != null) {
                    autoPublishLogService.error(autoRunId, "PUBLISH", "publish threw", java.util.Map.of(
                            "error", e.getMessage(),
                            "class", e.getClass().getName(),
                            "publishRunId", publishRunId
                    ));
                    autoPublishLogService.finishFailed(autoRunId, publishRunId, eligibilityJson, e.getMessage());
                    autoPublishLogService.info(autoRunId, "END", "auto publish run finished (exception)");
                }
            } catch (Exception ignored) {
            }
        }
        try {
            if (status != null) status.onWorkDone();
        } catch (Exception ignored) {
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
