package com.tminos.productscene.repository;

import com.tminos.productscene.entity.LogisticsProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LogisticsProviderConfigRepository extends JpaRepository<LogisticsProviderConfig, Long> {

    Optional<LogisticsProviderConfig> findByProviderCode(String providerCode);

    List<LogisticsProviderConfig> findByEnabledOrderByIdDesc(Boolean enabled);
}
