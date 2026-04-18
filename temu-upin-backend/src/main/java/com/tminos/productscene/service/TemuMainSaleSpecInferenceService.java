package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductCollectionSku;
import com.tminos.productscene.entity.ProductCollectionTemuSku;
import com.tminos.productscene.entity.TemuMainSaleSpecInferenceTask;
import com.tminos.productscene.entity.TemuPublishSuccessCase;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.tminos.productscene.repository.ProductCollectionSkuRepository;
import com.tminos.productscene.repository.ProductCollectionTemuSkuRepository;
import com.tminos.productscene.repository.TemuMainSaleSpecInferenceTaskRepository;
import com.tminos.productscene.repository.TemuPublishSuccessCaseRepository;
import com.tminos.temu.upin.sdk.v2.category.CategoryApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.dto.AddGloGoodsRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TemuMainSaleSpecInferenceService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;

    private final TemuMainSaleSpecInferenceTaskRepository taskRepo;
    private final ProductCollectionRepository productRepo;
    private final ProductCollectionSkuRepository originSkuRepo;
    private final ProductCollectionTemuSkuRepository temuSkuRepo;
    private final TemuPublishSuccessCaseRepository successCaseRepo;
    private final TemuShopService temuShopService;
    private final TemuOpenApiCredentialService temuOpenApiCredentialService;
    private final ObjectMapper objectMapper;
    private final TemuMainSaleSpecAiService aiService;

    public TemuMainSaleSpecInferenceService(TemuMainSaleSpecInferenceTaskRepository taskRepo,
                                           ProductCollectionRepository productRepo,
                                           ProductCollectionSkuRepository originSkuRepo,
                                           ProductCollectionTemuSkuRepository temuSkuRepo,
                                           TemuPublishSuccessCaseRepository successCaseRepo,
                                           TemuShopService temuShopService,
                                           TemuOpenApiCredentialService temuOpenApiCredentialService,
                                           ObjectMapper objectMapper,
                                           TemuMainSaleSpecAiService aiService) {
        this.taskRepo = taskRepo;
        this.productRepo = productRepo;
        this.originSkuRepo = originSkuRepo;
        this.temuSkuRepo = temuSkuRepo;
        this.successCaseRepo = successCaseRepo;
        this.temuShopService = temuShopService;
        this.temuOpenApiCredentialService = temuOpenApiCredentialService;
        this.objectMapper = objectMapper;
        this.aiService = aiService;
    }

    @Transactional
    public TemuMainSaleSpecInferenceTask createTask(Long spuId) {
        if (spuId == null) throw new IllegalArgumentException("spuId is required");
        TemuMainSaleSpecInferenceTask existing = taskRepo.findFirstBySpuIdOrderByIdDesc(spuId).orElse(null);
        if (existing != null) {
            return existing;
        }
        ProductCollection pc = productRepo.findById(spuId).orElseThrow(() -> new IllegalStateException("spu not found: " + spuId));

        TemuMainSaleSpecInferenceTask t = new TemuMainSaleSpecInferenceTask();
        t.setInferenceId(UUID.randomUUID().toString().replace("-", ""));
        t.setSpuId(spuId);
        t.setProductName(pc.getProductName());
        t.setProductMainImage(pc.getProductMainImage());
        t.setStatus(STATUS_PENDING);
        t.setLeafCatId(parseLeafCatId(pc.getTemuCatid()));
        t.setCreatedAt(LocalDateTime.now());
        return taskRepo.save(t);
    }

    @Transactional(readOnly = true)
    public Page<TemuMainSaleSpecInferenceTask> list(String q, Integer status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        String keyword = StringUtils.hasText(q) ? q.trim() : null;
        return taskRepo.search(keyword, status, pageable);
    }

    @Transactional(readOnly = true)
    public TemuMainSaleSpecInferenceTask get(Long id) {
        if (id == null) return null;
        return taskRepo.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public TemuMainSaleSpecInferenceTask getBySpuId(Long spuId) {
        if (spuId == null) return null;
        return taskRepo.findFirstBySpuIdOrderByIdDesc(spuId).orElse(null);
    }

    @Transactional
    public void deleteTask(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (!taskRepo.existsById(id)) {
            throw new IllegalStateException("task not found: " + id);
        }
        taskRepo.deleteById(id);
    }

    public Map<String, Object> searchProductsByTitle(String q, int limit) {
        int l = Math.min(Math.max(limit, 1), 20);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("limit", l);
        out.put("q", q);
        out.put("items", Collections.emptyList());
        return out;
    }

    @Transactional
    public TemuMainSaleSpecInferenceTask runOnce(Long taskId) throws Exception {
        TemuMainSaleSpecInferenceTask t = taskRepo.findById(taskId).orElse(null);
        if (t == null) throw new IllegalStateException("task not found: " + taskId);

        t.setStatus(STATUS_RUNNING);
        t.setStartedAt(LocalDateTime.now());
        t.setFinishedAt(null);
        t.setErrorMsg(null);
        t.setResponseRaw(null);
        t.setResponseContent(null);
        t.setParsedJson(null);
        t.setResultJson(null);
        t.setMainProductSkuSpecReqs(null);
        t.setProductSpecPropertyReqs(null);
        t.setProductSkuReqs(null);
        t.setResultSummary(null);
        t.setUpdatedAt(LocalDateTime.now());
        taskRepo.save(t);

        try {
            ProductCollection pc = productRepo.findById(t.getSpuId()).orElseThrow(() -> new IllegalStateException("spu not found"));

            TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
            CategoryApiClient categoryClient = new CategoryApiClient(creds);
            String parentSpecRaw = categoryClient.getParentSpecList();
            t.setParentSpecListRaw(parentSpecRaw);

            List<Map<String, Object>> allowedParentSpecs = extractAllowedParentSpecs(parentSpecRaw);
            List<String> allowedParentSpecNames = extractParentSpecNames(parentSpecRaw);
            List<Map<String, Object>> originSkus = buildOriginSkuSummary(pc.getId());
            List<Map<String, Object>> temuSkus = buildTemuSkuSummary(pc.getId());
            List<Map<String, Object>> skuRowsForAi = buildSkuRowsForAi(originSkus, temuSkus);
            String preferredParentSpecName = choosePreferredParentSpecName(allowedParentSpecs, temuSkus);

            Map<String, Object> promptParts = new LinkedHashMap<>();
            promptParts.put("spuId", pc.getId());
            promptParts.put("productTitle", pc.getProductName());
            promptParts.put("productMainImage", pc.getProductMainImage());
            promptParts.put("originalAttributes", safeJson(pc.getAttributesData()));
            promptParts.put("originalContent", safeJson(pc.getOriginalContent()));
            promptParts.put("originSkus", originSkus);
            promptParts.put("temuSkus", temuSkus);
            promptParts.put("skuRowsForAi", skuRowsForAi);
            promptParts.put("temuParentSpecList", safeJson(parentSpecRaw));
            promptParts.put("allowedParentSpecs", allowedParentSpecs);
            promptParts.put("allowedParentSpecNames", allowedParentSpecNames);
            promptParts.put("preferredParentSpecName", preferredParentSpecName);

            String promptText = objectMapper.writeValueAsString(promptParts);
            t.setPromptPartsJson(promptText);
            t.setPromptText(promptText);

            TemuMainSaleSpecAiService.AiPlanResult r = aiService.inferMainSaleSpecPlan(promptText, allowedParentSpecs);
            t.setResponseRaw(r == null ? null : r.getResponseRaw());
            t.setResponseContent(r == null ? null : r.getResponseContent());

            Map<String, Object> parsed = new LinkedHashMap<>();
            parsed.put("success", r != null && r.isSuccess());
            parsed.put("dimensions", r == null ? null : r.getDimensions());
            parsed.put("skuPlans", r == null ? null : r.getSkuPlans());
            parsed.put("reason", r == null ? null : r.getReason());
            parsed.put("errorMsg", r == null ? "empty" : r.getErrorMsg());
            String parsedJson = objectMapper.writeValueAsString(parsed);
            t.setParsedJson(parsedJson);

            if (r == null || !r.isSuccess()) {
                throw new IllegalStateException(r == null ? "AI failed" : r.getErrorMsg());
            }

            InferenceArtifacts artifacts = buildInferenceArtifacts(pc, allowedParentSpecs, preferredParentSpecName, r, originSkus, temuSkus);
            t.setResultJson(objectMapper.writeValueAsString(artifacts.result()));
            t.setMainProductSkuSpecReqs(objectMapper.writeValueAsString(artifacts.mainProductSkuSpecReqGroups()));
            t.setProductSpecPropertyReqs(objectMapper.writeValueAsString(artifacts.productSpecPropertyReqs()));
            t.setProductSkuReqs(objectMapper.writeValueAsString(artifacts.productSkuReqGroups()));
            t.setResultSummary(buildResultSummary(artifacts.result()));

            t.setStatus(STATUS_DONE);
            t.setFinishedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            return taskRepo.save(t);
        } catch (Exception e) {
            t.setErrorMsg(e.getMessage());
            t.setResultSummary(buildFailureSummary(e));
            t.setFinishedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            t.setStatus(STATUS_DONE);
            return taskRepo.save(t);
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

    private List<String> extractParentSpecNames(String raw) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : extractAllowedParentSpecs(raw)) {
            Object nameValue = row.get("parentSpecName");
            if (nameValue == null) continue;
            String name = String.valueOf(nameValue).trim();
            if (StringUtils.hasText(name)) out.add(name);
        }
        return out;
    }

    private List<Map<String, Object>> extractAllowedParentSpecs(String raw) {
        if (!StringUtils.hasText(raw)) return Collections.emptyList();
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode dtos = root.path("result").path("parentSpecDTOS");
            if (!dtos.isArray()) return Collections.emptyList();
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode n : dtos) {
                String name = n.path("parentSpecName").asText(null);
                long id = n.path("parentSpecId").asLong(0);
                if (!StringUtils.hasText(name) || id <= 0) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("parentSpecName", name.trim());
                row.put("parentSpecId", id);
                out.add(row);
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private Object safeJson(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private List<Map<String, Object>> buildOriginSkuSummary(Long spuId) {
        if (spuId == null) return Collections.emptyList();
        List<ProductCollectionSku> skus = originSkuRepo.findBySpuId(spuId);
        if (skus == null) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProductCollectionSku s : skus) {
            if (s == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuIndex", out.size());
            row.put("dbId", s.getId());
            row.put("skuId", s.getSkuId());
            row.put("specKey", s.getSpecKey());
            row.put("specJson", safeJson(s.getSpecJson()));
            row.put("image", s.getImage());
            row.put("stock", s.getStock());
            row.put("price", s.getPrice());
            out.add(row);
            if (out.size() >= 50) break;
        }
        return out;
    }

    private List<Map<String, Object>> buildTemuSkuSummary(Long spuId) {
        if (spuId == null) return Collections.emptyList();
        List<ProductCollectionTemuSku> skus = temuSkuRepo.findBySpuIdOrderByIdAsc(spuId);
        if (skus == null) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProductCollectionTemuSku s : skus) {
            if (s == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuIndex", out.size());
            row.put("dbId", s.getId());
            row.put("temuSkuId", s.getTemuSkuId());
            row.put("originSkuId", s.getOriginSkuId());
            row.put("specKey", s.getSpecKey());
            row.put("specJson", safeJson(s.getSpecJson()));
            row.put("image", s.getImage());
            row.put("originPrice", s.getOriginPrice());
            row.put("supplyPrice", s.getSupplyPrice());
            row.put("weightG", s.getWeightG());
            row.put("lengthCm", s.getLengthCm());
            row.put("widthCm", s.getWidthCm());
            row.put("heightCm", s.getHeightCm());
            out.add(row);
            if (out.size() >= 50) break;
        }
        return out;
    }

    private List<Map<String, Object>> buildSkuRowsForAi(List<Map<String, Object>> originSkus,
                                                        List<Map<String, Object>> temuSkus) {
        Map<String, Map<String, Object>> byOriginSkuId = new LinkedHashMap<>();
        if (originSkus != null) {
            for (Map<String, Object> origin : originSkus) {
                if (origin == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("skuIndex", origin.get("skuIndex"));
                row.put("originSku", origin);
                byOriginSkuId.put(origin.get("skuId") == null ? String.valueOf(origin.get("skuIndex")) : String.valueOf(origin.get("skuId")), row);
            }
        }

        if (temuSkus != null) {
            for (Map<String, Object> temu : temuSkus) {
                if (temu == null) continue;
                String originSkuId = temu.get("originSkuId") == null ? null : String.valueOf(temu.get("originSkuId"));
                String key = StringUtils.hasText(originSkuId) ? originSkuId : String.valueOf(temu.get("skuIndex"));
                Map<String, Object> row = byOriginSkuId.computeIfAbsent(key, k -> new LinkedHashMap<>());
                if (!row.containsKey("skuIndex")) row.put("skuIndex", temu.get("skuIndex"));
                row.put("temuSku", temu);
            }
        }

        List<Map<String, Object>> out = new ArrayList<>(byOriginSkuId.values());
        out.sort(Comparator.comparingInt(this::skuIndexOf));
        return out;
    }

    private int skuIndexOf(Map<String, Object> row) {
        if (row == null) return Integer.MAX_VALUE;
        Object v = row.get("skuIndex");
        if (v == null) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private InferenceArtifacts buildInferenceArtifacts(ProductCollection pc,
                                                       List<Map<String, Object>> allowedParentSpecs,
                                                       String preferredParentSpecName,
                                                       TemuMainSaleSpecAiService.AiPlanResult aiResult,
                                                       List<Map<String, Object>> originSkus,
                                                       List<Map<String, Object>> temuSkus) {
        TemuShopService.PublishConfig shopConfig = resolvePublishConfig(pc);
        int siteId = shopConfig.siteId();
        String warehouseId = shopConfig.warehouseId();
        int defaultStock = shopConfig.skuDefaultStock();
        int maxStock = shopConfig.skuMaxStock();

        Map<String, Long> parentSpecIdMap = new LinkedHashMap<>();
        if (allowedParentSpecs != null) {
            for (Map<String, Object> row : allowedParentSpecs) {
                if (row == null) continue;
                String name = row.get("parentSpecName") == null ? null : String.valueOf(row.get("parentSpecName")).trim();
                Long id = toLong(row.get("parentSpecId"));
                if (StringUtils.hasText(name) && id != null && id > 0) {
                    parentSpecIdMap.put(name, id);
                }
            }
        }

        Map<String, Map<String, Object>> originBySkuId = new LinkedHashMap<>();
        Map<String, Map<String, Object>> temuByTemuSkuId = new LinkedHashMap<>();
        Map<String, Map<String, Object>> temuByOriginSkuId = new LinkedHashMap<>();
        if (originSkus != null) {
            for (Map<String, Object> row : originSkus) {
                if (row == null) continue;
                String skuId = row.get("skuId") == null ? null : String.valueOf(row.get("skuId"));
                if (StringUtils.hasText(skuId)) originBySkuId.put(skuId, row);
            }
        }
        if (temuSkus != null) {
            for (Map<String, Object> row : temuSkus) {
                if (row == null) continue;
                String temuSkuId = row.get("temuSkuId") == null ? null : String.valueOf(row.get("temuSkuId"));
                String originSkuId = row.get("originSkuId") == null ? null : String.valueOf(row.get("originSkuId"));
                if (StringUtils.hasText(temuSkuId)) temuByTemuSkuId.put(temuSkuId, row);
                if (StringUtils.hasText(originSkuId)) temuByOriginSkuId.put(originSkuId, row);
            }
        }

        SuccessCaseStructure successCaseStructure = loadSuccessCaseStructure(pc);

        List<Map<String, Object>> normalizedDimensions = new ArrayList<>();
        if (successCaseStructure != null && !successCaseStructure.dimensionNames().isEmpty()) {
            int order = 0;
            for (String dimensionName : successCaseStructure.dimensionNames()) {
                Long parentSpecId = parentSpecIdMap.get(dimensionName);
                if (!StringUtils.hasText(dimensionName) || parentSpecId == null || parentSpecId <= 0) {
                    continue;
                }
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("order", order++);
                one.put("parentSpecName", dimensionName);
                one.put("parentSpecId", parentSpecId);
                one.put("reason", "matched exact-category success case structure");
                normalizedDimensions.add(one);
            }
        }
        if (normalizedDimensions.isEmpty() && aiResult.getDimensions() != null) {
            for (int i = 0; i < aiResult.getDimensions().size(); i++) {
                Map<String, Object> row = aiResult.getDimensions().get(i);
                if (row == null) continue;
                String parentSpecName = row.get("parentSpecName") == null ? null : String.valueOf(row.get("parentSpecName")).trim();
                Long parentSpecId = parentSpecIdMap.get(parentSpecName);
                if (!StringUtils.hasText(parentSpecName) || parentSpecId == null || parentSpecId <= 0) continue;
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("order", i);
                one.put("parentSpecName", parentSpecName);
                one.put("parentSpecId", parentSpecId);
                one.put("reason", row.get("reason"));
                appendNormalizedDimension(normalizedDimensions, one);
            }
        }
        if (normalizedDimensions.isEmpty() && StringUtils.hasText(preferredParentSpecName) && parentSpecIdMap.containsKey(preferredParentSpecName)) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("order", 0);
            one.put("parentSpecName", preferredParentSpecName);
            one.put("parentSpecId", parentSpecIdMap.get(preferredParentSpecName));
            one.put("reason", "preferred by legacy publish heuristic");
            normalizedDimensions.add(one);
        }
        if (normalizedDimensions.isEmpty() && allowedParentSpecs != null && !allowedParentSpecs.isEmpty()) {
            Map<String, Object> firstAllowed = allowedParentSpecs.get(0);
            String parentSpecName = asText(firstAllowed == null ? null : firstAllowed.get("parentSpecName"));
            Long parentSpecId = toLong(firstAllowed == null ? null : firstAllowed.get("parentSpecId"));
            if (StringUtils.hasText(parentSpecName) && parentSpecId != null && parentSpecId > 0) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("order", 0);
                one.put("parentSpecName", parentSpecName);
                one.put("parentSpecId", parentSpecId);
                one.put("reason", "fallback to first allowed dimension");
                normalizedDimensions.add(one);
            }
        }

        List<String> allowedDimensionNames = extractDimensionNames(normalizedDimensions);
        int maxMainSpecCount = successCaseStructure == null
            ? Math.max(1, allowedDimensionNames.isEmpty() ? 1 : allowedDimensionNames.size())
            : Math.max(1, successCaseStructure.maxMainSpecCount());

        Map<String, AddGloGoodsRequest.ProductSpecPropertyReq> uniqueSpecs = new LinkedHashMap<>();
        List<Map<String, Object>> normalizedSkuPlans = new ArrayList<>();
        Map<String, Map<String, Object>> skcPlanMap = new LinkedHashMap<>();
        Map<String, List<AddGloGoodsRequest.ProductSkuReq>> productSkuReqGroupMap = new LinkedHashMap<>();
        Map<String, List<AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq>> mainProductSkuSpecReqGroupMap = new LinkedHashMap<>();
        List<ResolvedSkuPlan> resolvedSkuPlans = new ArrayList<>();
        LinkedHashMap<String, LinkedHashSet<String>> distinctSpecValuesByDimension = new LinkedHashMap<>();
        Map<String, Integer> tempSpecIdByKey = new LinkedHashMap<>();
        int nextSpecId = 1;

        if (aiResult.getSkuPlans() != null) {
            for (Map<String, Object> plan : aiResult.getSkuPlans()) {
                if (plan == null) continue;
                Map<String, Object> outPlan = new LinkedHashMap<>();
                Integer skuIndex = toInt(plan.get("skuIndex"));
                String originSkuId = asText(plan.get("originSkuId"));
                String temuSkuId = asText(plan.get("temuSkuId"));

                Map<String, Object> originRow = StringUtils.hasText(originSkuId) ? originBySkuId.get(originSkuId) : null;
                Map<String, Object> temuRow = StringUtils.hasText(temuSkuId) ? temuByTemuSkuId.get(temuSkuId) : null;
                if (temuRow == null && StringUtils.hasText(originSkuId)) {
                    temuRow = temuByOriginSkuId.get(originSkuId);
                }
                if (originRow == null && skuIndex != null && skuIndex >= 0 && originSkus != null && skuIndex < originSkus.size()) {
                    originRow = originSkus.get(skuIndex);
                }
                if (temuRow == null && skuIndex != null && skuIndex >= 0 && temuSkus != null && skuIndex < temuSkus.size()) {
                    temuRow = temuSkus.get(skuIndex);
                }

                if (!StringUtils.hasText(originSkuId) && originRow != null && originRow.get("skuId") != null) {
                    originSkuId = String.valueOf(originRow.get("skuId"));
                }
                if (!StringUtils.hasText(temuSkuId) && temuRow != null && temuRow.get("temuSkuId") != null) {
                    temuSkuId = String.valueOf(temuRow.get("temuSkuId"));
                }

                outPlan.put("skuIndex", skuIndex);
                outPlan.put("originSkuId", originSkuId);
                outPlan.put("temuSkuId", temuSkuId);
                outPlan.put("reason", plan.get("reason"));
                outPlan.put("originSku", originRow);
                outPlan.put("temuSku", temuRow);

                List<Map<String, Object>> selectedSpecs = castMapList(plan.get("selectedSpecs"));
                List<Map<String, Object>> productSkuSpecReqs = new ArrayList<>();
                List<AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq> skuSpecReqDtos = new ArrayList<>();
                LinkedHashMap<String, AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq> dedupedSkuSpecs = new LinkedHashMap<>();
                for (int specIndex = 0; specIndex < selectedSpecs.size(); specIndex++) {
                    Map<String, Object> spec = selectedSpecs.get(specIndex);
                    if (spec == null) continue;
                    String parentSpecName = choosePlanParentSpecName(spec, specIndex, allowedDimensionNames, preferredParentSpecName);
                    String specName = canonicalizeSpecName(
                        parentSpecName,
                            asText(spec.get("specName")),
                            temuRow,
                            originRow
                    );
                    if (!StringUtils.hasText(parentSpecName) || !StringUtils.hasText(specName)) continue;
                    Long parentSpecId = parentSpecIdMap.get(parentSpecName);
                    if (parentSpecId == null || parentSpecId <= 0) continue;
                    String specKey = parentSpecName + "\u0001" + specName;
                    Integer tempSpecId = tempSpecIdByKey.get(specKey);
                    if (tempSpecId == null) {
                        tempSpecId = nextSpecId++;
                        tempSpecIdByKey.put(specKey, tempSpecId);
                    }

                    Map<String, Object> req = new LinkedHashMap<>();
                    req.put("parentSpecId", parentSpecId);
                    req.put("parentSpecName", parentSpecName);
                    req.put("specId", tempSpecId);
                    req.put("specName", specName);
                    productSkuSpecReqs.add(req);

                    AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq skuSpecReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq();
                    skuSpecReq.setParentSpecId(parentSpecId == null ? null : parentSpecId.intValue());
                    skuSpecReq.setParentSpecName(parentSpecName);
                    skuSpecReq.setSpecId(tempSpecId);
                    skuSpecReq.setSpecName(specName);
                    dedupedSkuSpecs.putIfAbsent(specKey, skuSpecReq);

                    if (!uniqueSpecs.containsKey(specKey)) {
                        AddGloGoodsRequest.ProductSpecPropertyReq top = new AddGloGoodsRequest.ProductSpecPropertyReq();
                        top.setVid(0);
                        top.setSpecId(tempSpecId);
                        top.setValueGroupId(0);
                        top.setParentSpecId(parentSpecId == null ? null : parentSpecId.intValue());
                        top.setValueGroupName("");
                        top.setValueUnit("");
                        top.setPid(0);
                        top.setTemplatePid(0);
                        top.setNumberInputValue("");
                        top.setPropValue(specName);
                        top.setPropName(parentSpecName);
                        top.setRefPid(0);
                        uniqueSpecs.put(specKey, top);
                    }
                }

                if (dedupedSkuSpecs.isEmpty() && !allowedDimensionNames.isEmpty()) {
                    String fallbackParentSpecName = allowedDimensionNames.get(0);
                    String specName = canonicalizeSpecName(fallbackParentSpecName, null, temuRow, originRow);
                    Long parentSpecId = parentSpecIdMap.get(fallbackParentSpecName);
                    if (StringUtils.hasText(specName) && parentSpecId != null && parentSpecId > 0) {
                        String specKey = fallbackParentSpecName + "\u0001" + specName;
                        Integer tempSpecId = tempSpecIdByKey.get(specKey);
                        if (tempSpecId == null) {
                            tempSpecId = nextSpecId++;
                            tempSpecIdByKey.put(specKey, tempSpecId);
                        }

                        Map<String, Object> req = new LinkedHashMap<>();
                        req.put("parentSpecId", parentSpecId);
                        req.put("parentSpecName", fallbackParentSpecName);
                        req.put("specId", tempSpecId);
                        req.put("specName", specName);
                        productSkuSpecReqs.add(req);

                        AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq skuSpecReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq();
                        skuSpecReq.setParentSpecId(parentSpecId.intValue());
                        skuSpecReq.setParentSpecName(fallbackParentSpecName);
                        skuSpecReq.setSpecId(tempSpecId);
                        skuSpecReq.setSpecName(specName);
                        dedupedSkuSpecs.put(specKey, skuSpecReq);

                        if (!uniqueSpecs.containsKey(specKey)) {
                            AddGloGoodsRequest.ProductSpecPropertyReq top = new AddGloGoodsRequest.ProductSpecPropertyReq();
                            top.setVid(0);
                            top.setSpecId(tempSpecId);
                            top.setValueGroupId(0);
                            top.setParentSpecId(parentSpecId.intValue());
                            top.setValueGroupName("");
                            top.setValueUnit("");
                            top.setPid(0);
                            top.setTemplatePid(0);
                            top.setNumberInputValue("");
                            top.setPropValue(specName);
                            top.setPropName(fallbackParentSpecName);
                            top.setRefPid(0);
                            uniqueSpecs.put(specKey, top);
                        }
                    }
                }

                if (!dedupedSkuSpecs.isEmpty()) {
                    productSkuSpecReqs = new ArrayList<>();
                    for (AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq skuSpecReq : dedupedSkuSpecs.values()) {
                        Map<String, Object> req = new LinkedHashMap<>();
                        req.put("parentSpecId", skuSpecReq.getParentSpecId());
                        req.put("parentSpecName", skuSpecReq.getParentSpecName());
                        req.put("specId", skuSpecReq.getSpecId());
                        req.put("specName", skuSpecReq.getSpecName());
                        productSkuSpecReqs.add(req);
                    }
                    skuSpecReqDtos = new ArrayList<>(dedupedSkuSpecs.values());
                }
                outPlan.put("productSkuSpecReqs", productSkuSpecReqs);
                normalizedSkuPlans.add(outPlan);

                AddGloGoodsRequest.ProductSkuReq skuReq = buildProductSkuReq(pc, skuIndex, originSkuId, temuSkuId, originRow, temuRow, siteId, warehouseId, defaultStock, maxStock, skuSpecReqDtos);
                resolvedSkuPlans.add(new ResolvedSkuPlan(outPlan, skuReq, skuSpecReqDtos, originSkuId, temuSkuId));
                for (AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq skuSpecReq : skuSpecReqDtos) {
                    if (skuSpecReq == null) {
                        continue;
                    }
                    String parentSpecName = asText(skuSpecReq.getParentSpecName());
                    String specName = normalizeSpecToken(skuSpecReq.getSpecName());
                    if (!StringUtils.hasText(parentSpecName) || !StringUtils.hasText(specName)) {
                        continue;
                    }
                    distinctSpecValuesByDimension
                            .computeIfAbsent(parentSpecName, ignored -> new LinkedHashSet<>())
                            .add(specName);
                }
            }
        }

        List<String> mainDimensionNames = resolveMainDimensionNames(successCaseStructure, allowedDimensionNames, distinctSpecValuesByDimension, resolvedSkuPlans.size());
        boolean forceEmptyMainSpecPlaceholder = shouldForceEmptyMainSpecPlaceholder(
            pc,
            mainDimensionNames,
            allowedDimensionNames,
            distinctSpecValuesByDimension
        );
        boolean useEmptyMainSpecPlaceholder = forceEmptyMainSpecPlaceholder
            || (successCaseStructure != null
            && successCaseStructure.usesEmptyMainSpecPlaceholder()
            && mainDimensionNames.isEmpty());
        if (useEmptyMainSpecPlaceholder) {
            mainDimensionNames = Collections.emptyList();
        }

        for (ResolvedSkuPlan resolvedSkuPlan : resolvedSkuPlans) {
            List<AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq> mainSkuSpecReqDtos = useEmptyMainSpecPlaceholder
                    ? List.of(buildEmptyMainProductSkuSpecReq())
                    : buildMainSkuSpecReqs(resolvedSkuPlan.skuSpecReqDtos(), mainDimensionNames, maxMainSpecCount);
            List<Map<String, Object>> mainProductSkuSpecReqs = toMainSpecReqMaps(mainSkuSpecReqDtos);
            String skcKey = buildSkcKey(mainSkuSpecReqDtos, useEmptyMainSpecPlaceholder);

            resolvedSkuPlan.outPlan().put("mainProductSkuSpecReqs", mainProductSkuSpecReqs);
            resolvedSkuPlan.outPlan().put("targetSkcKey", skcKey);

            productSkuReqGroupMap.computeIfAbsent(skcKey, k -> new ArrayList<>()).add(resolvedSkuPlan.skuReq());
            mainProductSkuSpecReqGroupMap.computeIfAbsent(skcKey, k -> new ArrayList<>(mainSkuSpecReqDtos));
            Map<String, Object> skcPlan = skcPlanMap.computeIfAbsent(skcKey, k -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("skcKey", k);
                row.put("mainProductSkuSpecReqs", new ArrayList<>(mainProductSkuSpecReqs));
                row.put("originSkuIds", new ArrayList<String>());
                row.put("temuSkuIds", new ArrayList<String>());
                return row;
            });
            if (StringUtils.hasText(resolvedSkuPlan.originSkuId())) {
                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) skcPlan.get("originSkuIds");
                if (!ids.contains(resolvedSkuPlan.originSkuId())) ids.add(resolvedSkuPlan.originSkuId());
            }
            if (StringUtils.hasText(resolvedSkuPlan.temuSkuId())) {
                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) skcPlan.get("temuSkuIds");
                if (!ids.contains(resolvedSkuPlan.temuSkuId())) ids.add(resolvedSkuPlan.temuSkuId());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", "add_glo_goods_main_sale_spec_plan_v1");
        result.put("spuId", pc == null ? null : pc.getId());
        result.put("productTitle", pc == null ? null : pc.getProductName());
        result.put("leafCatId", pc == null ? null : parseLeafCatId(pc.getTemuCatid()));
        result.put("dimensions", normalizedDimensions);
        result.put("productSpecPropertyReqs", new ArrayList<>(uniqueSpecs.values()));
        result.put("mainProductSkuSpecReqs", new ArrayList<>(mainProductSkuSpecReqGroupMap.values()));
        result.put("productSkuReqs", new ArrayList<>(productSkuReqGroupMap.values()));
        result.put("skuPlans", normalizedSkuPlans);
        result.put("skcPlans", new ArrayList<>(skcPlanMap.values()));
        result.put("reason", aiResult.getReason());
        if (forceEmptyMainSpecPlaceholder) {
            result.put("mainSpecStrategy", "empty-placeholder-by-leaf-cat-rule");
        }
        result.put("note", "specId is task-local temporary id for relation mapping before createSpec");
        validateNoDuplicateSkuSpecGroups(productSkuReqGroupMap);
        return new InferenceArtifacts(
            result,
            new ArrayList<>(uniqueSpecs.values()),
            new ArrayList<>(mainProductSkuSpecReqGroupMap.values()),
            new ArrayList<>(productSkuReqGroupMap.values())
        );
    }

    private TemuShopService.PublishConfig resolvePublishConfig(ProductCollection pc) {
        String shopId = firstTargetShopId(pc);
        if (StringUtils.hasText(shopId)) {
            return temuShopService.getPublishConfigByShopIdOrThrow(shopId);
        }
        return temuShopService.getAnyEnabledPublishConfigOrThrow();
    }

    private String firstTargetShopId(ProductCollection pc) {
        if (pc == null || !StringUtils.hasText(pc.getTargetShopIds())) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(pc.getTargetShopIds());
            if (node == null || !node.isArray()) {
                return null;
            }
            for (JsonNode child : node) {
                if (child == null || child.isNull()) {
                    continue;
                }
                String shopId = child.asText(null);
                if (StringUtils.hasText(shopId)) {
                    return shopId.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String buildResultSummary(Map<String, Object> result) {
        if (result == null) return "AI result empty";
        List<Map<String, Object>> dimensions = castMapList(result.get("dimensions"));
        List<Map<String, Object>> skuPlans = castMapList(result.get("skuPlans"));
        List<Map<String, Object>> skcPlans = castMapList(result.get("skcPlans"));
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : dimensions) {
            String name = asText(row.get("parentSpecName"));
            if (StringUtils.hasText(name)) names.add(name);
        }
        return "dimensions=" + String.join(",", names) + "; skuPlans=" + skuPlans.size() + "; skcPlans=" + skcPlans.size();
    }

    private String buildFailureSummary(Exception exception) {
        String message = exception == null ? null : asText(exception.getMessage());
        if (!StringUtils.hasText(message)) {
            return "task failed";
        }
        String safeMessage = Objects.requireNonNull(message);
        if (safeMessage.contains("duplicate productSkuSpecReqs detected in draft")) {
            return "draft-invalid: duplicate sku sales-spec combination; " + safeMessage;
        }
        return "task failed: " + safeMessage;
    }

    private void appendNormalizedDimension(List<Map<String, Object>> normalizedDimensions, Map<String, Object> candidate) {
        String parentSpecName = asText(candidate == null ? null : candidate.get("parentSpecName"));
        if (!StringUtils.hasText(parentSpecName)) {
            return;
        }
        for (Map<String, Object> existing : normalizedDimensions) {
            if (sameSpecDimension(parentSpecName, asText(existing == null ? null : existing.get("parentSpecName")))) {
                return;
            }
        }
        normalizedDimensions.add(candidate);
    }

    private void validateNoDuplicateSkuSpecGroups(Map<String, List<AddGloGoodsRequest.ProductSkuReq>> productSkuReqGroupMap) {
        if (productSkuReqGroupMap == null || productSkuReqGroupMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<AddGloGoodsRequest.ProductSkuReq>> entry : productSkuReqGroupMap.entrySet()) {
            String skcKey = entry.getKey();
            List<AddGloGoodsRequest.ProductSkuReq> skuReqs = entry.getValue();
            if (skuReqs == null || skuReqs.isEmpty()) {
                continue;
            }
            LinkedHashMap<String, String> seen = new LinkedHashMap<>();
            for (AddGloGoodsRequest.ProductSkuReq skuReq : skuReqs) {
                String comboKey = buildSkuSpecComboKey(skuReq);
                if (!StringUtils.hasText(comboKey)) {
                    continue;
                }
                String extCode = asText(skuReq.getExtCode());
                String firstExtCode = seen.putIfAbsent(comboKey, extCode);
                if (firstExtCode != null) {
                    throw new IllegalStateException(
                            "duplicate productSkuSpecReqs detected in draft for skcKey=" + skcKey
                                    + ", combo=" + comboKey
                                    + ", extCodes=" + firstExtCode + "/" + extCode
                    );
                }
            }
        }
    }

    private String buildSkuSpecComboKey(AddGloGoodsRequest.ProductSkuReq skuReq) {
        if (skuReq == null || skuReq.getProductSkuSpecReqs() == null || skuReq.getProductSkuSpecReqs().isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq specReq : skuReq.getProductSkuSpecReqs()) {
            if (specReq == null) {
                continue;
            }
            String parentSpecName = normalizeSpecToken(specReq.getParentSpecName());
            String specName = normalizeSpecToken(specReq.getSpecName());
            if (!StringUtils.hasText(parentSpecName) || !StringUtils.hasText(specName)) {
                continue;
            }
            parts.add(parentSpecName + "=" + specName);
        }
        Collections.sort(parts);
        return parts.isEmpty() ? null : String.join("|", parts);
    }

    private String normalizeSpecToken(String value) {
        String text = asText(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.replaceAll("\\s+", "");
    }

    private SuccessCaseStructure loadSuccessCaseStructure(ProductCollection pc) {
        if (pc == null) {
            return null;
        }
        String temuCatid = asText(pc.getTemuCatid());
        if (!StringUtils.hasText(temuCatid)) {
            return null;
        }

        List<TemuPublishSuccessCase> cases = new ArrayList<>(successCaseRepo.findByTemuCatidOrderByIdDesc(temuCatid));
        if (cases.isEmpty()) {
            String leafCatId = extractLeafCatIdText(temuCatid);
            if (StringUtils.hasText(leafCatId) && !leafCatId.equals(temuCatid)) {
                cases.addAll(successCaseRepo.findByTemuCatidOrderByIdDesc(leafCatId));
            }
        }

        for (TemuPublishSuccessCase successCase : cases) {
            SuccessCaseStructure structure = parseSuccessCaseStructure(successCase);
            if (structure != null && !structure.dimensionNames().isEmpty()) {
                return structure;
            }
        }
        return null;
    }

    private SuccessCaseStructure parseSuccessCaseStructure(TemuPublishSuccessCase successCase) {
        if (successCase == null || !StringUtils.hasText(successCase.getRequestJson())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(successCase.getRequestJson());
            LinkedHashSet<String> dimensionNames = new LinkedHashSet<>();
            LinkedHashSet<String> mainDimensionNames = new LinkedHashSet<>();
            int maxMainSpecCount = 0;
            boolean sawMainSpecArray = false;
            boolean sawNamedMainSpec = false;
            boolean sawBlankMainSpec = false;

            JsonNode productSpecPropertyReqs = root.path("productSpecPropertyReqs");
            if (productSpecPropertyReqs.isArray()) {
                for (JsonNode item : productSpecPropertyReqs) {
                    String parentSpecName = asText(item.path("propName").asText(null));
                    if (StringUtils.hasText(parentSpecName)) {
                        dimensionNames.add(parentSpecName);
                    }
                }
            }

            JsonNode skcReqs = root.path("productSkcReqs");
            if (skcReqs.isArray()) {
                for (JsonNode skcReq : skcReqs) {
                    JsonNode mainSpecReqs = skcReq.path("mainProductSkuSpecReqs");
                    if (!mainSpecReqs.isArray()) {
                        continue;
                    }
                    if (!mainSpecReqs.isEmpty()) {
                        sawMainSpecArray = true;
                    }
                    int count = 0;
                    for (JsonNode mainSpec : mainSpecReqs) {
                        String parentSpecName = asText(mainSpec.path("parentSpecName").asText(null));
                        if (StringUtils.hasText(parentSpecName)) {
                            dimensionNames.add(parentSpecName);
                            mainDimensionNames.add(parentSpecName);
                            sawNamedMainSpec = true;
                            count++;
                        } else {
                            sawBlankMainSpec = true;
                        }
                    }
                    maxMainSpecCount = Math.max(maxMainSpecCount, count);
                }
            }

            if (dimensionNames.isEmpty()) {
                return null;
            }
            boolean usesEmptyMainSpecPlaceholder = sawMainSpecArray && !sawNamedMainSpec && sawBlankMainSpec;
            return new SuccessCaseStructure(
                    new ArrayList<>(dimensionNames),
                    new ArrayList<>(mainDimensionNames),
                    Math.max(1, maxMainSpecCount),
                    usesEmptyMainSpecPlaceholder
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractLeafCatIdText(String temuCatid) {
        if (!StringUtils.hasText(temuCatid)) {
            return null;
        }
        String[] parts = temuCatid.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String value = asText(parts[i]);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private List<String> extractDimensionNames(List<Map<String, Object>> normalizedDimensions) {
        if (normalizedDimensions == null || normalizedDimensions.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : normalizedDimensions) {
            String parentSpecName = asText(row == null ? null : row.get("parentSpecName"));
            if (StringUtils.hasText(parentSpecName) && !names.contains(parentSpecName)) {
                names.add(parentSpecName);
            }
        }
        return names;
    }

    private List<String> resolveMainDimensionNames(SuccessCaseStructure successCaseStructure,
                                                   List<String> allowedDimensionNames,
                                                   Map<String, LinkedHashSet<String>> distinctSpecValuesByDimension,
                                                   int skuPlanCount) {
        if (successCaseStructure != null && !successCaseStructure.mainDimensionNames().isEmpty()) {
            List<String> resolved = new ArrayList<>();
            for (String mainDimensionName : successCaseStructure.mainDimensionNames()) {
                String matched = resolveAllowedDimension(mainDimensionName, allowedDimensionNames);
                if (StringUtils.hasText(matched) && !resolved.contains(matched)) {
                    resolved.add(matched);
                }
            }
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }

        List<String> varyingDimensions = new ArrayList<>();
        for (String allowedDimensionName : allowedDimensionNames) {
            LinkedHashSet<String> values = findDistinctValuesForDimension(allowedDimensionName, distinctSpecValuesByDimension);
            if (values != null && values.size() > 1) {
                varyingDimensions.add(allowedDimensionName);
            }
        }
        if (!varyingDimensions.isEmpty()) {
            return varyingDimensions;
        }

        if (skuPlanCount <= 1 && !allowedDimensionNames.isEmpty()) {
            return List.of(allowedDimensionNames.get(0));
        }
        return Collections.emptyList();
    }

    private boolean shouldForceEmptyMainSpecPlaceholder(ProductCollection pc,
                                                        List<String> mainDimensionNames,
                                                        List<String> allowedDimensionNames,
                                                        Map<String, LinkedHashSet<String>> distinctSpecValuesByDimension) {
        String leafCatId = extractLeafCatIdText(pc == null ? null : pc.getTemuCatid());
        if (!"29271".equals(leafCatId)) {
            return false;
        }
        if (mainDimensionNames == null || mainDimensionNames.isEmpty()) {
            return true;
        }
        if (mainDimensionNames.size() != 1) {
            return false;
        }
        String mainDimensionName = mainDimensionNames.get(0);
        if (!isAppearanceLikeSpec(mainDimensionName)) {
            return false;
        }
        LinkedHashSet<String> mainValues = findDistinctValuesForDimension(mainDimensionName, distinctSpecValuesByDimension);
        if (mainValues == null || mainValues.size() <= 1) {
            return false;
        }
        if (allowedDimensionNames != null) {
            for (String allowedDimensionName : allowedDimensionNames) {
                if (sameSpecDimension(allowedDimensionName, mainDimensionName)) {
                    continue;
                }
                LinkedHashSet<String> values = findDistinctValuesForDimension(allowedDimensionName, distinctSpecValuesByDimension);
                if (values != null && values.size() > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    private LinkedHashSet<String> findDistinctValuesForDimension(String parentSpecName,
                                                                 Map<String, LinkedHashSet<String>> distinctSpecValuesByDimension) {
        if (!StringUtils.hasText(parentSpecName) || distinctSpecValuesByDimension == null || distinctSpecValuesByDimension.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, LinkedHashSet<String>> entry : distinctSpecValuesByDimension.entrySet()) {
            if (sameSpecDimension(parentSpecName, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq> buildMainSkuSpecReqs(
            List<AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq> skuSpecReqDtos,
            List<String> mainDimensionNames,
            int maxMainSpecCount) {
        if (skuSpecReqDtos == null || skuSpecReqDtos.isEmpty() || mainDimensionNames == null || mainDimensionNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq> mainReqs = new ArrayList<>();
        for (String mainDimensionName : mainDimensionNames) {
            if (mainReqs.size() >= maxMainSpecCount) {
                break;
            }
            AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq matched = findSkuSpecByDimension(skuSpecReqDtos, mainDimensionName);
            if (matched == null) {
                continue;
            }
            AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq mainReq = new AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq();
            mainReq.setParentSpecId(matched.getParentSpecId());
            mainReq.setParentSpecName(matched.getParentSpecName());
            mainReq.setSpecId(matched.getSpecId());
            mainReq.setSpecName(matched.getSpecName());
            mainReqs.add(mainReq);
        }
        return mainReqs;
    }

    private AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq findSkuSpecByDimension(
            List<AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq> skuSpecReqDtos,
            String mainDimensionName) {
        if (skuSpecReqDtos == null || skuSpecReqDtos.isEmpty() || !StringUtils.hasText(mainDimensionName)) {
            return null;
        }
        for (AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq skuSpecReq : skuSpecReqDtos) {
            if (skuSpecReq == null) {
                continue;
            }
            if (sameSpecDimension(mainDimensionName, skuSpecReq.getParentSpecName())) {
                return skuSpecReq;
            }
        }
        return null;
    }

    private List<Map<String, Object>> toMainSpecReqMaps(List<AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq> mainSkuSpecReqDtos) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (mainSkuSpecReqDtos == null || mainSkuSpecReqDtos.isEmpty()) {
            return out;
        }
        for (AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq mainReq : mainSkuSpecReqDtos) {
            if (mainReq == null) {
                continue;
            }
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("parentSpecId", mainReq.getParentSpecId());
            req.put("parentSpecName", mainReq.getParentSpecName());
            req.put("specId", mainReq.getSpecId());
            req.put("specName", mainReq.getSpecName());
            out.add(req);
        }
        return out;
    }

    private String buildSkcKey(List<AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq> mainSkuSpecReqDtos,
                               boolean useEmptyMainSpecPlaceholder) {
        if (useEmptyMainSpecPlaceholder) {
            return "__EMPTY_MAIN_SPEC__";
        }
        if (mainSkuSpecReqDtos == null || mainSkuSpecReqDtos.isEmpty()) {
            return "__NO_MAIN_DIMENSION__";
        }
        List<String> parts = new ArrayList<>();
        for (AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq mainReq : mainSkuSpecReqDtos) {
            if (mainReq == null) {
                continue;
            }
            String parentSpecName = normalizeSpecToken(mainReq.getParentSpecName());
            String specName = normalizeSpecToken(mainReq.getSpecName());
            if (!StringUtils.hasText(parentSpecName) || !StringUtils.hasText(specName)) {
                continue;
            }
            parts.add(parentSpecName + "=" + specName);
        }
        return parts.isEmpty() ? "__NO_MAIN_DIMENSION__" : String.join("|", parts);
    }

    private AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq buildEmptyMainProductSkuSpecReq() {
        AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq item = new AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq();
        item.setParentSpecId(0);
        item.setParentSpecName("");
        item.setSpecId(0);
        item.setSpecName("");
        return item;
    }

    private String choosePlanParentSpecName(Map<String, Object> spec,
                                            int specIndex,
                                            List<String> allowedDimensionNames,
                                            String preferredParentSpecName) {
        String rawParentSpecName = asText(spec == null ? null : spec.get("parentSpecName"));
        if (StringUtils.hasText(rawParentSpecName) && matchesAllowedDimension(rawParentSpecName, allowedDimensionNames)) {
            return resolveAllowedDimension(rawParentSpecName, allowedDimensionNames);
        }
        if (allowedDimensionNames != null && !allowedDimensionNames.isEmpty()) {
            int safeIndex = Math.max(0, Math.min(specIndex, allowedDimensionNames.size() - 1));
            return allowedDimensionNames.get(safeIndex);
        }
        return rawParentSpecName != null ? rawParentSpecName : preferredParentSpecName;
    }

    private boolean matchesAllowedDimension(String rawParentSpecName, List<String> allowedDimensionNames) {
        if (!StringUtils.hasText(rawParentSpecName) || allowedDimensionNames == null || allowedDimensionNames.isEmpty()) {
            return false;
        }
        for (String allowedDimensionName : allowedDimensionNames) {
            if (sameSpecDimension(rawParentSpecName, allowedDimensionName)) {
                return true;
            }
        }
        return false;
    }

    private String resolveAllowedDimension(String rawParentSpecName, List<String> allowedDimensionNames) {
        if (!StringUtils.hasText(rawParentSpecName) || allowedDimensionNames == null || allowedDimensionNames.isEmpty()) {
            return rawParentSpecName;
        }
        for (String allowedDimensionName : allowedDimensionNames) {
            if (sameSpecDimension(rawParentSpecName, allowedDimensionName)) {
                return allowedDimensionName;
            }
        }
        return rawParentSpecName;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castMapList(Object value) {
        if (!(value instanceof List<?> list)) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private String asText(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String canonicalizeSpecName(String parentSpecName,
                                        String aiSpecName,
                                        Map<String, Object> temuRow,
                                        Map<String, Object> originRow) {
        String normalizedParentSpecName = asText(parentSpecName);
        if (!StringUtils.hasText(normalizedParentSpecName)) {
            return asText(aiSpecName);
        }

        String exactTemuValue = extractSpecJsonValue(temuRow == null ? null : temuRow.get("specJson"), normalizedParentSpecName);
        if (StringUtils.hasText(exactTemuValue)) {
            return exactTemuValue;
        }

        String exactOriginValue = extractSpecJsonValue(originRow == null ? null : originRow.get("specJson"), normalizedParentSpecName);
        if (StringUtils.hasText(exactOriginValue)) {
            return exactOriginValue;
        }

        if (isModelLikeSpec(normalizedParentSpecName)) {
            String specKey = extractSpecKeyValue(temuRow, originRow);
            if (StringUtils.hasText(specKey)) {
                return specKey;
            }
        }

        return asText(aiSpecName);
    }

    private String choosePreferredParentSpecName(List<Map<String, Object>> allowedParentSpecs,
                                                 List<Map<String, Object>> temuSkus) {
        Map<String, Long> nameToId = new LinkedHashMap<>();
        if (allowedParentSpecs != null) {
            for (Map<String, Object> row : allowedParentSpecs) {
                if (row == null) continue;
                String name = asText(row.get("parentSpecName"));
                Long id = toLong(row.get("parentSpecId"));
                if (StringUtils.hasText(name) && id != null && id > 0) {
                    nameToId.put(name, id);
                }
            }
        }
        if (nameToId.isEmpty()) {
            return null;
        }

        int colorScore = 0;
        int sizeScore = 0;
        int qtyScore = 0;
        int modelScore = 0;
        if (temuSkus != null) {
            for (Map<String, Object> row : temuSkus) {
                String head = asText(row == null ? null : row.get("specKey"));
                if (!StringUtils.hasText(head)) continue;
                head = head.toLowerCase();
                if (head.contains("颜色") || head.contains("color")) colorScore++;
                if (head.contains("尺码") || head.contains("尺寸") || head.contains("size")) sizeScore++;
                if (head.contains("数量") || head.contains("件数") || head.contains("quantity") || head.contains("pack")) qtyScore++;
                if (head.contains("型号") || head.contains("model") || head.contains("规格") || head.contains("spec")) modelScore++;
            }
        }

        int max = Math.max(Math.max(colorScore, sizeScore), Math.max(qtyScore, modelScore));
        if (max <= 0) {
            return firstExistingParentSpecName(nameToId, "规格", "型号", "尺码", "尺寸", "数量", "颜色");
        }
        if (max == colorScore) return firstExistingParentSpecName(nameToId, "颜色");
        if (max == sizeScore) return firstExistingParentSpecName(nameToId, "尺码", "尺寸");
        if (max == qtyScore) return firstExistingParentSpecName(nameToId, "数量");
        return firstExistingParentSpecName(nameToId, "型号", "规格");
    }

    private String firstExistingParentSpecName(Map<String, Long> nameToId, String... candidates) {
        if (nameToId == null || nameToId.isEmpty() || candidates == null) return null;
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) continue;
            if (nameToId.containsKey(candidate)) return candidate;
        }
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) continue;
            for (String key : nameToId.keySet()) {
                if (StringUtils.hasText(key) && (key.contains(candidate) || candidate.contains(key))) {
                    return key;
                }
            }
        }
        return null;
    }

    private boolean isModelLikeSpec(String parentSpecName) {
        String value = asText(parentSpecName);
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.contains("型号") || value.contains("规格") || value.toLowerCase().contains("model") || value.toLowerCase().contains("spec");
    }

    private boolean isAppearanceLikeSpec(String parentSpecName) {
        String value = asText(parentSpecName);
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return value.contains("颜色")
                || value.contains("花色")
                || value.contains("图案")
                || lower.contains("color")
                || lower.contains("pattern");
    }

    private String extractSpecKeyValue(Map<String, Object> temuRow,
                                       Map<String, Object> originRow) {
        String temuSpecKey = asText(temuRow == null ? null : temuRow.get("specKey"));
        if (StringUtils.hasText(temuSpecKey)) {
            return temuSpecKey;
        }
        return asText(originRow == null ? null : originRow.get("specKey"));
    }

    private String extractSpecJsonValue(Object specJson, String parentSpecName) {
        if (!StringUtils.hasText(parentSpecName) || specJson == null) {
            return null;
        }
        if (specJson instanceof JsonNode node && node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String fieldName = names.next();
                if (!sameSpecDimension(fieldName, parentSpecName)) {
                    continue;
                }
                String value = asText(node.path(fieldName).asText(null));
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            return null;
        }
        if (specJson instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String fieldName = asText(entry.getKey());
                if (!sameSpecDimension(fieldName, parentSpecName)) {
                    continue;
                }
                String value = asText(entry.getValue());
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private boolean sameSpecDimension(String left, String right) {
        String a = asText(left);
        String b = asText(right);
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return false;
        }
        return a.equalsIgnoreCase(b) || a.contains(b) || b.contains(a);
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private AddGloGoodsRequest.ProductSkuReq buildProductSkuReq(ProductCollection pc,
                                                                Integer skuIndex,
                                                                String originSkuId,
                                                                String temuSkuId,
                                                                Map<String, Object> originRow,
                                                                Map<String, Object> temuRow,
                                                                int siteId,
                                                                String warehouseId,
                                                                int defaultStock,
                                                                int maxStock,
                                                                List<AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq> skuSpecReqDtos) {
        AddGloGoodsRequest.ProductSkuReq sku = new AddGloGoodsRequest.ProductSkuReq();
        sku.setCurrencyType("CNY");

        BigDecimal supplyPrice = toBigDecimal(temuRow == null ? null : temuRow.get("supplyPrice"));
        BigDecimal originPrice = toBigDecimal(originRow == null ? null : originRow.get("price"));
        BigDecimal finalPrice = supplyPrice != null ? supplyPrice : originPrice;
        sku.setSiteSupplierPrices(new ArrayList<>(List.of(
                new AddGloGoodsRequest.ProductSkuReq.SiteSupplierPrice(siteId, priceToCents(finalPrice))
        )));

        Integer stock = toInt(originRow == null ? null : originRow.get("stock"));
        int publishStock = normalizePublishStock(stock, defaultStock, maxStock);
        AddGloGoodsRequest.ProductSkuReq.ProductSkuStockQuantityReq stockReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuStockQuantityReq();
        stockReq.setWarehouseStockQuantityReqs(new ArrayList<>(List.of(
                new AddGloGoodsRequest.ProductSkuReq.ProductSkuStockQuantityReq.WarehouseStockQuantityReq(
                publishStock,
                        warehouseId,
                        null
                )
        )));
        sku.setProductSkuStockQuantityReq(stockReq);

        String thumb = firstNonBlank(
                temuRow == null ? null : asText(temuRow.get("image")),
                originRow == null ? null : asText(originRow.get("image")),
                pc == null ? null : pc.getProductMainImage()
        );
        sku.setThumbUrl(thumb);

        Integer weightG = toInt(temuRow == null ? null : temuRow.get("weightG"));
        int weightMg = Math.max(30, weightG == null ? 150 : weightG) * 1000;
        int lenMm = cmToMmOrDefault(toBigDecimal(temuRow == null ? null : temuRow.get("lengthCm")), 10);
        int widthMm = cmToMmOrDefault(toBigDecimal(temuRow == null ? null : temuRow.get("widthCm")), 5);
        int heightMm = cmToMmOrDefault(toBigDecimal(temuRow == null ? null : temuRow.get("heightCm")), 5);
        int[] dims = new int[]{Math.max(1, lenMm), Math.max(1, widthMm), Math.max(1, heightMm)};
        Arrays.sort(dims);
        int longest = dims[2];
        int middle = dims[1];
        int shortest = Math.max(2, dims[0]);

        AddGloGoodsRequest.ProductSkuReq.ProductSkuWeightReq weightReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuWeightReq();
        weightReq.setValue(weightMg);
        AddGloGoodsRequest.ProductSkuReq.ProductSkuVolumeReq volReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuVolumeReq();
        volReq.setLen(longest);
        volReq.setWidth(middle);
        volReq.setHeight(shortest);
        AddGloGoodsRequest.ProductSkuReq.ProductSkuSensitiveAttrReq senAttr = new AddGloGoodsRequest.ProductSkuReq.ProductSkuSensitiveAttrReq();
        senAttr.setIsSensitive(0);
        senAttr.setSensitiveList(new ArrayList<>());
        senAttr.setSensitiveTypes(new ArrayList<>());
        AddGloGoodsRequest.ProductSkuReq.ProductSkuSensitiveLimitReq senLimit = new AddGloGoodsRequest.ProductSkuReq.ProductSkuSensitiveLimitReq();

        AddGloGoodsRequest.ProductSkuReq.ProductSkuWhExtAttrReq whExt = new AddGloGoodsRequest.ProductSkuReq.ProductSkuWhExtAttrReq();
        whExt.setProductSkuWeightReq(weightReq);
        whExt.setProductSkuVolumeReq(volReq);
        whExt.setProductSkuSensitiveAttrReq(senAttr);
        whExt.setProductSkuSensitiveLimitReq(senLimit);
        sku.setProductSkuWhExtAttrReq(whExt);

        int idx = skuIndex == null ? 0 : skuIndex;
        sku.setExtCode(safeSkuExtCode(temuSkuId, originSkuId, idx));
        sku.setProductSkuSpecReqs(skuSpecReqDtos == null ? new ArrayList<>() : skuSpecReqDtos);
        return sku;
    }

    private int normalizePublishStock(Integer rawStock, int defaultStock, int maxStock) {
        int fallbackStock = defaultStock > 0 ? defaultStock : 100;
        int upperBound = maxStock > 0 ? maxStock : 10842;
        int candidate = rawStock == null || rawStock <= 0 ? fallbackStock : rawStock;
        if (candidate <= 0) {
            candidate = fallbackStock;
        }
        return Math.min(candidate, upperBound);
    }

    private int parseInt(String value, int def) {
        if (!StringUtils.hasText(value)) return def;
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return def;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }

    private int priceToCents(BigDecimal price) {
        BigDecimal p = price;
        if (p == null || p.compareTo(BigDecimal.ZERO) <= 0) {
            p = new BigDecimal("0.01");
        }
        return p.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private int cmToMmOrDefault(BigDecimal cm, int defCm) {
        BigDecimal v = cm == null || cm.compareTo(BigDecimal.ZERO) <= 0 ? new BigDecimal(defCm) : cm;
        return v.multiply(new BigDecimal("10")).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String safeSkuExtCode(String temuSkuId, String originSkuId, int idx) {
        String v = firstNonBlank(temuSkuId, originSkuId);
        if (!StringUtils.hasText(v)) v = "SKU_" + idx;
        return v.length() > 60 ? v.substring(0, 60) : v;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private record InferenceArtifacts(
            Map<String, Object> result,
            List<AddGloGoodsRequest.ProductSpecPropertyReq> productSpecPropertyReqs,
            List<List<AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq>> mainProductSkuSpecReqGroups,
            List<List<AddGloGoodsRequest.ProductSkuReq>> productSkuReqGroups
    ) {
    }

        private record ResolvedSkuPlan(
            Map<String, Object> outPlan,
            AddGloGoodsRequest.ProductSkuReq skuReq,
            List<AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq> skuSpecReqDtos,
            String originSkuId,
            String temuSkuId
        ) {
        }

        private record SuccessCaseStructure(
            List<String> dimensionNames,
            List<String> mainDimensionNames,
            int maxMainSpecCount,
            boolean usesEmptyMainSpecPlaceholder
        ) {
        }
}
