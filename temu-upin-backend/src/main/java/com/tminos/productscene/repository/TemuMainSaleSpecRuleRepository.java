package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuMainSaleSpecRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemuMainSaleSpecRuleRepository extends JpaRepository<TemuMainSaleSpecRule, Long> {

    TemuMainSaleSpecRule findTop1ByEnabledAndSkuSignature(Boolean enabled, String skuSignature);

    List<TemuMainSaleSpecRule> findByEnabledOrderByRuleTypeAscIdAsc(Boolean enabled);
}
