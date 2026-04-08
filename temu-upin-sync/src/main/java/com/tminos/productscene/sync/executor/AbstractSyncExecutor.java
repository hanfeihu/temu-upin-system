package com.tminos.productscene.sync.executor;

import com.google.gson.Gson;
import com.tminos.productscene.service.TemuOpenApiCredentialService;
import com.tminos.productscene.sync.entity.TemuSyncStepLog;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.repository.TemuSyncStepLogRepository;
import com.tminos.productscene.sync.repository.TemuSyncTaskRepository;
import com.tminos.productscene.sync.service.TemuSyncConfigService;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractSyncExecutor<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractSyncExecutor.class);
    protected static final Gson gson = new Gson();

    @Autowired protected TemuSyncTaskRepository taskRepo;
    @Autowired protected TemuSyncStepLogRepository stepLogRepo;
    @Autowired protected TemuOpenApiCredentialService credentialService;
    @Autowired protected TemuSyncConfigService syncConfigService;

    @Autowired
    @Qualifier("syncWorkerPool")
    protected ExecutorService workerPool;

    protected abstract String getSyncType();
    protected abstract List<T> doDownload(TemuSyncTask task, TemuOpenApiClient client) throws Exception;
    protected abstract void doPersistBatch(TemuSyncTask task, List<T> batch, int batchIndex);

    protected int getBatchSize() { return 200; }

    protected String getPageParamName() { return "page"; }

    /** 获取该同步类型对应的并发线程数配置 key，子类可覆盖 */
    protected String getThreadCountConfigKey() {
        return getSyncType().toLowerCase() + "_sync_thread_count";
    }

    /** 获取当前任务的并发数（从店铺配置读取） */
    protected int getConcurrency(TemuSyncTask task) {
        return syncConfigService.getIntConfig(task.getShopId(), getThreadCountConfigKey(), 5);
    }

    protected int getDownloadConcurrency(TemuSyncTask task) {
        return getConcurrency(task);
    }

    protected int getPageDownloadMaxRetries() {
        return 1;
    }

    protected long getPageDownloadRetryDelayMillis(int attempt, String apiType, int pageNum, String errorMsg) {
        return 0L;
    }

    public void execute(TemuSyncTask task) {
        task.setStartedAt(LocalDateTime.now());
        taskRepo.save(task);

        try {
            TemuOpenApiCredentials creds = credentialService.getTemuOpenApiCredentialsByShopIdOrThrow(task.getShopId());
            TemuOpenApiClient client = new TemuOpenApiClient(creds);

            // Phase 1: Download
            updateStatus(task, "DOWNLOADING", "DOWNLOAD");
            logStep(task, "DOWNLOAD", "INFO", "开始下载");

            List<T> allData = doDownload(task, client);

            task.setDownloadTotal(allData.size());
            task.setDownloadCompleted(allData.size());
            updateStatus(task, "DOWNLOADED", "DOWNLOAD");
            logStep(task, "DOWNLOAD", "INFO",
                    String.format("下载完成, 共 %d 条", allData.size()));

            // Phase 2: Persist
            executePersist(task, allData);

        } catch (Exception e) {
            log.error("同步任务执行失败 taskId={}, syncType={}", task.getId(), task.getSyncType(), e);
            String phase = task.getCurrentPhase() != null ? task.getCurrentPhase() : "DOWNLOAD";
            logStep(task, phase, "ERROR", "执行失败: " + e.getMessage());

            if ("PERSIST".equals(phase) || "PERSISTING".equals(task.getStatus())) {
                updateStatus(task, "PERSIST_FAILED", "PERSIST");
            } else {
                updateStatus(task, "DOWNLOAD_FAILED", "DOWNLOAD");
            }
            task.setLastErrorMsg(e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            taskRepo.save(task);
        }
    }

    // ==================== 并发入库 ====================

    protected void executePersist(TemuSyncTask task, List<T> allData) {
        updateStatus(task, "PERSISTING", "PERSIST");

        int batchSize = getBatchSize();
        List<List<T>> batches = partition(allData, batchSize);
        task.setTotalBatches(batches.size());
        task.setPersistTotal(allData.size());
        taskRepo.save(task);

        int concurrency = getConcurrency(task);
        logStep(task, "PERSIST", "INFO",
                String.format("开始入库, %d 条, %d 批次, 并发度 %d", allData.size(), batches.size(), concurrency));

        int startBatch = task.getPersistedBatches() != null ? task.getPersistedBatches() : 0;

        Semaphore semaphore = new Semaphore(concurrency);
        AtomicInteger completedBatches = new AtomicInteger(startBatch);
        AtomicReference<Exception> firstError = new AtomicReference<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = startBatch; i < batches.size(); i++) {
            // 如果之前有批次失败了则停止提交新的
            if (firstError.get() != null) break;

            final int batchIndex = i;
            final List<T> batch = batches.get(i);

            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("入库被中断", e);
            }

            futures.add(workerPool.submit(() -> {
                try {
                    if (firstError.get() != null) return; // 其它批次已失败

                    doPersistBatch(task, batch, batchIndex);

                    int done = completedBatches.incrementAndGet();
                    // 周期性更新进度（每 5 批或最后一批）
                    if (done % 5 == 0 || done == batches.size()) {
                        synchronized (task) {
                            task.setPersistedBatches(done);
                            task.setPersistCompleted(Math.min(done * batchSize, allData.size()));
                            taskRepo.save(task);
                        }
                        logStep(task, "PERSIST", "INFO",
                                String.format("已入库 %d/%d 批次", done, batches.size()));
                    }
                } catch (Exception e) {
                    firstError.compareAndSet(null, e);
                    log.error("批次 {} 入库失败: {}", batchIndex, e.getMessage(), e);
                } finally {
                    semaphore.release();
                }
            }));
        }

        // 等待所有提交的 future 完成
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }

        // 处理结果
        if (firstError.get() != null) {
            Exception error = firstError.get();
            task.setLastErrorMsg(error.getMessage());
            int done = completedBatches.get();
            task.setPersistedBatches(done);
            task.setPersistCompleted(Math.min(done * batchSize, allData.size()));
            int failedCount = allData.size() - Math.min(done * batchSize, allData.size());
            task.setPersistFailed(failedCount > 0 ? failedCount : batch_size_fallback(batches));
            taskRepo.save(task);

            logStep(task, "PERSIST", "ERROR",
                    String.format("入库失败 (已完成 %d/%d 批次): %s", done, batches.size(), error.getMessage()));
            updateStatus(task, "PERSIST_FAILED", "PERSIST");
            task.setFinishedAt(LocalDateTime.now());
            taskRepo.save(task);
            return;
        }

        // All succeeded
        int done = completedBatches.get();
        task.setPersistedBatches(done);
        task.setPersistCompleted(allData.size());
        updateStatus(task, "SUCCEEDED", "PERSIST");
        task.setFinishedAt(LocalDateTime.now());
        taskRepo.save(task);
        logStep(task, "PERSIST", "INFO",
                String.format("入库完成, 共 %d 条", allData.size()));
    }

    private int batch_size_fallback(List<List<T>> batches) {
        return batches.isEmpty() ? 0 : batches.get(batches.size() - 1).size();
    }

    // ==================== 并发分页下载 ====================

    /**
     * 不带额外业务参数的分页下载
     */
    protected List<Map<String, Object>> doPagedDownload(TemuSyncTask task, TemuOpenApiClient client,
                                                         String apiType, int pageSize, String... listKeys) throws Exception {
        return doPagedDownload(task, client, apiType, null, pageSize, listKeys);
    }

    /**
     * 带额外业务参数的并发分页下载
     */
    protected List<Map<String, Object>> doPagedDownload(TemuSyncTask task, TemuOpenApiClient client,
                                                         String apiType, Map<String, Object> bizParams,
                                                         int pageSize, String... listKeys) throws Exception {
        int concurrency = Math.max(1, getDownloadConcurrency(task));
        String pageParamName = getPageParamName();

        // 构建请求参数（合并业务参数 + 分页参数）
        Map<String, Object> baseParams = new HashMap<>(bizParams != null ? bizParams : Map.of());

        // 第 1 页：同步拉取，确定 totalCount
        Map<String, Object> firstParams = new HashMap<>(baseParams);
        firstParams.put(pageParamName, 1);
        firstParams.put("pageSize", pageSize);
        TemuOpenApiClient.ApiResult firstResult = callPagedApiWithRetry(client, apiType, firstParams, 1);
        if (!firstResult.success) {
            throw new RuntimeException("API " + apiType + " 第1页失败: " + firstResult.errorMsg);
        }
        Map<String, Object> firstMap = firstResult.resultAsMap();
        if (firstMap == null) return new ArrayList<>();

        Integer totalCount = toInt(firstMap.getOrDefault("totalCount", firstMap.get("total")));
        List<Map<String, Object>> firstList = extractList(firstMap, listKeys);
        if (firstList == null || firstList.isEmpty()) return new ArrayList<>();

        // allData 用 ConcurrentHashMap 按页号存储，保证顺序
        ConcurrentHashMap<Integer, List<Map<String, Object>>> pageDataMap = new ConcurrentHashMap<>();
        pageDataMap.put(1, firstList);

        int total = totalCount != null ? totalCount : firstList.size();
        task.setDownloadTotal(total);
        task.setDownloadCompleted(firstList.size());
        taskRepo.save(task);

        logStep(task, "DOWNLOAD", "INFO",
                String.format("第 1 页成功, %d 条, 共 %d 条, 并发度 %d", firstList.size(), total, concurrency));

        int totalPages = (int) Math.ceil((double) total / pageSize);
        if (totalPages <= 1) {
            return new ArrayList<>(firstList);
        }

        // 并发拉取剩余页
        Semaphore semaphore = new Semaphore(concurrency);
        AtomicInteger downloadedCount = new AtomicInteger(firstList.size());
        AtomicReference<Exception> firstError = new AtomicReference<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int p = 2; p <= totalPages; p++) {
            if (firstError.get() != null) break;

            final int pageNum = p;
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("下载被中断", e);
            }

            futures.add(workerPool.submit(() -> {
                try {
                    if (firstError.get() != null) return;

                    Map<String, Object> reqParams = new HashMap<>(baseParams);
                    reqParams.put(pageParamName, pageNum);
                    reqParams.put("pageSize", pageSize);
                     TemuOpenApiClient.ApiResult result = callPagedApiWithRetry(client, apiType, reqParams, pageNum);
                     if (!result.success) {
                         throw new RuntimeException("API " + apiType + " 第" + pageNum + "页失败: " + result.errorMsg);
                     }

                    Map<String, Object> resultMap = result.resultAsMap();
                    if (resultMap != null) {
                        List<Map<String, Object>> list = extractList(resultMap, listKeys);
                        if (list != null && !list.isEmpty()) {
                            pageDataMap.put(pageNum, list);
                            int count = downloadedCount.addAndGet(list.size());
                            // 周期性更新进度
                            if (pageNum % 5 == 0 || pageNum == totalPages) {
                                synchronized (task) {
                                    task.setDownloadCompleted(count);
                                    taskRepo.save(task);
                                }
                                logStep(task, "DOWNLOAD", "INFO",
                                        String.format("第 %d/%d 页成功, 累计 %d/%d 条",
                                                pageNum, totalPages, count, total));
                            }
                        }
                    }
                } catch (Exception e) {
                    firstError.compareAndSet(null, e);
                    log.error("并发下载第 {} 页失败: {}", pageNum, e.getMessage());
                } finally {
                    semaphore.release();
                }
            }));
        }

        // 等待全部完成
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }

        if (firstError.get() != null) {
            throw new RuntimeException("并发下载失败: " + firstError.get().getMessage(), firstError.get());
        }

        // 按页号顺序合并结果
        List<Map<String, Object>> allData = new ArrayList<>(total);
        for (int p = 1; p <= totalPages; p++) {
            List<Map<String, Object>> pageData = pageDataMap.get(p);
            if (pageData != null) allData.addAll(pageData);
        }

        task.setDownloadCompleted(allData.size());
        taskRepo.save(task);
        return allData;
    }

    protected TemuOpenApiClient.ApiResult callPagedApiWithRetry(TemuOpenApiClient client,
                                                                String apiType,
                                                                Map<String, Object> reqParams,
                                                                int pageNum) throws Exception {
        Exception lastException = null;
        int maxRetries = Math.max(1, getPageDownloadMaxRetries());
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                TemuOpenApiClient.ApiResult result = client.callApiParsed(apiType, reqParams);
                if (result.success) {
                    return result;
                }
                String errorMsg = result.errorMsg == null || result.errorMsg.isBlank() ? "UNKNOWN_ERROR" : result.errorMsg;
                if (attempt >= maxRetries) {
                    throw new RuntimeException("API " + apiType + " 第" + pageNum + "页失败: " + errorMsg);
                }
                sleepBeforePageRetry(attempt, apiType, pageNum, errorMsg);
            } catch (Exception e) {
                lastException = e;
                if (attempt >= maxRetries) {
                    throw e;
                }
                sleepBeforePageRetry(attempt, apiType, pageNum, e.getMessage());
            }
        }
        throw lastException == null
                ? new RuntimeException("API " + apiType + " 第" + pageNum + "页失败")
                : lastException;
    }

    protected void sleepBeforePageRetry(int attempt, String apiType, int pageNum, String errorMsg) throws InterruptedException {
        long waitMillis = Math.max(0L, getPageDownloadRetryDelayMillis(attempt, apiType, pageNum, errorMsg));
        if (waitMillis > 0) {
            log.warn("接口 {} 第{}页第{}次重试，等待 {}ms，原因: {}", apiType, pageNum, attempt, waitMillis, errorMsg);
            Thread.sleep(waitMillis);
        }
    }

    // ==================== Single-call download helper ====================

    protected List<Map<String, Object>> doSingleDownload(TemuSyncTask task, TemuOpenApiClient client,
                                                          String apiType, String... listKeys) throws Exception {
        TemuOpenApiClient.ApiResult result = client.callApiParsed(apiType, null);
        if (!result.success) {
            throw new RuntimeException("API 调用失败: " + result.errorMsg);
        }

        if (result.resultAsList() != null) {
            return result.resultAsList();
        }

        Map<String, Object> resultMap = result.resultAsMap();
        if (resultMap == null) return new ArrayList<>();

        List<Map<String, Object>> list = extractList(resultMap, listKeys);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    // ==================== Helpers ====================

    protected void logStep(TemuSyncTask task, String phase, String level, String message) {
        try {
            TemuSyncStepLog stepLog = TemuSyncStepLog.builder()
                    .taskId(task.getId())
                    .phase(phase)
                    .level(level)
                    .message(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            stepLogRepo.save(stepLog);
        } catch (Exception e) {
            log.warn("Failed to save step log for task {}: {}", task.getId(), e.getMessage());
        }
    }

    protected void updateStatus(TemuSyncTask task, String status, String phase) {
        task.setStatus(status);
        task.setCurrentPhase(phase);
        task.setUpdatedAt(LocalDateTime.now());
        taskRepo.save(task);
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> extractList(Map<String, Object> resultMap, String... preferredKeys) {
        for (String key : preferredKeys) {
            Object val = resultMap.get(key);
            if (val instanceof List<?> list && !list.isEmpty()) {
                return (List<Map<String, Object>>) list;
            }
        }
        for (Object val : resultMap.values()) {
            if (val instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
                return (List<Map<String, Object>>) list;
            }
        }
        return null;
    }

    protected static <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(new ArrayList<>(list.subList(i, Math.min(i + batchSize, list.size()))));
        }
        return batches;
    }

    protected static Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.longValue();
        try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { return null; }
    }

    protected static Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (NumberFormatException e) { return null; }
    }

    protected static String toStr(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    protected static Boolean toBool(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Boolean b) return b;
        return Boolean.parseBoolean(obj.toString());
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    protected static Object getFirst(Map<String, Object> raw, String... keys) {
        if (raw == null || keys == null) return null;
        for (String key : keys) {
            if (key != null && raw.containsKey(key)) {
                Object value = raw.get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    protected String toJson(Object obj) {
        if (obj == null) return null;
        return gson.toJson(obj);
    }
}
