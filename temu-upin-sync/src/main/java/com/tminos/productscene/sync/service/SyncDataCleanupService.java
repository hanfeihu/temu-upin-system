package com.tminos.productscene.sync.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SyncDataCleanupService {

    private static final List<String> RUNNING_TASK_STATUSES = List.of("PENDING", "DOWNLOADING", "DOWNLOADED", "PERSISTING");
    private static final int BATCH_SIZE = 500;

    @PersistenceContext
    private EntityManager entityManager;

    private final TemuGoodsRepairJobService goodsRepairJobService;

    public SyncDataCleanupService(TemuGoodsRepairJobService goodsRepairJobService) {
        this.goodsRepairJobService = goodsRepairJobService;
    }

    @Transactional
    public Map<String, Object> clearShopSyncData(String shopId) {
        String safeShopId = normalizeShopId(shopId);
        validateNoRunningJobs(safeShopId);

        List<Long> goodsIds = selectIds("select g.id from TemuGoods g where g.shopId = :shopId", safeShopId);
        List<Long> skuIds = goodsIds.isEmpty()
                ? List.of()
                : selectIdsByList("select s.id from TemuGoodsSku s where s.goodsId in :ids", "ids", goodsIds);
        List<Long> skuPriceIds = selectIds("select p.id from TemuGoodsSkuPrice p where p.shopId = :shopId", safeShopId);
        List<Long> reviewOrderIds = selectIds("select o.id from TemuPriceReviewOrder o where o.shopId = :shopId", safeShopId);
        List<Long> adjustOrderIds = selectIds("select o.id from TemuPriceAdjustOrder o where o.shopId = :shopId", safeShopId);
        List<Long> activityIds = selectIds("select a.id from TemuActivity a where a.shopId = :shopId", safeShopId);
        List<Long> enrollmentIds = selectIds("select e.id from TemuActivityEnrollment e where e.shopId = :shopId", safeShopId);
        List<Long> taskIds = selectIds("select t.id from TemuSyncTask t where t.shopId = :shopId", safeShopId);

        LinkedHashMap<String, Integer> deleted = new LinkedHashMap<>();
        deleted.put("goodsSkuSpecs", deleteByIds("delete from TemuGoodsSkuSpec s where s.skuId in :ids", "ids", skuIds));
        deleted.put("goodsSkuBarcodes", deleteByIds("delete from TemuGoodsSkuBarcode b where b.skuId in :ids", "ids", skuIds));
        deleted.put("goodsSites", deleteByIds("delete from TemuGoodsSite s where s.goodsId in :ids", "ids", goodsIds));
        deleted.put("goodsProperties", deleteByIds("delete from TemuGoodsProperty p where p.goodsId in :ids", "ids", goodsIds));
        deleted.put("goodsDecorations", deleteByIds("delete from TemuGoodsDecoration d where d.goodsId in :ids", "ids", goodsIds));
        deleted.put("goodsSkuSitePrices", deleteByIds("delete from TemuGoodsSkuSitePrice sp where sp.skuPriceId in :ids", "ids", skuPriceIds));
        deleted.put("goodsSkuPrices", deleteByShopId("delete from TemuGoodsSkuPrice p where p.shopId = :shopId", safeShopId));
        deleted.put("goodsSkus", deleteByIds("delete from TemuGoodsSku s where s.goodsId in :ids", "ids", goodsIds));
        deleted.put("goodsLifecycles", deleteByShopId("delete from TemuGoodsLifecycle l where l.shopId = :shopId", safeShopId));
        deleted.put("goods", deleteByShopId("delete from TemuGoods g where g.shopId = :shopId", safeShopId));

        deleted.put("freightTemplates", deleteByShopId("delete from TemuFreightTemplate f where f.shopId = :shopId", safeShopId));
        deleted.put("warehouses", deleteByShopId("delete from TemuWarehouse w where w.shopId = :shopId", safeShopId));

        deleted.put("priceReviewSkus", deleteByIds("delete from TemuPriceReviewSku s where s.reviewOrderId in :ids", "ids", reviewOrderIds));
        deleted.put("priceReviewOrders", deleteByShopId("delete from TemuPriceReviewOrder o where o.shopId = :shopId", safeShopId));

        deleted.put("priceAdjustSkus", deleteByIds("delete from TemuPriceAdjustSku s where s.adjustOrderId in :ids", "ids", adjustOrderIds));
        deleted.put("priceAdjustOrders", deleteByShopId("delete from TemuPriceAdjustOrder o where o.shopId = :shopId", safeShopId));

        deleted.put("activityEnrollPrices", deleteByIds("delete from TemuActivityEnrollPrice p where p.enrollmentId in :ids", "ids", enrollmentIds));
        deleted.put("activityEnrollments", deleteByShopId("delete from TemuActivityEnrollment e where e.shopId = :shopId", safeShopId));
        deleted.put("activityThematics", deleteByIds("delete from TemuActivityThematic t where t.activityId in :ids", "ids", activityIds));
        deleted.put("activitySessions", deleteByShopId("delete from TemuActivitySession s where s.shopId = :shopId", safeShopId));
        deleted.put("activities", deleteByShopId("delete from TemuActivity a where a.shopId = :shopId", safeShopId));

        deleted.put("syncStepLogs", deleteByIds("delete from TemuSyncStepLog l where l.taskId in :ids", "ids", taskIds));
        deleted.put("syncLogs", deleteByShopId("delete from TemuSyncLog l where l.shopId = :shopId", safeShopId));
        deleted.put("syncTasks", deleteByShopId("delete from TemuSyncTask t where t.shopId = :shopId", safeShopId));

        int totalDeleted = deleted.values().stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("shopId", safeShopId);
        result.put("deleted", deleted);
        result.put("totalDeleted", totalDeleted);
        result.put("message", String.format("已按店铺清空同步数据，共删除 %d 条记录", totalDeleted));
        result.put("note", "同步配置、店铺配置和平台凭证未删除");
        return result;
    }

    private void validateNoRunningJobs(String shopId) {
        Long runningTaskCount = entityManager.createQuery(
                        "select count(t.id) from TemuSyncTask t where t.shopId = :shopId and t.status in :statuses",
                        Long.class)
                .setParameter("shopId", shopId)
                .setParameter("statuses", RUNNING_TASK_STATUSES)
                .getSingleResult();
        if (runningTaskCount != null && runningTaskCount > 0) {
            throw new IllegalStateException("当前店铺仍有执行中的同步任务，请等待完成或先取消任务后再清空数据");
        }

        if (goodsRepairJobService.isRepairRunning(shopId)) {
            throw new IllegalStateException("当前店铺仍有进行中的商品明细回填任务，请等待完成后再清空数据");
        }
    }

    private String normalizeShopId(String shopId) {
        if (shopId == null || shopId.isBlank()) {
            throw new IllegalArgumentException("shopId不能为空");
        }
        return shopId.trim();
    }

    private List<Long> selectIds(String jpql, String shopId) {
        return entityManager.createQuery(jpql, Long.class)
                .setParameter("shopId", shopId)
                .getResultList();
    }

    private List<Long> selectIdsByList(String jpql, String parameterName, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
            TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
            query.setParameter(parameterName, batch);
            result.addAll(query.getResultList());
        }
        return result;
    }

    private int deleteByShopId(String jpql, String shopId) {
        return entityManager.createQuery(jpql)
                .setParameter("shopId", shopId)
                .executeUpdate();
    }

    private int deleteByIds(String jpql, String parameterName, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
            deleted += entityManager.createQuery(jpql)
                    .setParameter(parameterName, batch)
                    .executeUpdate();
        }
        return deleted;
    }
}