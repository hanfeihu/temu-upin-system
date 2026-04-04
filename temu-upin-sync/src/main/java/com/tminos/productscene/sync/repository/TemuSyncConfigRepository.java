package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuSyncConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuSyncConfigRepository extends JpaRepository<TemuSyncConfig, Long> {

    Optional<TemuSyncConfig> findByShopIdAndConfigKey(String shopId, String configKey);

    List<TemuSyncConfig> findByShopId(String shopId);
}
