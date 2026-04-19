package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.TemuAttrAiFillTask;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.tminos.productscene.repository.TemuAttrAiFillTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TemuAttrAiFillService {

    private static final Logger log = LoggerFactory.getLogger(TemuAttrAiFillService.class);

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;

    private final TemuAttrAiFillTaskRepository taskRepo;
    private final ProductCollectionRepository productRepo;
    private final ProductCollectionService productCollectionService;
    private final TemuAttributeAiService aiService;
    private final TemuAttrRuleApplyService ruleApplyService;
    private final ObjectMapper objectMapper;

    public TemuAttrAiFillService(TemuAttrAiFillTaskRepository taskRepo,
                                ProductCollectionRepository productRepo,
                                ProductCollectionService productCollectionService,
                                TemuAttributeAiService aiService,
                                TemuAttrRuleApplyService ruleApplyService,
                                ObjectMapper objectMapper) {
        this.taskRepo = taskRepo;
        this.productRepo = productRepo;
        this.productCollectionService = productCollectionService;
        this.aiService = aiService;
        this.ruleApplyService = ruleApplyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TemuAttrAiFillTask createTask(Long spuId) {
        if (spuId == null) throw new IllegalArgumentException("spuId is required");
        ProductCollection pc = productRepo.findById(spuId)
                .orElseThrow(() -> new IllegalStateException("spu not found: " + spuId));

        TemuAttrAiFillTask t = new TemuAttrAiFillTask();
        t.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        t.setSpuId(spuId);
        t.setProductName(pc.getProductName());
        t.setProductMainImage(pc.getProductMainImage());
        t.setStatus(STATUS_PENDING);
        t.setLeafCatId(parseLeafCatId(pc.getTemuCatid()));
        t.setCreatedAt(LocalDateTime.now());
        return taskRepo.save(t);
    }

    @Transactional(readOnly = true)
    public Page<TemuAttrAiFillTask> list(String q, Integer status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        String keyword = StringUtils.hasText(q) ? q.trim() : null;
        return taskRepo.search(keyword, status, pageable);
    }

    @Transactional(readOnly = true)
    public TemuAttrAiFillTask get(Long id) {
        if (id == null) return null;
        return taskRepo.findById(id).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EnsureForPublishResult ensureReadyForPublish(Long spuId, boolean applyToProductWhenMissing) {
        if (spuId == null) throw new IllegalArgumentException("spuId is required");
        ProductCollection pc = productRepo.findById(spuId)
                .orElseThrow(() -> new IllegalStateException("spu not found: " + spuId));

        Long currentLeafCatId = parseLeafCatId(pc.getTemuCatid());
        TemuAttrAiFillTask task = taskRepo.findFirstBySpuIdOrderByIdDesc(spuId).orElse(null);

        boolean created = false;
        boolean executed = false;
        boolean applied = false;

        if (!isUsableForLeaf(task, currentLeafCatId)) {
            task = createTask(spuId);
            created = true;
        }

        if (!StringUtils.hasText(task.getResultJson())) {
            task = runOnce(task.getId());
            executed = true;
        }

        if (applyToProductWhenMissing
                && !StringUtils.hasText(pc.getTemuAttributes())
                && task != null
                && StringUtils.hasText(task.getResultJson())) {
            productCollectionService.saveTemuAttributes(spuId, task.getResultJson());
            applied = true;
        }

        return new EnsureForPublishResult(task, created, executed, applied);
    }

    @Transactional
    public TemuAttrAiFillTask runOnce(Long taskId) {
        TemuAttrAiFillTask t = taskRepo.findById(taskId).orElse(null);
        if (t == null) throw new IllegalStateException("task not found: " + taskId);
        if (t.getStatus() != null && t.getStatus() == STATUS_DONE) return t;

        t.setStatus(STATUS_RUNNING);
        t.setStartedAt(LocalDateTime.now());
        t.setFinishedAt(null);
        t.setErrorMsg(null);
        t.setUpdatedAt(LocalDateTime.now());
        taskRepo.save(t);

        try {
            ProductCollection pc = productRepo.findById(t.getSpuId())
                    .orElseThrow(() -> new IllegalStateException("spu not found: " + t.getSpuId()));

            String templateRaw = productCollectionService.getTemuCategoryAttributesRaw(t.getSpuId());
            t.setTemplateRaw(templateRaw);
            if (!StringUtils.hasText(templateRaw)) {
                throw new IllegalStateException("Missing TEMU attribute template (temuCatid not set or template fetch failed)");
            }

            Map<String, Object> skuSummary = buildSkuSummaryForAiSafe(t.getSpuId());
            TemuAttributeAiService.AiFillResult r = aiService.fill(
                    pc.getProductName(),
                    pc.getAttributesData(),
                    pc.getOriginalContent(),
                    templateRaw,
                    skuSummary
            );

            Map<String, Object> parsed = new LinkedHashMap<>();
            parsed.put("success", r != null && r.isSuccess());
            parsed.put("errorMsg", r == null ? "empty" : r.getErrorMsg());
            parsed.put("properties", r == null || r.getProperties() == null ? List.of() : r.getProperties());
            parsed.put("missingRequiredPids", r == null || r.getMissingRequiredPids() == null ? List.of() : r.getMissingRequiredPids());
            parsed.put("warnings", r == null || r.getWarnings() == null ? List.of() : r.getWarnings());
            t.setParsedJson(objectMapper.writeValueAsString(parsed));
            t.setResponseRaw(r == null ? null : r.getRaw());

            if (r == null || !r.isSuccess()) {
                String em = r == null ? "AI fill failed" : r.getErrorMsg();
                throw new IllegalStateException(StringUtils.hasText(em) ? em : "AI fill failed");
            }

            TemuAttrRuleApplyService.ApplyResult applied = ruleApplyService.apply(t.getLeafCatId(), r.getProperties());
            t.setRuleActions(objectMapper.writeValueAsString(applied.actions()));

            String resultJson = buildTemuAttributesJson(t.getSpuId(), templateRaw, applied.properties(), r.getMissingRequiredPids());
            t.setResultJson(resultJson);
            t.setResultSummary(summaryFromResult(resultJson, applied.actions()));

            t.setStatus(STATUS_DONE);
            t.setFinishedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            return taskRepo.save(t);
        } catch (Exception e) {
            log.warn("TemuAttrAiFillService runOnce failed taskId={}: {}", taskId, e.getMessage());
            t.setErrorMsg(e.getMessage());
            t.setStatus(STATUS_DONE);
            t.setFinishedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            return taskRepo.save(t);
        }
    }

    private Map<String, Object> buildSkuSummaryForAiSafe(Long spuId) {
        try {
            java.lang.reflect.Method m = productCollectionService.getClass().getDeclaredMethod("buildSkuSummaryForAi", Long.class);
            m.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) m.invoke(productCollectionService, spuId);
            return out == null ? Collections.emptyMap() : out;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private long parseLeafCatId(String temuCatid) {
        if (!StringUtils.hasText(temuCatid)) return 0;
        String[] parts = temuCatid.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String s = parts[i] == null ? "" : parts[i].trim();
            if (s.isEmpty()) continue;
            try {
                long v = Long.parseLong(s);
                if (v > 0) return v;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private boolean isUsableForLeaf(TemuAttrAiFillTask task, Long currentLeafCatId) {
        if (task == null) return false;
        if (task.getStatus() == null || task.getStatus() != STATUS_DONE) return false;
        if (!StringUtils.hasText(task.getResultJson())) return false;
        long taskLeaf = task.getLeafCatId() == null ? 0L : task.getLeafCatId();
        long currentLeaf = currentLeafCatId == null ? 0L : currentLeafCatId;
        return taskLeaf > 0 && taskLeaf == currentLeaf;
    }

    private String summaryFromResult(String resultJson, List<Map<String, Object>> actions) {
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            int propCount = root.path("properties").isArray() ? root.path("properties").size() : 0;
            int actionCount = actions == null ? 0 : actions.size();
            String leaf = root.path("leafCatId").asText("");
            return "leafCatId=" + leaf + ", properties=" + propCount + ", ruleActions=" + actionCount;
        } catch (Exception ignored) {
            int actionCount = actions == null ? 0 : actions.size();
            return "resultJsonLen=" + (resultJson == null ? 0 : resultJson.length()) + ", ruleActions=" + actionCount;
        }
    }

    private String buildTemuAttributesJson(Long spuId,
                                          String templateRaw,
                                          List<Map<String, Object>> filled,
                                          List<Integer> missingRequiredPids) throws Exception {
        if (spuId == null) throw new IllegalArgumentException("spuId is required");
        if (!StringUtils.hasText(templateRaw)) throw new IllegalArgumentException("templateRaw is required");

        ProductCollection pc = productCollectionService.get(spuId);
        String leafCatId = extractLeafCatId(pc == null ? null : pc.getTemuCatid());

        JsonNode root = objectMapper.readTree(templateRaw);
        JsonNode props = root.path("result").path("properties");
        if (!props.isArray()) throw new IllegalStateException("Template properties not found");

        Map<Integer, JsonNode> templateByPid = new LinkedHashMap<>();
        Map<Integer, JsonNode> templateByTemplatePid = new LinkedHashMap<>();
        for (JsonNode p : props) {
            int pid = p.path("pid").asInt(0);
            int templatePid = p.path("templatePid").asInt(0);
            if (pid > 0) templateByPid.put(pid, p);
            if (templatePid > 0) templateByTemplatePid.put(templatePid, p);
        }

        filled = pruneInapplicableFilledProps(filled, templateByPid, templateByTemplatePid);

        Set<String> selectedVidsAll = new LinkedHashSet<>();
        Map<Integer, Set<String>> selectedVidsByRefPid = new LinkedHashMap<>();
        if (filled != null) {
            for (Map<String, Object> p : filled) {
                if (p == null) continue;
                JsonNode tpl = resolveTemplateNode(p, templateByPid, templateByTemplatePid);
                Object sv = p.get("selectedVids");
                if (!(sv instanceof List<?> list)) continue;
                Integer refPid = tpl == null ? null : toInt(tpl.path("refPid").asText(""));
                for (Object o : list) {
                    if (o == null) continue;
                    String s = String.valueOf(o).trim();
                    if (!StringUtils.hasText(s)) continue;
                    selectedVidsAll.add(s);
                    if (refPid != null && refPid > 0) {
                        selectedVidsByRefPid.computeIfAbsent(refPid, ignored -> new LinkedHashSet<>()).add(s);
                    }
                }
            }
        }

        Set<Integer> filledPids = new HashSet<>();
        if (filled != null) {
            for (Map<String, Object> p : filled) {
                if (p == null) continue;
                Integer pid = toInt(p.get("pid"));
                if (pid == null || pid <= 0) continue;
                if (hasAnySelection(p)) {
                    filledPids.add(pid);
                }
            }
        }

        List<Integer> miss = missingRequiredPids == null ? Collections.emptyList() : missingRequiredPids;
        List<Integer> effectiveMissing = new ArrayList<>();
        for (Integer m : miss) {
            if (m == null) continue;
            if (filledPids.contains(m)) continue;

            JsonNode tpl = templateByPid.get(m);
            if (tpl != null && !isTemplateApplicable(tpl, selectedVidsAll, selectedVidsByRefPid)) {
                continue;
            }
            effectiveMissing.add(m);
        }

        if (!effectiveMissing.isEmpty()) {
            throw new IllegalStateException("Missing required attributes: " + effectiveMissing);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leafCatId", leafCatId);
        out.put("savedAt", java.time.OffsetDateTime.now().toString());
        List<Map<String, Object>> outProps = new ArrayList<>();
        Set<String> emittedKeys = new LinkedHashSet<>();

        if (filled != null) {
            for (Map<String, Object> p : filled) {
                if (p == null) continue;
                Integer pid = toInt(p.get("pid"));
                if (pid == null || pid <= 0) continue;
            Integer filledTemplatePid = toInt(p.get("templatePid"));

            JsonNode tpl = filledTemplatePid != null && filledTemplatePid > 0
                ? templateByTemplatePid.get(filledTemplatePid)
                : null;
            if (tpl == null) {
                tpl = templateByPid.get(pid);
            }
                if (tpl == null) continue;
                if (!isTemplateApplicable(tpl, selectedVidsAll, selectedVidsByRefPid)) continue;

                Map<String, Object> one = new LinkedHashMap<>();
                one.put("pid", pid);
                one.put("templatePid", tpl.path("templatePid").asInt(0));
                one.put("refPid", tpl.path("refPid").asInt(0));
                one.put("name", tpl.path("name").asText(""));
                one.put("required", tpl.path("required").asBoolean(false));

                String valueUnit = "";
                JsonNode vu = tpl.path("valueUnit");
                if (vu.isArray() && vu.size() > 0) valueUnit = vu.get(0).asText("");
                one.put("valueUnit", valueUnit);

                @SuppressWarnings("unchecked")
                List<String> selectedVids = p.get("selectedVids") instanceof List<?> sv
                        ? sv.stream().filter(Objects::nonNull).map(x -> String.valueOf(x).trim()).filter(StringUtils::hasText).toList()
                        : List.of();
                String freeText = p.get("freeText") == null ? null : String.valueOf(p.get("freeText")).trim();
                String numberInputValue = p.get("numberInputValue") == null ? "" : String.valueOf(p.get("numberInputValue")).trim();

                Map<String, String> vidToValue = new LinkedHashMap<>();
                JsonNode vals = tpl.path("values");
                if (vals.isArray()) {
                    for (JsonNode v : vals) {
                        String vid = v.path("vid").asText("");
                        if (!StringUtils.hasText(vid)) {
                            int n = v.path("vid").asInt(0);
                            if (n > 0) vid = String.valueOf(n);
                        }
                        String vv = v.path("value").asText("");
                        if (StringUtils.hasText(vid)) vidToValue.put(vid, vv);
                    }
                }

                one.put("selectedVids", selectedVids);
                List<Map<String, Object>> selectedValues = new ArrayList<>();
                for (String vid : selectedVids) {
                    Map<String, Object> svv = new LinkedHashMap<>();
                    svv.put("vid", toInt(vid));
                    svv.put("value", vidToValue.getOrDefault(vid, vid));
                    selectedValues.add(svv);
                }
                one.put("selectedValues", selectedValues);

                if (StringUtils.hasText(freeText)) {
                    one.put("freeText", freeText);
                    one.put("numberInputValue", templateSupportsNumberInput(tpl) ? numberInputValue : "");
                } else {
                    one.put("freeText", null);
                    one.put("numberInputValue", templateSupportsNumberInput(tpl) ? numberInputValue : "");
                }

                boolean isEmpty = selectedVids.isEmpty() && !StringUtils.hasText(freeText);
                if (!isEmpty) {
                    String dedupeKey = buildPropertyDedupeKey(one);
                    if (emittedKeys.add(dedupeKey)) {
                        outProps.add(one);
                    }
                }
            }
        }

        out.put("properties", outProps);
        return objectMapper.writeValueAsString(out);
    }

    private String extractLeafCatId(String catid) {
        if (!StringUtils.hasText(catid)) return null;
        String[] parts = catid.trim().split(",");
        return parts.length == 0 ? null : parts[parts.length - 1].trim();
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> pruneInapplicableFilledProps(List<Map<String, Object>> filled,
                                                                   Map<Integer, JsonNode> templateByPid,
                                                                   Map<Integer, JsonNode> templateByTemplatePid) {
        if (filled == null || filled.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> current = new ArrayList<>(filled);
        for (int i = 0; i < 5; i++) {
            Set<String> selectedVidsAll = new LinkedHashSet<>();
            Map<Integer, Set<String>> selectedVidsByRefPid = new LinkedHashMap<>();
            for (Map<String, Object> p : current) {
                if (p == null || !hasAnySelection(p)) continue;
                JsonNode tpl = resolveTemplateNode(p, templateByPid, templateByTemplatePid);
                Object sv = p.get("selectedVids");
                if (!(sv instanceof List<?> list)) continue;
                Integer refPid = tpl == null ? null : toInt(tpl.path("refPid").asText(""));
                for (Object o : list) {
                    if (o == null) continue;
                    String s = String.valueOf(o).trim();
                    if (!StringUtils.hasText(s)) continue;
                    selectedVidsAll.add(s);
                    if (refPid != null && refPid > 0) {
                        selectedVidsByRefPid.computeIfAbsent(refPid, ignored -> new LinkedHashSet<>()).add(s);
                    }
                }
            }

            List<Map<String, Object>> next = new ArrayList<>();
            boolean changed = false;
            for (Map<String, Object> p : current) {
                if (p == null) continue;
                JsonNode tpl = resolveTemplateNode(p, templateByPid, templateByTemplatePid);
                if (tpl != null && hasAnySelection(p) && !isTemplateApplicable(tpl, selectedVidsAll, selectedVidsByRefPid)) {
                    changed = true;
                    continue;
                }
                next.add(p);
            }
            current = next;
            if (!changed) {
                break;
            }
        }
        return current;
    }

    private JsonNode resolveTemplateNode(Map<String, Object> property,
                                         Map<Integer, JsonNode> templateByPid,
                                         Map<Integer, JsonNode> templateByTemplatePid) {
        if (property == null) return null;
        Integer templatePid = toInt(property.get("templatePid"));
        if (templatePid != null && templatePid > 0) {
            JsonNode tpl = templateByTemplatePid.get(templatePid);
            if (tpl != null) {
                return tpl;
            }
        }
        Integer pid = toInt(property.get("pid"));
        if (pid == null || pid <= 0) return null;
        return templateByPid.get(pid);
    }

    private boolean isTemplateApplicable(JsonNode tpl,
                                         Set<String> selectedVidsAll,
                                         Map<Integer, Set<String>> selectedVidsByRefPid) {
        if (tpl == null || tpl.isNull()) {
            return true;
        }
        JsonNode parents = tpl.get("templatePropertyValueParentList");
        if (parents != null && parents.isArray() && parents.size() > 0) {
            boolean hasParentSelected = false;
            for (JsonNode rule : parents) {
                JsonNode parentVidList = rule == null ? null : rule.get("parentVidList");
                if (parentVidList == null || !parentVidList.isArray()) continue;
                for (JsonNode pv : parentVidList) {
                    if (pv == null || pv.isNull()) continue;
                    String s = pv.isTextual() ? pv.asText("") : String.valueOf(pv.asInt(0));
                    if (StringUtils.hasText(s) && selectedVidsAll.contains(s.trim())) {
                        hasParentSelected = true;
                        break;
                    }
                }
                if (hasParentSelected) break;
            }
            if (!hasParentSelected) {
                return false;
            }
        }

        JsonNode showConditions = tpl.get("showCondition");
        if (showConditions != null && showConditions.isArray() && showConditions.size() > 0) {
            for (JsonNode cond : showConditions) {
                if (cond == null || cond.isNull()) continue;
                Integer parentRefPid = toInt(cond.path("parentRefPid").asText(""));
                Set<String> selected = parentRefPid == null ? Collections.emptySet() : selectedVidsByRefPid.getOrDefault(parentRefPid, Collections.emptySet());
                JsonNode parentVids = cond.get("parentVids");
                boolean matched = false;
                if (parentVids != null && parentVids.isArray()) {
                    for (JsonNode pv : parentVids) {
                        if (pv == null || pv.isNull()) continue;
                        String s = pv.isTextual() ? pv.asText("") : String.valueOf(pv.asInt(0));
                        if (StringUtils.hasText(s) && selected.contains(s.trim())) {
                            matched = true;
                            break;
                        }
                    }
                }
                if (!matched) {
                    return false;
                }
            }
        }
        return true;
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

    private String buildPropertyDedupeKey(Map<String, Object> property) {
        if (property == null) return "";
        Object templatePid = property.get("templatePid");
        Object pid = property.get("pid");
        Object freeText = property.get("freeText");
        Object selectedVids = property.get("selectedVids");
        return String.valueOf(templatePid) + "|"
                + String.valueOf(pid) + "|"
                + String.valueOf(freeText) + "|"
                + String.valueOf(selectedVids);
    }

    private boolean templateSupportsNumberInput(JsonNode tpl) {
        if (tpl == null || tpl.isNull()) return false;
        JsonNode vals = tpl.get("values");
        boolean hasValues = vals != null && vals.isArray() && vals.size() > 0;
        if (!hasValues) return false;
        String title = tpl.path("numberInputTitle").asText("");
        int controlType = tpl.path("controlType").asInt(0);
        return StringUtils.hasText(title) || controlType == 16;
    }

    public record EnsureForPublishResult(TemuAttrAiFillTask task,
                                         boolean created,
                                         boolean executed,
                                         boolean appliedToProduct) {
    }
}
