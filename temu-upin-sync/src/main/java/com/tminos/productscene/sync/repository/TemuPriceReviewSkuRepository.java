package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuPriceReviewSku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TemuPriceReviewSkuRepository extends JpaRepository<TemuPriceReviewSku, Long> {

    List<TemuPriceReviewSku> findByReviewOrderId(Long reviewOrderId);

    List<TemuPriceReviewSku> findByReviewOrderIdIn(List<Long> reviewOrderIds);

    @Transactional
    @Modifying
    void deleteByReviewOrderId(Long reviewOrderId);
}
