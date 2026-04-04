package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsSkuSitePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuGoodsSkuSitePriceRepository extends JpaRepository<TemuGoodsSkuSitePrice, Long> {

    Optional<TemuGoodsSkuSitePrice> findBySkuPriceIdAndSiteId(Long skuPriceId, Integer siteId);

    List<TemuGoodsSkuSitePrice> findBySkuPriceId(Long skuPriceId);

    List<TemuGoodsSkuSitePrice> findBySkuPriceIdIn(List<Long> skuPriceIds);

    void deleteBySkuPriceId(Long skuPriceId);
}
