package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuPriceAdjustOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuPriceAdjustOrderRepository extends JpaRepository<TemuPriceAdjustOrder, Long> {

    Optional<TemuPriceAdjustOrder> findByShopIdAndPriceOrderSn(String shopId, String priceOrderSn);

    Page<TemuPriceAdjustOrder> findByShopId(String shopId, Pageable pageable);

    Page<TemuPriceAdjustOrder> findByShopIdAndStatus(String shopId, Integer status, Pageable pageable);

    Page<TemuPriceAdjustOrder> findByShopIdAndStatusAndReviewAction(String shopId, Integer status, String reviewAction, Pageable pageable);

    Page<TemuPriceAdjustOrder> findByShopIdAndStatusAndReviewActionIn(String shopId, Integer status, List<String> reviewActions, Pageable pageable);

    Page<TemuPriceAdjustOrder> findByShopIdAndReviewAction(String shopId, String reviewAction, Pageable pageable);

    Page<TemuPriceAdjustOrder> findByShopIdAndReviewActionIn(String shopId, List<String> reviewActions, Pageable pageable);

    long countByShopId(String shopId);

    @Query("select o.id from TemuPriceAdjustOrder o where o.shopId = :shopId")
    List<Long> findIdsByShopId(@Param("shopId") String shopId);

    @Modifying
    @Query("delete from TemuPriceAdjustOrder o where o.shopId = :shopId")
    int deleteAllByShopId(@Param("shopId") String shopId);

    @Query("select o from TemuPriceAdjustOrder o where o.shopId = :shopId and (o.reviewAction is null or trim(o.reviewAction) = '')")
    Page<TemuPriceAdjustOrder> findPendingByShopId(@Param("shopId") String shopId, Pageable pageable);

    @Query("select o from TemuPriceAdjustOrder o where o.shopId = :shopId and o.status = :status and (o.reviewAction is null or trim(o.reviewAction) = '')")
    Page<TemuPriceAdjustOrder> findPendingByShopIdAndStatus(@Param("shopId") String shopId,
                                                            @Param("status") Integer status,
                                                            Pageable pageable);

    List<TemuPriceAdjustOrder> findByIdIn(List<Long> ids);

    List<TemuPriceAdjustOrder> findByShopIdAndIdIn(String shopId, List<Long> ids);
}
