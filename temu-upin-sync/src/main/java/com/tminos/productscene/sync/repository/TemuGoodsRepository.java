package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoods;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
              and (:keywordPattern is null or lower(g.productName) like :keywordPattern)
              and (:skcSiteStatus is null or coalesce(g.skcSiteStatus, 0) = :skcSiteStatus)
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
        @Param("skcSiteStatus") Integer skcSiteStatus,
        @Param("minSupplierPrice") Integer minSupplierPrice,
        @Param("maxSupplierPrice") Integer maxSupplierPrice,
        Pageable pageable);

    List<TemuGoods> findByShopIdAndProductIdIn(String shopId, List<Long> productIds);

    Optional<TemuGoods> findByShopIdAndProductId(String shopId, Long productId);
}
