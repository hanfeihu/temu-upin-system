package com.tminos.productscene.repository;

import com.tminos.productscene.entity.AlibabaImageProxyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlibabaImageProxyConfigRepository extends JpaRepository<AlibabaImageProxyConfig, Long> {

    Optional<AlibabaImageProxyConfig> findTopByOrderByUpdatedAtDescIdDesc();
}
