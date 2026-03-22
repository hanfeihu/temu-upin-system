package com.tminos.productscene.service;

import com.tminos.productscene.entity.ProductCollection;
import com.tminos.temu.openapi.dto.AddGloGoodsRequest;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.dto.TemuApiResponse;
import com.tminos.temu.upin.sdk.v2.sizechart.TemuSizeChartV2Client;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TemuSizeChartService {

    private static final Logger log = LoggerFactory.getLogger(TemuSizeChartService.class);

    private final PlatformConfigService platformConfigService;

    public TemuSizeChartService(PlatformConfigService platformConfigService) {
        this.platformConfigService = platformConfigService;
    }

    public Map<String, Object> debugQueryForProduct(ProductCollection pc,
                                                    String apiType,
                                                    Map<String, Object> request) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        if (pc == null) {
            out.put("success", false);
            out.put("message", "product is null");
            return out;
        }
        int leafCatId = parseLeafCatId(pc.getTemuCatid());
        TemuOpenApiCredentials creds = platformConfigService.getDefaultTemuOpenApiCredentialsOrThrow();
        TemuSizeChartV2Client client = new TemuSizeChartV2Client(creds);

        String api = normalizeApiType(apiType);
        Integer classId = toInt(request == null ? null : request.get("classId"));
        Integer catId = toInt(request == null ? null : request.get("catId"));
        if (catId == null || catId <= 0) catId = leafCatId > 0 ? leafCatId : null;
        if ((TemuSizeChartV2Client.API_META_GET.equals(api) || TemuSizeChartV2Client.API_SETTINGS_GET.equals(api))
                && (classId == null || classId <= 0)) {
            TemuApiResponse<TemuSizeChartV2Client.SizeChartClassGetResult> classResp = client.getClassInfo(catId, null);
            if (classResp != null && classResp.isSuccess() && classResp.getResult() != null && classResp.getResult().getSizeSpecClassCat() != null) {
                classId = classResp.getResult().getSizeSpecClassCat().getClassId();
            }
        }

        out.put("apiType", api);
        Map<String, Object> effectiveParams = new LinkedHashMap<>();
        String raw;
        switch (api) {
            case TemuSizeChartV2Client.API_CLASS_GET -> {
                if (catId != null) effectiveParams.put("catId", catId);
                if (classId != null) effectiveParams.put("classId", classId);
                raw = client.getClassInfoRaw(catId, classId);
            }
            case TemuSizeChartV2Client.API_META_GET -> {
                if (catId != null) effectiveParams.put("catId", catId);
                if (classId != null) effectiveParams.put("classId", classId);
                raw = client.getMetaRaw(catId, classId);
            }
            case TemuSizeChartV2Client.API_SETTINGS_GET -> {
                if (catId != null) effectiveParams.put("catId", catId);
                if (classId != null) effectiveParams.put("classId", classId);
                raw = client.getSettingsRaw(catId, classId);
            }
            case TemuSizeChartV2Client.API_LIST -> {
                Integer offset = toInt(request == null ? null : request.get("offset"));
                Integer pageSize = toInt(request == null ? null : request.get("pageSize"));
                if (offset == null) offset = 0;
                if (pageSize == null || pageSize <= 0) pageSize = 20;
                if (catId != null) effectiveParams.put("catId", catId);
                effectiveParams.put("offset", offset);
                effectiveParams.put("pageSize", pageSize);
                raw = client.getTemplatesRaw(catId, offset, pageSize);
            }
            default -> throw new IllegalArgumentException("unsupported apiType: " + apiType);
        }
        out.put("effectiveParams", effectiveParams);
        out.put("raw", raw);
        out.put("success", true);
        return out;
    }

    public Map<String, Object> debugForProduct(ProductCollection pc, String forcedSizeValue) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        if (pc == null) {
            out.put("success", false);
            out.put("message", "product is null");
            return out;
        }
        int leafCatId = parseLeafCatId(pc.getTemuCatid());
        out.put("productId", pc.getId());
        out.put("leafCatId", leafCatId);
        out.put("temuCatid", pc.getTemuCatid());
        out.put("temuCatname", pc.getTemuCatname());

        TemuOpenApiCredentials creds = platformConfigService.getDefaultTemuOpenApiCredentialsOrThrow();
        out.put("shopId", creds.getShopId());
        out.put("appKey", creds.getAppKey());
        TemuSizeChartV2Client client = new TemuSizeChartV2Client(creds);

        TemuApiResponse<TemuSizeChartV2Client.SizeChartClassGetResult> classResp = client.getClassInfo(leafCatId, null);
        out.put("classRespSuccess", classResp != null && classResp.isSuccess());
        out.put("classRespCode", classResp == null ? null : classResp.getErrorCode());
        out.put("classRespMsg", classResp == null ? null : classResp.getErrorMsg());
        Integer classId = null;
        if (classResp != null && classResp.getResult() != null && classResp.getResult().getSizeSpecClassCat() != null) {
            TemuSizeChartV2Client.SizeSpecClassCat c = classResp.getResult().getSizeSpecClassCat();
            classId = c.getClassId();
            out.put("classInfo", Map.of(
                    "classId", c.getClassId(),
                    "parentClassId", c.getParentClassId() == null ? 0 : c.getParentClassId(),
                    "classType", c.getClassType() == null ? 0 : c.getClassType(),
                    "relatedClassIds", c.getRelatedClassIds() == null ? List.of() : c.getRelatedClassIds()
            ));
        }

        TemuApiResponse<TemuSizeChartV2Client.SizeChartMetaGetResult> metaResp = client.getMeta(leafCatId, classId);
        out.put("metaRespSuccess", metaResp != null && metaResp.isSuccess());
        out.put("metaRespCode", metaResp == null ? null : metaResp.getErrorCode());
        out.put("metaRespMsg", metaResp == null ? null : metaResp.getErrorMsg());

        TemuApiResponse<TemuSizeChartV2Client.SizeChartSettingsResult> settingsResp = client.getSettings(leafCatId, classId);
        out.put("settingsRespSuccess", settingsResp != null && settingsResp.isSuccess());
        out.put("settingsRespCode", settingsResp == null ? null : settingsResp.getErrorCode());
        out.put("settingsRespMsg", settingsResp == null ? null : settingsResp.getErrorMsg());
        if (settingsResp != null && settingsResp.getResult() != null) {
            out.put("settingsGroupChName", settingsResp.getResult().getGroupChName());
            out.put("settingsGroupEnName", settingsResp.getResult().getGroupEnName());
            out.put("settingsSizeList", settingsResp.getResult().getSizeList());
        }

        String sizeValue = StringUtils.hasText(forcedSizeValue) ? forcedSizeValue.trim() : null;
        if (!StringUtils.hasText(sizeValue)) sizeValue = "110";
        out.put("sizeValue", sizeValue);

        List<TemuSizeChartV2Client.MetaGroup> groups = metaResp != null && metaResp.getResult() != null && metaResp.getResult().getSizeSpecMeta() != null
                ? metaResp.getResult().getSizeSpecMeta().getGroupList() : null;
        List<Map<String, Object>> groupList = new ArrayList<>();
        if (groups != null) {
            for (TemuSizeChartV2Client.MetaGroup g : groups) {
                if (g == null || g.getId() == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", g.getId());
                row.put("name", g.getName());
                groupList.add(row);
            }
        }
        Map<String, Object> values = new LinkedHashMap<>();
        if (!groupList.isEmpty()) {
            values.put(String.valueOf(groupList.get(0).get("id")), sizeValue);
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("records", List.of(Map.of("values", values)));
        content.put("meta", Map.of("groupList", groupList, "elementList", List.of()));
        Integer generalSizeType = groupList.isEmpty() ? 1 : toInt(groupList.get(0).get("id"));
        content.put("generalSizeType", generalSizeType);
        if (settingsResp != null && settingsResp.getResult() != null && settingsResp.getResult().getMappingContent() != null) {
            Map<String, Object> mapped = buildFromSettingsMapping(settingsResp.getResult().getMappingContent(), sizeValue, generalSizeType);
            if (mapped != null) {
                content = mapped;
            }
        }

        List<CreateVariant> variants = buildCreateVariants(content, generalSizeType, List.of(), null);
        List<Map<String, Object>> attempts = new ArrayList<>();
        for (CreateVariant variant : variants) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", variant.name);
            row.put("payload", variant.content);
            TemuApiResponse<TemuSizeChartV2Client.SizeChartCreateResult> createResp = client.createSizeChart(variant.content, leafCatId, classId, buildTemplateName(pc, sizeValue), false, variant.ext);
            row.put("success", createResp != null && createResp.isSuccess());
            row.put("errorCode", createResp == null ? null : createResp.getErrorCode());
            row.put("errorMsg", createResp == null ? null : createResp.getErrorMsg());
            row.put("requestId", createResp == null ? null : createResp.getRequestId());
            if (createResp != null && createResp.getResult() != null) {
                row.put("businessId", createResp.getResult().getBusinessId());
            }
            if (createResp == null || !createResp.isSuccess()) {
                try {
                    row.put("raw", client.createSizeChartRaw(variant.content, leafCatId, classId, buildTemplateName(pc, sizeValue), false, variant.ext));
                } catch (Exception e) {
                    row.put("raw", e.getMessage());
                }
            }
            attempts.add(row);
        }
        out.put("attempts", attempts);
        out.put("success", true);
        return out;
    }

    public Integer ensureSizeTemplateForApparel(ProductCollection pc, AddGloGoodsRequest req) throws Exception {
        if (pc == null || req == null) return null;
        if (!isApparelCategory(pc)) return null;
        if (req.getSizeTemplateId() != null && req.getSizeTemplateId() > 0) return req.getSizeTemplateId();

        int leafCatId = parseLeafCatId(pc.getTemuCatid());
        if (leafCatId <= 0) return null;

        TemuOpenApiCredentials creds = platformConfigService.getDefaultTemuOpenApiCredentialsOrThrow();
        TemuSizeChartV2Client client = new TemuSizeChartV2Client(creds);

        Integer classId = null;
        Integer parentClassId = null;
        TemuApiResponse<TemuSizeChartV2Client.SizeChartClassGetResult> classResp = client.getClassInfo(leafCatId, null);
        if (classResp != null && classResp.isSuccess() && classResp.getResult() != null && classResp.getResult().getSizeSpecClassCat() != null) {
            classId = classResp.getResult().getSizeSpecClassCat().getClassId();
            parentClassId = classResp.getResult().getSizeSpecClassCat().getParentClassId();
        }

        TemuApiResponse<TemuSizeChartV2Client.SizeChartListResult> listResp = client.getTemplates(leafCatId, 0, 100);
        if (listResp == null || !listResp.isSuccess()) {
            String raw = null;
            try {
                raw = client.getTemplatesRaw(leafCatId, 0, 100);
            } catch (Exception ignored) {
            }
            throw new IllegalStateException("size chart template list failed: errorCode="
                    + (listResp == null ? "null" : listResp.getErrorCode())
                    + ", errorMsg=" + (listResp == null ? "empty" : listResp.getErrorMsg())
                    + ", raw=" + raw);
        }

        List<TemuSizeChartV2Client.SizeSpecData> templateList = listResp.getResult() == null ? null : listResp.getResult().getSizeSpecDataList();
        TemuSizeChartV2Client.SizeSpecData chosen = chooseTemplate(templateList, classId, pc);
        if (chosen == null) {
            TemuApiResponse<TemuSizeChartV2Client.SizeChartListResult> globalResp = client.getTemplates(null, 0, 200);
            if (globalResp != null && globalResp.isSuccess() && globalResp.getResult() != null) {
                chosen = chooseTemplate(globalResp.getResult().getSizeSpecDataList(), classId, pc);
            }
        }
        if (chosen == null) {
            chosen = createSimpleSizeTemplate(client, leafCatId, classId, parentClassId, pc, req);
        }
        if (chosen == null || chosen.getBusinessId() == null || chosen.getBusinessId() <= 0) {
            throw new IllegalStateException("no reusable size chart template for catId=" + leafCatId);
        }

        TemuApiResponse<TemuSizeChartV2Client.SizeChartTemplateCreateResult> createResp = client.createTemplate(chosen.getBusinessId());
        if (createResp == null || !createResp.isSuccess() || createResp.getResult() == null || createResp.getResult().getTempBusinessId() == null) {
            throw new IllegalStateException("size chart template create failed: errorCode="
                    + (createResp == null ? "null" : createResp.getErrorCode())
                    + ", errorMsg=" + (createResp == null ? "empty" : createResp.getErrorMsg()));
        }

        int tempBusinessId = createResp.getResult().getTempBusinessId().intValue();
        req.setSizeTemplateId(tempBusinessId);
        req.setSizeTemplateIds(new ArrayList<>(List.of(tempBusinessId)));
        req.setShowSizeTemplateIds(new ArrayList<>(List.of(tempBusinessId)));
        return tempBusinessId;
    }

    private TemuSizeChartV2Client.SizeSpecData createSimpleSizeTemplate(TemuSizeChartV2Client client,
                                                                        int leafCatId,
                                                                        Integer classId,
                                                                        Integer parentClassId,
                                                                        ProductCollection pc,
                                                                        AddGloGoodsRequest req) throws Exception {
        if (classId == null || classId <= 0) return null;
        TemuApiResponse<TemuSizeChartV2Client.SizeChartMetaGetResult> metaResp = client.getMeta(leafCatId, classId);
        if (metaResp == null || !metaResp.isSuccess() || metaResp.getResult() == null || metaResp.getResult().getSizeSpecMeta() == null) {
            throw new IllegalStateException("size chart meta failed: errorCode="
                    + (metaResp == null ? "null" : metaResp.getErrorCode())
                    + ", errorMsg=" + (metaResp == null ? "empty" : metaResp.getErrorMsg())
                    + ", raw=" + client.getMetaRaw(leafCatId, classId));
        }
        log.info("size chart meta allowRange={}, groupList={}, elementList={}",
                metaResp.getResult().getAllowRange(),
                metaResp.getResult().getSizeSpecMeta().getGroupList(),
                metaResp.getResult().getSizeSpecMeta().getElementList());
        TemuApiResponse<TemuSizeChartV2Client.SizeChartSettingsResult> settingsResp = client.getSettings(leafCatId, classId);
        if (settingsResp != null) {
            log.info("size chart settings groupChName={}, groupEnName={}, sizeList={}, mappingContent={}",
                    settingsResp.getResult() == null ? null : settingsResp.getResult().getGroupChName(),
                    settingsResp.getResult() == null ? null : settingsResp.getResult().getGroupEnName(),
                    settingsResp.getResult() == null ? null : settingsResp.getResult().getSizeList(),
                    settingsResp.getResult() == null ? null : settingsResp.getResult().getMappingContent());
        }
        String sizeValue = findSizeValue(req);
        if (!StringUtils.hasText(sizeValue)) return null;

        List<TemuSizeChartV2Client.MetaGroup> groups = metaResp.getResult().getSizeSpecMeta().getGroupList();
        if (groups == null || groups.isEmpty()) return null;

        List<Map<String, Object>> groupList = new ArrayList<>();
        for (TemuSizeChartV2Client.MetaGroup g : groups) {
            if (g == null || g.getId() == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", g.getId());
            row.put("name", g.getName());
            groupList.add(row);
        }

        List<Map<String, Object>> elementList = new ArrayList<>();
        List<TemuSizeChartV2Client.MetaElement> elements = metaResp.getResult().getSizeSpecMeta().getElementList();
        if (elements != null) {
            for (TemuSizeChartV2Client.MetaElement e : elements) {
                if (e == null || e.getId() == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", e.getId());
                row.put("name", e.getName());
                elementList.add(row);
            }
        }

        List<Map<String, Object>> effectiveElementList = elementList.stream()
                .filter(it -> {
                    Integer id = toInt(it.get("id"));
                    if (id == null) return false;
                    if (elements == null) return false;
                    for (TemuSizeChartV2Client.MetaElement e : elements) {
                        if (e != null && id.equals(e.getId())) {
                            return Boolean.TRUE.equals(e.getNecessary());
                        }
                    }
                    return false;
                })
                .toList();
        List<Map<String, Object>> optionalElementList = new ArrayList<>(elementList);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(String.valueOf(groupList.get(0).get("id")), sizeValue);
        for (Map<String, Object> e : effectiveElementList) {
            values.put(String.valueOf(e.get("id")), "1");
        }

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("values", values);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("groupList", groupList);
        meta.put("elementList", effectiveElementList);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("records", List.of(record));
        content.put("meta", meta);
        String name = buildTemplateName(pc, sizeValue);
        Integer generalSizeType = toInt(groupList.get(0).get("id"));
        Map<String, Object> fullGroupMapped = null;
        if (settingsResp != null && settingsResp.isSuccess() && settingsResp.getResult() != null && settingsResp.getResult().getMappingContent() != null) {
            Map<String, Object> mapped = buildFromSettingsMapping(settingsResp.getResult().getMappingContent(), sizeValue, generalSizeType);
            if (mapped != null) {
                content = mapped;
            }
            fullGroupMapped = buildFromSettingsMappingAllGroups(settingsResp.getResult().getMappingContent(), sizeValue, generalSizeType);
        }
        List<CreateVariant> variants = buildCreateVariants(content, generalSizeType, optionalElementList, fullGroupMapped);
        TemuApiResponse<TemuSizeChartV2Client.SizeChartCreateResult> createResp = null;
        String raw = null;
        CreateVariant used = null;
        List<Integer> classCandidates = new ArrayList<>();
        if (classId != null && classId > 0) classCandidates.add(classId);
        if (parentClassId != null && parentClassId > 0 && !classCandidates.contains(parentClassId)) classCandidates.add(parentClassId);
        if (classCandidates.isEmpty()) classCandidates.add(null);
        List<Integer> catCandidates = new ArrayList<>();
        catCandidates.add(leafCatId);
        catCandidates.add(null);
        List<String> nameCandidates = new ArrayList<>();
        nameCandidates.add(name);
        nameCandidates.add(null);
        for (Integer catCandidate : catCandidates) {
            for (Integer classCandidate : classCandidates) {
                for (String nameCandidate : nameCandidates) {
                for (CreateVariant variant : variants) {
                createResp = client.createSizeChart(variant.content, catCandidate, classCandidate, nameCandidate, false, variant.ext);
                if (createResp != null && createResp.isSuccess() && createResp.getResult() != null && createResp.getResult().getBusinessId() != null) {
                    used = new CreateVariant(variant.name + "@catId=" + catCandidate + "@classId=" + classCandidate + "@name=" + nameCandidate, variant.content, variant.ext);
                    break;
                }
                try {
                    raw = client.createSizeChartRaw(variant.content, catCandidate, classCandidate, nameCandidate, false, variant.ext);
                } catch (Exception ignored) {
                }
                log.warn("size chart create catId={} classId={} name={} variant={} payload={} raw={}", catCandidate, classCandidate, nameCandidate, variant.name, variant.content, raw);
                used = new CreateVariant(variant.name + "@catId=" + catCandidate + "@classId=" + classCandidate + "@name=" + nameCandidate, variant.content, variant.ext);
                }
                if (createResp != null && createResp.isSuccess() && createResp.getResult() != null && createResp.getResult().getBusinessId() != null) {
                    break;
                }
                }
            }
            if (createResp != null && createResp.isSuccess() && createResp.getResult() != null && createResp.getResult().getBusinessId() != null) {
                break;
            }
        }
        if (createResp == null || !createResp.isSuccess() || createResp.getResult() == null || createResp.getResult().getBusinessId() == null) {
            throw new IllegalStateException("size chart create failed: errorCode="
                    + (createResp == null ? "null" : createResp.getErrorCode())
                    + ", errorMsg=" + (createResp == null ? "empty" : createResp.getErrorMsg())
                    + ", variant=" + (used == null ? null : used.name)
                    + ", payload=" + (used == null ? null : used.content)
                    + ", raw=" + raw);
        }
        TemuSizeChartV2Client.SizeSpecData data = new TemuSizeChartV2Client.SizeSpecData();
        data.setBusinessId(createResp.getResult().getBusinessId());
        data.setClassId(classId);
        data.setName(name);
        return data;
    }

    private List<CreateVariant> buildCreateVariants(Map<String, Object> baseContent,
                                                    Integer generalSizeType,
                                                    List<Map<String, Object>> optionalElementList,
                                                    Map<String, Object> fullGroupMapped) {
        List<CreateVariant> out = new ArrayList<>();
        if (fullGroupMapped != null) {
            out.add(new CreateVariant("full-groups-no-elements", withoutLocalSizeSource(fullGroupMapped), null));
        }
        out.add(new CreateVariant("minimal-primary-only", minimalPrimaryOnly(baseContent, generalSizeType), null));
        out.add(new CreateVariant("minimal-primary-standard-name", minimalPrimaryStandardName(baseContent, generalSizeType), null));
        out.add(new CreateVariant("minimal-primary-original-value", minimalPrimaryOriginalValue(baseContent, generalSizeType), null));
        out.add(new CreateVariant("minimal-primary-no-local-source", withoutLocalSizeSource(minimalPrimaryOriginalValue(baseContent, generalSizeType)), null));
        out.add(new CreateVariant("minimal-primary-standard-no-local-source", withoutLocalSizeSource(minimalPrimaryStandardName(baseContent, generalSizeType)), null));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> minimalPrimaryOnly(Map<String, Object> baseContent, Integer generalSizeType) {
        Map<String, Object> out = new LinkedHashMap<>();
        String key = String.valueOf(generalSizeType == null ? 1 : generalSizeType);
        Object recordsObj = baseContent.get("records");
        if (recordsObj instanceof List<?> records && !records.isEmpty() && records.get(0) instanceof Map<?, ?> first) {
            Object valuesObj = ((Map<String, Object>) first).get("values");
            if (valuesObj instanceof Map<?, ?> valuesMap) {
                Map<String, Object> values = new LinkedHashMap<>();
                Object v = valuesMap.get(key);
                if (v != null) {
                    values.put(key, v);
                }
                out.put("records", List.of(Map.of("values", values)));
            }
        }
        out.put("meta", buildPrimaryOnlyMeta((Map<String, Object>) baseContent.get("meta"), generalSizeType));
        out.put("generalSizeType", generalSizeType);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> minimalPrimaryStandardName(Map<String, Object> baseContent, Integer generalSizeType) {
        Map<String, Object> out = minimalPrimaryOnly(baseContent, generalSizeType);
        Object recordsObj = out.get("records");
        if (recordsObj instanceof List<?> records && !records.isEmpty() && records.get(0) instanceof Map<?, ?> first) {
            Object valuesObj = ((Map<String, Object>) first).get("values");
            if (valuesObj instanceof Map<?, ?> rawValues) {
                Map<String, Object> values = (Map<String, Object>) rawValues;
                Object us = ((Map<String, Object>) ((List<?>) baseContent.get("records")).get(0)).get("values");
                if (us instanceof Map<?, ?> originValues) {
                    Object usValue = originValues.get("6");
                    if (usValue != null && !String.valueOf(usValue).trim().isEmpty()) {
                        values.put(String.valueOf(generalSizeType == null ? 1 : generalSizeType), String.valueOf(usValue).trim());
                    }
                }
            }
        }
        out.put("localSizeSource", 0);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> minimalPrimaryOriginalValue(Map<String, Object> baseContent, Integer generalSizeType) {
        Map<String, Object> out = minimalPrimaryOnly(baseContent, generalSizeType);
        Object recordsObj = out.get("records");
        if (recordsObj instanceof List<?> records && !records.isEmpty() && records.get(0) instanceof Map<?, ?> first) {
            Object valuesObj = ((Map<String, Object>) first).get("values");
            if (valuesObj instanceof Map<?, ?> rawValues) {
                Map<String, Object> values = (Map<String, Object>) rawValues;
                values.put(String.valueOf(generalSizeType == null ? 1 : generalSizeType), "110");
            }
        }
        out.put("localSizeSource", 0);
        return out;
    }

    private Map<String, Object> withoutLocalSizeSource(Map<String, Object> content) {
        Map<String, Object> out = new LinkedHashMap<>(content);
        out.remove("localSizeSource");
        return out;
    }

    @SuppressWarnings("unchecked")
    private void applyElementValues(Map<String, Object> content, List<Map<String, Object>> elements) {
        if (content == null || elements == null) return;
        Map<String, Object> meta = (Map<String, Object>) content.get("meta");
        if (meta != null) {
            meta.put("elementList", new ArrayList<>(elements));
        }
        Object recordsObj = content.get("records");
        if (!(recordsObj instanceof List<?> records) || records.isEmpty()) return;
        Object first = records.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) return;
        Object valuesObj = ((Map<String, Object>) firstMap).get("values");
        if (!(valuesObj instanceof Map<?, ?>)) return;
        Map<String, Object> values = (Map<String, Object>) valuesObj;
        for (Map<String, Object> element : elements) {
            Integer id = toInt(element.get("id"));
            if (id != null) {
                values.put(String.valueOf(id), "1");
            }
        }
    }

    private Map<String, Object> cloneContent(Map<String, Object> baseContent,
                                             Integer generalSizeType,
                                             Integer localSizeSource,
                                             boolean withBody,
                                             boolean bodyEmptyLists) {
        Map<String, Object> content = new LinkedHashMap<>(baseContent);
        content.put("generalSizeType", generalSizeType);
        content.put("localSizeSource", localSizeSource);
        if (withBody) {
            content.put("bodyRecords", new ArrayList<>());
            Map<String, Object> bodyMeta = new LinkedHashMap<>();
            bodyMeta.put("groupList", bodyEmptyLists ? new ArrayList<>() : null);
            bodyMeta.put("elementList", bodyEmptyLists ? new ArrayList<>() : null);
            content.put("bodyMeta", bodyMeta);
        }
        return content;
    }

    private Map<String, Object> ext(boolean isDoubleSize, boolean manualGroup) {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("isDoubleSize", isDoubleSize);
        if (manualGroup) {
            ext.put("manualGroupIdList", List.of(1));
        } else {
            ext.put("manualGroupIdList", new ArrayList<>());
        }
        return ext;
    }

    private static class CreateVariant {
        final String name;
        final Map<String, Object> content;
        final Map<String, Object> ext;

        CreateVariant(String name, Map<String, Object> content, Map<String, Object> ext) {
            this.name = name;
            this.content = content;
            this.ext = ext;
        }
    }

    private String findSizeValue(AddGloGoodsRequest req) {
        if (req == null) return null;
        if (req.getProductPropertyReqs() != null) {
            for (AddGloGoodsRequest.ProductPropertyReq p : req.getProductPropertyReqs()) {
                if (p != null && "尺码".equals(p.getPropName()) && StringUtils.hasText(p.getPropValue())) {
                    return p.getPropValue().trim();
                }
            }
        }
        if (req.getProductSpecPropertyReqs() != null) {
            for (AddGloGoodsRequest.ProductSpecPropertyReq p : req.getProductSpecPropertyReqs()) {
                if (p != null && "尺码".equals(p.getPropName()) && StringUtils.hasText(p.getPropValue())) {
                    return p.getPropValue().trim();
                }
            }
        }
        return null;
    }

    private String buildTemplateName(ProductCollection pc, String sizeValue) {
        String base = pc.getProductName();
        if (!StringUtils.hasText(base)) base = "TEMU尺寸模板";
        base = base.trim();
        if (base.length() > 24) base = base.substring(0, 24);
        return base + "-" + sizeValue;
    }

    private Integer toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildFromSettingsMapping(TemuSizeChartV2Client.MappingContent mappingContent,
                                                         String sizeValue,
                                                         Integer generalSizeType) {
        if (mappingContent == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> records = mappingContent.getRecords();
        if (records == null || records.isEmpty()) return null;

        String generalKey = String.valueOf(generalSizeType == null ? 1 : generalSizeType);
        List<Map<String, Object>> matchedRecords = new ArrayList<>();
        for (Map<String, Object> r : records) {
            if (r == null) continue;
            Object valuesObj = r.get("values");
            if (!(valuesObj instanceof Map<?, ?> valuesMap)) continue;
            Object mainValue = valuesMap.get(generalKey);
            if (mainValue != null && sizeValue.equals(String.valueOf(mainValue).trim())) {
                Map<String, Object> row = new LinkedHashMap<>(r);
                Map<String, Object> values = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : valuesMap.entrySet()) {
                    values.put(String.valueOf(e.getKey()), e.getValue());
                }
                row.put("values", values);
                matchedRecords.add(row);
            }
        }
        if (matchedRecords.isEmpty()) {
            return null;
        }
        out.put("records", matchedRecords);
        out.put("meta", buildPrimaryOnlyMeta(mappingContent.getMeta(), generalSizeType));
        out.put("generalSizeType", generalSizeType);
        out.put("localSizeSource", 0);
        return out;
    }

    private Map<String, Object> buildFromSettingsMappingAllGroups(TemuSizeChartV2Client.MappingContent mappingContent,
                                                                  String sizeValue,
                                                                  Integer generalSizeType) {
        if (mappingContent == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> records = mappingContent.getRecords();
        if (records == null || records.isEmpty()) return null;
        String generalKey = String.valueOf(generalSizeType == null ? 1 : generalSizeType);
        Map<String, Object> matchedRecord = null;
        for (Map<String, Object> r : records) {
            if (r == null) continue;
            Object valuesObj = r.get("values");
            if (!(valuesObj instanceof Map<?, ?> valuesMap)) continue;
            Object mainValue = valuesMap.get(generalKey);
            if (mainValue != null && sizeValue.equals(String.valueOf(mainValue).trim())) {
                matchedRecord = new LinkedHashMap<>(r);
                break;
            }
        }
        if (matchedRecord == null) return null;
        out.put("records", List.of(matchedRecord));
        Map<String, Object> rawMeta = mappingContent.getMeta();
        Map<String, Object> meta = new LinkedHashMap<>();
        if (rawMeta != null) {
            Object groups = rawMeta.get("groupList");
            meta.put("groupList", groups == null ? new ArrayList<>() : groups);
        } else {
            meta.put("groupList", new ArrayList<>());
        }
        meta.put("elementList", new ArrayList<>());
        out.put("meta", meta);
        out.put("generalSizeType", generalSizeType);
        out.put("localSizeSource", 0);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPrimaryOnlyMeta(Map<String, Object> metaObj, Integer generalSizeType) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (metaObj == null) {
            out.put("groupList", new ArrayList<>());
            out.put("elementList", new ArrayList<>());
            return out;
        }
        Integer target = generalSizeType == null ? 1 : generalSizeType;
        List<Map<String, Object>> groups = new ArrayList<>();
        for (String key : List.of("groupList", "groups")) {
            Object raw = metaObj.get(key);
            if (!(raw instanceof List<?> list)) continue;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> gm)) continue;
                Object id = gm.get("id");
                Integer gid = toInt(id);
                if (gid != null && gid.equals(target)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", gid);
                    row.put("name", gm.get("name"));
                    groups.add(row);
                }
            }
            if (!groups.isEmpty()) break;
        }
        out.put("groupList", groups);
        out.put("elementList", new ArrayList<>());
        return out;
    }

    private TemuSizeChartV2Client.SizeSpecData chooseTemplate(List<TemuSizeChartV2Client.SizeSpecData> dataList,
                                                              Integer classId,
                                                              ProductCollection pc) {
        if (dataList == null || dataList.isEmpty()) return null;
        List<TemuSizeChartV2Client.SizeSpecData> candidates = dataList.stream()
                .filter(it -> it != null && Boolean.TRUE.equals(it.getReusable()))
                .sorted(Comparator.comparing(TemuSizeChartV2Client.SizeSpecData::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (candidates.isEmpty()) return null;

        String title = pc.getProductName() == null ? "" : pc.getProductName().toLowerCase(Locale.ROOT);
        for (TemuSizeChartV2Client.SizeSpecData row : candidates) {
            if (classId != null && classId.equals(row.getClassId()) && looksLikeMatch(row.getName(), title)) {
                return row;
            }
        }
        for (TemuSizeChartV2Client.SizeSpecData row : candidates) {
            if (classId != null && classId.equals(row.getClassId())) return row;
        }
        return candidates.get(0);
    }

    private boolean looksLikeMatch(String templateName, String productTitle) {
        String name = templateName == null ? "" : templateName.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(name) || !StringUtils.hasText(productTitle)) return false;
        return (name.contains("童") && productTitle.contains("童"))
                || (name.contains("雨衣") && productTitle.contains("雨衣"))
                || (name.contains("男童") && productTitle.contains("男童"));
    }

    private boolean isApparelCategory(ProductCollection pc) {
        String text = ((pc.getTemuCatname() == null ? "" : pc.getTemuCatname()) + " "
                + (pc.getTemuCatid() == null ? "" : pc.getTemuCatid())).toLowerCase(Locale.ROOT);
        return text.contains("服饰") || text.contains("童装") || text.contains("男童") || text.contains("女童") || text.contains("雨衣");
    }

    private int parseLeafCatId(String csv) {
        if (!StringUtils.hasText(csv)) return 0;
        String[] parts = csv.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i] == null ? "" : parts[i].trim();
            if (p.isEmpty()) continue;
            try {
                int v = Integer.parseInt(p);
                if (v > 0) return v;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private String normalizeApiType(String apiType) {
        String s = apiType == null ? "" : apiType.trim();
        if (TemuSizeChartV2Client.API_CLASS_GET.equals(s) || "class.get".equals(s)) return TemuSizeChartV2Client.API_CLASS_GET;
        if (TemuSizeChartV2Client.API_META_GET.equals(s) || "meta.get".equals(s)) return TemuSizeChartV2Client.API_META_GET;
        if (TemuSizeChartV2Client.API_SETTINGS_GET.equals(s) || "settings.get".equals(s)) return TemuSizeChartV2Client.API_SETTINGS_GET;
        if (TemuSizeChartV2Client.API_LIST.equals(s) || "list".equals(s) || "templates.get".equals(s)) return TemuSizeChartV2Client.API_LIST;
        return s;
    }
}
