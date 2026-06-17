package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688SelectionAiReportWorkerConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface Alibaba1688SelectionAiReportWorkerConfigRepository extends JpaRepository<Alibaba1688SelectionAiReportWorkerConfig, Long> {

    Optional<Alibaba1688SelectionAiReportWorkerConfig> findTopByOrderByUpdatedAtDescIdDesc();
}
