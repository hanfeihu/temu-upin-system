package com.tminos.productscene.sync.repository;

import com.tminos.productscene.sync.entity.TemuFreightTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemuFreightTemplateRepository extends JpaRepository<TemuFreightTemplate, Long> {

    Optional<TemuFreightTemplate> findByShopIdAndFreightTemplateId(String shopId, String freightTemplateId);

    List<TemuFreightTemplate> findByShopId(String shopId);
}
