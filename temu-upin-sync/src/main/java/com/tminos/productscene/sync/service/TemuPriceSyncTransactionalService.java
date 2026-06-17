package com.tminos.productscene.sync.service;

import com.tminos.productscene.sync.entity.TemuGoodsSkuPrice;
import com.tminos.productscene.sync.entity.TemuGoodsSkuPriceChange;
import com.tminos.productscene.sync.entity.TemuGoodsSkuSitePrice;
import com.tminos.productscene.sync.entity.TemuSyncTask;
import com.tminos.productscene.sync.repository.TemuGoodsSkuPriceRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuPriceChangeRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuSitePriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
public class TemuPriceSyncTransactionalService {

    private final TemuGoodsSkuPriceRepository skuPriceRepo;
    private final TemuGoodsSkuPriceChangeRepository skuPriceChangeRepo;
    private final TemuGoodsSkuSitePriceRepository skuSitePriceRepo;

    public TemuPriceSyncTransactionalService(TemuGoodsSkuPriceRepository skuPriceRepo,
                                             TemuGoodsSkuPriceChangeRepository skuPriceChangeRepo,
                                             TemuGoodsSkuSitePriceRepository skuSitePriceRepo) {
        this.skuPriceRepo = skuPriceRepo;
        this.skuPriceChangeRepo = skuPriceChangeRepo;
        this.skuSitePriceRepo = skuSitePriceRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistBatch(TemuSyncTask task,
                             List<Map<String, Object>> batch,
                             Function<Object, Long> toLong,
                             Function<Object, Integer> toInt,
                             Function<Object, String> toStr,
                             Function<Object, List<Map<String, Object>>> extractSiteSupplierPrices) {
        persistShopBatch(task == null ? null : task.getShopId(), batch, toLong, toInt, toStr, extractSiteSupplierPrices);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistShopBatch(String shopId,
                                 List<Map<String, Object>> batch,
                                 Function<Object, Long> toLong,
                                 Function<Object, Integer> toInt,
                                 Function<Object, String> toStr,
                                 Function<Object, List<Map<String, Object>>> extractSiteSupplierPrices) {
        for (Map<String, Object> raw : batch) {
            Long productSkuId = toLong.apply(raw.get("productSkuId"));
            if (productSkuId == null) continue;
            Long productId = toLong.apply(raw.get("productId"));
            Long productSkcId = toLong.apply(raw.get("productSkcId"));

            TemuGoodsSkuPrice price = skuPriceRepo.findByShopIdAndProductSkuId(shopId, productSkuId)
                    .orElse(new TemuGoodsSkuPrice());
            Integer oldSupplierPrice = price.getSupplierPrice();
            Integer newSupplierPrice = toInt.apply(raw.get("supplierPrice"));

            price.setShopId(shopId);
            price.setProductId(productId);
            price.setProductSkcId(productSkcId);
            price.setProductSkuId(productSkuId);
            price.setSupplierPrice(newSupplierPrice);
            price.setCurrencyType(toStr.apply(raw.get("currencyType")));
            price.setSyncedAt(LocalDateTime.now());

            price = skuPriceRepo.save(price);

            if (!Objects.equals(oldSupplierPrice, newSupplierPrice)) {
                skuPriceChangeRepo.save(TemuGoodsSkuPriceChange.builder()
                        .shopId(shopId)
                        .productId(productId)
                        .productSkcId(productSkcId)
                        .productSkuId(productSkuId)
                        .skuPriceId(price.getId())
                        .siteId(null)
                        .oldSupplierPrice(oldSupplierPrice)
                        .newSupplierPrice(newSupplierPrice)
                        .changedAt(LocalDateTime.now())
                        .build());
            }

            List<Map<String, Object>> rawSiteSupplierPrices = extractSiteSupplierPrices.apply(raw.get("siteSupplierPrices"));
            LinkedHashMap<Integer, Map<String, Object>> latestBySiteId = new LinkedHashMap<>();
            for (Map<String, Object> siteRaw : rawSiteSupplierPrices) {
                Integer siteId = toInt.apply(siteRaw.get("siteId"));
                if (siteId == null) continue;
                latestBySiteId.put(siteId, siteRaw);
            }

            List<TemuGoodsSkuSitePrice> existingSitePrices = skuSitePriceRepo.findBySkuPriceId(price.getId());
            Map<Integer, TemuGoodsSkuSitePrice> existingBySiteId = new LinkedHashMap<>();
            for (TemuGoodsSkuSitePrice existing : existingSitePrices) {
                if (existing.getSiteId() != null) {
                    existingBySiteId.put(existing.getSiteId(), existing);
                }
            }

            List<Long> staleIds = new ArrayList<>();
            for (TemuGoodsSkuSitePrice existing : existingSitePrices) {
                Integer siteId = existing.getSiteId();
                if (siteId != null && !latestBySiteId.containsKey(siteId)) {
                    staleIds.add(existing.getId());
                }
            }
            if (!staleIds.isEmpty()) {
                skuSitePriceRepo.deleteAllByIdInBatch(staleIds);
            }

            for (Map.Entry<Integer, Map<String, Object>> entry : latestBySiteId.entrySet()) {
                Integer siteId = entry.getKey();
                Map<String, Object> siteRaw = entry.getValue();

                TemuGoodsSkuSitePrice sitePrice = existingBySiteId.get(siteId);
                Integer oldSiteSupplierPrice = sitePrice == null ? null : sitePrice.getSupplierPrice();
                Integer newSiteSupplierPrice = toInt.apply(siteRaw.get("supplierPrice"));
                if (sitePrice == null) {
                    sitePrice = new TemuGoodsSkuSitePrice();
                    sitePrice.setSkuPriceId(price.getId());
                    sitePrice.setSiteId(siteId);
                    sitePrice.setCreatedAt(LocalDateTime.now());
                }
                sitePrice.setSupplierPrice(newSiteSupplierPrice);
                sitePrice.setPriceReviewStatus(toInt.apply(siteRaw.get("priceReviewStatus")));
                sitePrice.setUpdatedAt(LocalDateTime.now());
                skuSitePriceRepo.save(sitePrice);

                if (!Objects.equals(oldSiteSupplierPrice, newSiteSupplierPrice)) {
                    skuPriceChangeRepo.save(TemuGoodsSkuPriceChange.builder()
                            .shopId(shopId)
                            .productId(productId)
                            .productSkcId(productSkcId)
                            .productSkuId(productSkuId)
                            .skuPriceId(price.getId())
                            .siteId(siteId)
                            .oldSupplierPrice(oldSiteSupplierPrice)
                            .newSupplierPrice(newSiteSupplierPrice)
                            .changedAt(LocalDateTime.now())
                            .build());
                }
            }
        }
    }
}
