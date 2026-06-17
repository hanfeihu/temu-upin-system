package com.tminos.productscene.service;

import com.tminos.productscene.dto.Alibaba1688SelectionAiReportWorkerDTO;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class Alibaba1688SelectionAiReportWorkerStatus {

    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(30);
    private static final int MAX_RECENT_TASK_EVENTS = 18;

    private final AtomicReference<Boolean> running = new AtomicReference<>(false);
    private final AtomicReference<Integer> configuredThreadCount = new AtomicReference<>(1);
    private final AtomicReference<Long> pollMs = new AtomicReference<>(300_000L);
    private final AtomicReference<LocalDateTime> startedAt = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> stoppedAt = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> lastScanAt = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> lastWorkAt = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> lastErrorAt = new AtomicReference<>(null);
    private final AtomicReference<String> lastError = new AtomicReference<>(null);

    private final AtomicReference<Integer> pendingCount = new AtomicReference<>(0);
    private final AtomicReference<Integer> dbRunningCount = new AtomicReference<>(0);
    private final AtomicReference<Integer> readyCount = new AtomicReference<>(0);
    private final AtomicReference<Integer> failedCount = new AtomicReference<>(0);

    private final Map<Integer, ThreadRuntime> runtimeMap = new ConcurrentHashMap<>();
    private final Deque<WorkerTaskEvent> recentTaskEvents = new LinkedList<>();
    private final AtomicLong taskEventSequence = new AtomicLong(0);

    public void onStart(int threadCount, long pollMs) {
        this.running.set(true);
        this.configuredThreadCount.set(Math.max(1, threadCount));
        this.pollMs.set(Math.max(1L, pollMs));
        this.startedAt.set(LocalDateTime.now());
        this.stoppedAt.set(null);
        this.lastError.set(null);
        this.lastErrorAt.set(null);
    }

    public void onStop() {
        this.running.set(false);
        this.stoppedAt.set(LocalDateTime.now());
    }

    public void onScan(int pendingCount, int dbRunningCount, int readyCount, int failedCount) {
        this.lastScanAt.set(LocalDateTime.now());
        this.pendingCount.set(Math.max(pendingCount, 0));
        this.dbRunningCount.set(Math.max(dbRunningCount, 0));
        this.readyCount.set(Math.max(readyCount, 0));
        this.failedCount.set(Math.max(failedCount, 0));
    }

    public void onConfig(int threadCount, long pollMs) {
        this.configuredThreadCount.set(Math.max(1, threadCount));
        this.pollMs.set(Math.max(1L, pollMs));
    }

    public void registerThread(int workerIndex, String threadName) {
        runtimeMap.compute(workerIndex, (key, current) -> {
            ThreadRuntime runtime = current == null ? new ThreadRuntime(workerIndex) : current;
            runtime.threadName = threadName;
            runtime.alive = true;
            runtime.working = false;
            runtime.lastHeartbeatAt = LocalDateTime.now();
            return runtime;
        });
    }

    public void unregisterThread(int workerIndex) {
        runtimeMap.computeIfPresent(workerIndex, (key, runtime) -> {
            runtime.markStopped();
            return runtime;
        });
    }

    public void heartbeat(int workerIndex) {
        runtimeMap.computeIfPresent(workerIndex, (key, runtime) -> {
            runtime.lastHeartbeatAt = LocalDateTime.now();
            return runtime;
        });
    }

    public void onTaskStart(int workerIndex, String threadName, Long poolId, String offerId) {
        LocalDateTime now = LocalDateTime.now();
        runtimeMap.compute(workerIndex, (key, current) -> {
            ThreadRuntime runtime = current == null ? new ThreadRuntime(workerIndex) : current;
            runtime.threadName = threadName;
            runtime.alive = true;
            runtime.working = true;
            runtime.currentPoolId = poolId;
            runtime.currentOfferId = offerId;
            runtime.currentTaskStartedAt = now;
            runtime.lastHeartbeatAt = now;
            return runtime;
        });
        this.lastWorkAt.set(now);
        appendTaskEvent(WorkerTaskEvent.started(
                taskEventSequence.incrementAndGet(),
                workerIndex,
                threadName,
                poolId,
                offerId,
                now
        ));
    }

    public void onTaskSuccess(int workerIndex) {
        LocalDateTime finishedAt = LocalDateTime.now();
        AtomicReference<WorkerTaskEvent> finishedEvent = new AtomicReference<>(null);
        runtimeMap.computeIfPresent(workerIndex, (key, runtime) -> {
            runtime.successCount.incrementAndGet();
            finishedEvent.set(runtime.buildFinishedEvent(taskEventSequence.incrementAndGet(), "SUCCESS", finishedAt, null));
            runtime.finishCurrentTask(finishedAt);
            return runtime;
        });
        appendTaskEvent(finishedEvent.get());
        this.lastWorkAt.set(finishedAt);
    }

    public void onTaskFailure(int workerIndex, Throwable error) {
        LocalDateTime finishedAt = LocalDateTime.now();
        AtomicReference<WorkerTaskEvent> finishedEvent = new AtomicReference<>(null);
        runtimeMap.computeIfPresent(workerIndex, (key, runtime) -> {
            runtime.failureCount.incrementAndGet();
            runtime.lastError = error == null ? null : error.getMessage();
            finishedEvent.set(runtime.buildFinishedEvent(taskEventSequence.incrementAndGet(), "FAILED", finishedAt, runtime.lastError));
            runtime.finishCurrentTask(finishedAt);
            return runtime;
        });
        appendTaskEvent(finishedEvent.get());
        onError(error);
    }

    public void onIdle(int workerIndex) {
        runtimeMap.computeIfPresent(workerIndex, (key, runtime) -> {
            runtime.working = false;
            runtime.currentPoolId = null;
            runtime.currentOfferId = null;
            runtime.currentTaskStartedAt = null;
            runtime.lastHeartbeatAt = LocalDateTime.now();
            return runtime;
        });
    }

    public void onError(Throwable error) {
        this.lastErrorAt.set(LocalDateTime.now());
        this.lastError.set(error == null ? null : error.getClass().getSimpleName() + ": " + error.getMessage());
    }

    public Alibaba1688SelectionAiReportWorkerDTO.StatusView snapshot() {
        Alibaba1688SelectionAiReportWorkerDTO.StatusView view = new Alibaba1688SelectionAiReportWorkerDTO.StatusView();
        view.setRunning(running.get());
        view.setConfiguredThreadCount(configuredThreadCount.get());
        view.setPollMs(pollMs.get());
        view.setPendingCount(pendingCount.get());
        view.setRunningCount(dbRunningCount.get());
        view.setReadyCount(readyCount.get());
        view.setFailedCount(failedCount.get());
        view.setStartedAt(startedAt.get());
        view.setStoppedAt(stoppedAt.get());
        view.setLastScanAt(lastScanAt.get());
        view.setLastWorkAt(lastWorkAt.get());
        view.setLastErrorAt(lastErrorAt.get());
        view.setLastError(lastError.get());

        List<Alibaba1688SelectionAiReportWorkerDTO.ThreadSnapshot> threads = runtimeMap.values().stream()
                .sorted(Comparator.comparingInt(runtime -> runtime.workerIndex))
                .map(ThreadRuntime::toSnapshot)
                .toList();
        view.setThreads(threads);
        int activeThreadCount = (int) threads.stream().filter(item -> Boolean.TRUE.equals(item.getWorking())).count();
        view.setActiveThreadCount(activeThreadCount);
        view.setAliveThreadCount((int) threads.stream().filter(item -> Boolean.TRUE.equals(item.getAlive())).count());
        // UI "处理中数量" represents real workers currently busy, not stale DB RUNNING rows.
        view.setRunningCount(activeThreadCount);
        view.setHasStuckThreads(
                threads.stream().anyMatch(item -> Boolean.TRUE.equals(item.getStuck()) || Boolean.FALSE.equals(item.getAlive()))
                        || (Boolean.TRUE.equals(view.getRunning()) && view.getAliveThreadCount() < Math.max(view.getConfiguredThreadCount(), 0))
        );
        view.setRecentTaskEvents(recentTaskEventsSnapshot());
        return view;
    }

    private void appendTaskEvent(WorkerTaskEvent event) {
        if (event == null) {
            return;
        }
        synchronized (recentTaskEvents) {
            recentTaskEvents.addFirst(event);
            while (recentTaskEvents.size() > MAX_RECENT_TASK_EVENTS) {
                recentTaskEvents.removeLast();
            }
        }
    }

    private List<Alibaba1688SelectionAiReportWorkerDTO.TaskEvent> recentTaskEventsSnapshot() {
        synchronized (recentTaskEvents) {
            return recentTaskEvents.stream()
                    .map(WorkerTaskEvent::toSnapshot)
                    .toList();
        }
    }

    private static class ThreadRuntime {
        private final int workerIndex;
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);

        private volatile String threadName;
        private volatile boolean alive;
        private volatile boolean working;
        private volatile Long currentPoolId;
        private volatile String currentOfferId;
        private volatile LocalDateTime lastHeartbeatAt;
        private volatile LocalDateTime currentTaskStartedAt;
        private volatile LocalDateTime lastFinishedAt;
        private volatile String lastError;

        private ThreadRuntime(int workerIndex) {
            this.workerIndex = workerIndex;
        }

        private WorkerTaskEvent buildFinishedEvent(long sequence, String status, LocalDateTime finishedAt, String error) {
            LocalDateTime startedAt = this.currentTaskStartedAt;
            Long durationMs = startedAt == null || finishedAt == null ? null : Duration.between(startedAt, finishedAt).toMillis();
            return WorkerTaskEvent.finished(
                    sequence,
                    workerIndex,
                    threadName,
                    currentPoolId,
                    currentOfferId,
                    status,
                    startedAt,
                    finishedAt,
                    durationMs,
                    error
            );
        }

        private void finishCurrentTask(LocalDateTime finishedAt) {
            this.working = false;
            this.currentPoolId = null;
            this.currentOfferId = null;
            this.currentTaskStartedAt = null;
            this.lastFinishedAt = finishedAt == null ? LocalDateTime.now() : finishedAt;
            this.lastHeartbeatAt = this.lastFinishedAt;
        }

        private void markStopped() {
            this.alive = false;
            this.working = false;
            this.currentPoolId = null;
            this.currentOfferId = null;
            this.currentTaskStartedAt = null;
            this.lastHeartbeatAt = LocalDateTime.now();
        }

        private Alibaba1688SelectionAiReportWorkerDTO.ThreadSnapshot toSnapshot() {
            Alibaba1688SelectionAiReportWorkerDTO.ThreadSnapshot snapshot = new Alibaba1688SelectionAiReportWorkerDTO.ThreadSnapshot();
            snapshot.setWorkerIndex(workerIndex);
            snapshot.setThreadName(threadName);
            snapshot.setAlive(alive);
            snapshot.setWorking(working);
            snapshot.setCurrentPoolId(currentPoolId);
            snapshot.setCurrentOfferId(currentOfferId);
            snapshot.setLastHeartbeatAt(lastHeartbeatAt);
            snapshot.setCurrentTaskStartedAt(currentTaskStartedAt);
            snapshot.setLastFinishedAt(lastFinishedAt);
            snapshot.setSuccessCount(successCount.get());
            snapshot.setFailureCount(failureCount.get());
            snapshot.setLastError(lastError);
            boolean stuck = working
                    && currentTaskStartedAt != null
                    && Duration.between(currentTaskStartedAt, LocalDateTime.now()).compareTo(STUCK_THRESHOLD) > 0;
            snapshot.setStuck(stuck);
            return snapshot;
        }
    }

    private record WorkerTaskEvent(
            long sequence,
            int workerIndex,
            String threadName,
            Long poolId,
            String offerId,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Long durationMs,
            String error
    ) {
        private static WorkerTaskEvent started(
                long sequence,
                int workerIndex,
                String threadName,
                Long poolId,
                String offerId,
                LocalDateTime startedAt
        ) {
            return new WorkerTaskEvent(sequence, workerIndex, threadName, poolId, offerId, "RUNNING", startedAt, null, null, null);
        }

        private static WorkerTaskEvent finished(
                long sequence,
                int workerIndex,
                String threadName,
                Long poolId,
                String offerId,
                String status,
                LocalDateTime startedAt,
                LocalDateTime finishedAt,
                Long durationMs,
                String error
        ) {
            return new WorkerTaskEvent(sequence, workerIndex, threadName, poolId, offerId, status, startedAt, finishedAt, durationMs, error);
        }

        private Alibaba1688SelectionAiReportWorkerDTO.TaskEvent toSnapshot() {
            Alibaba1688SelectionAiReportWorkerDTO.TaskEvent snapshot = new Alibaba1688SelectionAiReportWorkerDTO.TaskEvent();
            snapshot.setSequence(sequence);
            snapshot.setWorkerIndex(workerIndex);
            snapshot.setThreadName(threadName);
            snapshot.setPoolId(poolId);
            snapshot.setOfferId(offerId);
            snapshot.setStatus(status);
            snapshot.setStartedAt(startedAt);
            snapshot.setFinishedAt(finishedAt);
            snapshot.setDurationMs(durationMs);
            snapshot.setError(error);
            return snapshot;
        }
    }
}
