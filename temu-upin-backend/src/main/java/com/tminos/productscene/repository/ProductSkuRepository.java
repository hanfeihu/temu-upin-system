package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ProductSku;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {
    List<ProductSku> findByProductId(Long productId);
    Optional<ProductSku> findByProductIdAndSkuCode(Long productId, String skuCode);
}
