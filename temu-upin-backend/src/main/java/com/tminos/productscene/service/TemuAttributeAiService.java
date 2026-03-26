package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.AITemuAttrFillerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class TemuAttributeAiService {

    private static final Logger log = LoggerFactory.getLogger(TemuAttributeAiService.class);

    private final AITemuAttrFillerConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public TemuAttributeAiService(AITemuAttrFillerConfig config,
                                 ObjectMapper objectMapper,
                                 @Qualifier("aiLongRestTemplate") RestTemplate restTemplate) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public AiFillResult fill(String productName,
                             String attributesDataJson,
                             String originalContentJson,
                             String templateRawJson,
                             Map<String, Object> skuSummary) {
        if (config.getEnabled() == null || !config.getEnabled()) {
            AiFillResult r = new AiFillResult();
            r.setSuccess(false);
            r.setErrorMsg("AI filler disabled");
            return r;
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            AiFillResult r = new AiFillResult();
            r.setSuccess(false);
            r.setErrorMsg("Missing TEMU_ATTR_AI_API_KEY");
            return r;
        }
        if (!StringUtils.hasText(templateRawJson)) {
            AiFillResult r = new AiFillResult();
            r.setSuccess(false);
            r.setErrorMsg("Missing template");
            return r;
        }

        try {
            JsonNode templateRoot = objectMapper.readTree(templateRawJson);
            JsonNode props = templateRoot.path("result").path("properties");
            if (!props.isArray()) {
                AiFillResult r = new AiFillResult();
                r.setSuccess(false);
                r.setErrorMsg("Template properties not found");
                return r;
            }

            List<Map<String, Object>> templateSimplified = new ArrayList<>();
            // Used for post-processing rules.
            Map<Integer, TemplateMeta> templateMetaByPid = new LinkedHashMap<>();
            Map<Integer, List<ParentRule>> parentRulesByTemplatePid = new LinkedHashMap<>();

            // For required backfill and parent inference.
            Map<Integer, TemplatePropInfo> templateByTemplatePid = new LinkedHashMap<>();
            Map<Integer, Integer> vidToOwnerTemplatePid = new LinkedHashMap<>();
            for (JsonNode p : props) {
                Map<String, Object> one = new LinkedHashMap<>();
                Integer pid = p.hasNonNull("pid") ? p.get("pid").asInt() : null;
                Integer templatePid = p.hasNonNull("templatePid") ? p.get("templatePid").asInt() : null;
                Integer refPid = p.hasNonNull("refPid") ? p.get("refPid").asInt() : null;
                one.put("pid", pid);
                one.put("templatePid", templatePid);
                one.put("refPid", refPid);
                one.put("name", text(p, "name"));
                one.put("required", p.path("required").asBoolean(false));
                one.put("chooseMaxNum", p.hasNonNull("chooseMaxNum") ? p.get("chooseMaxNum").asInt() : null);
                one.put("controlType", p.hasNonNull("controlType") ? p.get("controlType").asInt() : null);

                // values: [{vid,value}]
                List<Map<String, Object>> values = new ArrayList<>();
                JsonNode vals = p.get("values");
                if (vals != null && vals.isArray()) {
                    for (JsonNode v : vals) {
                        if (!v.hasNonNull("vid")) continue;
                        Map<String, Object> vv = new LinkedHashMap<>();
                        vv.put("vid", v.get("vid").asInt());
                        vv.put("value", text(v, "value"));
                        values.add(vv);
                        if (values.size() >= 80) break;
                    }
                }
                one.put("values", values);

                // Parent-child constraints (keep small)
                List<ParentRule> parentRules = new ArrayList<>();
                JsonNode parents = p.get("templatePropertyValueParentList");
                if (parents != null && parents.isArray()) {
                    for (JsonNode r : parents) {
                        List<Integer> parentVidList = readIntList(r.get("parentVidList"), 20);
                        List<Integer> vidList = readIntList(r.get("vidList"), 120);
                        if (!parentVidList.isEmpty() && !vidList.isEmpty()) {
                            parentRules.add(new ParentRule(parentVidList, vidList));
                        }
                        if (parentRules.size() >= 8) break;
                    }
                }
                if (templatePid != null && templatePid > 0 && !parentRules.isEmpty()) {
                    parentRulesByTemplatePid.put(templatePid, parentRules);
                    // Also send to AI so it can avoid invalid combos.
                    List<Map<String, Object>> pr = new ArrayList<>();
                    for (ParentRule r : parentRules) {
                        pr.add(Map.of("parentVidList", r.parentVidList, "vidList", r.vidList));
                    }
                    one.put("parentRules", pr);
                }

                List<ShowConditionRule> showConditions = new ArrayList<>();
                JsonNode showConditionNode = p.get("showCondition");
                if (showConditionNode != null && showConditionNode.isArray()) {
                    List<Map<String, Object>> sc = new ArrayList<>();
                    for (JsonNode condition : showConditionNode) {
                        Integer parentRefPid = condition != null && condition.hasNonNull("parentRefPid") ? condition.get("parentRefPid").asInt() : null;
                        List<Integer> parentVids = readIntList(condition == null ? null : condition.get("parentVids"), 20);
                        if (parentRefPid != null && parentRefPid > 0 && !parentVids.isEmpty()) {
                            showConditions.add(new ShowConditionRule(parentRefPid, parentVids));
                            sc.add(Map.of("parentRefPid", parentRefPid, "parentVids", parentVids));
                        }
                    }
                    if (!sc.isEmpty()) {
                        one.put("showConditions", sc);
                    }
                }
                templateSimplified.add(one);

                if (templatePid != null && templatePid > 0) {
                    TemplatePropInfo info = new TemplatePropInfo();
                    info.templatePid = templatePid;
                    info.pid = pid == null ? 0 : pid;
                    info.name = text(p, "name");
                    info.required = p.path("required").asBoolean(false);
                    info.refPid = refPid;
                    info.parentRules = parentRules;
                    info.showConditions = showConditions;
                    info.valueVids = new ArrayList<>();
                    if (vals != null && vals.isArray()) {
                        for (JsonNode v : vals) {
                            int vid = v.path("vid").asInt(0);
                            if (vid > 0) {
                                info.valueVids.add(vid);
                                // first-win mapping is enough for inferring which property owns a parent vid
                                vidToOwnerTemplatePid.putIfAbsent(vid, templatePid);
                            }
                        }
                    }
                    templateByTemplatePid.put(templatePid, info);
                }

                if (pid != null && pid > 0) {
                    boolean required = p.path("required").asBoolean(false);
                    boolean hasValues = values != null && !values.isEmpty();
                    templateMetaByPid.put(pid, new TemplateMeta(required, hasValues));
                }
            }

            Map<String, Object> productCtx = new LinkedHashMap<>();
            productCtx.put("productName", productName);
            productCtx.put("attributes", safeReadMap(attributesDataJson));
            if (skuSummary != null && !skuSummary.isEmpty()) {
                productCtx.put("skuSummary", skuSummary);
            }

            // originalContent may contain packaging, category etc.
            Map<String, Object> original = safeReadMap(originalContentJson);
            if (original != null && !original.isEmpty()) {
                // keep small
                productCtx.put("productId", original.get("productId"));
                productCtx.put("originalCategory", original.get("originalCategory"));
                productCtx.put("productCategory", original.get("productCategory"));
                productCtx.put("companyName", original.get("companyName"));
            }

            // Reduce hallucinations: for the common workflow we only need required attributes.
            // Optional attributes can be filled manually later.
            List<Map<String, Object>> requiredOnly = new ArrayList<>();
            for (Map<String, Object> one : templateSimplified) {
                if (one == null) continue;
                Object req = one.get("required");
                boolean isReq = req instanceof Boolean b && b;
                if (isReq) requiredOnly.add(one);
            }
            String prompt = buildPrompt(productCtx, requiredOnly);

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", defaultModel());
            req.put("messages", List.of(
                    Map.of("role", "system", "content", systemInstruction()),
                    Map.of("role", "user", "content", prompt)
            ));
            req.put("max_tokens", config.getMaxTokens() != null ? config.getMaxTokens() : 2500);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());

            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(req, headers);
            String url = completionsUrl(config.getBaseUrl());

            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, httpEntity, String.class);
            if (resp.getStatusCode() != HttpStatus.OK || !StringUtils.hasText(resp.getBody())) {
                AiFillResult r = new AiFillResult();
                r.setSuccess(false);
                r.setErrorMsg("AI request failed: " + resp.getStatusCode());
                return r;
            }

            String content = extractAssistantContent(resp.getBody());
            if (!StringUtils.hasText(content)) {
                AiFillResult r = new AiFillResult();
                r.setSuccess(false);
                r.setErrorMsg("Empty AI response");
                return r;
            }

            // Expect pure JSON
            Map<String, Object> ai = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filled = ai.get("properties") instanceof List<?> l ? (List<Map<String, Object>>) ai.get("properties") : Collections.emptyList();

            List<Integer> missing = new ArrayList<>();
            if (ai.get("missingRequiredPids") instanceof List<?> l2) {
                for (Object o : l2) {
                    try {
                        missing.add(Integer.valueOf(String.valueOf(o)));
                    } catch (Exception ignored) {
                    }
                }
            }

            List<String> warnings = new ArrayList<>();
            if (ai.get("warnings") instanceof List<?> w) {
                for (Object o : w) {
                    if (o != null) warnings.add(String.valueOf(o));
                }
            }

            // Keep only required templatePids/pids in case model returns extras.
            filled = filterToTemplate(filled, templateByTemplatePid);

            // Post-process: special required input pids must not be 0.
            applyRequiredNonZeroOverrides(filled, templateMetaByPid, warnings);

            // Normalize: attach templatePid when missing (AI may omit it)
            attachTemplatePidIfMissing(filled, templateByTemplatePid);

            // Ensure REQUIRED attributes are filled, even for parent-child rules (best-effort defaults)
            ensureRequiredAttributesFilled(filled, templateByTemplatePid, vidToOwnerTemplatePid, warnings);

            // Post-process: enforce parent-child constraints to avoid TEMU validation errors.
            enforceParentChildConstraints(filled, parentRulesByTemplatePid, warnings);
            enforceShowConditionConstraints(filled, templateByTemplatePid, warnings);

            // After enforcing constraints, required children may have been removed. Backfill again (best-effort)
            // by selecting an appropriate parent and then a valid child value.
            ensureRequiredAttributesFilled(filled, templateByTemplatePid, vidToOwnerTemplatePid, warnings);
            enforceParentChildConstraints(filled, parentRulesByTemplatePid, warnings);
            enforceShowConditionConstraints(filled, templateByTemplatePid, warnings);

            // Final trim: keep required attributes + any selected parent attributes required to satisfy
            // required child constraints. This is critical because a child can be required while its parent
            // is not required; we must still persist the parent selection.
            filled = filterToRequiredPlusSelectedParents(filled, templateByTemplatePid, vidToOwnerTemplatePid);

            // Recompute missing required pids based on our final post-processed output.
            missing = computeMissingRequiredPids(filled, templateByTemplatePid);

            AiFillResult ok = new AiFillResult();
            ok.setSuccess(true);
            ok.setProperties(filled);
            ok.setMissingRequiredPids(missing);
            ok.setWarnings(warnings);
            ok.setRaw(content);
            return ok;

        } catch (Exception e) {
            log.warn("AI fill failed: {}", e.getMessage());
            AiFillResult r = new AiFillResult();
            r.setSuccess(false);
            r.setErrorMsg(e.getMessage());
            return r;
        }
    }

    private void applyRequiredNonZeroOverrides(List<Map<String, Object>> filled,
                                               Map<Integer, TemplateMeta> templateMetaByPid,
                                               List<String> warnings) {
        if (filled == null || filled.isEmpty()) return;
        List<Integer> special = config.getRequiredInputNonZeroPids();
        if (special == null || special.isEmpty()) return;

        String fallback = "1";
        if (StringUtils.hasText(config.getRequiredInputNonZeroDefault())) {
            fallback = config.getRequiredInputNonZeroDefault().trim();
        }
        if (!StringUtils.hasText(fallback)) fallback = "1";

        Set<Integer> specialSet = new HashSet<>(special);
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer pid;
            try {
                Object o = p.get("pid");
                pid = o == null ? null : Integer.valueOf(String.valueOf(o));
            } catch (Exception ignored) {
                pid = null;
            }
            if (pid == null || !specialSet.contains(pid)) continue;

            TemplateMeta meta = templateMetaByPid == null ? null : templateMetaByPid.get(pid);
            boolean required = meta != null && meta.required;
            boolean isInput = meta != null && !meta.hasValues;
            if (!required || !isInput) continue;

            Object free = p.get("freeText");
            String freeText = free == null ? "" : String.valueOf(free).trim();
            if (!StringUtils.hasText(freeText)) {
                p.put("freeText", fallback);
                warnings.add("AI override: pid=" + pid + " freeText was empty; set to " + fallback);
                continue;
            }

            if (isZeroNumber(freeText)) {
                p.put("freeText", fallback);
                warnings.add("AI override: pid=" + pid + " freeText was 0; set to " + fallback);
            }
        }
    }

    private List<Map<String, Object>> filterToTemplate(List<Map<String, Object>> filled,
                                                       Map<Integer, TemplatePropInfo> templateByTemplatePid) {
        if (filled == null) return Collections.emptyList();
        if (templateByTemplatePid == null || templateByTemplatePid.isEmpty()) return filled;
        Set<Integer> validTemplatePids = new HashSet<>(templateByTemplatePid.keySet());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer tp = toInt(p.get("templatePid"));
            if (tp != null && tp > 0) {
                if (validTemplatePids.contains(tp)) out.add(p);
                continue;
            }
            // if no templatePid, keep it (we may attach later by pid)
            out.add(p);
        }
        return out;
    }

    private List<Map<String, Object>> filterToRequiredOnly(List<Map<String, Object>> filled,
                                                           Map<Integer, TemplatePropInfo> templateByTemplatePid) {
        if (filled == null) return Collections.emptyList();
        if (templateByTemplatePid == null || templateByTemplatePid.isEmpty()) return filled;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer tp = toInt(p.get("templatePid"));
            if (tp == null || tp <= 0) continue;
            TemplatePropInfo info = templateByTemplatePid.get(tp);
            if (info != null && info.required) {
                out.add(p);
            }
        }
        return out;
    }

    private List<Map<String, Object>> filterToRequiredPlusSelectedParents(List<Map<String, Object>> filled,
                                                                          Map<Integer, TemplatePropInfo> templateByTemplatePid,
                                                                          Map<Integer, Integer> vidToOwnerTemplatePid) {
        if (filled == null) return Collections.emptyList();
        if (templateByTemplatePid == null || templateByTemplatePid.isEmpty()) return filled;

        Set<Integer> requiredTps = new HashSet<>();
        Set<Integer> possibleParentTps = new HashSet<>();
        for (TemplatePropInfo info : templateByTemplatePid.values()) {
            if (info == null) continue;
            if (info.required) {
                requiredTps.add(info.templatePid);
                if (info.parentRules != null) {
                    for (ParentRule r : info.parentRules) {
                        if (r == null || r.parentVidList == null) continue;
                        for (Integer pv : r.parentVidList) {
                            if (pv == null) continue;
                            Integer parentTp = vidToOwnerTemplatePid == null ? null : vidToOwnerTemplatePid.get(pv);
                            if (parentTp != null && parentTp > 0) {
                                possibleParentTps.add(parentTp);
                            }
                        }
                    }
                }
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer tp = toInt(p.get("templatePid"));
            if (tp == null || tp <= 0) continue;
            if (requiredTps.contains(tp)) {
                out.add(p);
                continue;
            }
            if (possibleParentTps.contains(tp) && hasAnySelection(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private List<Integer> computeMissingRequiredPids(List<Map<String, Object>> filled,
                                                      Map<Integer, TemplatePropInfo> templateByTemplatePid) {
        List<Integer> out = new ArrayList<>();
        if (templateByTemplatePid == null || templateByTemplatePid.isEmpty()) return out;

        // Applicability depends on selected parent vids.
        Set<Integer> selectedVids = collectSelectedVids(filled);
        Map<Integer, Set<Integer>> selectedVidsByRefPid = collectSelectedVidsByRefPid(filled, templateByTemplatePid);

        Map<Integer, Map<String, Object>> filledByTp = new LinkedHashMap<>();
        if (filled != null) {
            for (Map<String, Object> p : filled) {
                if (p == null) continue;
                Integer tp = toInt(p.get("templatePid"));
                if (tp == null || tp <= 0) continue;
                filledByTp.put(tp, p);
            }
        }
        for (TemplatePropInfo info : templateByTemplatePid.values()) {
            if (info == null || !info.required) continue;

            // Required children are only required when applicable under selected parents.
            if (!isApplicable(info, selectedVids, selectedVidsByRefPid)) {
                continue;
            }

            Map<String, Object> cur = filledByTp.get(info.templatePid);
            if (!hasAnySelection(cur)) {
                if (info.pid > 0) out.add(info.pid);
            }
        }
        return out;
    }

    private boolean isApplicable(TemplatePropInfo info,
                                 Set<Integer> selectedVids,
                                 Map<Integer, Set<Integer>> selectedVidsByRefPid) {
        if (info == null) return true;
        if (info.parentRules != null && !info.parentRules.isEmpty()) {
            if (selectedVids == null || selectedVids.isEmpty()) return false;
            boolean parentMatched = false;
            for (ParentRule r : info.parentRules) {
                if (r == null || r.parentVidList == null || r.parentVidList.isEmpty()) continue;
                for (Integer pv : r.parentVidList) {
                    if (pv != null && selectedVids.contains(pv)) {
                        parentMatched = true;
                        break;
                    }
                }
                if (parentMatched) break;
            }
            if (!parentMatched) return false;
        }
        if (info.showConditions != null && !info.showConditions.isEmpty()) {
            for (ShowConditionRule condition : info.showConditions) {
                if (condition == null || condition.parentRefPid == null || condition.parentVids == null || condition.parentVids.isEmpty()) continue;
                Set<Integer> selected = selectedVidsByRefPid == null ? Collections.emptySet() : selectedVidsByRefPid.getOrDefault(condition.parentRefPid, Collections.emptySet());
                boolean matched = false;
                for (Integer pv : condition.parentVids) {
                    if (pv != null && selected.contains(pv)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return false;
            }
        }
        return true;
    }

    private boolean isZeroNumber(String s) {
        if (!StringUtils.hasText(s)) return false;
        String t = s.trim();
        if ("0".equals(t) || "0.0".equals(t) || "0.00".equals(t)) return true;
        try {
            double v = Double.parseDouble(t);
            return Math.abs(v) < 1e-9;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static class TemplateMeta {
        final boolean required;
        final boolean hasValues;

        TemplateMeta(boolean required, boolean hasValues) {
            this.required = required;
            this.hasValues = hasValues;
        }
    }

    private String systemInstruction() {
        return String.join("\n",
                "You are a product listing assistant.",
                "Return ONLY valid JSON. No markdown.",
                "Default language is English.",
                "You MUST fill all REQUIRED attributes whenever possible.",
                "If a REQUIRED attribute has predefined values, you MUST choose from those values.",
                "If a REQUIRED attribute has predefined values and you are unsure, do NOT blindly select the first value. Prefer the safest generic option that matches product data (e.g. 'No', 'None', 'Not included', '无需接电使用', '不带电池'). Only select the first value as a last resort.",
                "If a REQUIRED attribute allows multiple selections (chooseMaxNum > 1), choose 1 to chooseMaxNum values; if unsure, select the FIRST value only.",
                "If a REQUIRED attribute is free-text (no predefined values), write a reasonable short English value inferred from product data; if unsure, write the most generic safe option.",
                "For REQUIRED free-text numeric inputs, NEVER use 0. Use 1 as the default fallback if unsure.",
                "If an attribute is NOT required and you are not confident, leave it empty.",
                "BRAND RULE (strict): NEVER fill any brand-related attribute (name contains 'brand' or the Chinese word '品牌'). Do not guess. Leave it empty.",
                "If a brand-related attribute is REQUIRED, do NOT fill it; add its pid to missingRequiredPids and add a warning (all products are no-brand).",
                "POWER RULE (strict default): Most products are NOT powered. Unless product data clearly indicates an electrical product (keywords like USB/充电/电池/插电/电动/电压/V/W/LED/灯/风扇/加热/温度/电器/智能), you MUST choose non-powered options for power/battery related attributes when such options exist (e.g. choose '无需接电使用', '不带电池', '无需电池'). Avoid selecting any parent option that would make voltage/plug/battery-chemistry child attributes applicable.",
                "PARENT-CHILD RULE: Some attributes have parentRules. If you fill a child attribute, you MUST only choose a vid that is allowed by at least one parentRule given the parent vids you selected elsewhere. If parent isn't selected/known, leave the child empty.",
                "SHOW-CONDITION RULE: Some attributes have showConditions with parentRefPid and parentVids. You MUST fill these attributes only when all referenced parent attributes already select one of the required vids. Otherwise leave them empty and do not include them in missingRequiredPids.",
                "APPLICABILITY RULE: If a child attribute is required but it is NOT applicable under the selected parent options (no matching parent vid selected), then you should NOT fill it and should NOT list it in missingRequiredPids.",
                "When an attribute has predefined values (vid/value), pick ONLY from those values and return selectedVids.",
                "For free-text attributes (no values), return freeText in English.",
                "Output schema:",
                "{\"properties\":[{\"templatePid\":<int|null>,\"pid\":<int>,\"selectedVids\":[<string>...],\"freeText\":<string|null>}],\"missingRequiredPids\":[<int>...],\"warnings\":[<string>...]}");
    }

    private void enforceShowConditionConstraints(List<Map<String, Object>> filled,
                                                 Map<Integer, TemplatePropInfo> templateByTemplatePid,
                                                 List<String> warnings) {
        if (filled == null || filled.isEmpty()) return;
        if (templateByTemplatePid == null || templateByTemplatePid.isEmpty()) return;

        Map<Integer, Set<Integer>> selectedVidsByRefPid = collectSelectedVidsByRefPid(filled, templateByTemplatePid);
        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer templatePid = toInt(p.get("templatePid"));
            TemplatePropInfo info = templatePid == null ? null : templateByTemplatePid.get(templatePid);
            if (info == null || info.showConditions == null || info.showConditions.isEmpty() || !hasAnySelection(p)) {
                kept.add(p);
                continue;
            }

            boolean applicable = true;
            for (ShowConditionRule condition : info.showConditions) {
                if (condition == null || condition.parentRefPid == null || condition.parentVids == null || condition.parentVids.isEmpty()) continue;
                Set<Integer> selected = selectedVidsByRefPid.getOrDefault(condition.parentRefPid, Collections.emptySet());
                boolean matched = false;
                for (Integer vid : condition.parentVids) {
                    if (vid != null && selected.contains(vid)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    applicable = false;
                    break;
                }
            }

            if (!applicable) {
                warnings.add("AI removed attr by showCondition: templatePid=" + templatePid);
                continue;
            }
            kept.add(p);
        }
        filled.clear();
        filled.addAll(kept);
    }

    private void enforceParentChildConstraints(List<Map<String, Object>> filled,
                                               Map<Integer, List<ParentRule>> parentRulesByTemplatePid,
                                               List<String> warnings) {
        if (filled == null || filled.isEmpty()) return;
        if (parentRulesByTemplatePid == null || parentRulesByTemplatePid.isEmpty()) return;

        // Collect all selected vids across filled properties
        Set<Integer> selectedVids = new LinkedHashSet<>();
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Object sv = p.get("selectedVids");
            if (!(sv instanceof List<?> list)) continue;
            for (Object o : list) {
                try {
                    if (o == null) continue;
                    selectedVids.add(Integer.parseInt(String.valueOf(o).trim()));
                } catch (Exception ignored) {
                }
            }
        }

        // Filter invalid child selections
        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer templatePid = null;
            try {
                Object tp = p.get("templatePid");
                if (tp != null) templatePid = Integer.valueOf(String.valueOf(tp));
            } catch (Exception ignored) {
            }
            if (templatePid == null || templatePid <= 0) {
                kept.add(p);
                continue;
            }

            List<ParentRule> rules = parentRulesByTemplatePid.get(templatePid);
            if (rules == null || rules.isEmpty()) {
                kept.add(p);
                continue;
            }

            Object sv = p.get("selectedVids");
            if (!(sv instanceof List<?> list) || list.isEmpty()) {
                kept.add(p);
                continue;
            }

            // Determine allowed vids based on currently selected parent vids
            Set<Integer> allowed = new LinkedHashSet<>();
            boolean hasParentSelected = false;
            for (ParentRule r : rules) {
                for (Integer pv : r.parentVidList) {
                    if (pv != null && selectedVids.contains(pv)) {
                        hasParentSelected = true;
                        allowed.addAll(r.vidList);
                        break;
                    }
                }
            }

            if (!hasParentSelected) {
                // AI shouldn't fill this child without its parent.
                warnings.add("AI removed child attr without parent: templatePid=" + templatePid);
                continue;
            }

            List<String> newSelected = new ArrayList<>();
            for (Object o : list) {
                try {
                    int vid = Integer.parseInt(String.valueOf(o).trim());
                    if (allowed.contains(vid)) {
                        newSelected.add(String.valueOf(vid));
                    }
                } catch (Exception ignored) {
                }
            }
            if (newSelected.isEmpty()) {
                warnings.add("AI removed invalid child selection: templatePid=" + templatePid);
                continue;
            }
            p.put("selectedVids", newSelected);
            kept.add(p);
        }

        filled.clear();
        filled.addAll(kept);
    }

    private void attachTemplatePidIfMissing(List<Map<String, Object>> filled,
                                            Map<Integer, TemplatePropInfo> templateByTemplatePid) {
        if (filled == null || filled.isEmpty()) return;
        if (templateByTemplatePid == null || templateByTemplatePid.isEmpty()) return;

        // Build pid -> first templatePid mapping (good enough for most categories; duplicates exist but rare)
        Map<Integer, Integer> firstTemplatePidByPid = new LinkedHashMap<>();
        for (TemplatePropInfo info : templateByTemplatePid.values()) {
            if (info == null) continue;
            if (info.pid > 0 && info.templatePid != null && info.templatePid > 0) {
                firstTemplatePidByPid.putIfAbsent(info.pid, info.templatePid);
            }
        }

        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer tp = toInt(p.get("templatePid"));
            if (tp != null && tp > 0) continue;
            Integer pid = toInt(p.get("pid"));
            if (pid == null || pid <= 0) continue;
            Integer mapped = firstTemplatePidByPid.get(pid);
            if (mapped != null && mapped > 0) {
                p.put("templatePid", mapped);
            }
        }
    }

    private void ensureRequiredAttributesFilled(List<Map<String, Object>> filled,
                                               Map<Integer, TemplatePropInfo> templateByTemplatePid,
                                               Map<Integer, Integer> vidToOwnerTemplatePid,
                                               List<String> warnings) {
        if (templateByTemplatePid == null || templateByTemplatePid.isEmpty()) return;
        if (filled == null) return;

        // Index filled by templatePid
        Map<Integer, Map<String, Object>> filledByTemplatePid = new LinkedHashMap<>();
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer tp = toInt(p.get("templatePid"));
            if (tp == null || tp <= 0) continue;
            filledByTemplatePid.put(tp, p);
        }

        // Collect selected vids for parent inference
        Set<Integer> selectedVids = collectSelectedVids(filled);
        Map<Integer, Set<Integer>> selectedVidsByRefPid = collectSelectedVidsByRefPid(filled, templateByTemplatePid);

        // Iterate a few times to resolve chains (e.g. 2231 depends on 2230 depends on material)
        for (int iter = 0; iter < 3; iter++) {
            boolean changed = false;
            for (TemplatePropInfo info : templateByTemplatePid.values()) {
                if (info == null || !info.required) continue;

                Map<String, Object> cur = filledByTemplatePid.get(info.templatePid);
                boolean hasValue = hasAnySelection(cur);
                if (hasValue) continue;

                // Required children should NOT force-select a parent when parent isn't selected.
                // If no matching parent is currently selected, treat it as non-applicable.
                if (!isApplicable(info, selectedVids, selectedVidsByRefPid)) {
                    continue;
                }

                // free-text required without values
                if (info.valueVids == null || info.valueVids.isEmpty()) {
                    Map<String, Object> m = ensureItem(filled, filledByTemplatePid, info);
                    // default: generic safe token
                    m.put("freeText", "None");
                    m.put("selectedVids", Collections.emptyList());
                    changed = true;
                    continue;
                }

                // has predefined values
                int chosenVid = info.valueVids.get(0);

                // If this is a parent-constrained attribute and it is applicable (a parent is already selected),
                // try to pick a child vid that matches the active parent rule.
                if (info.parentRules != null && !info.parentRules.isEmpty()) {
                    ParentRule rule = pickRule(info.parentRules, selectedVids);
                    if (rule != null && rule.vidList != null && !rule.vidList.isEmpty()) {
                        for (Integer cv : rule.vidList) {
                            if (cv == null) continue;
                            if (info.valueVids.contains(cv)) {
                                chosenVid = cv;
                                break;
                            }
                        }
                    }
                }

                Map<String, Object> m = ensureItem(filled, filledByTemplatePid, info);
                m.put("selectedVids", List.of(String.valueOf(chosenVid)));
                m.put("freeText", null);
                selectedVids.add(chosenVid);
                if (info.refPid != null && info.refPid > 0) {
                    selectedVidsByRefPid.computeIfAbsent(info.refPid, ignored -> new LinkedHashSet<>()).add(chosenVid);
                }
                changed = true;
            }
            if (!changed) break;
        }

        // If still missing required fields, record warnings
        for (TemplatePropInfo info : templateByTemplatePid.values()) {
            if (info == null || !info.required) continue;
            Map<String, Object> cur = filledByTemplatePid.get(info.templatePid);
            if (!hasAnySelection(cur)) {
                warnings.add("AI required backfill failed: templatePid=" + info.templatePid + " pid=" + info.pid + " name=" + (info.name == null ? "" : info.name));
            }
        }
    }

    private ParentRule pickRule(List<ParentRule> rules, Set<Integer> selectedVids) {
        if (rules == null || rules.isEmpty()) return null;
        if (selectedVids != null && !selectedVids.isEmpty()) {
            for (ParentRule r : rules) {
                if (r == null || r.parentVidList == null) continue;
                for (Integer pv : r.parentVidList) {
                    if (pv != null && selectedVids.contains(pv)) {
                        return r;
                    }
                }
            }
        }
        // No parent selected -> do not guess a parent.
        return null;
    }

    private boolean hasAnySelection(Map<String, Object> p) {
        if (p == null) return false;
        Object free = p.get("freeText");
        if (free != null && StringUtils.hasText(String.valueOf(free))) return true;
        Object sv = p.get("selectedVids");
        if (sv instanceof List<?> l) {
            for (Object o : l) {
                if (o != null && StringUtils.hasText(String.valueOf(o))) return true;
            }
        }
        return false;
    }

    private Map<String, Object> ensureItem(List<Map<String, Object>> filled,
                                          Map<Integer, Map<String, Object>> filledByTemplatePid,
                                          TemplatePropInfo info) {
        Map<String, Object> cur = filledByTemplatePid.get(info.templatePid);
        if (cur != null) return cur;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("templatePid", info.templatePid);
        m.put("pid", info.pid);
        m.put("selectedVids", new ArrayList<>());
        m.put("freeText", null);
        filled.add(m);
        filledByTemplatePid.put(info.templatePid, m);
        return m;
    }

    private Set<Integer> collectSelectedVids(List<Map<String, Object>> filled) {
        Set<Integer> out = new LinkedHashSet<>();
        if (filled == null) return out;
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Object sv = p.get("selectedVids");
            if (!(sv instanceof List<?> list)) continue;
            for (Object o : list) {
                try {
                    if (o == null) continue;
                    out.add(Integer.parseInt(String.valueOf(o).trim()));
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    private Map<Integer, Set<Integer>> collectSelectedVidsByRefPid(List<Map<String, Object>> filled,
                                                                   Map<Integer, TemplatePropInfo> templateByTemplatePid) {
        Map<Integer, Set<Integer>> out = new LinkedHashMap<>();
        if (filled == null || filled.isEmpty()) return out;
        for (Map<String, Object> p : filled) {
            if (p == null) continue;
            Integer templatePid = toInt(p.get("templatePid"));
            TemplatePropInfo info = templatePid == null ? null : templateByTemplatePid.get(templatePid);
            Integer refPid = info == null ? null : info.refPid;
            if (refPid == null || refPid <= 0) continue;
            Object sv = p.get("selectedVids");
            if (!(sv instanceof List<?> list)) continue;
            for (Object o : list) {
                try {
                    if (o == null) continue;
                    out.computeIfAbsent(refPid, ignored -> new LinkedHashSet<>()).add(Integer.parseInt(String.valueOf(o).trim()));
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    private Integer toInt(Object o) {
        try {
            if (o == null) return null;
            String s = String.valueOf(o).trim();
            if (!StringUtils.hasText(s)) return null;
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class TemplatePropInfo {
        Integer templatePid;
        int pid;
        Integer refPid;
        String name;
        boolean required;
        List<Integer> valueVids;
        List<ParentRule> parentRules;
        List<ShowConditionRule> showConditions;
    }

    private static class ParentRule {
        final List<Integer> parentVidList;
        final List<Integer> vidList;

        ParentRule(List<Integer> parentVidList, List<Integer> vidList) {
            this.parentVidList = parentVidList;
            this.vidList = vidList;
        }
    }

    private static class ShowConditionRule {
        final Integer parentRefPid;
        final List<Integer> parentVids;

        ShowConditionRule(Integer parentRefPid, List<Integer> parentVids) {
            this.parentRefPid = parentRefPid;
            this.parentVids = parentVids;
        }
    }

    private List<Integer> readIntList(JsonNode node, int limit) {
        List<Integer> out = new ArrayList<>();
        if (node == null || !node.isArray()) return out;
        for (JsonNode n : node) {
            if (n == null || n.isNull()) continue;
            if (n.isInt() || n.isLong()) {
                out.add(n.asInt());
            } else {
                try {
                    String s = n.asText();
                    if (StringUtils.hasText(s)) out.add(Integer.parseInt(s.trim()));
                } catch (Exception ignored) {
                }
            }
            if (out.size() >= limit) break;
        }
        return out;
    }

    private String buildPrompt(Map<String, Object> productCtx, List<Map<String, Object>> templateSimplified) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product", productCtx);
        payload.put("template", templateSimplified);
        return "Fill TEMU category attributes based on product data and template. Pay attention to parentRules and showConditions, and only fill attributes that are currently applicable.\n\nINPUT_JSON=\n" +
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    }

    private Map<String, Object> safeReadMap(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(json, Map.class);
            return m;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String completionsUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) return "https://cliapi.tminos.com/v1/chat/completions";
        String b = baseUrl.trim();
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/v1")) return b + "/chat/completions";
        return b + "/v1/chat/completions";
    }

    private String defaultModel() {
        if (StringUtils.hasText(config.getModel())) return config.getModel().trim();
        return "gpt-5.2";
    }

    private String extractAssistantContent(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return null;
        JsonNode msg = choices.get(0).path("message");
        String content = msg.path("content").asText(null);
        if (content == null) return null;
        return content.trim();
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    public static class AiFillResult {
        private boolean success;
        private String errorMsg;
        private List<Map<String, Object>> properties;
        private List<Integer> missingRequiredPids;
        private List<String> warnings;
        private String raw;

        public AiFillResult() {}

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
        public List<Map<String, Object>> getProperties() { return properties; }
        public void setProperties(List<Map<String, Object>> properties) { this.properties = properties; }
        public List<Integer> getMissingRequiredPids() { return missingRequiredPids; }
        public void setMissingRequiredPids(List<Integer> missingRequiredPids) { this.missingRequiredPids = missingRequiredPids; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        public String getRaw() { return raw; }
        public void setRaw(String raw) { this.raw = raw; }
    }
}
