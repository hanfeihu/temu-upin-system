package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688SelectionPoolSku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface Alibaba1688SelectionPoolSkuRepository extends JpaRepository<Alibaba1688SelectionPoolSku, Long> {

    List<Alibaba1688SelectionPoolSku> findAllByPoolIdOrderByIsPrimarySkuDescIdAsc(Long poolId);

    @Query("""
            select distinct s.poolId
            from Alibaba1688SelectionPoolSku s
            where s.skuPriceSnapshot is not null
              and (:minPrice is null or s.skuPriceSnapshot >= :minPrice)
              and (:maxPrice is null or s.skuPriceSnapshot <= :maxPrice)
            """)
    List<Long> findDistinctPoolIdsBySkuPriceRange(
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice
    );

    @Query("""
            select s.poolId as poolId,
                   count(s.id) as skuCount
            from Alibaba1688SelectionPoolSku s
            where s.poolId in :poolIds
            group by s.poolId
            """)
    List<PoolSkuCountView> countByPoolIds(@Param("poolIds") Collection<Long> poolIds);

    @Query("""
            select s.poolId as poolId,
                   min(s.skuPriceSnapshot) as minPrice,
                   max(s.skuPriceSnapshot) as maxPrice
            from Alibaba1688SelectionPoolSku s
            where s.poolId in :poolIds
              and s.skuPriceSnapshot is not null
            group by s.poolId
            """)
    List<PoolSkuPriceRangeView> summarizePriceRangeByPoolIds(@Param("poolIds") Collection<Long> poolIds);

    interface PoolSkuPriceRangeView {
        Long getPoolId();

        BigDecimal getMinPrice();

        BigDecimal getMaxPrice();
    }

    interface PoolSkuCountView {
        Long getPoolId();

        long getSkuCount();
    }
}
