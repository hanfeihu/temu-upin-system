package com.tminos.productscene.repository;

import com.tminos.productscene.entity.GeneratedImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GeneratedImageRepository extends JpaRepository<GeneratedImage, Long> {
    List<GeneratedImage> findByProductId(Long productId);
    List<GeneratedImage> findByProductIdAndImageType(Long productId, GeneratedImage.ImageType imageType);
    List<GeneratedImage> findBySkuId(Long skuId);
    void deleteByProductId(Long productId);
}
