package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuSelfApp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemuSelfAppRepository extends JpaRepository<TemuSelfApp, Long> {

    List<TemuSelfApp> findByEnabledOrderByIdDesc(Boolean enabled);

    boolean existsByAppKey(String appKey);

    boolean existsByAppName(String appName);

    boolean existsByAppKeyAndIdNot(String appKey, Long id);

    boolean existsByAppNameAndIdNot(String appName, Long id);
}
