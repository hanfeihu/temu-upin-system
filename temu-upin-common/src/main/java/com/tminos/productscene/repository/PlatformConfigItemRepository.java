package com.tminos.productscene.repository;

import com.tminos.productscene.entity.PlatformConfigItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformConfigItemRepository extends JpaRepository<PlatformConfigItem, Long> {
    List<PlatformConfigItem> findByProfileIdOrderByIdAsc(Long profileId);
    Optional<PlatformConfigItem> findByProfileIdAndConfigKey(Long profileId, String configKey);
    void deleteByProfileId(Long profileId);
}
