package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsSkuPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuGoodsSkuPriceRepository extends JpaRepository<TemuGoodsSkuPrice, Long> {

    Optional<TemuGoodsSkuPrice> findByShopIdAndProductSkuId(String shopId, Long productSkuId);

    List<TemuGoodsSkuPrice> findByShopIdAndProductSkuIdIn(String shopId, List<Long> productSkuIds);

    List<TemuGoodsSkuPrice> findByProductId(Long productId);

    List<TemuGoodsSkuPrice> findByProductSkcId(Long productSkcId);

    List<TemuGoodsSkuPrice> findByShopIdAndProductSkcId(String shopId, Long productSkcId);
}
