package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TemuOrderRepository extends JpaRepository<TemuOrder, Long>, JpaSpecificationExecutor<TemuOrder> {

    Optional<TemuOrder> findByShopRecordIdAndOrderSn(Long shopRecordId, String orderSn);

    List<TemuOrder> findByShopRecordIdAndParentOrderSnIn(Long shopRecordId, Collection<String> parentOrderSns);

    List<TemuOrder> findByMatchedSpuId(Long matchedSpuId);

    @Query("""
            select
                o.shopRecordId as shopRecordId,
                o.matchedTemuSkuId as matchedTemuSkuId,
                coalesce(sum(o.quantity), 0) as quantity
            from TemuOrder o
            where o.shopRecordId in :shopRecordIds
              and o.matchedTemuSkuId in :matchedTemuSkuIds
              and (o.orderStatus is null or o.orderStatus <> 3)
            group by o.shopRecordId, o.matchedTemuSkuId
            """)
    List<SkuQuantityAggregate> aggregateSalesQuantityByMatchedSku(
            @Param("shopRecordIds") Collection<Long> shopRecordIds,
            @Param("matchedTemuSkuIds") Collection<String> matchedTemuSkuIds
    );

    @Query("""
            select
                o.shopRecordId as shopRecordId,
                o.matchedTemuSkuId as matchedTemuSkuId,
                coalesce(sum(o.quantity), 0) as quantity
            from TemuOrder o
            where o.shopRecordId in :shopRecordIds
              and o.matchedTemuSkuId in :matchedTemuSkuIds
              and (o.orderStatus is null or o.orderStatus <> 3)
              and exists (
                  select a.id
                  from TemuOrderAftersale a
                  where a.shopRecordId = o.shopRecordId
                    and a.parentOrderSn = o.parentOrderSn
                    and a.parentAfterSalesStatus = 5
              )
            group by o.shopRecordId, o.matchedTemuSkuId
            """)
    List<SkuQuantityAggregate> aggregateAftersaleQuantityByMatchedSku(
            @Param("shopRecordIds") Collection<Long> shopRecordIds,
            @Param("matchedTemuSkuIds") Collection<String> matchedTemuSkuIds
    );

    @Query("""
            select
                o.shopRecordId as shopRecordId,
                o.matchedTemuSkuId as matchedTemuSkuId,
                coalesce(sum(o.quantity), 0) as quantity
            from TemuOrder o
            where o.shopRecordId in :shopRecordIds
              and o.matchedTemuSkuId in :matchedTemuSkuIds
              and o.orderStatus in (5, 51)
            group by o.shopRecordId, o.matchedTemuSkuId
            """)
    List<SkuQuantityAggregate> aggregateSignedQuantityByMatchedSku(
            @Param("shopRecordIds") Collection<Long> shopRecordIds,
            @Param("matchedTemuSkuIds") Collection<String> matchedTemuSkuIds
    );

    @Query("""
            select
                o.shopRecordId as shopRecordId,
                o.matchedTemuSkuId as matchedTemuSkuId,
                coalesce(sum(o.quantity), 0) as quantity
            from TemuOrder o
            where o.shopRecordId in :shopRecordIds
              and o.matchedTemuSkuId in :matchedTemuSkuIds
              and o.orderStatus in (5, 51)
              and exists (
                  select a.id
                  from TemuOrderAftersale a
                  where a.shopRecordId = o.shopRecordId
                    and a.parentOrderSn = o.parentOrderSn
                    and a.parentAfterSalesStatus = 5
              )
            group by o.shopRecordId, o.matchedTemuSkuId
            """)
    List<SkuQuantityAggregate> aggregateSignedAftersaleQuantityByMatchedSku(
            @Param("shopRecordIds") Collection<Long> shopRecordIds,
            @Param("matchedTemuSkuIds") Collection<String> matchedTemuSkuIds
    );

    interface SkuQuantityAggregate {
        Long getShopRecordId();

        String getMatchedTemuSkuId();

        Long getQuantity();
    }
}
