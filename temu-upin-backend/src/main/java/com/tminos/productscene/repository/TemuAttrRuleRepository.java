package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuAttrRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemuAttrRuleRepository extends JpaRepository<TemuAttrRule, Long> {

    List<TemuAttrRule> findByEnabledOrderByRuleTypeAscLeafCatIdAscSortOrderAscIdAsc(Boolean enabled);

    List<TemuAttrRule> findByEnabledAndLeafCatIdOrderBySortOrderAscIdAsc(Boolean enabled, String leafCatId);
}
