package com.tminos.productscene.repository;

import com.tminos.productscene.entity.ImageOcrSizeFilterConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImageOcrSizeFilterConfigRepository extends JpaRepository<ImageOcrSizeFilterConfig, Long> {

    List<ImageOcrSizeFilterConfig> findByEnabledTrueOrderByImageWidthAscImageHeightAsc();

    List<ImageOcrSizeFilterConfig> findAllByOrderByImageWidthAscImageHeightAsc();

    Optional<ImageOcrSizeFilterConfig> findByImageWidthAndImageHeight(Integer imageWidth, Integer imageHeight);

    boolean existsByImageWidthAndImageHeightAndEnabledTrue(Integer imageWidth, Integer imageHeight);
}
