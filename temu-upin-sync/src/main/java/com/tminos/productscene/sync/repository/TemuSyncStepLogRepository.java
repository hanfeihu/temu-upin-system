package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuSyncStepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuSyncStepLogRepository extends JpaRepository<TemuSyncStepLog, Long> {

    List<TemuSyncStepLog> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    List<TemuSyncStepLog> findTop20ByTaskIdOrderByCreatedAtDesc(Long taskId);
}
