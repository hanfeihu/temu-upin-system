package com.tminos.productscene.sync.executor;

import com.tminos.productscene.sync.entity.TemuPriceReviewOrder;
import com.tminos.productscene.sync.entity.TemuPriceReviewSku;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.repository.TemuPriceReviewOrderRepository;
import com.tminos.productscene.sync.repository.TemuPriceReviewSkuRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class PriceReviewSyncExecutor extends AbstractSyncExecutor<Map<String, Object>> {

    @Autowired private TemuPriceReviewOrderRepository reviewOrderRepo;
    @Autowired private TemuPriceReviewSkuRepository reviewSkuRepo;

    @Override
    protected String getSyncType() { return "PRICE_REVIEW"; }

    @Override
    protected String getPageParamName() { return "pageNo"; }

    @Override
    protected List<Map<String, Object>> doDownload(TemuSyncTask task, TemuOpenApiClient client) throws Exception {
        return doPagedDownload(task, client, TemuOpenApiClient.API_PRICE_REVIEW_QUERY, 50,
                "reviewSamplePriceList");
    }

    @Override
    @Transactional
    protected void doPersistBatch(TemuSyncTask task, List<Map<String, Object>> batch, int batchIndex) {
        for (Map<String, Object> raw : batch) {
            Long orderId = toLong(raw.get("orderId"));
            if (orderId == null) continue;

            TemuPriceReviewOrder order = reviewOrderRepo.findByShopIdAndOrderId(task.getShopId(), orderId)
                    .orElse(new TemuPriceReviewOrder());

            order.setShopId(task.getShopId());
            order.setOrderId(orderId);
            order.setOrderStatus(toInt(raw.get("orderStatus")));
            order.setSupplyPrice(toInt(raw.get("supplyPrice")));
            order.setPriceCurrency(toStr(raw.get("priceCurrency")));
            order.setSuggestSupplyPrice(toInt(raw.get("suggestSupplyPrice")));
            order.setSuggestPriceCurrency(toStr(raw.get("suggestPriceCurrency")));
            order.setCanBargain(toBool(raw.get("canBargain")));
            order.setSiteIdsJson(toJson(raw.get("siteIds")));
            order.setSiteNamesJson(toJson(raw.get("siteNameList")));
            order.setSyncedAt(LocalDateTime.now());

            reviewOrderRepo.save(order);

            reviewSkuRepo.deleteByReviewOrderId(order.getId());
            List<TemuPriceReviewSku> skuRows = buildSkuRows(order.getId(), raw);
            if (!skuRows.isEmpty()) {
                reviewSkuRepo.saveAll(skuRows);
            }
        }
    }

    private List<TemuPriceReviewSku> buildSkuRows(Long reviewOrderId, Map<String, Object> raw) {
        Object productSkuIdList = raw.get("productSkuIdList");
        if (!(productSkuIdList instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }

        List<TemuPriceReviewSku> rows = new ArrayList<>();
        for (Long productSkuId : new LinkedHashSet<>((List<Long>) rawList.stream().map(AbstractSyncExecutor::toLong).filter(v -> v != null).toList())) {
            rows.add(TemuPriceReviewSku.builder()
                    .reviewOrderId(reviewOrderId)
                    .productSkuId(productSkuId)
                    .build());
        }
        return rows;
    }
}
