package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuPriceAdjustSku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TemuPriceAdjustSkuRepository extends JpaRepository<TemuPriceAdjustSku, Long> {

    List<TemuPriceAdjustSku> findByAdjustOrderId(Long adjustOrderId);

    List<TemuPriceAdjustSku> findByAdjustOrderIdIn(List<Long> adjustOrderIds);

    long countByAdjustOrderIdIn(List<Long> adjustOrderIds);

    @Transactional
    @Modifying
    void deleteByAdjustOrderId(Long adjustOrderId);

    @Transactional
    @Modifying
    @Query("delete from TemuPriceAdjustSku s where s.adjustOrderId in :adjustOrderIds")
    int deleteByAdjustOrderIdIn(@Param("adjustOrderIds") List<Long> adjustOrderIds);
}
