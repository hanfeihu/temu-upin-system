package com.tminos.productscene.sync.executor;

import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.repository.TemuGoodsRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Component
public class LifecycleSyncExecutor extends AbstractSyncExecutor<Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleSyncExecutor.class);

    @Autowired private TemuGoodsRepository goodsRepo;
    @Autowired private TemuGoodsSkuRepository skuRepo;

    private static final int SKU_BATCH_SIZE = 100;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_BATCH_RETRIES = 4;
    private static final long BASE_RETRY_DELAY_MILLIS = 1500L;
    private static final long INTER_BATCH_DELAY_MILLIS = 900L;

    @Override
    protected String getSyncType() { return "LIFECYCLE"; }

    @Override
    protected String getPageParamName() { return "pageNum"; }

    @Override
    protected int getPageDownloadMaxRetries() {
        return 3;
    }

    @Override
    protected long getPageDownloadRetryDelayMillis(int attempt, String apiType, int pageNum, String errorMsg) {
        return 1200L * attempt;
    }

    @Override
    protected List<Map<String, Object>> doDownload(TemuSyncTask task, TemuOpenApiClient client) throws Exception {
        List<Long> productSkuIds = skuRepo.findDistinctProductSkuIdsByShopId(task.getShopId()).stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));

        if (productSkuIds.isEmpty()) {
            task.setDownloadTotal(0);
            task.setDownloadCompleted(0);
            taskRepo.save(task);
            logStep(task, "DOWNLOAD", "INFO", "未找到可查询生命周期的 SKU");
            return List.of();
        }

        task.setDownloadTotal(productSkuIds.size());
        task.setDownloadCompleted(0);
        taskRepo.save(task);

        List<List<Long>> batches = partition(productSkuIds, SKU_BATCH_SIZE);
        int concurrency = Math.max(1, Math.min(getDownloadConcurrency(task), 8));
        CompletionService<LifecycleBatchResult> completionService = new ExecutorCompletionService<>(workerPool);
        Map<Future<LifecycleBatchResult>, Integer> futureBatchIndexes = new ConcurrentHashMap<>();
        List<Map<String, Object>> allData = Collections.synchronizedList(new ArrayList<>());
        int submitted = 0;
        int completed = 0;
        int completedSkuCount = 0;

        while (submitted < batches.size() || completed < submitted) {
            while (submitted < batches.size() && (submitted - completed) < concurrency) {
                final int batchIndex = submitted;
                final List<Long> batchSkuIds = batches.get(batchIndex);
                Future<LifecycleBatchResult> future = completionService.submit(() -> fetchBatch(client, batchIndex, batchSkuIds));
                futureBatchIndexes.put(future, batchIndex);
                submitted++;
            }

            Future<LifecycleBatchResult> future = completionService.take();
            Integer batchIndex = futureBatchIndexes.remove(future);
            LifecycleBatchResult batchResult = future.get();

            if (!batchResult.success()) {
                throw new RuntimeException("API " + TemuOpenApiClient.API_PRODUCT_SEARCH + " 第" + (batchIndex + 1) + "批失败: " + batchResult.errorMessage());
            }

            if (!batchResult.rows().isEmpty()) {
                allData.addAll(batchResult.rows());
            }

            completed++;
            completedSkuCount += batchResult.requestedSkuCount();
            task.setDownloadCompleted(Math.min(completedSkuCount, productSkuIds.size()));
            taskRepo.save(task);

            if (completed % 5 == 0 || completed == batches.size()) {
                logStep(task, "DOWNLOAD", "INFO",
                        String.format("生命周期已完成 %d/%d 批, 累计 %d/%d 个 SKU", completed, batches.size(), completedSkuCount, productSkuIds.size()));
            }

            if (submitted < batches.size()) {
                sleepBetweenBatches();
            }
        }

        return allData;
    }

    private LifecycleBatchResult fetchBatch(TemuOpenApiClient client, int batchIndex, List<Long> batchSkuIds) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_BATCH_RETRIES; attempt++) {
            try {
                List<Map<String, Object>> rows = fetchBatchPages(client, batchSkuIds);
                return LifecycleBatchResult.success(batchSkuIds.size(), rows);
            } catch (Exception e) {
                lastException = e;
                if (attempt >= MAX_BATCH_RETRIES) {
                    throw e;
                }
                sleepBeforeBatchRetry(attempt, batchIndex, e.getMessage());
            }
        }
        throw lastException == null
                ? new RuntimeException("API " + TemuOpenApiClient.API_PRODUCT_SEARCH + " 第" + (batchIndex + 1) + "批失败")
                : lastException;
    }

    private List<Map<String, Object>> fetchBatchPages(TemuOpenApiClient client, List<Long> batchSkuIds) throws Exception {
        List<Map<String, Object>> allRows = new ArrayList<>();
        int pageNum = 1;

        while (true) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("productSkuIdList", batchSkuIds);
            params.put("pageNum", pageNum);
            params.put("pageSize", PAGE_SIZE);

            TemuOpenApiClient.ApiResult result = client.callApiParsed(TemuOpenApiClient.API_PRODUCT_SEARCH, params);
            if (!result.success) {
                String errorMsg = result.errorMsg == null || result.errorMsg.isBlank() ? "UNKNOWN_ERROR" : result.errorMsg;
                throw new RuntimeException(errorMsg);
            }

            List<Map<String, Object>> pageRows = flattenLifecycleRows(result.resultAsMap());
            if (pageRows.isEmpty()) {
                break;
            }

            allRows.addAll(pageRows);
            if (pageRows.size() < PAGE_SIZE) {
                break;
            }
            pageNum++;
        }

        return allRows;
    }

    private void sleepBetweenBatches() throws InterruptedException {
        Thread.sleep(INTER_BATCH_DELAY_MILLIS);
    }

    private void sleepBeforeBatchRetry(int attempt, int batchIndex, String errorMsg) throws InterruptedException {
        long waitMillis = BASE_RETRY_DELAY_MILLIS * attempt;
        logger.warn("生命周期接口第{}批第{}次重试，等待 {}ms，原因: {}", batchIndex + 1, attempt, waitMillis, errorMsg);
        Thread.sleep(waitMillis);
    }

    private boolean isRateLimitError(String errorMsg) {
        if (errorMsg == null || errorMsg.isBlank()) {
            return false;
        }
        String normalized = errorMsg.toLowerCase();
        return normalized.contains("requests too frequently") || normalized.contains("limit threshold");
    }

    private List<Map<String, Object>> flattenLifecycleRows(Map<String, Object> resultMap) {
        if (resultMap == null) {
            return List.of();
        }

        Object resultObj = resultMap.get("result");
        Map<String, Object> lifecycleRoot = toMap(resultObj);
        if (lifecycleRoot == null) {
            lifecycleRoot = resultMap;
        }

        List<Map<String, Object>> productRows = extractList(lifecycleRoot, "dataList", "goodsProductList", "productList");
        if (productRows == null || productRows.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> flattened = new ArrayList<>();
        for (Map<String, Object> productRow : productRows) {
            if (productRow == null) {
                continue;
            }
            Long productId = toLong(getFirst(productRow, "productId"));
            List<Map<String, Object>> skcList = extractList(productRow, "skcList");
            if (skcList == null || skcList.isEmpty()) {
                Map<String, Object> direct = flattenSingleLifecycleRow(productRow, productId);
                if (direct != null) {
                    flattened.add(direct);
                }
                continue;
            }

            for (Map<String, Object> skcRow : skcList) {
                Map<String, Object> flattenedRow = flattenSingleLifecycleRow(skcRow, productId);
                if (flattenedRow != null) {
                    flattened.add(flattenedRow);
                }
            }
        }
        return flattened;
    }

    private Map<String, Object> flattenSingleLifecycleRow(Map<String, Object> raw, Long fallbackProductId) {
        if (raw == null) {
            return null;
        }

        Long skcId = toLong(getFirst(raw, "productSkcId", "skcId"));
        if (skcId == null) {
            return null;
        }

        List<Long> skuIds = extractSkuIds(raw.get("skuList"));
        if (skuIds.isEmpty()) {
            skuIds = extractSkuIds(raw.get("productSkuIdList"));
        }
        if (skuIds.isEmpty()) {
            Long directSkuId = toLong(getFirst(raw, "productSkuId", "skuId"));
            if (directSkuId != null) {
                skuIds = List.of(directSkuId);
            }
        }

        Map<String, Object> flattened = new LinkedHashMap<>();
        flattened.put("productId", toLong(getFirst(raw, "productId")) != null ? toLong(getFirst(raw, "productId")) : fallbackProductId);
        flattened.put("productSkcId", skcId);
        flattened.put("selectStatus", getFirst(raw, "selectStatus"));
        flattened.put("applyJitStatus", getFirst(raw, "applyJitStatus"));
        flattened.put("suggestCloseJit", getFirst(raw, "suggestCloseJit"));
        flattened.put("skuIds", skuIds);
        return flattened;
    }

    private List<Long> extractSkuIds(Object rawSkuList) {
        if (!(rawSkuList instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }

        List<Long> skuIds = new ArrayList<>();
        for (Object item : rawList) {
            Long skuId = null;
            if (item instanceof Map<?, ?> map) {
                skuId = toLong(getFirst((Map<String, Object>) map, "productSkuId", "skuId"));
            } else {
                skuId = toLong(item);
            }
            if (skuId != null && skuId > 0) {
                skuIds.add(skuId);
            }
        }
        return skuIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private record LifecycleBatchResult(boolean success, int requestedSkuCount, List<Map<String, Object>> rows, String errorMessage) {
        private static LifecycleBatchResult success(int requestedSkuCount, List<Map<String, Object>> rows) {
            return new LifecycleBatchResult(true, requestedSkuCount, rows == null ? List.of() : rows, null);
        }

        private static LifecycleBatchResult failure(int requestedSkuCount, String errorMessage) {
            return new LifecycleBatchResult(false, requestedSkuCount, List.of(), errorMessage);
        }
    }

    @Override
    @Transactional
    protected void doPersistBatch(TemuSyncTask task, List<Map<String, Object>> batch, int batchIndex) {
        if (batch == null || batch.isEmpty()) {
            return;
        }

        Map<Long, Map<String, Object>> mergedBySkcId = new LinkedHashMap<>();
        for (Map<String, Object> raw : batch) {
            if (raw == null) {
                continue;
            }
            Long skcId = toLong(raw.get("productSkcId"));
            if (skcId == null) {
                skcId = toLong(raw.get("skcId"));
            }
            if (skcId == null) {
                continue;
            }

            Map<String, Object> merged = mergedBySkcId.computeIfAbsent(skcId, key -> new LinkedHashMap<>());
            merged.putIfAbsent("productSkcId", skcId);
            if (merged.get("productId") == null) {
                merged.put("productId", toLong(raw.get("productId")));
            }
            merged.put("selectStatus", raw.get("selectStatus"));
            merged.put("applyJitStatus", raw.get("applyJitStatus"));
            merged.put("suggestCloseJit", raw.get("suggestCloseJit"));

            List<Long> existingSkuIds = castSkuIds(merged.get("skuIds"));
            List<Long> incomingSkuIds = castSkuIds(raw.get("skuIds"));
            LinkedHashSet<Long> mergedSkuIds = new LinkedHashSet<>(existingSkuIds);
            mergedSkuIds.addAll(incomingSkuIds);
            merged.put("skuIds", new ArrayList<>(mergedSkuIds));
        }

        List<Long> skcIds = new ArrayList<>(mergedBySkcId.keySet());
        Map<Long, TemuGoods> goodsBySkcId = goodsRepo.findByShopIdAndProductSkcIdIn(task.getShopId(), skcIds).stream()
                .filter(goods -> goods.getProductSkcId() != null)
                .collect(Collectors.toMap(TemuGoods::getProductSkcId, goods -> goods, (left, right) -> left, LinkedHashMap::new));

        for (Map.Entry<Long, Map<String, Object>> entry : mergedBySkcId.entrySet()) {
            Long skcId = entry.getKey();
            TemuGoods goods = goodsBySkcId.get(skcId);
            if (goods == null) {
                logger.warn("生命周期同步跳过未落库商品, shopId={}, skcId={}", task.getShopId(), skcId);
                continue;
            }

            Map<String, Object> raw = entry.getValue();
            goods.setSelectStatus(toInt(raw.get("selectStatus")));
            goods.setApplyJitStatus(toInt(raw.get("applyJitStatus")));
            goods.setSuggestCloseJit(toBool(raw.get("suggestCloseJit")));
            goods.setSkuIdsJson(toJson(raw.get("skuIds")));
            goods.setLifecycleSyncedAt(LocalDateTime.now());
            goodsRepo.save(goods);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> castSkuIds(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Long> skuIds = new ArrayList<>();
        for (Object item : list) {
            Long skuId = toLong(item);
            if (skuId != null) {
                skuIds.add(skuId);
            }
        }
        return skuIds;
    }
}
