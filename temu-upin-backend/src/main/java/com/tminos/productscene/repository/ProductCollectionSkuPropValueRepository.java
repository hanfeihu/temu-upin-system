package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ProductCollectionSkuPropValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductCollectionSkuPropValueRepository extends JpaRepository<ProductCollectionSkuPropValue, Long> {
    List<ProductCollectionSkuPropValue> findByPropIdOrderBySortAsc(Long propId);
    List<ProductCollectionSkuPropValue> findByPropIdIn(Collection<Long> propIds);
    void deleteByPropIdIn(Collection<Long> propIds);
}
