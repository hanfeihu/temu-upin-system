package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuPriceReviewOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuPriceReviewOrderRepository extends JpaRepository<TemuPriceReviewOrder, Long> {

    Optional<TemuPriceReviewOrder> findByShopIdAndOrderId(String shopId, Long orderId);

    Page<TemuPriceReviewOrder> findByShopId(String shopId, Pageable pageable);

    Page<TemuPriceReviewOrder> findByShopIdAndOrderStatus(String shopId, Integer orderStatus, Pageable pageable);

    Page<TemuPriceReviewOrder> findByShopIdAndOrderStatusAndReviewAction(String shopId, Integer orderStatus, String reviewAction, Pageable pageable);

    Page<TemuPriceReviewOrder> findByShopIdAndOrderStatusAndReviewActionIn(String shopId, Integer orderStatus, List<String> reviewActions, Pageable pageable);

    Page<TemuPriceReviewOrder> findByShopIdAndReviewAction(String shopId, String reviewAction, Pageable pageable);

    Page<TemuPriceReviewOrder> findByShopIdAndReviewActionIn(String shopId, List<String> reviewActions, Pageable pageable);

    @Query("select o from TemuPriceReviewOrder o where o.shopId = :shopId and (o.reviewAction is null or trim(o.reviewAction) = '')")
    Page<TemuPriceReviewOrder> findPendingByShopId(@Param("shopId") String shopId, Pageable pageable);

    @Query("select o from TemuPriceReviewOrder o where o.shopId = :shopId and o.orderStatus = :orderStatus and (o.reviewAction is null or trim(o.reviewAction) = '')")
    Page<TemuPriceReviewOrder> findPendingByShopIdAndOrderStatus(@Param("shopId") String shopId,
                                                                 @Param("orderStatus") Integer orderStatus,
                                                                 Pageable pageable);

    List<TemuPriceReviewOrder> findByShopIdAndIdIn(String shopId, List<Long> ids);

    List<TemuPriceReviewOrder> findByIdIn(List<Long> ids);
}
