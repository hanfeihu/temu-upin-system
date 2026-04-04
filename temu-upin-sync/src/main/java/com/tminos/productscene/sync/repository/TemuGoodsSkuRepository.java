package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsSku;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuGoodsSkuRepository extends JpaRepository<TemuGoodsSku, Long> {

    List<TemuGoodsSku> findByShopId(String shopId);

    List<TemuGoodsSku> findByGoodsId(Long goodsId);

    List<TemuGoodsSku> findByGoodsIdIn(List<Long> goodsIds);

    Optional<TemuGoodsSku> findByShopIdAndProductSkuId(String shopId, Long productSkuId);

    List<TemuGoodsSku> findByShopIdAndProductSkuIdIn(String shopId, List<Long> productSkuIds);

        @Query("""
                        select sku from TemuGoodsSku sku
                        join TemuGoods g on g.id = sku.goodsId
                        where sku.shopId = :shopId
                            and coalesce(g.skcSiteStatus, 0) = 1
                        """)
        List<TemuGoodsSku> findAddedSiteSkusByShopId(@Param("shopId") String shopId);

    void deleteByGoodsId(Long goodsId);
}
