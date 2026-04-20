package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuShopSkuPurchasePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuShopSkuPurchasePriceRepository extends JpaRepository<TemuShopSkuPurchasePrice, Long> {

    Optional<TemuShopSkuPurchasePrice> findByShopIdAndProductSkuId(String shopId, Long productSkuId);

    List<TemuShopSkuPurchasePrice> findByShopIdAndProductSkuIdIn(String shopId, List<Long> productSkuIds);
}
