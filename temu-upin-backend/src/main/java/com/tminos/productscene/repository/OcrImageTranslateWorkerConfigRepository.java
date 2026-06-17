package com.tminos.productscene.repository;

import com.tminos.productscene.entity.OcrImageTranslateWorkerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OcrImageTranslateWorkerConfigRepository extends JpaRepository<OcrImageTranslateWorkerConfig, Long> {

    Optional<OcrImageTranslateWorkerConfig> findTopByOrderByUpdatedAtDescIdDesc();
}
