package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ImageConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImageConfigRepository extends JpaRepository<ImageConfig, Long> {
    List<ImageConfig> findByImageType(ImageConfig.ImageType imageType);
    List<ImageConfig> findByIsDefaultTrue();
    Optional<ImageConfig> findByImageTypeAndIsDefaultTrue(ImageConfig.ImageType imageType);
}
