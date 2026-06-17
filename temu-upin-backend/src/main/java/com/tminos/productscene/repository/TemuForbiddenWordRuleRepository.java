package com.tminos.productscene.repository;

import com.tminos.productscene.entity.TemuForbiddenWordRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemuForbiddenWordRuleRepository extends JpaRepository<TemuForbiddenWordRule, Long>, JpaSpecificationExecutor<TemuForbiddenWordRule> {
    List<TemuForbiddenWordRule> findByEnabledTrueOrderByIdAsc();
}
