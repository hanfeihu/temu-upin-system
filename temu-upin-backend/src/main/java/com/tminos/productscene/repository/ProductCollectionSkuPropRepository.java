package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ProductCollectionSkuProp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCollectionSkuPropRepository extends JpaRepository<ProductCollectionSkuProp, Long> {
    List<ProductCollectionSkuProp> findBySpuIdOrderBySortAsc(Long spuId);
    void deleteBySpuId(Long spuId);
}
