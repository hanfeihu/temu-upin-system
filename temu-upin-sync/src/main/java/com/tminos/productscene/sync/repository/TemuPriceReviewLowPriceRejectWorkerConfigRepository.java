package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuPriceReviewLowPriceRejectWorkerConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TemuPriceReviewLowPriceRejectWorkerConfigRepository extends JpaRepository<TemuPriceReviewLowPriceRejectWorkerConfig, Long> {

    Optional<TemuPriceReviewLowPriceRejectWorkerConfig> findTopByOrderByUpdatedAtDescIdDesc();
}
