package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuPublishLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuPublishLogRepository extends JpaRepository<TemuPublishLog, Long> {
    List<TemuPublishLog> findByRunIdOrderByIdAsc(Long runId);
    void deleteByRunId(Long runId);
}
