package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsSkuSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuGoodsSkuSpecRepository extends JpaRepository<TemuGoodsSkuSpec, Long> {

    List<TemuGoodsSkuSpec> findBySkuId(Long skuId);

    List<TemuGoodsSkuSpec> findBySkuIdIn(List<Long> skuIds);

    void deleteBySkuId(Long skuId);

    void deleteBySkuIdIn(List<Long> skuIds);
}
