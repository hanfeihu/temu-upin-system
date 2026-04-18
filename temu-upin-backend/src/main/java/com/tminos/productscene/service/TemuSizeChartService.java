package com.tminos.productscene.service;

import com.tminos.productscene.entity.ProductCollection;
import com.tminos.temu.upin.sdk.v2.dto.AddGloGoodsRequest;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import com.tminos.temu.upin.sdk.v2.dto.TemuApiResponse;
import com.tminos.temu.upin.sdk.v2.sizechart.TemuSizeChartV2Client;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TemuSizeChartService {

    private static final Logger log = LoggerFactory.getLogger(TemuSizeChartService.class);

    private final TemuOpenApiCredentialService temuOpenApiCredentialService;

    public TemuSizeChartService(TemuOpenApiCredentialService temuOpenApiCredentialService) {
        this.temuOpenApiCredentialService = temuOpenApiCredentialService;
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
        TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
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

        TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
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

        List<CreateVariant> variants = buildCreateVariants(content, generalSizeType, sizeValue, List.of(), null);
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

    public SizeTemplateBinding ensureSizeTemplateForApparel(ProductCollection pc, AddGloGoodsRequest req) throws Exception {
        TemuOpenApiCredentials creds = temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow();
        return ensureSizeTemplateForApparel(pc, req, creds);
    }

    public SizeTemplateBinding ensureSizeTemplateForApparel(ProductCollection pc,
                                                            AddGloGoodsRequest req,
                                                            TemuOpenApiCredentials creds) throws Exception {
        if (pc == null || req == null) return null;
        if (!requiresSizeTemplate(pc, req)) return null;
        if (req.getSizeTemplateId() != null && req.getSizeTemplateId() > 0) {
            Long baseBusinessId = firstPositiveId(req.getSizeTemplateIds());
            if (baseBusinessId == null) {
                baseBusinessId = firstPositiveId(req.getShowSizeTemplateIds());
            }
            if (baseBusinessId == null) {
                baseBusinessId = req.getSizeTemplateId();
            }
            return new SizeTemplateBinding(baseBusinessId, req.getSizeTemplateId());
        }

        int leafCatId = parseLeafCatId(pc.getTemuCatid());
        if (leafCatId <= 0) return null;

        TemuOpenApiCredentials effectiveCreds = creds == null
                ? temuOpenApiCredentialService.getDefaultTemuOpenApiCredentialsOrThrow()
                : creds;
        TemuSizeChartV2Client client = new TemuSizeChartV2Client(effectiveCreds);

        Integer classId = null;
        Integer parentClassId = null;
        TemuApiResponse<TemuSizeChartV2Client.SizeChartClassGetResult> classResp = client.getClassInfo(leafCatId, null);
        if (classResp != null && classResp.isSuccess() && classResp.getResult() != null && classResp.getResult().getSizeSpecClassCat() != null) {
            classId = classResp.getResult().getSizeSpecClassCat().getClassId();
            parentClassId = classResp.getResult().getSizeSpecClassCat().getParentClassId();
        }

        boolean forceFreshTemplate = shouldForceFreshTemplate(pc, req);
        TemuSizeChartV2Client.SizeSpecData chosen = null;
        if (!forceFreshTemplate) {
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
            chosen = chooseTemplate(templateList, classId, pc);
            if (chosen == null) {
                TemuApiResponse<TemuSizeChartV2Client.SizeChartListResult> globalResp = client.getTemplates(null, 0, 200);
                if (globalResp != null && globalResp.isSuccess() && globalResp.getResult() != null) {
                    chosen = chooseTemplate(globalResp.getResult().getSizeSpecDataList(), classId, pc);
                }
            }
        } else {
            log.info("size chart reuse skipped: spuId={}, catId={}, reason=fresh-template-required", pc.getId(), leafCatId);
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

        long baseBusinessId = chosen.getBusinessId();
        long tempBusinessId = createResp.getResult().getTempBusinessId();
        req.setSizeTemplateId(tempBusinessId);
        req.setSizeTemplateIds(new ArrayList<>(List.of(baseBusinessId)));
        req.setShowSizeTemplateIds(new ArrayList<>(List.of(baseBusinessId)));
        log.info("publish size template binding prepared: baseBusinessId={}, tempBusinessId={}, catId={}, classId={}",
                baseBusinessId, tempBusinessId, leafCatId, classId);
        return new SizeTemplateBinding(baseBusinessId, tempBusinessId);
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
        boolean denseFootwearFallback = isDenseFootwearTemplate(groups, elements, settingsResp);
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
        List<CreateVariant> variants = buildCreateVariants(content, generalSizeType, sizeValue, optionalElementList, fullGroupMapped);
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
                createResp = client.createSizeChart(variant.content, catCandidate, classCandidate, nameCandidate, true, variant.ext);
                if (createResp != null && createResp.isSuccess() && createResp.getResult() != null && createResp.getResult().getBusinessId() != null) {
                    used = new CreateVariant(variant.name + "@catId=" + catCandidate + "@classId=" + classCandidate + "@name=" + nameCandidate, variant.content, variant.ext);
                    break;
                }
                try {
                    raw = client.createSizeChartRaw(variant.content, catCandidate, classCandidate, nameCandidate, true, variant.ext);
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
            if (denseFootwearFallback) {
                log.info("size chart minimal variants failed, fallback to dense footwear template: spuId={}, catId={}",
                        pc == null ? null : pc.getId(), leafCatId);
                return createDenseFootwearTemplate(client, leafCatId, classId, parentClassId, pc, req);
            }
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

    private TemuSizeChartV2Client.SizeSpecData createDenseFootwearTemplate(TemuSizeChartV2Client client,
                                                                           int leafCatId,
                                                                           Integer classId,
                                                                           Integer parentClassId,
                                                                           ProductCollection pc,
                                                                           AddGloGoodsRequest req) throws Exception {
        List<Integer> classCandidates = new ArrayList<>();
        if (classId != null && classId > 0) classCandidates.add(classId);
        if (parentClassId != null && parentClassId > 0 && !classCandidates.contains(parentClassId)) {
            classCandidates.add(parentClassId);
        }
        if (classCandidates.isEmpty()) {
            classCandidates.add(null);
        }
        List<Integer> catCandidates = new ArrayList<>();
        catCandidates.add(leafCatId);
        catCandidates.add(null);

        List<String> sizeValues = findAllPublishSizeValues(req);
        Map<String, Object> content = buildDenseFootwearTemplateContent(sizeValues);
        String name = buildTemplateName(pc, sizeValues.isEmpty() ? "EU36-40" : sizeValues.get(0));
        TemuApiResponse<TemuSizeChartV2Client.SizeChartCreateResult> createResp = null;
        String raw = null;
        Integer usedCat = null;
        Integer usedClass = null;
        for (Integer catCandidate : catCandidates) {
            for (Integer classCandidate : classCandidates) {
                createResp = client.createSizeChart(content, catCandidate, classCandidate, name, true, null);
                if (createResp != null && createResp.isSuccess() && createResp.getResult() != null && createResp.getResult().getBusinessId() != null) {
                    usedCat = catCandidate;
                    usedClass = classCandidate;
                    break;
                }
                try {
                    raw = client.createSizeChartRaw(content, catCandidate, classCandidate, name, true, null);
                } catch (Exception ignored) {
                }
                log.warn("dense footwear size chart create catId={} classId={} name={} payload={} raw={}",
                        catCandidate, classCandidate, name, content, raw);
            }
            if (createResp != null && createResp.isSuccess() && createResp.getResult() != null && createResp.getResult().getBusinessId() != null) {
                break;
            }
        }
        if (createResp == null || !createResp.isSuccess() || createResp.getResult() == null || createResp.getResult().getBusinessId() == null) {
            throw new IllegalStateException("dense footwear size chart create failed: errorCode="
                    + (createResp == null ? "null" : createResp.getErrorCode())
                    + ", errorMsg=" + (createResp == null ? "empty" : createResp.getErrorMsg())
                    + ", payload=" + content
                    + ", raw=" + raw);
        }
        TemuSizeChartV2Client.SizeSpecData data = new TemuSizeChartV2Client.SizeSpecData();
        data.setBusinessId(createResp.getResult().getBusinessId());
        data.setClassId(usedClass == null ? classId : usedClass);
        data.setName(name);
        log.info("dense footwear size chart created: businessId={}, catId={}, classId={}",
                data.getBusinessId(), usedCat, usedClass);
        return data;
    }

    private boolean isDenseFootwearTemplate(List<TemuSizeChartV2Client.MetaGroup> groups,
                                            List<TemuSizeChartV2Client.MetaElement> elements,
                                            TemuApiResponse<TemuSizeChartV2Client.SizeChartSettingsResult> settingsResp) {
        if (groups == null || elements == null) {
            return false;
        }
        Set<Integer> groupIds = new LinkedHashSet<>();
        for (TemuSizeChartV2Client.MetaGroup group : groups) {
            if (group != null && group.getId() != null) {
                groupIds.add(group.getId());
            }
        }
        Set<Integer> elementIds = new LinkedHashSet<>();
        for (TemuSizeChartV2Client.MetaElement element : elements) {
            if (element != null && element.getId() != null && Boolean.TRUE.equals(element.getNecessary())) {
                elementIds.add(element.getId());
            }
        }
        if (!groupIds.containsAll(Set.of(1, 2, 4, 6, 8, 9, 20, 21, 28, 54))) {
            return false;
        }
        if (!elementIds.containsAll(Set.of(10013, 20033))) {
            return false;
        }
        return settingsResp != null
                && settingsResp.isSuccess()
                && settingsResp.getResult() != null
                && settingsResp.getResult().getMappingContent() == null;
    }

    private Map<String, Object> buildDenseFootwearTemplateContent(List<String> sizeValues) {
        List<Map<String, Object>> groups = new ArrayList<>();
        groups.add(groupRow(1, "尺码"));
        groups.add(groupRow(2, "欧码"));
        groups.add(groupRow(4, "英码"));
        groups.add(groupRow(6, "美码"));
        groups.add(groupRow(20, "日本码"));
        groups.add(groupRow(21, "韩国码"));
        groups.add(groupRow(9, "墨西哥码"));
        groups.add(groupRow(8, "巴西码"));
        groups.add(groupRow(54, "哥伦比亚码"));
        groups.add(groupRow(28, "智利码"));

        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(elementRow(10013, "脚长"));
        elements.add(elementRow(20033, "鞋内长"));

        List<Map<String, Object>> records = new ArrayList<>();
        List<String> effectiveSizeValues = (sizeValues == null || sizeValues.isEmpty())
                ? List.of("36-37", "38-39", "40-41")
                : sizeValues;
        for (int i = 0; i < effectiveSizeValues.size(); i++) {
            records.add(denseFootwearRecord(effectiveSizeValues.get(i), i));
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("groupList", groups);
        meta.put("elementList", elements);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("records", records);
        content.put("meta", meta);
        content.put("generalSizeType", 1);
        content.put("localSizeSource", 1);
        return content;
    }

    private Map<String, Object> denseFootwearRecord(String sizeValue,
                                                    int index) {
        double[] bounds = extractSizeBounds(sizeValue, 36 + (index * 2));
        double start = bounds[0];
        double end = bounds[1];
        double average = Math.max(start, (start + end) / 2.0d);

        String cn = normalizePublishSizeValue(sizeValue);
        String eu = formatHalfStep(average);
        String uk = formatHalfStep(Math.max(1.0d, average - 32.5d));
        String us = formatHalfStep(Math.max(1.0d, average - 30.5d));
        String jp = formatHalfStep(Math.max(10.0d, average - 13.0d));
        String kr = formatSizeNumber(Math.max(100.0d, (average - 13.0d) * 10.0d));
        String mx = jp;
        String br = formatSizeNumber(Math.max(20.0d, average - 2.0d));
        String co = br;
        String cl = formatSizeNumber(Math.max(20.0d, average - 1.0d));
        String footLength = formatSizeNumber(210 + (index * 10.0d));
        String innerLength = formatSizeNumber(220 + (index * 10.0d));

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("1", cn);
        values.put("2", eu);
        values.put("4", uk);
        values.put("6", us);
        values.put("20", jp);
        values.put("21", kr);
        values.put("9", mx);
        values.put("8", br);
        values.put("54", co);
        values.put("28", cl);
        values.put("10013", footLength);
        values.put("20033", innerLength);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("values", values);
        return record;
    }

    private List<String> findAllPublishSizeValues(AddGloGoodsRequest req) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (req == null) {
            return new ArrayList<>();
        }
        if (req.getProductSpecPropertyReqs() != null) {
            for (AddGloGoodsRequest.ProductSpecPropertyReq property : req.getProductSpecPropertyReqs()) {
                if (property == null || !isSizeLikeName(property.getPropName()) || !StringUtils.hasText(property.getPropValue())) {
                    continue;
                }
                String normalized = normalizePublishSizeValue(property.getPropValue());
                if (StringUtils.hasText(normalized)) {
                    values.add(normalized);
                }
            }
        }
        if (values.isEmpty()) {
            String single = findSizeValue(req);
            if (StringUtils.hasText(single)) {
                values.add(normalizePublishSizeValue(single));
            }
        }
        return new ArrayList<>(values);
    }

    private String normalizePublishSizeValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        java.util.regex.Matcher pairMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*[-/]\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(value);
        if (pairMatcher.find()) {
            return formatSizeNumber(pairMatcher.group(1)) + "-" + formatSizeNumber(pairMatcher.group(2));
        }
        java.util.regex.Matcher singleMatcher = java.util.regex.Pattern
                .compile("(?<!\\d)(\\d{1,3}(?:\\.\\d+)?)(?!\\d)")
                .matcher(value);
        if (singleMatcher.find()) {
            return formatSizeNumber(singleMatcher.group(1));
        }
        return value;
    }

    private double[] extractSizeBounds(String sizeValue,
                                       double fallbackStart) {
        double start = fallbackStart;
        double end = fallbackStart + 1.0d;
        if (!StringUtils.hasText(sizeValue)) {
            return new double[]{start, end};
        }
        java.util.regex.Matcher pairMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*[-/]\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(sizeValue);
        if (pairMatcher.find()) {
            try {
                start = Double.parseDouble(pairMatcher.group(1));
                end = Double.parseDouble(pairMatcher.group(2));
                if (end < start) {
                    end = start;
                }
                return new double[]{start, end};
            } catch (NumberFormatException ignored) {
                return new double[]{fallbackStart, fallbackStart + 1.0d};
            }
        }
        java.util.regex.Matcher singleMatcher = java.util.regex.Pattern
                .compile("(?<!\\d)(\\d{1,3}(?:\\.\\d+)?)(?!\\d)")
                .matcher(sizeValue);
        if (singleMatcher.find()) {
            try {
                start = Double.parseDouble(singleMatcher.group(1));
                return new double[]{start, start};
            } catch (NumberFormatException ignored) {
                return new double[]{fallbackStart, fallbackStart + 1.0d};
            }
        }
        return new double[]{start, end};
    }

    private String formatHalfStep(double value) {
        double rounded = Math.round(value * 2.0d) / 2.0d;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.001d) {
            return String.valueOf((int) Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private String formatSizeNumber(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        try {
            return formatSizeNumber(Double.parseDouble(raw));
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    private String formatSizeNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001d) {
            return String.valueOf((int) Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private Map<String, Object> groupRow(int id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private Map<String, Object> elementRow(int id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private List<CreateVariant> buildCreateVariants(Map<String, Object> baseContent,
                                                    Integer generalSizeType,
                                                    String sizeValue,
                                                    List<Map<String, Object>> optionalElementList,
                                                    Map<String, Object> fullGroupMapped) {
        List<CreateVariant> out = new ArrayList<>();
        if (fullGroupMapped != null) {
            out.add(new CreateVariant("full-groups-no-elements", withoutLocalSizeSource(fullGroupMapped), null));
        }
        out.add(new CreateVariant("minimal-primary-only", minimalPrimaryOnly(baseContent, generalSizeType), null));
        out.add(new CreateVariant("minimal-primary-standard-name", minimalPrimaryStandardName(baseContent, generalSizeType), null));
        out.add(new CreateVariant("minimal-primary-original-value", minimalPrimaryOriginalValue(baseContent, generalSizeType, sizeValue), null));
        out.add(new CreateVariant("minimal-primary-no-local-source", withoutLocalSizeSource(minimalPrimaryOriginalValue(baseContent, generalSizeType, sizeValue)), null));
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
    private Map<String, Object> minimalPrimaryOriginalValue(Map<String, Object> baseContent,
                                                            Integer generalSizeType,
                                                            String sizeValue) {
        Map<String, Object> out = minimalPrimaryOnly(baseContent, generalSizeType);
        Object recordsObj = out.get("records");
        if (recordsObj instanceof List<?> records && !records.isEmpty() && records.get(0) instanceof Map<?, ?> first) {
            Object valuesObj = ((Map<String, Object>) first).get("values");
            if (valuesObj instanceof Map<?, ?> rawValues) {
                Map<String, Object> values = (Map<String, Object>) rawValues;
                String effectiveSizeValue = StringUtils.hasText(sizeValue)
                        ? sizeValue.trim()
                        : String.valueOf(values.get(String.valueOf(generalSizeType == null ? 1 : generalSizeType)));
                values.put(String.valueOf(generalSizeType == null ? 1 : generalSizeType), effectiveSizeValue);
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
                if (p != null && isSizeLikeName(p.getPropName()) && StringUtils.hasText(p.getPropValue())) {
                    String normalized = normalizeSizeValue(p.getPropValue());
                    if (StringUtils.hasText(normalized)) {
                        return normalized;
                    }
                }
            }
        }
        if (req.getProductSpecPropertyReqs() != null) {
            for (AddGloGoodsRequest.ProductSpecPropertyReq p : req.getProductSpecPropertyReqs()) {
                if (p != null && isSizeLikeName(p.getPropName()) && StringUtils.hasText(p.getPropValue())) {
                    String normalized = normalizeSizeValue(p.getPropValue());
                    if (StringUtils.hasText(normalized)) {
                        return normalized;
                    }
                }
            }
        }
        return null;
    }

    private String normalizeSizeValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        java.util.regex.Matcher recommendedPair = java.util.regex.Pattern
                .compile("(?i)(?:foot size of|recommended(?: a)? foot size(?: is| of)?|recommend(?:ed)?[^0-9]*)(\\d+(?:\\.\\d+)?)\\s*[-/]\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(value);
        if (recommendedPair.find()) {
            return midpointOrRangeValue(recommendedPair.group(1), recommendedPair.group(2));
        }
        java.util.regex.Matcher directPair = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*[-/]\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(value);
        if (directPair.find()) {
            return midpointOrRangeValue(directPair.group(1), directPair.group(2));
        }
        java.util.regex.Matcher singleValue = java.util.regex.Pattern
                .compile("(?<!\\d)(\\d{1,3}(?:\\.\\d+)?)(?!\\d)")
                .matcher(value);
        if (singleValue.find()) {
            return formatSizeNumber(singleValue.group(1));
        }
        return value;
    }

    private String midpointOrRangeValue(String startRaw, String endRaw) {
        String start = formatSizeNumber(startRaw);
        String end = formatSizeNumber(endRaw);
        if (!StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return StringUtils.hasText(start) ? start : end;
        }
        try {
            double startValue = Double.parseDouble(start);
            double endValue = Double.parseDouble(end);
            return formatHalfStep((startValue + endValue) / 2.0d);
        } catch (NumberFormatException ignored) {
            return start + "-" + end;
        }
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
                .filter(it -> it != null && it.getBusinessId() != null && it.getBusinessId() > 0)
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

    private boolean requiresSizeTemplate(ProductCollection pc, AddGloGoodsRequest req) {
        if (hasSizeSpec(req)) {
            return true;
        }
        String text = ((pc.getTemuCatname() == null ? "" : pc.getTemuCatname()) + " "
                + (pc.getTemuCatid() == null ? "" : pc.getTemuCatid())).toLowerCase(Locale.ROOT);
        return text.contains("服饰")
                || text.contains("服装")
                || text.contains("童装")
                || text.contains("男童")
                || text.contains("女童")
                || text.contains("雨衣")
                || text.contains("鞋")
                || text.contains("鞋靴")
                || text.contains("凉鞋")
                || text.contains("拖鞋")
                || text.contains("靴");
    }

    private boolean shouldForceFreshTemplate(ProductCollection pc,
                                             AddGloGoodsRequest req) {
        return false;
    }

    private int countPublishSizeValues(AddGloGoodsRequest req) {
        return findAllPublishSizeValues(req).size();
    }

    private boolean hasSizeSpec(AddGloGoodsRequest req) {
        if (req == null) {
            return false;
        }
        if (req.getProductPropertyReqs() != null) {
            for (AddGloGoodsRequest.ProductPropertyReq propertyReq : req.getProductPropertyReqs()) {
                if (propertyReq != null && isSizeLikeName(propertyReq.getPropName())) {
                    return true;
                }
            }
        }
        if (req.getProductSpecPropertyReqs() != null) {
            for (AddGloGoodsRequest.ProductSpecPropertyReq propertyReq : req.getProductSpecPropertyReqs()) {
                if (propertyReq != null && isSizeLikeName(propertyReq.getPropName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSizeLikeName(String propName) {
        if (!StringUtils.hasText(propName)) {
            return false;
        }
        String normalized = propName.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("尺码")
                || normalized.contains("鞋码")
                || normalized.equals("size");
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

    private Long firstPositiveId(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        for (Long id : ids) {
            if (id != null && id > 0) {
                return id;
            }
        }
        return null;
    }

    public record SizeTemplateBinding(Long baseBusinessId, Long tempBusinessId) {
    }
}
