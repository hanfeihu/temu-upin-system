package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoods;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TemuGoodsRepository extends JpaRepository<TemuGoods, Long> {

    Optional<TemuGoods> findByShopIdAndProductSkcId(String shopId, Long productSkcId);

    long countByShopId(String shopId);

    Page<TemuGoods> findByShopId(String shopId, Pageable pageable);

    Page<TemuGoods> findByShopIdAndProductNameContaining(String shopId, String keyword, Pageable pageable);

    @Query("""
            select g from TemuGoods g
            where g.shopId = :shopId
              and (
                :keywordPattern is null
                or lower(g.productName) like :keywordPattern
                or lower(str(g.productId)) like :keywordPattern
                or lower(str(g.productSkcId)) like :keywordPattern
                or lower(coalesce(g.extCode, '')) like :keywordPattern
                or exists (
                    select 1 from TemuGoodsSku sku
                    where sku.goodsId = g.id
                      and (
                        lower(str(sku.productSkuId)) like :keywordPattern
                        or lower(coalesce(sku.extCode, '')) like :keywordPattern
                      )
                )
              )
              and (
                :productSkuId is null
                or exists (
                    select 1 from TemuGoodsSku sku
                    where sku.goodsId = g.id
                      and sku.productSkuId = :productSkuId
                )
              )
              and (:skcSiteStatus is null or coalesce(g.skcSiteStatus, 0) = :skcSiteStatus)
              and (
                :activityBlacklisted is null
                or (:activityBlacklisted = true and exists (
                    select 1 from TemuActivityBlacklist b
                    where b.shopId = g.shopId
                      and b.productId = g.productId
                ))
                or (:activityBlacklisted = false and not exists (
                    select 1 from TemuActivityBlacklist b
                    where b.shopId = g.shopId
                      and b.productId = g.productId
                ))
              )
              and (
                :allSkuOutOfStock is null
                or :allSkuOutOfStock = false
                or (
                    exists (
                        select 1 from TemuGoodsSku sku
                        where sku.goodsId = g.id
                    )
                    and not exists (
                        select 1 from TemuGoodsSku sku
                        where sku.goodsId = g.id
                          and coalesce(sku.virtualStock, 0) > 0
                    )
                )
              )
              and ((:minSupplierPrice is null and :maxSupplierPrice is null)
                or exists (
                select 1 from TemuGoodsSkuPrice p, TemuGoodsSkuSitePrice sp
                where p.shopId = g.shopId
                  and p.productId = g.productId
                  and sp.skuPriceId = p.id
                  and sp.siteId = 100
                  and (:minSupplierPrice is null or sp.supplierPrice >= :minSupplierPrice)
                  and (:maxSupplierPrice is null or sp.supplierPrice <= :maxSupplierPrice)
                ))
            """)
    Page<TemuGoods> searchGoods(
        @Param("shopId") String shopId,
        @Param("keywordPattern") String keywordPattern,
        @Param("productSkuId") Long productSkuId,
        @Param("skcSiteStatus") Integer skcSiteStatus,
        @Param("activityBlacklisted") Boolean activityBlacklisted,
        @Param("allSkuOutOfStock") Boolean allSkuOutOfStock,
        @Param("minSupplierPrice") Integer minSupplierPrice,
        @Param("maxSupplierPrice") Integer maxSupplierPrice,
        Pageable pageable);

    List<TemuGoods> findByShopIdAndProductIdIn(String shopId, List<Long> productIds);

    List<TemuGoods> findByShopIdAndProductSkcIdIn(String shopId, List<Long> productSkcIds);

    Optional<TemuGoods> findByShopIdAndProductId(String shopId, Long productId);

    @Query("""
            select distinct g from TemuGoods g
            join TemuGoodsSku sku on sku.goodsId = g.id
            where g.productId is not null
              and g.shopId is not null
              and coalesce(g.skcSiteStatus, 0) = 1
              and (g.sensitiveAttrConfirmStatus is null or g.sensitiveAttrConfirmStatus in ('PENDING', 'FAILED', 'SKIPPED'))
              and (g.sensitiveAttrConfirmAt is null or g.sensitiveAttrConfirmAt < :retryBefore)
              and coalesce(sku.isSensitive, 0) = 0
              and sku.productSkuId is not null
            order by coalesce(g.syncedAt, g.updatedAt, g.createdAt) desc
            """)
    List<TemuGoods> findSensitiveAttrConfirmCandidates(@Param("retryBefore") LocalDateTime retryBefore,
                                                       Pageable pageable);
}
