package com.tminos.productscene.service;

import com.tminos.productscene.dto.TemuMainSaleSpecRuleDTO;
import com.tminos.productscene.entity.TemuMainSaleSpecRule;
import com.tminos.productscene.repository.TemuMainSaleSpecRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TemuMainSaleSpecRuleService {

    private final TemuMainSaleSpecRuleRepository repo;

    public TemuMainSaleSpecRuleService(TemuMainSaleSpecRuleRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<TemuMainSaleSpecRule> list(Boolean enabled) {
        Boolean en = enabled == null ? Boolean.TRUE : enabled;
        return repo.findByEnabledOrderByRuleTypeAscIdAsc(en);
    }

    @Transactional(readOnly = true)
    public TemuMainSaleSpecRule findExactEnabledBySignature(String skuSignature) {
        if (!StringUtils.hasText(skuSignature)) return null;
        return repo.findTop1ByEnabledAndSkuSignature(Boolean.TRUE, skuSignature.trim());
    }

    @Transactional
    public TemuMainSaleSpecRule create(TemuMainSaleSpecRuleDTO.UpsertRequest req) {
        TemuMainSaleSpecRule e = new TemuMainSaleSpecRule();
        apply(e, req);
        return repo.save(e);
    }

    @Transactional
    public TemuMainSaleSpecRule update(Long id, TemuMainSaleSpecRuleDTO.UpsertRequest req) {
        TemuMainSaleSpecRule e = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("rule not found"));
        apply(e, req);
        return repo.save(e);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) return;
        repo.deleteById(id);
    }

    @Transactional
    public void upsertLearned(String skuSignature, String parentSpecName, String dimKey, Long runId) {
        if (!StringUtils.hasText(skuSignature) || !StringUtils.hasText(parentSpecName)) return;
        String sig = skuSignature.trim();

        TemuMainSaleSpecRule existing = repo.findTop1ByEnabledAndSkuSignature(Boolean.TRUE, sig);
        if (existing != null) {
            // Do not overwrite MANUAL rules.
            if (existing.getRuleType() == TemuMainSaleSpecRule.RuleType.MANUAL) return;
            existing.setRuleType(TemuMainSaleSpecRule.RuleType.LEARNED);
            existing.setParentSpecName(parentSpecName.trim());
            existing.setDimKey(StringUtils.hasText(dimKey) ? dimKey.trim() : null);
            if (runId != null) existing.setCreatedFromRunId(runId);
            repo.save(existing);
            return;
        }

        TemuMainSaleSpecRule e = new TemuMainSaleSpecRule();
        e.setEnabled(true);
        e.setRuleType(TemuMainSaleSpecRule.RuleType.LEARNED);
        e.setSkuSignature(sig);
        e.setParentSpecName(parentSpecName.trim());
        e.setDimKey(StringUtils.hasText(dimKey) ? dimKey.trim() : null);
        e.setCreatedFromRunId(runId);
        repo.save(e);
    }

    private void apply(TemuMainSaleSpecRule e, TemuMainSaleSpecRuleDTO.UpsertRequest req) {
        if (req == null) throw new IllegalArgumentException("request is required");
        if (!StringUtils.hasText(req.getRuleType())) throw new IllegalArgumentException("ruleType is required");
        if (!StringUtils.hasText(req.getSkuSignature())) throw new IllegalArgumentException("skuSignature is required");
        if (!StringUtils.hasText(req.getParentSpecName())) throw new IllegalArgumentException("parentSpecName is required");

        TemuMainSaleSpecRule.RuleType rt;
        try {
            rt = TemuMainSaleSpecRule.RuleType.valueOf(req.getRuleType().trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid ruleType");
        }
        e.setRuleType(rt);
        e.setSkuSignature(req.getSkuSignature().trim());
        e.setParentSpecName(req.getParentSpecName().trim());
        e.setDimKey(StringUtils.hasText(req.getDimKey()) ? req.getDimKey().trim() : null);
        if (req.getEnabled() != null) e.setEnabled(req.getEnabled());
    }
}
