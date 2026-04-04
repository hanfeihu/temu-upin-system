package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuSyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemuSyncLogRepository extends JpaRepository<TemuSyncLog, Long> {

    Page<TemuSyncLog> findByShopId(String shopId, Pageable pageable);

    Page<TemuSyncLog> findByShopIdAndSyncType(String shopId, String syncType, Pageable pageable);

    Page<TemuSyncLog> findByShopIdAndStatus(String shopId, String status, Pageable pageable);
}
