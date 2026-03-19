package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuAttrRuleDTO;
import com.tminos.productscene.entity.TemuAttrRule;
import com.tminos.productscene.repository.TemuAttrRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class TemuAttrRuleService {

    private final TemuAttrRuleRepository repo;

    public TemuAttrRuleService(TemuAttrRuleRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<TemuAttrRule> list(Boolean enabled, String leafCatId) {
        Boolean en = enabled == null ? Boolean.TRUE : enabled;
        if (StringUtils.hasText(leafCatId)) {
            return repo.findByEnabledAndLeafCatIdOrderBySortOrderAscIdAsc(en, leafCatId.trim());
        }
        return repo.findByEnabledOrderByRuleTypeAscLeafCatIdAscSortOrderAscIdAsc(en);
    }

    @Transactional
    public TemuAttrRule create(TemuAttrRuleDTO.UpsertRequest req) {
        TemuAttrRule e = new TemuAttrRule();
        apply(e, req);
        return repo.save(e);
    }

    @Transactional
    public TemuAttrRule update(Long id, TemuAttrRuleDTO.UpsertRequest req) {
        TemuAttrRule e = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("rule not found"));
        apply(e, req);
        return repo.save(e);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) return;
        repo.deleteById(id);
    }

    private void apply(TemuAttrRule e, TemuAttrRuleDTO.UpsertRequest req) {
        if (req == null) throw new IllegalArgumentException("request is required");
        if (req.getRuleType() == null) throw new IllegalArgumentException("ruleType is required");
        if (!StringUtils.hasText(req.getAttrName())) throw new IllegalArgumentException("attrName is required");
        if (req.getFillMode() == null) throw new IllegalArgumentException("fillMode is required");

        TemuAttrRule.RuleType rt = req.getRuleType();
        String leaf = req.getLeafCatId();
        if (leaf != null) leaf = leaf.trim();
        if (rt == TemuAttrRule.RuleType.FIXED_CATEGORY) {
            if (!StringUtils.hasText(leaf)) {
                throw new IllegalArgumentException("leafCatId is required when ruleType=FIXED_CATEGORY");
            }
        } else {
            leaf = null;
        }

        e.setRuleType(rt);
        e.setLeafCatId(leaf);
        e.setAttrName(req.getAttrName().trim());
        e.setFillMode(req.getFillMode());
        String fv = req.getFixedValue();
        if (fv != null) fv = fv.trim();
        e.setFixedValue(StringUtils.hasText(fv) ? fv : null);
        if (req.getEnabled() != null) e.setEnabled(req.getEnabled());
        if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
    }
}
