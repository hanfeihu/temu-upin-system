package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuSyncTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuSyncTaskRepository extends JpaRepository<TemuSyncTask, Long> {

    Page<TemuSyncTask> findByShopId(String shopId, Pageable pageable);

    Page<TemuSyncTask> findByShopIdAndSyncType(String shopId, String syncType, Pageable pageable);

    Optional<TemuSyncTask> findFirstByShopIdAndSyncTypeAndStatusIn(String shopId, String syncType, List<String> statuses);

    Optional<TemuSyncTask> findFirstByShopIdAndSyncTypeAndTriggerTypeAndCreatedAtAfter(
            String shopId, String syncType, String triggerType, java.time.LocalDateTime createdAt);
}
