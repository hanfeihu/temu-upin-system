package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ProductCollectionTemuSku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductCollectionTemuSkuRepository extends JpaRepository<ProductCollectionTemuSku, Long> {
    List<ProductCollectionTemuSku> findBySpuIdOrderByIdAsc(Long spuId);
    List<ProductCollectionTemuSku> findByTemuSkuIdIn(Collection<String> temuSkuIds);
    void deleteBySpuId(Long spuId);
}
