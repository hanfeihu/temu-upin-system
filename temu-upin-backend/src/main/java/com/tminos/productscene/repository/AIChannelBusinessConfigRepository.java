package com.tminos.productscene.repository;

import com.tminos.productscene.entity.AIChannelBusinessConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIChannelBusinessConfigRepository extends JpaRepository<AIChannelBusinessConfig, Long> {
    Optional<AIChannelBusinessConfig> findByBusinessCode(String businessCode);

    Optional<AIChannelBusinessConfig> findByBusinessCodeAndEnabledTrue(String businessCode);

    List<AIChannelBusinessConfig> findAllByOrderByIdAsc();
}
