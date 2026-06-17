package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsSku;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface TemuGoodsSkuRepository extends JpaRepository<TemuGoodsSku, Long> {

    List<TemuGoodsSku> findByShopId(String shopId);

    List<TemuGoodsSku> findByGoodsId(Long goodsId);

    List<TemuGoodsSku> findByGoodsIdIn(List<Long> goodsIds);

    Optional<TemuGoodsSku> findByShopIdAndProductSkuId(String shopId, Long productSkuId);

    List<TemuGoodsSku> findByShopIdAndProductSkuIdIn(String shopId, List<Long> productSkuIds);

    @Query("""
            select sku from TemuGoodsSku sku
            join TemuGoods g on g.id = sku.goodsId
            where sku.shopId = :shopId
                and coalesce(g.skcSiteStatus, 0) = 1
            """)
    List<TemuGoodsSku> findAddedSiteSkusByShopId(@Param("shopId") String shopId);

    @Query(
            value = """
                    select sku from TemuGoodsSku sku
                    join TemuGoods g on g.id = sku.goodsId
                    left join TemuGoodsSkuPrice price
                        on price.shopId = sku.shopId
                       and price.productSkuId = sku.productSkuId
                    left join TemuGoodsSkuSitePrice usSitePrice
                        on usSitePrice.skuPriceId = price.id
                       and usSitePrice.siteId = 100
                    where sku.shopId = :shopId
                      and coalesce(g.skcSiteStatus, 0) = 1
                      and (:productSkcId is null or g.productSkcId = :productSkcId)
                      and (:productSkuId is null or sku.productSkuId = :productSkuId)
                      and (:skuExtCodePattern is null or lower(coalesce(sku.extCode, '')) like :skuExtCodePattern)
                      and (:virtualStockGtZero = false or coalesce(sku.virtualStock, 0) > 0)
                      and (:minSupplierPrice is null or coalesce(usSitePrice.supplierPrice, price.supplierPrice) >= :minSupplierPrice)
                      and (:maxSupplierPrice is null or coalesce(usSitePrice.supplierPrice, price.supplierPrice) <= :maxSupplierPrice)
                    order by
                      case when coalesce(usSitePrice.supplierPrice, price.supplierPrice) is null then 1 else 0 end asc,
                      coalesce(usSitePrice.supplierPrice, price.supplierPrice) asc,
                      coalesce(g.temuCreatedAt, 0) desc,
                      sku.id desc
                    """,
            countQuery = """
                    select count(sku) from TemuGoodsSku sku
                    join TemuGoods g on g.id = sku.goodsId
                    left join TemuGoodsSkuPrice price
                        on price.shopId = sku.shopId
                       and price.productSkuId = sku.productSkuId
                    left join TemuGoodsSkuSitePrice usSitePrice
                        on usSitePrice.skuPriceId = price.id
                       and usSitePrice.siteId = 100
                    where sku.shopId = :shopId
                      and coalesce(g.skcSiteStatus, 0) = 1
                      and (:productSkcId is null or g.productSkcId = :productSkcId)
                      and (:productSkuId is null or sku.productSkuId = :productSkuId)
                      and (:skuExtCodePattern is null or lower(coalesce(sku.extCode, '')) like :skuExtCodePattern)
                      and (:virtualStockGtZero = false or coalesce(sku.virtualStock, 0) > 0)
                      and (:minSupplierPrice is null or coalesce(usSitePrice.supplierPrice, price.supplierPrice) >= :minSupplierPrice)
                      and (:maxSupplierPrice is null or coalesce(usSitePrice.supplierPrice, price.supplierPrice) <= :maxSupplierPrice)
                    """
    )
    Page<TemuGoodsSku> searchAddedSiteSkus(@Param("shopId") String shopId,
                                           @Param("productSkcId") Long productSkcId,
                                           @Param("productSkuId") Long productSkuId,
                                           @Param("skuExtCodePattern") String skuExtCodePattern,
                                           @Param("virtualStockGtZero") boolean virtualStockGtZero,
                                           @Param("minSupplierPrice") Integer minSupplierPrice,
                                           @Param("maxSupplierPrice") Integer maxSupplierPrice,
                                           Pageable pageable);

    @Query("""
            select sku from TemuGoodsSku sku
            join TemuGoods g on g.id = sku.goodsId
            left join TemuGoodsSkuPrice price
                on price.shopId = sku.shopId
               and price.productSkuId = sku.productSkuId
            left join TemuGoodsSkuSitePrice usSitePrice
                on usSitePrice.skuPriceId = price.id
               and usSitePrice.siteId = 100
            where sku.shopId = :shopId
              and coalesce(g.skcSiteStatus, 0) = 1
              and (:productSkcId is null or g.productSkcId = :productSkcId)
              and (:productSkuId is null or sku.productSkuId = :productSkuId)
              and (:skuExtCodePattern is null or lower(coalesce(sku.extCode, '')) like :skuExtCodePattern)
              and (:virtualStockGtZero = false or coalesce(sku.virtualStock, 0) > 0)
              and (:minSupplierPrice is null or coalesce(usSitePrice.supplierPrice, price.supplierPrice) >= :minSupplierPrice)
              and (:maxSupplierPrice is null or coalesce(usSitePrice.supplierPrice, price.supplierPrice) <= :maxSupplierPrice)
            order by
              case when coalesce(usSitePrice.supplierPrice, price.supplierPrice) is null then 1 else 0 end asc,
              coalesce(usSitePrice.supplierPrice, price.supplierPrice) asc,
              coalesce(g.temuCreatedAt, 0) desc,
              sku.id desc
            """)
    List<TemuGoodsSku> findAddedSiteSkusByFilters(@Param("shopId") String shopId,
                                                  @Param("productSkcId") Long productSkcId,
                                                  @Param("productSkuId") Long productSkuId,
                                                  @Param("skuExtCodePattern") String skuExtCodePattern,
                                                  @Param("virtualStockGtZero") boolean virtualStockGtZero,
                                                  @Param("minSupplierPrice") Integer minSupplierPrice,
                                                  @Param("maxSupplierPrice") Integer maxSupplierPrice);

    @Query("""
            select distinct sku.productSkuId from TemuGoodsSku sku
            where sku.shopId = :shopId
              and sku.productSkuId is not null
            order by sku.productSkuId asc
            """)
    List<Long> findDistinctProductSkuIdsByShopId(@Param("shopId") String shopId);

    void deleteByGoodsId(Long goodsId);
}
