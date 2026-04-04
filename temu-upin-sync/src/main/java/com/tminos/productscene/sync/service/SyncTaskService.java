package com.tminos.productscene.sync.service;

import com.tminos.productscene.sync.dto.SyncTaskDTO;
import com.tminos.productscene.sync.entity.TemuSyncStepLog;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.executor.*;
import com.tminos.productscene.sync.repository.TemuSyncStepLogRepository;
import com.tminos.productscene.sync.repository.TemuSyncTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SyncTaskService {

    private static final Logger log = LoggerFactory.getLogger(SyncTaskService.class);
    private static final List<String> RUNNING_STATUSES = List.of("PENDING", "DOWNLOADING", "DOWNLOADED", "PERSISTING");
    private static final String DEFAULT_AUTO_PRICE_ADJUST_SCOPE = "LAST_7_DAYS";

    private final TemuSyncTaskRepository taskRepo;
    private final TemuSyncStepLogRepository stepLogRepo;
    private final TemuSyncConfigService syncConfigService;
    private final Map<String, AbstractSyncExecutor<?>> executors;

    public SyncTaskService(TemuSyncTaskRepository taskRepo,
                           TemuSyncStepLogRepository stepLogRepo,
                           TemuSyncConfigService syncConfigService,
                           GoodsSyncExecutor goodsExecutor,
                           LifecycleSyncExecutor lifecycleExecutor,
                           PriceSyncExecutor priceExecutor,
                           FreightSyncExecutor freightExecutor,
                           WarehouseSyncExecutor warehouseExecutor,
                           PriceReviewSyncExecutor priceReviewExecutor,
                           PriceAdjustSyncExecutor priceAdjustExecutor,
                           ActivitySyncExecutor activityExecutor) {
        this.taskRepo = taskRepo;
        this.stepLogRepo = stepLogRepo;
                this.syncConfigService = syncConfigService;

        this.executors = new LinkedHashMap<>();
        executors.put("GOODS", goodsExecutor);
        executors.put("LIFECYCLE", lifecycleExecutor);
        executors.put("PRICE", priceExecutor);
        executors.put("FREIGHT", freightExecutor);
        executors.put("WAREHOUSE", warehouseExecutor);
        executors.put("PRICE_REVIEW", priceReviewExecutor);
        executors.put("PRICE_ADJUST", priceAdjustExecutor);
        executors.put("ACTIVITY", activityExecutor);
    }

    /**
     * 创建同步任务（支持多类型批量提交）
     */
    public List<SyncTaskDTO.TaskItem> createTasks(SyncTaskDTO.CreateRequest request) {
        String shopId = request.getShopId();
        List<String> syncTypes = request.getSyncTypes();
        List<SyncTaskDTO.TaskItem> result = new ArrayList<>();

        for (String syncType : syncTypes) {
            TemuSyncTask task = createSingleTask(
                    shopId,
                    syncType,
                    request.getGoodsSyncMode(),
                    request.getPriceAdjustSyncMode(),
                    "MANUAL",
                    true);
            result.add(toTaskItem(task, false));
        }

        return result;
    }

    public List<SyncTaskDTO.TaskItem> createAutoTasks(String shopId) {
        List<SyncTaskDTO.TaskItem> result = new ArrayList<>();
        for (String syncType : List.of("GOODS", "LIFECYCLE", "PRICE", "PRICE_ADJUST")) {
            TemuSyncTask task = createSingleTask(
                    shopId,
                    syncType,
                    "LAST_WEEK",
                    resolveAutoPriceAdjustScope(shopId),
                    "AUTO",
                    false);
            if (task != null) {
                result.add(toTaskItem(task, false));
            }
        }
        return result;
    }

    /**
     * 异步执行任务
     */
    @Async("syncTaskExecutor")
    public void executeTaskAsync(Long taskId) {
        Long safeTaskId = Objects.requireNonNull(taskId, "taskId must not be null");
        TemuSyncTask task = taskRepo.findById(safeTaskId).orElse(null);
        if (task == null) {
            log.error("任务不存在: {}", safeTaskId);
            return;
        }

        AbstractSyncExecutor<?> executor = executors.get(task.getSyncType());
        if (executor == null) {
            log.error("未找到同步执行器: {}", task.getSyncType());
            task.setStatus("DOWNLOAD_FAILED");
            task.setLastErrorMsg("未找到同步执行器: " + task.getSyncType());
            task.setFinishedAt(LocalDateTime.now());
            taskRepo.save(task);
            return;
        }

        try {
            executor.execute(task);
        } catch (Exception e) {
            log.error("任务执行异常 taskId={}", taskId, e);
            task.setStatus("DOWNLOAD_FAILED");
            task.setLastErrorMsg("未预期异常: " + e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            taskRepo.save(task);
        }
    }

    /**
     * 查询任务列表
     */
    public Page<TemuSyncTask> listTasks(String shopId, String syncType, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        if (syncType != null && !syncType.isBlank()) {
            return taskRepo.findByShopIdAndSyncType(shopId, syncType, pageRequest);
        }
        return taskRepo.findByShopId(shopId, pageRequest);
    }

    /**
     * 查询任务详情（含步骤日志）
     */
    public SyncTaskDTO.TaskItem getTaskDetail(Long taskId) {
        Long safeTaskId = Objects.requireNonNull(taskId, "taskId must not be null");
        TemuSyncTask task = taskRepo.findById(safeTaskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        return toTaskItem(task, true);
    }

    /**
     * 查询任务进度（轮询用，轻量级）
     */
    public SyncTaskDTO.ProgressResponse getProgress(Long taskId) {
        Long safeTaskId = Objects.requireNonNull(taskId, "taskId must not be null");
        TemuSyncTask task = taskRepo.findById(safeTaskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        SyncTaskDTO.ProgressResponse resp = new SyncTaskDTO.ProgressResponse();
        resp.setTaskId(task.getId());
        resp.setStatus(task.getStatus());
        resp.setCurrentPhase(task.getCurrentPhase());
        resp.setDownloadTotal(task.getDownloadTotal());
        resp.setDownloadCompleted(task.getDownloadCompleted());
        resp.setPersistTotal(task.getPersistTotal());
        resp.setPersistCompleted(task.getPersistCompleted());
        resp.setTotalBatches(task.getTotalBatches());
        resp.setPersistedBatches(task.getPersistedBatches());

        // 最近 5 条日志
        List<TemuSyncStepLog> recentLogs = stepLogRepo.findTop20ByTaskIdOrderByCreatedAtDesc(safeTaskId);
        List<SyncTaskDTO.StepLogItem> logItems = new ArrayList<>();
        // 反转为正序（最多取5条）
        int start = Math.max(0, recentLogs.size() - 5);
        for (int i = recentLogs.size() - 1; i >= start; i--) {
            logItems.add(toStepLogItem(recentLogs.get(i)));
        }
        resp.setLatestLogs(logItems);

        return resp;
    }

    /**
     * 重试任务（仅 PERSIST_FAILED / DOWNLOAD_FAILED 状态可用）
     */
    public SyncTaskDTO.TaskItem retryTask(Long taskId, String mode) {
        Long safeTaskId = Objects.requireNonNull(taskId, "taskId must not be null");
        TemuSyncTask task = taskRepo.findById(safeTaskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        if (!"PERSIST_FAILED".equals(task.getStatus()) && !"DOWNLOAD_FAILED".equals(task.getStatus())) {
            throw new IllegalStateException("仅下载失败或入库失败状态可重试，当前状态: " + task.getStatus());
        }

        task.setRetryCount(task.getRetryCount() + 1);
        task.setLastErrorMsg(null);
        task.setFinishedAt(null);

        if ("FULL".equals(mode)) {
            task.setStatus("PENDING");
            task.setCurrentPhase("DOWNLOAD");
            task.setDownloadTotal(null);
            task.setDownloadCompleted(0);
            task.setDownloadFailed(null);
            task.setPersistTotal(null);
            task.setPersistCompleted(0);
            task.setPersistFailed(null);
            task.setTotalBatches(null);
            task.setPersistedBatches(0);
            task.setFailedBatchIndex(null);
        } else {
            // CONTINUE: 对于下载失败就完全重来，对于入库失败从断点继续
            if ("DOWNLOAD_FAILED".equals(task.getStatus())) {
                task.setStatus("PENDING");
                task.setCurrentPhase("DOWNLOAD");
                task.setDownloadTotal(null);
                task.setDownloadCompleted(0);
                task.setDownloadFailed(null);
            } else {
                task.setStatus("DOWNLOADED");
                task.setCurrentPhase("PERSIST");
            }
        }

        taskRepo.save(task);
        executeTaskAsync(task.getId());

        return toTaskItem(task, false);
    }

    /**
     * 取消任务
     */
    public void cancelTask(Long taskId) {
        Long safeTaskId = Objects.requireNonNull(taskId, "taskId must not be null");
        TemuSyncTask task = taskRepo.findById(safeTaskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));

        if ("SUCCEEDED".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus())) {
            throw new IllegalStateException("已完成或已取消的任务不可取消");
        }

        task.setStatus("CANCELLED");
        task.setFinishedAt(LocalDateTime.now());
        taskRepo.save(task);
    }

    // ==================== Mapping ====================

    private SyncTaskDTO.TaskItem toTaskItem(TemuSyncTask task, boolean withLogs) {
        SyncTaskDTO.TaskItem item = new SyncTaskDTO.TaskItem();
        item.setId(task.getId());
        item.setShopId(task.getShopId());
        item.setSyncType(task.getSyncType());
        item.setSyncScope(task.getSyncScope());
        item.setTriggerType(task.getTriggerType());
        item.setStatus(task.getStatus());
        item.setCurrentPhase(task.getCurrentPhase());
        item.setDownloadTotal(task.getDownloadTotal());
        item.setDownloadCompleted(task.getDownloadCompleted());
        item.setDownloadFailed(task.getDownloadFailed());
        item.setPersistTotal(task.getPersistTotal());
        item.setPersistCompleted(task.getPersistCompleted());
        item.setPersistFailed(task.getPersistFailed());
        item.setTotalBatches(task.getTotalBatches());
        item.setPersistedBatches(task.getPersistedBatches());
        item.setFailedBatchIndex(task.getFailedBatchIndex());
        item.setLastErrorMsg(task.getLastErrorMsg());
        item.setRetryCount(task.getRetryCount());
        item.setStartedAt(task.getStartedAt());
        item.setFinishedAt(task.getFinishedAt());
        item.setCreatedAt(task.getCreatedAt());

        if (withLogs) {
            List<TemuSyncStepLog> logs = stepLogRepo.findByTaskIdOrderByCreatedAtAsc(task.getId());
            item.setLogs(logs.stream().map(this::toStepLogItem).toList());
        }

        return item;
    }

    private SyncTaskDTO.StepLogItem toStepLogItem(TemuSyncStepLog log) {
        SyncTaskDTO.StepLogItem item = new SyncTaskDTO.StepLogItem();
        item.setPhase(log.getPhase());
        item.setLevel(log.getLevel());
        item.setMessage(log.getMessage());
        item.setCreatedAt(log.getCreatedAt());
        return item;
    }

    @SuppressWarnings("null")
    private TemuSyncTask createSingleTask(String shopId,
                                          String syncType,
                                          String goodsSyncMode,
                                          String priceAdjustSyncMode,
                                          String triggerType,
                                          boolean strictRunningCheck) {
        if (!executors.containsKey(syncType)) {
            throw new IllegalArgumentException("不支持的同步类型: " + syncType);
        }

        Optional<TemuSyncTask> running = taskRepo.findFirstByShopIdAndSyncTypeAndStatusIn(
                shopId, syncType, RUNNING_STATUSES);
        if (running.isPresent()) {
            if (strictRunningCheck) {
                throw new IllegalStateException(
                        String.format("同步类型 %s 已有进行中的任务 #%d", syncType, running.get().getId()));
            }
            return null;
        }

        if ("AUTO".equals(triggerType)) {
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            Optional<TemuSyncTask> todayTask = taskRepo.findFirstByShopIdAndSyncTypeAndTriggerTypeAndCreatedAtAfter(
                    shopId, syncType, triggerType, todayStart);
            if (todayTask.isPresent()) {
                return null;
            }
        }

        TemuSyncTask task = TemuSyncTask.builder()
                .shopId(shopId)
                .syncType(syncType)
            .syncScope(resolveSyncScope(syncType, goodsSyncMode, priceAdjustSyncMode, triggerType))
                .triggerType(triggerType)
                .status("PENDING")
                .currentPhase("DOWNLOAD")
                .downloadCompleted(0)
                .persistCompleted(0)
                .persistedBatches(0)
                .retryCount(0)
                .build();
        task = taskRepo.save(task);
        executeTaskAsync(task.getId());
        return task;
    }

    private String resolveSyncScope(String syncType, String goodsSyncMode, String priceAdjustSyncMode, String triggerType) {
        if ("GOODS".equals(syncType)) {
            if ("AUTO".equals(triggerType)) {
                return "LAST_WEEK";
            }
            return normalizeManualScope(goodsSyncMode, "LAST_WEEK");
        }
        if ("PRICE_ADJUST".equals(syncType)) {
            if ("AUTO".equals(triggerType)) {
                return normalizeAutoPriceAdjustScope(priceAdjustSyncMode);
            }
            return normalizeManualScope(priceAdjustSyncMode, "LAST_WEEK");
        }
        return null;
    }

    private String normalizeManualScope(String scope, String defaultScope) {
        if (scope == null || scope.isBlank()) {
            return defaultScope;
        }
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        if ("LAST_WEEK".equals(normalized) || "LAST_YEAR".equals(normalized)) {
            return normalized;
        }
        return defaultScope;
    }

    private String resolveAutoPriceAdjustScope(String shopId) {
        int days = syncConfigService.getIntConfig(shopId, "price_adjust_auto_sync_days", 7);
        return normalizeAutoPriceAdjustScope(days <= 0 ? null : "LAST_" + days + "_DAYS");
    }

    private String normalizeAutoPriceAdjustScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return DEFAULT_AUTO_PRICE_ADJUST_SCOPE;
        }
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches("LAST_[1-9]\\d*_DAYS")) {
            return normalized;
        }
        return DEFAULT_AUTO_PRICE_ADJUST_SCOPE;
    }
}
