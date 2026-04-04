package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuGoodsSkuBarcode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuGoodsSkuBarcodeRepository extends JpaRepository<TemuGoodsSkuBarcode, Long> {

    List<TemuGoodsSkuBarcode> findBySkuId(Long skuId);

    void deleteBySkuId(Long skuId);

    void deleteBySkuIdIn(List<Long> skuIds);
}
