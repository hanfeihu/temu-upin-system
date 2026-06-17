package com.tminos.productscene.service;

import com.tminos.productscene.dto.Alibaba1688SelectionPoolDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class Alibaba1688SelectionDetailImportWorker {

    private static final Logger log = LoggerFactory.getLogger(Alibaba1688SelectionDetailImportWorker.class);

    private final Alibaba1688SelectionPoolService selectionPoolService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${alibaba1688.selection-pool.detail-import-worker.enabled:true}")
    private boolean enabled;

    public Alibaba1688SelectionDetailImportWorker(Alibaba1688SelectionPoolService selectionPoolService) {
        this.selectionPoolService = selectionPoolService;
    }

    @Scheduled(
            fixedDelayString = "${alibaba1688.selection-pool.detail-import-worker.poll-ms:1200000}",
            initialDelayString = "${alibaba1688.selection-pool.detail-import-worker.initial-delay-ms:120000}"
    )
    public void importReadyDetails() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            Alibaba1688SelectionPoolDTO.BatchImportResponse result = selectionPoolService.batchImportFromDetail(
                    Alibaba1688SelectionPoolDTO.BatchImportRequest.builder()
                            .allMatching(true)
                            .note("自动导入详情数据")
                            .build()
            );
            if (result == null || result.getTotal() == null || result.getTotal() <= 0) {
                return;
            }
            log.info(
                    "1688 selection detail import worker finished: total={}, success={}, created={}, refreshed={}, skipped={}, failed={}",
                    result.getTotal(),
                    result.getSuccessCount(),
                    result.getCreatedCount(),
                    result.getRefreshedCount(),
                    result.getSkippedCount(),
                    result.getFailedCount()
            );
        } catch (Throwable e) {
            log.warn("1688 selection detail import worker failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }
}
