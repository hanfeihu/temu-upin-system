package com.tminos.productscene.sync.executor;

import com.tminos.productscene.sync.entity.TemuGoodsLifecycle;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.repository.TemuGoodsLifecycleRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class LifecycleSyncExecutor extends AbstractSyncExecutor<Map<String, Object>> {

    @Autowired private TemuGoodsLifecycleRepository lifecycleRepo;

    @Override
    protected String getSyncType() { return "LIFECYCLE"; }

    @Override
    protected String getPageParamName() { return "pageNum"; }

    @Override
    protected int getDownloadConcurrency(TemuSyncTask task) {
        return 1;
    }

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
        return doPagedDownload(task, client, TemuOpenApiClient.API_PRODUCT_SEARCH, 50, "goodsProductList", "productList");
    }

    @Override
    @Transactional
    protected void doPersistBatch(TemuSyncTask task, List<Map<String, Object>> batch, int batchIndex) {
        for (Map<String, Object> raw : batch) {
            Long skcId = toLong(raw.get("productSkcId"));
            if (skcId == null) skcId = toLong(raw.get("skcId"));
            if (skcId == null) continue;

            TemuGoodsLifecycle lc = lifecycleRepo.findByShopIdAndSkcId(task.getShopId(), skcId)
                    .orElse(new TemuGoodsLifecycle());

            lc.setShopId(task.getShopId());
            lc.setProductId(toLong(raw.get("productId")));
            lc.setSkcId(skcId);
            lc.setSelectStatus(toInt(raw.get("selectStatus")));
            lc.setApplyJitStatus(toInt(raw.get("applyJitStatus")));
            lc.setSuggestCloseJit(toBool(raw.get("suggestCloseJit")));
            lc.setSkuIdsJson(toJson(raw.get("skuIds")));
            lc.setSyncedAt(LocalDateTime.now());

            lifecycleRepo.save(lc);
        }
    }
}
