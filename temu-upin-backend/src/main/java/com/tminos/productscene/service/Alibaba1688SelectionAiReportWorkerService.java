package com.tminos.productscene.service;

import com.tminos.productscene.dto.Alibaba1688SelectionAiReportWorkerDTO;
import com.tminos.productscene.dto.Alibaba1688SelectionPoolDTO;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class Alibaba1688SelectionAiReportWorkerService {

    private static final Logger log = LoggerFactory.getLogger(Alibaba1688SelectionAiReportWorkerService.class);
    private static final long POLL_MS = Alibaba1688SelectionAiReportWorkerConfigService.DEFAULT_POLL_MS;

    private final Alibaba1688SelectionAiReportWorkerConfigService configService;
    private final Alibaba1688SelectionAiReportTaskClaimService claimService;
    private final Alibaba1688SelectionPoolService selectionPoolService;
    private final Alibaba1688SelectionAiReportWorkerStatus status;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Thread> workerThreads = new CopyOnWriteArrayList<>();

    public Alibaba1688SelectionAiReportWorkerService(
            Alibaba1688SelectionAiReportWorkerConfigService configService,
            Alibaba1688SelectionAiReportTaskClaimService claimService,
            Alibaba1688SelectionPoolService selectionPoolService,
            Alibaba1688SelectionAiReportWorkerStatus status
    ) {
        this.configService = configService;
        this.claimService = claimService;
        this.selectionPoolService = selectionPoolService;
        this.status = status;
    }

    public synchronized Alibaba1688SelectionAiReportWorkerDTO.StatusView start() {
        return start(true);
    }

    public synchronized Alibaba1688SelectionAiReportWorkerDTO.StatusView start(boolean allowEmpty) {
        refreshStats();
        if (running.get()) {
            return status.snapshot();
        }
        if (hasAliveWorkerThreads()) {
            IllegalStateException error = new IllegalStateException("旧 AI 报告线程仍在结束中，暂不启动新线程，避免超过配置线程数");
            status.onError(error);
            log.warn(error.getMessage());
            return status.snapshot();
        }
        Alibaba1688SelectionAiReportTaskClaimService.TaskStats stats = claimService.stats();
        if (!allowEmpty && stats.pendingCount() <= 0) {
            throw new IllegalArgumentException("当前无待执行的 1688 选品池 AI 报告任务");
        }

        int threadCount = configService.currentThreadCount();
        running.set(true);
        workerThreads.clear();
        status.onStart(threadCount, POLL_MS);
        for (int i = 1; i <= threadCount; i += 1) {
            final int workerIndex = i;
            Thread thread = new Thread(() -> loop(workerIndex), "alibaba1688-selection-ai-report-worker-" + workerIndex);
            thread.setDaemon(true);
            workerThreads.add(thread);
            thread.start();
        }
        log.info("Alibaba1688SelectionAiReportWorker started (threadCount={}, pollMs={}ms)", threadCount, POLL_MS);
        return status.snapshot();
    }

    public synchronized Alibaba1688SelectionAiReportWorkerDTO.StatusView restartIfRunning() {
        if (!running.get()) {
            refreshStats();
            status.onConfig(configService.currentThreadCount(), POLL_MS);
            return status.snapshot();
        }
        stop();
        if (hasAliveWorkerThreads()) {
            IllegalStateException error = new IllegalStateException("旧 AI 报告线程仍在结束中，暂不启动新线程，避免超过配置线程数");
            status.onError(error);
            log.warn(error.getMessage());
            return status.snapshot();
        }
        return start(true);
    }

    public synchronized Alibaba1688SelectionAiReportWorkerDTO.StatusView stop() {
        running.set(false);
        for (Thread thread : workerThreads) {
            if (thread != null) {
                thread.interrupt();
            }
        }
        waitForWorkerThreadsToExit(5_000L);
        workerThreads.removeIf(thread -> thread == null || !thread.isAlive());
        status.onStop();
        log.info("Alibaba1688SelectionAiReportWorker stopping (aliveThreads={})", workerThreads.size());
        refreshStats();
        return status.snapshot();
    }

    public Alibaba1688SelectionAiReportWorkerDTO.StatusView status() {
        status.onConfig(configService.currentThreadCount(), POLL_MS);
        refreshStats();
        return status.snapshot();
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoStartIfEnabled() {
        status.onConfig(configService.currentThreadCount(), POLL_MS);
        refreshStats();
        if (configService.currentEnabled()) {
            try {
                start(true);
            } catch (Exception e) {
                status.onError(e);
                log.warn("Alibaba1688SelectionAiReportWorker auto start failed: {}", e.getMessage());
            }
        }
    }

    private void loop(int workerIndex) {
        String threadName = Thread.currentThread().getName();
        status.registerThread(workerIndex, threadName);
        try {
            while (running.get()) {
                try {
                    status.heartbeat(workerIndex);
                    refreshStats();
                    Alibaba1688SelectionAiReportTaskClaimService.ClaimedTask task = claimService.claimNextPending();
                    if (task == null || task.poolId() == null) {
                        status.onIdle(workerIndex);
                        sleepQuietly(POLL_MS);
                        continue;
                    }
                    processTask(workerIndex, threadName, task);
                } catch (Throwable e) {
                    status.onTaskFailure(workerIndex, e);
                    log.warn("Alibaba1688SelectionAiReportWorker unexpected error workerIndex={}: {}", workerIndex, e.getMessage());
                    sleepQuietly(5_000L);
                }
            }
        } finally {
            status.unregisterThread(workerIndex);
        }
    }

    private void processTask(
            int workerIndex,
            String threadName,
            Alibaba1688SelectionAiReportTaskClaimService.ClaimedTask task
    ) {
        status.onTaskStart(workerIndex, threadName, task.poolId(), task.offerId());
        try {
            Alibaba1688SelectionPoolDTO.SaveReportRequest request = Alibaba1688SelectionPoolDTO.SaveReportRequest.builder()
                    .reportType("AI_SELECTION")
                    .sourceType("AI")
                    .status("READY")
                    .build();
            selectionPoolService.saveReport(task.poolId(), request);
            status.onTaskSuccess(workerIndex);
        } catch (Throwable e) {
            claimService.markFailed(task.poolId(), e.getMessage());
            status.onTaskFailure(workerIndex, e);
            log.warn("1688 selection AI report task failed poolId={}, offerId={}, error={}", task.poolId(), task.offerId(), e.getMessage());
        } finally {
            refreshStats();
        }
    }

    private void refreshStats() {
        try {
            Alibaba1688SelectionAiReportTaskClaimService.TaskStats stats = claimService.stats();
            status.onScan(stats.pendingCount(), stats.runningCount(), stats.readyCount(), stats.failedCount());
        } catch (Exception e) {
            status.onError(e);
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(Math.max(1L, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean hasAliveWorkerThreads() {
        return workerThreads.stream().anyMatch(thread -> thread != null && thread.isAlive());
    }

    private void waitForWorkerThreadsToExit(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        for (Thread thread : workerThreads) {
            if (thread == null || !thread.isAlive() || thread == Thread.currentThread()) {
                continue;
            }
            long remainMs = deadline - System.currentTimeMillis();
            if (remainMs <= 0L) {
                break;
            }
            try {
                thread.join(remainMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
