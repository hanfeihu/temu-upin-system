package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.TemuAttrRule;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class TemuAttrRuleApplyService {

    private final TemuAttrRuleService ruleService;
    private final ObjectMapper objectMapper;

    public TemuAttrRuleApplyService(TemuAttrRuleService ruleService, ObjectMapper objectMapper) {
        this.ruleService = ruleService;
        this.objectMapper = objectMapper;
    }

    public record ApplyResult(List<Map<String, Object>> properties,
                              List<Map<String, Object>> actions) {
    }

    public ApplyResult apply(Long leafCatId, List<Map<String, Object>> properties) {
        List<Map<String, Object>> props = properties == null ? new ArrayList<>() : new ArrayList<>(properties);
        List<Map<String, Object>> actions = new ArrayList<>();

        List<TemuAttrRule> rules = loadRules(leafCatId);
        if (rules.isEmpty() || props.isEmpty()) {
            return new ApplyResult(props, actions);
        }

        for (TemuAttrRule r : rules) {
            if (r == null || r.getFillMode() == null || !StringUtils.hasText(r.getAttrName())) continue;
            String attrName = r.getAttrName().trim();
            int before = props.size();

            if (r.getFillMode() == TemuAttrRule.FillMode.SKIP) {
                props = filterOutByName(props, attrName);
                int removed = before - props.size();
                if (removed > 0) {
                    actions.add(action("SKIP", attrName, "removed", removed, r));
                }
                continue;
            }

            if (r.getFillMode() == TemuAttrRule.FillMode.FORCE_EMPTY) {
                int changed = forceEmptyByName(props, attrName);
                if (changed > 0) {
                    actions.add(action("FORCE_EMPTY", attrName, "cleared", changed, r));
                }
                continue;
            }

            if (r.getFillMode() == TemuAttrRule.FillMode.FIXED_VALUE) {
                String fv = r.getFixedValue();
                if (!StringUtils.hasText(fv)) continue;
                int changed = forceFixedValueByName(props, attrName, fv.trim());
                if (changed > 0) {
                    actions.add(action("FIXED_VALUE", attrName, "set", changed, r));
                }
            }
        }

        return new ApplyResult(props, actions);
    }

    private List<TemuAttrRule> loadRules(Long leafCatId) {
        try {
            List<TemuAttrRule> general = ruleService.list(true, null);
            List<TemuAttrRule> scoped = (leafCatId == null || leafCatId <= 0) ? Collections.emptyList() : ruleService.list(true, String.valueOf(leafCatId));
            List<TemuAttrRule> out = new ArrayList<>();
            if (general != null) out.addAll(general);
            if (scoped != null) out.addAll(scoped);
            out.sort(Comparator.comparingInt(a -> a == null || a.getSortOrder() == null ? 0 : a.getSortOrder()));
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> filterOutByName(List<Map<String, Object>> props, String name) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : props) {
            if (p == null) continue;
            String n = asString(p.get("name"));
            if (StringUtils.hasText(n) && n.trim().equals(name)) {
                continue;
            }
            out.add(p);
        }
        return out;
    }

    private int forceEmptyByName(List<Map<String, Object>> props, String name) {
        int changed = 0;
        for (Map<String, Object> p : props) {
            if (p == null) continue;
            String n = asString(p.get("name"));
            if (!StringUtils.hasText(n) || !n.trim().equals(name)) continue;

            Object free = p.get("freeText");
            if (free != null && StringUtils.hasText(String.valueOf(free))) {
                p.put("freeText", "");
                changed++;
            }
            Object sel = p.get("selectedValues");
            if (sel instanceof List<?> l && !l.isEmpty()) {
                p.put("selectedValues", new ArrayList<>());
                changed++;
            }
            Object vids = p.get("selectedVids");
            if (vids instanceof List<?> l2 && !l2.isEmpty()) {
                p.put("selectedVids", new ArrayList<>());
                changed++;
            }
        }
        return changed;
    }

    private int forceFixedValueByName(List<Map<String, Object>> props, String name, String fixedValue) {
        int changed = 0;
        for (Map<String, Object> p : props) {
            if (p == null) continue;
            String n = asString(p.get("name"));
            if (!StringUtils.hasText(n) || !n.trim().equals(name)) continue;

            p.put("freeText", fixedValue);
            p.put("selectedValues", new ArrayList<>());
            p.put("selectedVids", new ArrayList<>());
            changed++;
        }
        return changed;
    }

    private Map<String, Object> action(String mode, String attrName, String op, int count, TemuAttrRule rule) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("mode", mode);
        a.put("attrName", attrName);
        a.put("op", op);
        a.put("count", count);
        if (rule != null) {
            a.put("ruleId", rule.getId());
            a.put("ruleType", rule.getRuleType() == null ? null : rule.getRuleType().name());
            a.put("leafCatId", rule.getLeafCatId());
            a.put("fillMode", rule.getFillMode() == null ? null : rule.getFillMode().name());
            a.put("fixedValue", rule.getFixedValue());
        }
        return a;
    }

    private String asString(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return StringUtils.hasText(s) ? s : null;
    }
}
