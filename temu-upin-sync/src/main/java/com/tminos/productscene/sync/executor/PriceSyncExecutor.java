package com.tminos.productscene.sync.executor;

import com.tminos.productscene.sync.entity.TemuGoodsSku;
import com.tminos.productscene.sync.entity.TemuGoodsSkuPrice;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.repository.TemuGoodsSkuRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuPriceRepository;
import com.tminos.productscene.sync.service.TemuPriceSyncTransactionalService;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Component
public class PriceSyncExecutor extends AbstractSyncExecutor<Map<String, Object>> {

    @Autowired private TemuGoodsSkuRepository skuRepo;
    @Autowired private TemuPriceSyncTransactionalService priceSyncTransactionalService;

    private static final int API_BATCH_SIZE = 50;

    @Override
    protected String getSyncType() { return "PRICE"; }

    @Override
    protected List<Map<String, Object>> doDownload(TemuSyncTask task, TemuOpenApiClient client) throws Exception {
        List<Long> productSkuIds = skuRepo.findAddedSiteSkusByShopId(task.getShopId()).stream()
                .map(TemuGoodsSku::getProductSkuId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));

        if (productSkuIds.isEmpty()) {
            task.setDownloadTotal(0);
            task.setDownloadCompleted(0);
            taskRepo.save(task);
            logStep(task, "DOWNLOAD", "INFO", "未找到可查询供货价的 SKU");
            return List.of();
        }

        task.setDownloadTotal(productSkuIds.size());
        task.setDownloadCompleted(0);
        taskRepo.save(task);

        List<List<Long>> batches = partition(productSkuIds, API_BATCH_SIZE);
        int concurrency = Math.max(1, Math.min(getConcurrency(task), 8));
        CompletionService<PriceBatchResult> completionService = new ExecutorCompletionService<>(workerPool);
        Map<Future<PriceBatchResult>, Integer> futureBatchIndexes = new ConcurrentHashMap<>();
        List<Map<String, Object>> allData = Collections.synchronizedList(new ArrayList<>());
        int submitted = 0;
        int completed = 0;
        int completedSkuCount = 0;

        while (submitted < batches.size() || completed < submitted) {
            while (submitted < batches.size() && (submitted - completed) < concurrency) {
                final int batchIndex = submitted;
                final List<Long> batchSkuIds = batches.get(batchIndex);
                Future<PriceBatchResult> future = completionService.submit(() -> fetchBatch(client, batchIndex, batchSkuIds));
                futureBatchIndexes.put(future, batchIndex);
                submitted++;
            }

            Future<PriceBatchResult> future = completionService.take();
            Integer batchIndex = futureBatchIndexes.remove(future);
            PriceBatchResult batchResult = future.get();

            if (!batchResult.success()) {
                throw new RuntimeException("API " + TemuOpenApiClient.API_GOODS_PRICE_LIST + " 第" + (batchIndex + 1) + "批失败: " + batchResult.errorMessage());
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
                        String.format("供货价已完成 %d/%d 批, 累计 %d/%d 个 SKU", completed, batches.size(), completedSkuCount, productSkuIds.size()));
            }
        }

        return allData;
    }

    private PriceBatchResult fetchBatch(TemuOpenApiClient client, int batchIndex, List<Long> batchSkuIds) throws Exception {
        Map<String, Object> params = Map.of("productSkuIds", batchSkuIds);
        TemuOpenApiClient.ApiResult result = client.callApiParsed(TemuOpenApiClient.API_GOODS_PRICE_LIST, params);
        if (!result.success) {
            return PriceBatchResult.failure(batchSkuIds.size(), result.errorMsg);
        }

        Map<String, Object> resultMap = result.resultAsMap();
        List<Map<String, Object>> list = resultMap == null ? List.of()
                : extractList(resultMap, "productSkuSupplierPriceList", "goodsSkuPriceList", "priceList");
        return PriceBatchResult.success(batchSkuIds.size(), list == null ? List.of() : list);
    }

    private record PriceBatchResult(boolean success, int requestedSkuCount, List<Map<String, Object>> rows, String errorMessage) {
        private static PriceBatchResult success(int requestedSkuCount, List<Map<String, Object>> rows) {
            return new PriceBatchResult(true, requestedSkuCount, rows, null);
        }

        private static PriceBatchResult failure(int requestedSkuCount, String errorMessage) {
            return new PriceBatchResult(false, requestedSkuCount, List.of(), errorMessage);
        }
    }

    @Override
    protected void doPersistBatch(TemuSyncTask task, List<Map<String, Object>> batch, int batchIndex) {
        priceSyncTransactionalService.persistBatch(task, batch,
                AbstractSyncExecutor::toLong,
                AbstractSyncExecutor::toInt,
                AbstractSyncExecutor::toStr,
                this::extractSiteSupplierPrices);
    }

    private List<Map<String, Object>> extractSiteSupplierPrices(Object rawSitePrices) {
        if (!(rawSitePrices instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> sitePrices = new ArrayList<>();
        for (Object item : rawList) {
            Map<String, Object> itemMap = toMap(item);
            if (itemMap != null) {
                sitePrices.add(itemMap);
            }
        }
        return sitePrices;
    }
}
