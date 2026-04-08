package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsSkuPriceChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TemuGoodsSkuPriceChangeRepository extends JpaRepository<TemuGoodsSkuPriceChange, Long> {

    List<TemuGoodsSkuPriceChange> findTop200ByShopIdAndProductSkuIdInOrderByChangedAtDesc(String shopId, Collection<Long> productSkuIds);

    long deleteByShopId(String shopId);
}
