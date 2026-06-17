package com.tminos.productscene.service;

import com.tminos.productscene.dto.Alibaba1688SelectionAutoPushDTO;
import com.tminos.productscene.dto.Alibaba1688SelectionPoolDTO;
import com.tminos.productscene.entity.Alibaba1688SelectionAutoPushLog;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class Alibaba1688SelectionAutoPushWorkerService {

    private static final Logger log = LoggerFactory.getLogger(Alibaba1688SelectionAutoPushWorkerService.class);

    private final Alibaba1688SelectionAutoPushConfigService configService;
    private final Alibaba1688SelectionAutoPushLogService logService;
    private final Alibaba1688SelectionPoolService selectionPoolService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong skippedCount = new AtomicLong(0);

    private volatile Thread workerThread;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime stoppedAt;
    private volatile LocalDateTime lastScanAt;
    private volatile LocalDateTime lastWorkAt;
    private volatile LocalDateTime lastErrorAt;
    private volatile String lastError;
    private volatile Long lastPoolId;
    private volatile String lastOfferId;

    public Alibaba1688SelectionAutoPushWorkerService(
            Alibaba1688SelectionAutoPushConfigService configService,
            Alibaba1688SelectionAutoPushLogService logService,
            Alibaba1688SelectionPoolService selectionPoolService
    ) {
        this.configService = configService;
        this.logService = logService;
        this.selectionPoolService = selectionPoolService;
    }

    public synchronized Alibaba1688SelectionAutoPushDTO.StatusView start() {
        if (running.get()) {
            return status();
        }
        if (workerThread != null && workerThread.isAlive()) {
            return status();
        }
        running.set(true);
        startedAt = LocalDateTime.now();
        stoppedAt = null;
        workerThread = new Thread(this::loop, "alibaba1688-selection-auto-push-worker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("Alibaba1688SelectionAutoPushWorker started");
        return status();
    }

    public synchronized Alibaba1688SelectionAutoPushDTO.StatusView stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
        stoppedAt = LocalDateTime.now();
        log.info("Alibaba1688SelectionAutoPushWorker stopping");
        return status();
    }

    public Alibaba1688SelectionAutoPushDTO.StatusView restartIfRunning() {
        if (!running.get()) {
            return status();
        }
        stop();
        return start();
    }

    public Alibaba1688SelectionAutoPushDTO.StatusView status() {
        Alibaba1688SelectionAutoPushDTO.ConfigView config = configService.current();
        Alibaba1688SelectionAutoPushDTO.StatusView view = new Alibaba1688SelectionAutoPushDTO.StatusView();
        view.setRunning(running.get());
        view.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        view.setBatchSize(config.getBatchSize());
        view.setPollMs(config.getPollMs());
        view.setPendingCount(selectionPoolService.findAutoPushCandidates(100).size());
        view.setSuccessCount(successCount.get());
        view.setFailureCount(failureCount.get());
        view.setSkippedCount(skippedCount.get());
        view.setLastPoolId(lastPoolId);
        view.setLastOfferId(lastOfferId);
        view.setStartedAt(startedAt);
        view.setStoppedAt(stoppedAt);
        view.setLastScanAt(lastScanAt);
        view.setLastWorkAt(lastWorkAt);
        view.setLastErrorAt(lastErrorAt);
        view.setLastError(lastError);
        return view;
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoStartIfEnabled() {
        if (configService.currentEnabled()) {
            try {
                start();
            } catch (Exception e) {
                onError(e);
            }
        }
    }

    private void loop() {
        while (running.get()) {
            try {
                runOnce();
                sleep(configService.currentPollMs());
            } catch (Throwable e) {
                onError(e);
                sleep(10_000L);
            }
        }
    }

    private void runOnce() {
        lastScanAt = LocalDateTime.now();
        Alibaba1688SelectionAutoPushDTO.ConfigView config = configService.current();
        List<String> targetShopIds = config.getTargetShopIds();
        List<String> targetShopNames = config.getTargetShopNames();
        if (targetShopIds == null || targetShopIds.isEmpty()) {
            skippedCount.incrementAndGet();
            lastError = "自动推送未配置店铺";
            lastErrorAt = LocalDateTime.now();
            return;
        }

        List<Alibaba1688SelectionPoolDTO.ListItem> candidates = selectionPoolService.findAutoPushCandidates(config.getBatchSize());
        if (candidates.isEmpty()) {
            return;
        }
        for (Alibaba1688SelectionPoolDTO.ListItem candidate : candidates) {
            if (!running.get()) {
                break;
            }
            pushOne(candidate, targetShopIds, targetShopNames, Boolean.TRUE.equals(config.getForceCreate()));
        }
    }

    private void pushOne(
            Alibaba1688SelectionPoolDTO.ListItem candidate,
            List<String> targetShopIds,
            List<String> targetShopNames,
            boolean forceCreate
    ) {
        LocalDateTime started = LocalDateTime.now();
        Long poolId = candidate == null ? null : candidate.getId();
        String offerId = candidate == null ? null : candidate.getOfferId();
        lastPoolId = poolId;
        lastOfferId = offerId;
        try {
            Alibaba1688SelectionPoolDTO.PushToProductCollectionRequest request =
                    Alibaba1688SelectionPoolDTO.PushToProductCollectionRequest.builder()
                            .targetShopIds(targetShopIds)
                            .forceCreate(forceCreate)
                            .build();
            Alibaba1688SelectionPoolDTO.PushToProductCollectionResponse result =
                    selectionPoolService.pushToProductCollection(poolId, request);
            Alibaba1688SelectionAutoPushLog entry = logService.build(
                    poolId,
                    offerId,
                    "SUCCESS",
                    targetShopIds,
                    targetShopNames,
                    result == null ? "推送成功" : "推送成功，商品库ID=" + result.getProductCollectionId(),
                    null,
                    started,
                    LocalDateTime.now()
            );
            entry.setProductCollectionId(result == null ? null : result.getProductCollectionId());
            logService.save(entry);
            successCount.incrementAndGet();
            lastWorkAt = LocalDateTime.now();
            lastError = null;
        } catch (Throwable e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            selectionPoolService.markPushFailed(poolId, message);
            logService.save(logService.build(
                    poolId,
                    offerId,
                    "FAILED",
                    targetShopIds,
                    targetShopNames,
                    null,
                    message,
                    started,
                    LocalDateTime.now()
            ));
            failureCount.incrementAndGet();
            onError(e);
        }
    }

    private void onError(Throwable e) {
        lastError = e == null || e.getMessage() == null ? "未知错误" : e.getMessage();
        lastErrorAt = LocalDateTime.now();
        log.warn("Alibaba1688SelectionAutoPushWorker error: {}", lastError);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(Math.max(ms, 1L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
