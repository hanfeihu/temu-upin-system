package com.tminos.productscene.sync.service;

import com.tminos.productscene.sync.repository.TemuGoodsRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class TemuGoodsRepairJobService {

    private static final long COMPLETION_POLL_SECONDS = 5L;

    private final TemuGoodsRepository goodsRepository;
    private final TemuSyncConfigService syncConfigService;
    private final TemuGoodsRepairTransactionalService goodsRepairTransactionalService;
    private final Executor syncTaskExecutor;
    private final ExecutorService syncWorkerPool;
    private final ConcurrentMap<String, RepairJobStatus> jobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> latestJobByShop = new ConcurrentHashMap<>();

    public TemuGoodsRepairJobService(TemuGoodsRepository goodsRepository,
                                     TemuSyncConfigService syncConfigService,
                                     TemuGoodsRepairTransactionalService goodsRepairTransactionalService,
                                     @Qualifier("syncTaskExecutor") Executor syncTaskExecutor,
                                     @Qualifier("syncWorkerPool") ExecutorService syncWorkerPool) {
        this.goodsRepository = goodsRepository;
        this.syncConfigService = syncConfigService;
        this.goodsRepairTransactionalService = goodsRepairTransactionalService;
        this.syncTaskExecutor = syncTaskExecutor;
        this.syncWorkerPool = syncWorkerPool;
    }

    public Map<String, Object> startRepair(String shopId) {
        String latestJobId = latestJobByShop.get(shopId);
        if (latestJobId != null) {
            RepairJobStatus existing = jobs.get(latestJobId);
            if (existing != null && existing.isRunning()) {
                return existing.snapshot();
            }
        }

        int pageSize = 200;
        long totalGoods = goodsRepository.countByShopId(shopId);
        int totalPages = totalGoods == 0 ? 0 : (int) Math.ceil((double) totalGoods / pageSize);
        int concurrency = Math.max(1, Math.min(syncConfigService.getIntConfig(shopId, "goods_sync_thread_count", 5), 8));

        RepairJobStatus status = new RepairJobStatus(
                UUID.randomUUID().toString(),
                shopId,
                pageSize,
                totalPages,
                concurrency
        );
        jobs.put(status.jobId, status);
        latestJobByShop.put(shopId, status.jobId);

        syncTaskExecutor.execute(() -> runRepair(status));
        return status.snapshot();
    }

    public Map<String, Object> getStatus(String jobId) {
        RepairJobStatus status = jobs.get(jobId);
        if (status == null) {
            throw new IllegalArgumentException("回填任务不存在: " + jobId);
        }
        return status.snapshot();
    }

    public Map<String, Object> getLatestStatus(String shopId) {
        String jobId = latestJobByShop.get(shopId);
        if (jobId == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("jobId", null);
            empty.put("shopId", shopId);
            empty.put("status", "IDLE");
            return empty;
        }
        return getStatus(jobId);
    }

    public boolean isRepairRunning(String shopId) {
        String latestJobId = latestJobByShop.get(shopId);
        if (latestJobId == null) {
            return false;
        }
        RepairJobStatus status = jobs.get(latestJobId);
        return status != null && status.isRunning();
    }

    private void runRepair(RepairJobStatus status) {
        status.markRunning();
        if (status.totalPages == 0) {
            status.markFinished("SUCCEEDED", "当前店铺没有可回填商品");
            return;
        }

        CompletionService<TemuGoodsRepairTransactionalService.RepairPageResult> completionService =
                new ExecutorCompletionService<>(syncWorkerPool);

        int submitted = 0;
        int completed = 0;
        Map<Future<TemuGoodsRepairTransactionalService.RepairPageResult>, Integer> runningFutures = new ConcurrentHashMap<>();

        while (submitted < status.totalPages || completed < submitted) {
            while (submitted < status.totalPages && (submitted - completed) < status.concurrency) {
                final int currentPage = submitted;
                int displayPage = currentPage + 1;
                Future<TemuGoodsRepairTransactionalService.RepairPageResult> future = completionService.submit(
                        () -> goodsRepairTransactionalService.repairGoodsDetailsPage(status.shopId, currentPage, status.pageSize));
                runningFutures.put(future, displayPage);
                status.markPageSubmitted(displayPage);
                submitted++;
            }

            Future<TemuGoodsRepairTransactionalService.RepairPageResult> completedFuture = null;
            try {
                completedFuture = completionService.poll(COMPLETION_POLL_SECONDS, TimeUnit.SECONDS);
                if (completedFuture == null) {
                    status.markWaitingForRunningPages();
                    continue;
                }

                TemuGoodsRepairTransactionalService.RepairPageResult pageResult = completedFuture.get();
                runningFutures.remove(completedFuture);
                completed++;
                status.acceptPageResult(pageResult);
            } catch (Exception e) {
                completed++;
                Integer failedPage = completedFuture == null ? -1 : runningFutures.remove(completedFuture);
                status.acceptUnexpectedError(failedPage == null ? -1 : failedPage, unwrapErrorMessage(e));
            }
        }

        status.finishByResult();
    }

    private String unwrapErrorMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        while (current.getCause() != null
                && current.getMessage() != null
                && (current.getMessage().contains("Transaction silently rolled back")
                || current.getClass().getName().contains("CompletionException")
                || current.getClass().getName().contains("ExecutionException"))) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.toString();
    }

    private static final class RepairJobStatus {
        private final String jobId;
        private final String shopId;
        private final int pageSize;
        private final int totalPages;
        private final int concurrency;
        private String status;
        private int submittedPages;
        private int completedPages;
        private int scanned;
        private int repaired;
        private int skipped;
        private final List<Map<String, Object>> failedPages;
        private final LinkedHashSet<Integer> runningPages;
        private String message;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime lastProgressAt;

        private RepairJobStatus(String jobId, String shopId, int pageSize, int totalPages, int concurrency) {
            this.jobId = jobId;
            this.shopId = shopId;
            this.pageSize = pageSize;
            this.totalPages = totalPages;
            this.concurrency = concurrency;
            this.status = "PENDING";
            this.failedPages = new ArrayList<>();
            this.runningPages = new LinkedHashSet<>();
            this.message = "回填任务已创建，等待执行";
        }

        private synchronized void markRunning() {
            this.status = "RUNNING";
            this.startedAt = LocalDateTime.now();
            this.lastProgressAt = this.startedAt;
            this.message = "开始回填商品明细";
        }

        private synchronized void markPageSubmitted(int displayPage) {
            this.submittedPages = Math.max(this.submittedPages, displayPage);
            this.runningPages.add(displayPage);
            this.lastProgressAt = LocalDateTime.now();
            this.message = buildRunningMessage("已提交分页 " + displayPage + "/" + this.totalPages);
        }

        private synchronized void acceptPageResult(TemuGoodsRepairTransactionalService.RepairPageResult pageResult) {
            int displayPage = pageResult.page() + 1;
            this.runningPages.remove(displayPage);
            this.completedPages++;
            this.scanned += pageResult.scanned();
            this.repaired += pageResult.repaired();
            this.skipped += pageResult.skipped();
            this.lastProgressAt = LocalDateTime.now();
            if (!pageResult.success()) {
                Map<String, Object> failed = new LinkedHashMap<>();
                failed.put("page", displayPage);
                failed.put("errorMessage", pageResult.errorMessage());
                this.failedPages.add(failed);
            }
            this.message = buildRunningMessage("已完成分页 " + this.completedPages + "/" + this.totalPages);
        }

        private synchronized void acceptUnexpectedError(Integer displayPage, String errorMessage) {
            if (displayPage != null && displayPage > 0) {
                this.runningPages.remove(displayPage);
            }
            this.lastProgressAt = LocalDateTime.now();
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("page", displayPage == null ? -1 : displayPage);
            failed.put("errorMessage", errorMessage);
            this.failedPages.add(failed);
            this.message = errorMessage;
        }

        private synchronized void markWaitingForRunningPages() {
            this.message = buildRunningMessage("等待已提交分页执行完成");
        }

        private synchronized void finishByResult() {
            this.finishedAt = LocalDateTime.now();
            this.runningPages.clear();
            if (this.failedPages.isEmpty()) {
                this.status = "SUCCEEDED";
                this.message = "商品明细回填完成";
            } else {
                this.status = "FAILED";
                this.message = "商品明细回填结束，但存在失败分页";
            }
        }

        private synchronized void markFinished(String finalStatus, String message) {
            this.status = finalStatus;
            this.startedAt = this.startedAt == null ? LocalDateTime.now() : this.startedAt;
            this.finishedAt = LocalDateTime.now();
            this.runningPages.clear();
            this.lastProgressAt = this.finishedAt;
            this.message = message;
        }

        private synchronized boolean isRunning() {
            return "PENDING".equals(this.status) || "RUNNING".equals(this.status);
        }

        private synchronized Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("jobId", this.jobId);
            result.put("shopId", this.shopId);
            result.put("status", this.status);
            result.put("pageSize", this.pageSize);
            result.put("totalPages", this.totalPages);
            result.put("submittedPages", this.submittedPages);
            result.put("completedPages", this.completedPages);
            result.put("concurrency", this.concurrency);
            result.put("scanned", this.scanned);
            result.put("repaired", this.repaired);
            result.put("skipped", this.skipped);
            result.put("runningPages", new ArrayList<>(this.runningPages));
            result.put("failedPages", new ArrayList<>(this.failedPages));
            result.put("message", this.message);
            result.put("startedAt", this.startedAt);
            result.put("finishedAt", this.finishedAt);
            result.put("lastProgressAt", this.lastProgressAt);
            return result;
        }

        private String buildRunningMessage(String prefix) {
            if (this.runningPages.isEmpty()) {
                return prefix;
            }
            return prefix + "，运行中分页：" + formatRunningPages();
        }

        private String formatRunningPages() {
            StringBuilder builder = new StringBuilder();
            int index = 0;
            for (Integer page : this.runningPages) {
                if (page == null) {
                    continue;
                }
                if (index > 0) {
                    builder.append('、');
                }
                builder.append(page);
                index++;
                if (index >= 8 && this.runningPages.size() > index) {
                    builder.append(" 等");
                    break;
                }
            }
            return builder.toString();
        }
    }
}