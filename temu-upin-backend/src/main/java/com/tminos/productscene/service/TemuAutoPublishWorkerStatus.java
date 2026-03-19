package com.tminos.productscene.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TemuAutoPublishWorkerStatus {

    private final AtomicReference<Boolean> enabled = new AtomicReference<>(null);
    private final AtomicReference<Boolean> running = new AtomicReference<>(false);
    private final AtomicLong pollMs = new AtomicLong(0);
    private final AtomicReference<String> threadName = new AtomicReference<>(null);

    private final AtomicReference<LocalDateTime> startedAt = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> lastTickAt = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> lastIdleAt = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> lastClaimAt = new AtomicReference<>(null);
    private final AtomicReference<Long> lastClaimedSpuId = new AtomicReference<>(null);
    private final AtomicReference<Long> lastAutoRunId = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> lastWorkAt = new AtomicReference<>(null);

    private final AtomicLong loopCount = new AtomicLong(0);
    private final AtomicLong idleCount = new AtomicLong(0);
    private final AtomicLong workCount = new AtomicLong(0);

    private final AtomicReference<LocalDateTime> lastErrorAt = new AtomicReference<>(null);
    private final AtomicReference<String> lastError = new AtomicReference<>(null);

    public void onDisabled(long pollMs) {
        this.enabled.set(false);
        this.pollMs.set(pollMs);
        this.running.set(false);
    }

    public void onStart(long pollMs, String threadName) {
        this.enabled.set(true);
        this.running.set(true);
        this.pollMs.set(pollMs);
        this.threadName.set(threadName);
        this.startedAt.set(LocalDateTime.now());
        tick();
    }

    public void onStop() {
        this.running.set(false);
        tick();
    }

    public void tick() {
        this.loopCount.incrementAndGet();
        this.lastTickAt.set(LocalDateTime.now());
    }

    public void onIdle() {
        this.idleCount.incrementAndGet();
        this.lastIdleAt.set(LocalDateTime.now());
    }

    public void onClaim(Long spuId, Long autoRunId) {
        this.lastClaimAt.set(LocalDateTime.now());
        this.lastClaimedSpuId.set(spuId);
        this.lastAutoRunId.set(autoRunId);
    }

    public void onWorkDone() {
        this.workCount.incrementAndGet();
        this.lastWorkAt.set(LocalDateTime.now());
    }

    public void onError(Throwable t) {
        this.lastErrorAt.set(LocalDateTime.now());
        this.lastError.set(t == null ? null : (t.getClass().getName() + ": " + t.getMessage()));
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled.get());
        out.put("running", running.get());
        out.put("pollMs", pollMs.get());
        out.put("threadName", threadName.get());
        out.put("startedAt", startedAt.get());
        out.put("lastTickAt", lastTickAt.get());
        out.put("lastIdleAt", lastIdleAt.get());
        out.put("lastClaimAt", lastClaimAt.get());
        out.put("lastClaimedSpuId", lastClaimedSpuId.get());
        out.put("lastAutoRunId", lastAutoRunId.get());
        out.put("lastWorkAt", lastWorkAt.get());
        out.put("loopCount", loopCount.get());
        out.put("idleCount", idleCount.get());
        out.put("workCount", workCount.get());
        out.put("lastErrorAt", lastErrorAt.get());
        out.put("lastError", lastError.get());
        return out;
    }
}
