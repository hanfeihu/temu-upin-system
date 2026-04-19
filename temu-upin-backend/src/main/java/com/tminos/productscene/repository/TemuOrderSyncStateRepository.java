package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuOrderSyncState;
import com.tminos.productscene.entity.TemuOrderSyncType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TemuOrderSyncStateRepository extends JpaRepository<TemuOrderSyncState, Long> {

    Optional<TemuOrderSyncState> findByShopRecordIdAndSyncType(Long shopRecordId, TemuOrderSyncType syncType);
}
