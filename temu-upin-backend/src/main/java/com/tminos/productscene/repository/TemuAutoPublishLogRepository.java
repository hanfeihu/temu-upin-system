package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuAutoPublishLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuAutoPublishLogRepository extends JpaRepository<TemuAutoPublishLog, Long> {
    List<TemuAutoPublishLog> findByRunIdOrderByIdAsc(Long runId);
}
