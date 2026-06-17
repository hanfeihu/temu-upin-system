package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.TemuParentSpecMappingTable;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductCollectionTemuSku;
import com.tminos.temu.upin.sdk.v2.category.CategoryApiClient;
import com.tminos.temu.upin.sdk.v2.dto.AddGloGoodsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class TemuSkuPublishDraftConverter {

    private static final Logger log = LoggerFactory.getLogger(TemuSkuPublishDraftConverter.class);

    private final ObjectMapper objectMapper;
    private final TemuParentSpecMappingTable parentSpecMappingTable;
    private final TemuSkuSpecTranslateService skuSpecTranslateService;

    public TemuSkuPublishDraftConverter(ObjectMapper objectMapper,
                                        TemuParentSpecMappingTable parentSpecMappingTable,
                                        TemuSkuSpecTranslateService skuSpecTranslateService) {
        this.objectMapper = objectMapper;
        this.parentSpecMappingTable = parentSpecMappingTable;
        this.skuSpecTranslateService = skuSpecTranslateService;
    }

    public StoredPublishSpecDraft convert(ProductCollection pc,
                                          List<ProductCollectionTemuSku> temuSkus,
                                          CategoryApiClient categoryClient,
                                          int siteId,
                                          String warehouseId,
                                          int defaultStock,
                                          int maxStock,
                                          List<String> warnings) throws Exception {
        List<ProductCollectionTemuSku> sourceSkus = temuSkus == null ? List.of() : temuSkus.stream()
                .filter(Objects::nonNull)
                .toList();
        if (sourceSkus.isEmpty()) {
            warnings.add("TEMU SKU 为空，无法生成发布规格");
            return null;
        }

        List<ParentSpec> allowedParentSpecs = loadAllowedParentSpecs(categoryClient);
        if (allowedParentSpecs.isEmpty()) {
            warnings.add("未获取到 TEMU 父规格列表，无法生成发布规格");
            return null;
        }

        List<String> orderedSpecKeys = resolveOrderedSpecKeys(sourceSkus);
        if (orderedSpecKeys.isEmpty()) {
            warnings.add("TEMU SKU 的 specJson 没有有效规格维度，无法生成发布规格");
            return null;
        }

        List<SpecDimension> dimensions = resolveDimensions(orderedSpecKeys, sourceSkus, allowedParentSpecs, warnings);
        if (dimensions.isEmpty()) {
            warnings.add("TEMU SKU 的 specJson 维度未匹配到可用父规格，无法生成发布规格");
            return null;
        }

        String mainFieldName = resolveMainFieldName(dimensions);
        Map<String, SpecValueMetadata> specValueMetadataByKey = loadSaleSpecValueMetadata(pc, categoryClient);
        log.info("temu sku sale spec metadata resolved: spuId={}, keys={}, sizeMeta={}",
                pc == null ? null : pc.getId(),
                specValueMetadataByKey.size(),
                summarizeSizeMetadata(specValueMetadataByKey));
        LinkedHashMap<String, Integer> tempSpecIdByKey = new LinkedHashMap<>();
        int[] nextTempSpecId = {1};
        LinkedHashMap<String, AddGloGoodsRequest.ProductSpecPropertyReq> uniqueSpecs = new LinkedHashMap<>();
        List<List<AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq>> mainGroups = new ArrayList<>();
        List<List<AddGloGoodsRequest.ProductSkuReq>> skuGroups = new ArrayList<>();

        if (!StringUtils.hasText(mainFieldName)) {
            List<AddGloGoodsRequest.ProductSkuReq> skuReqs = buildSkuReqs(
                    pc,
                    sourceSkus,
                    dimensions,
                    uniqueSpecs,
                    tempSpecIdByKey,
                    nextTempSpecId,
                    specValueMetadataByKey,
                    siteId,
                    warehouseId,
                    defaultStock,
                    maxStock
            );
            if (skuReqs.isEmpty()) {
                warnings.add("TEMU SKU 转换后没有可发布的 SKU 列表");
                return null;
            }
            mainGroups.add(new ArrayList<>(List.of(emptyMainProductSkuSpecReq())));
            skuGroups.add(skuReqs);
        } else {
            SpecDimension mainDimension = dimensions.stream()
                    .filter(dimension -> mainFieldName.equals(dimension.fieldName()))
                    .findFirst()
                    .orElse(null);
            if (mainDimension == null) {
                warnings.add("未找到主销售属性维度，无法生成发布规格");
                return null;
            }

            LinkedHashMap<String, List<ProductCollectionTemuSku>> grouped = new LinkedHashMap<>();
            for (ProductCollectionTemuSku sku : sourceSkus) {
                String mainValue = canonicalizeSpecValueForPublish(
                        mainDimension.parentSpec(),
                        resolveSpecValue(sku, mainDimension.fieldName()),
                        specValueMetadataByKey
                );
                if (!StringUtils.hasText(mainValue)) {
                    warnings.add("SKU " + firstNonBlank(sku.getTemuSkuId(), sku.getOriginSkuId(), String.valueOf(sku.getId())) + " 缺少主销售属性值，已跳过");
                    continue;
                }
                grouped.computeIfAbsent(mainValue, key -> new ArrayList<>()).add(sku);
            }

            for (Map.Entry<String, List<ProductCollectionTemuSku>> entry : grouped.entrySet()) {
                String mainValue = entry.getKey();
                Integer tempSpecId = ensureTempSpec(
                        uniqueSpecs,
                        tempSpecIdByKey,
                        nextTempSpecId,
                        mainDimension.parentSpec(),
                        mainValue,
                        resolveSpecValueMetadata(specValueMetadataByKey, mainDimension.parentSpec(), mainValue)
                );

                AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq mainReq = new AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq();
                mainReq.setParentSpecId(mainDimension.parentSpec().parentSpecId());
                mainReq.setParentSpecName(mainDimension.parentSpec().parentSpecName());
                mainReq.setSpecId(tempSpecId);
                mainReq.setSpecName(mainValue);

                List<AddGloGoodsRequest.ProductSkuReq> skuReqs = buildSkuReqs(
                        pc,
                        entry.getValue(),
                        dimensions,
                        uniqueSpecs,
                        tempSpecIdByKey,
                        nextTempSpecId,
                        specValueMetadataByKey,
                        siteId,
                        warehouseId,
                        defaultStock,
                        maxStock
                );
                if (skuReqs.isEmpty()) {
                    continue;
                }
                mainGroups.add(new ArrayList<>(List.of(mainReq)));
                skuGroups.add(skuReqs);
            }
        }

        if (skuGroups.isEmpty()) {
            warnings.add("TEMU SKU 转换后没有生成可发布的 SKC 分组");
            return null;
        }

        return new StoredPublishSpecDraft(new ArrayList<>(uniqueSpecs.values()), mainGroups, skuGroups);
    }

    private List<AddGloGoodsRequest.ProductSkuReq> buildSkuReqs(ProductCollection pc,
                                                                List<ProductCollectionTemuSku> skus,
                                                                List<SpecDimension> dimensions,
                                                                Map<String, AddGloGoodsRequest.ProductSpecPropertyReq> uniqueSpecs,
                                                                Map<String, Integer> tempSpecIdByKey,
                                                                int[] nextTempSpecId,
                                                                Map<String, SpecValueMetadata> specValueMetadataByKey,
                                                                int siteId,
                                                                String warehouseId,
                                                                int defaultStock,
                                                                int maxStock) {
        List<AddGloGoodsRequest.ProductSkuReq> out = new ArrayList<>();
        for (int index = 0; index < skus.size(); index++) {
            ProductCollectionTemuSku sku = skus.get(index);
            if (sku == null) {
                continue;
            }
            List<AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq> skuSpecReqs = new ArrayList<>();
            for (SpecDimension dimension : dimensions) {
                String specValue = canonicalizeSpecValueForPublish(
                        dimension.parentSpec(),
                        resolveSpecValue(sku, dimension.fieldName()),
                        specValueMetadataByKey
                );
                if (!StringUtils.hasText(specValue)) {
                    continue;
                }
                Integer tempSpecId = ensureTempSpec(
                        uniqueSpecs,
                        tempSpecIdByKey,
                        nextTempSpecId,
                        dimension.parentSpec(),
                        specValue,
                        resolveSpecValueMetadata(specValueMetadataByKey, dimension.parentSpec(), specValue)
                );
                AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq skuSpecReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq();
                skuSpecReq.setParentSpecId(dimension.parentSpec().parentSpecId());
                skuSpecReq.setParentSpecName(dimension.parentSpec().parentSpecName());
                skuSpecReq.setSpecId(tempSpecId);
                skuSpecReq.setSpecName(specValue);
                skuSpecReqs.add(skuSpecReq);
            }
            if (skuSpecReqs.isEmpty()) {
                continue;
            }
            out.add(buildProductSkuReq(pc, index, sku, siteId, warehouseId, defaultStock, maxStock, skuSpecReqs));
        }
        return out;
    }

    private List<ParentSpec> loadAllowedParentSpecs(CategoryApiClient categoryClient) throws Exception {
        if (categoryClient == null) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(categoryClient.getParentSpecList());
        return collectParentSpecs(root.path("result"));
    }

    private List<String> resolveOrderedSpecKeys(List<ProductCollectionTemuSku> temuSkus) {
        LinkedHashMap<String, Boolean> keys = new LinkedHashMap<>();
        for (ProductCollectionTemuSku sku : temuSkus) {
            LinkedHashMap<String, String> specMap = parseSpecJson(sku == null ? null : sku.getSpecJson());
            for (Map.Entry<String, String> entry : specMap.entrySet()) {
                String fieldName = firstNonBlank(entry.getKey());
                String fieldValue = firstNonBlank(entry.getValue());
                if (StringUtils.hasText(fieldName) && StringUtils.hasText(fieldValue) && !"*".equals(fieldValue)) {
                    keys.putIfAbsent(fieldName, Boolean.TRUE);
                }
            }
        }
        return new ArrayList<>(keys.keySet());
    }

    private List<SpecDimension> resolveDimensions(List<String> orderedSpecKeys,
                                                  List<ProductCollectionTemuSku> temuSkus,
                                                  List<ParentSpec> allowedParentSpecs,
                                                  List<String> warnings) {
        Map<String, List<String>> valuesByField = collectDimensionValues(orderedSpecKeys, temuSkus);
        String preferredColorField = resolvePreferredColorField(orderedSpecKeys, valuesByField, allowedParentSpecs);
        LinkedHashSet<String> seenParentNames = new LinkedHashSet<>();
        List<SpecDimension> out = new ArrayList<>();
        for (String fieldName : orderedSpecKeys) {
            ParentSpec parentSpec = resolveParentSpec(
                    fieldName,
                    valuesByField.getOrDefault(fieldName, List.of()),
                    preferredColorField,
                    seenParentNames,
                    allowedParentSpecs,
                    warnings
            );
            if (parentSpec == null || parentSpec.parentSpecId() <= 0) {
                continue;
            }
            seenParentNames.add(parentSpec.parentSpecName());
            boolean varying = isVaryingDimension(temuSkus, fieldName);
            out.add(new SpecDimension(fieldName, parentSpec, varying));
        }
        return out;
    }

    private ParentSpec resolveParentSpec(String fieldName,
                                         List<String> fieldValues,
                                         String preferredColorField,
                                         LinkedHashSet<String> seenParentNames,
                                         List<ParentSpec> allowedParentSpecs,
                                         List<String> warnings) {
        ParentSpec preferredColorSpec = chooseUsableParentSpec(
                findParentSpec("颜色", allowedParentSpecs),
                fieldName,
                preferredColorField,
                seenParentNames
        );
        if (preferredColorSpec != null && Objects.equals(fieldName, preferredColorField)) {
            return preferredColorSpec;
        }

        String mappedName = parentSpecMappingTable.getMappedParentSpecName(fieldName);
        ParentSpec mappedMatch = chooseUsableParentSpec(
                findParentSpec(mappedName, allowedParentSpecs),
                fieldName,
                preferredColorField,
                seenParentNames
        );
        if (mappedMatch != null) {
            return mappedMatch;
        }

        ParentSpec directMatch = chooseUsableParentSpec(
                findParentSpec(fieldName, allowedParentSpecs),
                fieldName,
                preferredColorField,
                seenParentNames
        );
        if (directMatch != null) {
            return directMatch;
        }

        ParentSpec modelFallback = chooseUsableParentSpec(
                findParentSpec("型号", allowedParentSpecs),
                fieldName,
                preferredColorField,
                seenParentNames
        );
        if (modelFallback != null && !Objects.equals(fieldName, preferredColorField)) {
            warnings.add("规格维度“" + fieldName + "”未命中父规格映射，已回退到“" + modelFallback.parentSpecName() + "”");
            return modelFallback;
        }

        ParentSpec fallback = firstUnusedParentSpec(seenParentNames, allowedParentSpecs);
        if (fallback != null) {
            warnings.add("规格维度“" + fieldName + "”未命中父规格映射，已回退到“" + fallback.parentSpecName() + "”");
        }
        return fallback;
    }

    private Map<String, List<String>> collectDimensionValues(List<String> orderedSpecKeys,
                                                             List<ProductCollectionTemuSku> temuSkus) {
        Map<String, List<String>> valuesByField = new LinkedHashMap<>();
        if (orderedSpecKeys == null || orderedSpecKeys.isEmpty()) {
            return valuesByField;
        }
        for (String fieldName : orderedSpecKeys) {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            if (temuSkus != null) {
                for (ProductCollectionTemuSku sku : temuSkus) {
                    String value = resolveSpecValue(sku, fieldName);
                    if (StringUtils.hasText(value)) {
                        values.add(value);
                    }
                }
            }
            valuesByField.put(fieldName, new ArrayList<>(values));
        }
        return valuesByField;
    }

    private String resolvePreferredColorField(List<String> orderedSpecKeys,
                                              Map<String, List<String>> valuesByField,
                                              List<ParentSpec> allowedParentSpecs) {
        if (orderedSpecKeys == null || orderedSpecKeys.isEmpty()) {
            return null;
        }
        if (findParentSpec("颜色", allowedParentSpecs) == null) {
            return null;
        }
        for (String fieldName : orderedSpecKeys) {
            if (isColorLikeDimension(valuesByField.get(fieldName))) {
                return fieldName;
            }
        }
        for (String fieldName : orderedSpecKeys) {
            ParentSpec directMatch = findParentSpec(fieldName, allowedParentSpecs);
            if (directMatch != null && "颜色".equals(directMatch.parentSpecName())) {
                return fieldName;
            }
        }
        return orderedSpecKeys.get(0);
    }

    private ParentSpec chooseUsableParentSpec(ParentSpec candidate,
                                              String fieldName,
                                              String preferredColorField,
                                              LinkedHashSet<String> seenParentNames) {
        if (candidate == null || !StringUtils.hasText(candidate.parentSpecName())) {
            return null;
        }
        if (seenParentNames != null && seenParentNames.contains(candidate.parentSpecName())) {
            return null;
        }
        if ("颜色".equals(candidate.parentSpecName())
                && StringUtils.hasText(preferredColorField)
                && !Objects.equals(fieldName, preferredColorField)) {
            return null;
        }
        return candidate;
    }

    private ParentSpec firstUnusedParentSpec(LinkedHashSet<String> seenParentNames,
                                             List<ParentSpec> allowedParentSpecs) {
        if (allowedParentSpecs == null || allowedParentSpecs.isEmpty()) {
            return null;
        }
        for (ParentSpec parentSpec : allowedParentSpecs) {
            if (parentSpec == null || !StringUtils.hasText(parentSpec.parentSpecName())) {
                continue;
            }
            if (seenParentNames == null || !seenParentNames.contains(parentSpec.parentSpecName())) {
                return parentSpec;
            }
        }
        return allowedParentSpecs.get(0);
    }

    private ParentSpec findParentSpec(String candidate,
                                      List<ParentSpec> allowedParentSpecs) {
        String target = normalizeSpecToken(candidate);
        if (!StringUtils.hasText(target) || allowedParentSpecs == null || allowedParentSpecs.isEmpty()) {
            return null;
        }
        for (ParentSpec parentSpec : allowedParentSpecs) {
            String parentName = normalizeSpecToken(parentSpec.parentSpecName());
            if (!StringUtils.hasText(parentName)) {
                continue;
            }
            if (parentName.equalsIgnoreCase(target) || parentName.contains(target) || target.contains(parentName)) {
                return parentSpec;
            }
        }
        return null;
    }

    private boolean isVaryingDimension(List<ProductCollectionTemuSku> temuSkus,
                                       String fieldName) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ProductCollectionTemuSku sku : temuSkus) {
            String value = resolveSpecValue(sku, fieldName);
            if (StringUtils.hasText(value)) {
                values.add(normalizeSpecToken(value));
            }
            if (values.size() > 1) {
                return true;
            }
        }
        return false;
    }

    private boolean isColorLikeDimension(List<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (isColorLikeValue(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isColorLikeValue(String value) {
        String normalized = normalizeSpecToken(value);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        List<String> nonColorTokens = List.of(
                "packaging", "package", "bag", "box", "opp", "gift", "cm", "mm", "kg", "pcs", "piece", "set"
        );
        for (String token : nonColorTokens) {
            if (normalized.contains(token)) {
                return false;
            }
        }
        List<String> colorTokens = List.of(
                "black", "white", "gray", "grey", "blue", "pink", "purple", "red", "green", "yellow",
                "orange", "brown", "beige", "khaki", "gold", "silver", "coffee", "transparent",
                "黑", "白", "灰", "蓝", "粉", "紫", "红", "绿", "黄", "橙", "棕", "咖", "金", "银"
        );
        for (String token : colorTokens) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String resolveMainFieldName(List<SpecDimension> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return null;
        }

        for (SpecDimension dimension : dimensions) {
            if (dimension == null || dimension.parentSpec() == null) {
                continue;
            }
            if ("颜色".equals(dimension.parentSpec().parentSpecName())) {
                return dimension.fieldName();
            }
        }

        for (SpecDimension dimension : dimensions) {
            if (dimension == null || dimension.parentSpec() == null || !dimension.varying()) {
                continue;
            }
            if (!isModelLikeParentSpec(dimension.parentSpec().parentSpecName())) {
                return dimension.fieldName();
            }
        }

        for (SpecDimension dimension : dimensions) {
            if (dimension != null && dimension.varying()) {
                return dimension.fieldName();
            }
        }

        return dimensions.get(0).fieldName();
    }

    private Integer ensureTempSpec(Map<String, AddGloGoodsRequest.ProductSpecPropertyReq> uniqueSpecs,
                                   Map<String, Integer> tempSpecIdByKey,
                                   int[] nextTempSpecId,
                                   ParentSpec parentSpec,
                                   String specValue,
                                   SpecValueMetadata metadata) {
        Integer valueGroupId = metadata == null || metadata.valueGroupId() == null ? 0 : metadata.valueGroupId();
        String key = parentSpec.parentSpecId() + "\u0001" + normalizeSpecToken(specValue) + "\u0001" + valueGroupId;
        Integer tempSpecId = tempSpecIdByKey.get(key);
        if (tempSpecId != null) {
            return tempSpecId;
        }
        tempSpecId = nextTempSpecId[0]++;
        tempSpecIdByKey.put(key, tempSpecId);

        AddGloGoodsRequest.ProductSpecPropertyReq top = new AddGloGoodsRequest.ProductSpecPropertyReq();
        top.setVid(metadata == null || metadata.vid() == null ? 0 : metadata.vid());
        top.setSpecId(tempSpecId);
        top.setValueGroupId(valueGroupId);
        top.setParentSpecId(parentSpec.parentSpecId());
        top.setValueGroupName(metadata == null ? "" : firstNonBlank(metadata.valueGroupName(), ""));
        top.setValueUnit("");
        top.setPid(0);
        top.setTemplatePid(0);
        top.setNumberInputValue("");
        top.setPropValue(specValue);
        top.setPropName(parentSpec.parentSpecName());
        top.setRefPid(0);
        uniqueSpecs.put(key, top);
        return tempSpecId;
    }

    private Map<String, SpecValueMetadata> loadSaleSpecValueMetadata(ProductCollection pc,
                                                                     CategoryApiClient categoryClient) {
        LinkedHashMap<String, SpecValueMetadata> out = new LinkedHashMap<>();
        Integer leafCatId = resolveLeafCatId(pc == null ? null : pc.getTemuCatid());
        if (leafCatId == null || leafCatId <= 0 || categoryClient == null) {
            return out;
        }

        try {
            String raw = categoryClient.getCategoryAttributes(leafCatId);
            JsonNode root = objectMapper.readTree(raw);
            if (!root.path("success").asBoolean(false)) {
                return out;
            }
            JsonNode properties = root.path("result").path("properties");
            if (!properties.isArray() || properties.isEmpty()) {
                return out;
            }
            Map<Integer, LinkedHashSet<Integer>> selectedVidsByPid = parseSelectedVidsByPid(pc == null ? null : pc.getTemuAttributes());
            for (JsonNode property : properties) {
                if (property == null
                        || property.isNull()
                        || !property.path("isSale").asBoolean(false)) {
                    continue;
                }
                int parentSpecId = property.path("parentSpecId").asInt(0);
                if (parentSpecId <= 0) {
                    continue;
                }
                JsonNode values = property.path("values");
                if (!values.isArray() || values.isEmpty()) {
                    continue;
                }
                LinkedHashSet<Integer> selectedVids = selectedVidsByPid.getOrDefault(property.path("pid").asInt(0), new LinkedHashSet<>());
                String propertyName = firstNonBlank(property.path("name").asText(null));
                for (JsonNode value : values) {
                    if (value == null || value.isNull()) {
                        continue;
                    }
                    String normalizedValue = normalizeSpecValueForField(propertyName, value.path("value").asText(null));
                    if (!StringUtils.hasText(normalizedValue)) {
                        continue;
                    }
                    JsonNode group = value.path("group");
                    Integer groupId = parseIntegerNode(group.path("id"));
                    String groupName = firstNonBlank(group.path("name").asText(null), "");
                    SpecValueMetadata candidate = new SpecValueMetadata(
                            parseIntegerNode(value.path("vid")),
                            groupId == null ? 0 : groupId,
                            groupName
                    );
                    String key = specValueMetadataKey(parentSpecId, normalizedValue);
                    SpecValueMetadata existing = out.get(key);
                    if (shouldReplaceSpecValueMetadata(existing, candidate, selectedVids)) {
                        out.put(key, candidate);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private boolean shouldReplaceSpecValueMetadata(SpecValueMetadata existing,
                                                   SpecValueMetadata candidate,
                                                   LinkedHashSet<Integer> selectedVids) {
        if (candidate == null) {
            return false;
        }
        if (existing == null) {
            return true;
        }
        boolean existingSelected = existing.vid() != null && selectedVids != null && selectedVids.contains(existing.vid());
        boolean candidateSelected = candidate.vid() != null && selectedVids != null && selectedVids.contains(candidate.vid());
        if (candidateSelected != existingSelected) {
            return candidateSelected;
        }
        boolean existingHasGroup = existing.valueGroupId() != null && existing.valueGroupId() > 0;
        boolean candidateHasGroup = candidate.valueGroupId() != null && candidate.valueGroupId() > 0;
        return candidateHasGroup && !existingHasGroup;
    }

    private SpecValueMetadata resolveSpecValueMetadata(Map<String, SpecValueMetadata> specValueMetadataByKey,
                                                       ParentSpec parentSpec,
                                                       String specValue) {
        if (specValueMetadataByKey == null || specValueMetadataByKey.isEmpty() || parentSpec == null || parentSpec.parentSpecId() <= 0) {
            return null;
        }
        return specValueMetadataByKey.get(specValueMetadataKey(parentSpec.parentSpecId(), specValue));
    }

    private String specValueMetadataKey(Integer parentSpecId,
                                        String specValue) {
        return String.valueOf(parentSpecId) + "\u0001" + normalizeSpecToken(specValue);
    }

    private Map<Integer, LinkedHashSet<Integer>> parseSelectedVidsByPid(String temuAttributesJson) {
        LinkedHashMap<Integer, LinkedHashSet<Integer>> out = new LinkedHashMap<>();
        if (!StringUtils.hasText(temuAttributesJson)) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(temuAttributesJson);
            JsonNode properties = root.path("properties");
            if (!properties.isArray()) {
                return out;
            }
            for (JsonNode property : properties) {
                if (property == null || property.isNull()) {
                    continue;
                }
                int pid = property.path("pid").asInt(0);
                if (pid <= 0) {
                    continue;
                }
                LinkedHashSet<Integer> selectedVids = out.computeIfAbsent(pid, ignored -> new LinkedHashSet<>());
                JsonNode vidsNode = property.path("selectedVids");
                if (vidsNode.isArray()) {
                    for (JsonNode vidNode : vidsNode) {
                        Integer vid = parseIntegerNode(vidNode);
                        if (vid != null && vid > 0) {
                            selectedVids.add(vid);
                        }
                    }
                }
                JsonNode selectedValues = property.path("selectedValues");
                if (selectedValues.isArray()) {
                    for (JsonNode selectedValue : selectedValues) {
                        Integer vid = parseIntegerNode(selectedValue.path("vid"));
                        if (vid != null && vid > 0) {
                            selectedVids.add(vid);
                        }
                    }
                }
                if (selectedVids.isEmpty()) {
                    out.remove(pid);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private Integer parseIntegerNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            int value = node.asInt(0);
            return value > 0 ? value : null;
        }
        if (node.isTextual()) {
            String text = firstNonBlank(node.asText(null));
            if (!StringUtils.hasText(text)) {
                return null;
            }
            try {
                int value = Integer.parseInt(text);
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer resolveLeafCatId(String temuCatid) {
        String raw = firstNonBlank(temuCatid);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String[] parts = raw.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = firstNonBlank(parts[i]);
            if (!StringUtils.hasText(part)) {
                continue;
            }
            try {
                int value = Integer.parseInt(part);
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Map<String, String> summarizeSizeMetadata(Map<String, SpecValueMetadata> specValueMetadataByKey) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (specValueMetadataByKey == null || specValueMetadataByKey.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, SpecValueMetadata> entry : specValueMetadataByKey.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().startsWith("3001")) {
                continue;
            }
            SpecValueMetadata metadata = entry.getValue();
            out.put(
                    entry.getKey().replace('\u0001', '|'),
                    "vid=" + (metadata == null ? null : metadata.vid())
                            + ",groupId=" + (metadata == null ? null : metadata.valueGroupId())
                            + ",groupName=" + (metadata == null ? null : metadata.valueGroupName())
            );
        }
        return out;
    }

    private AddGloGoodsRequest.ProductSkuReq buildProductSkuReq(ProductCollection pc,
                                                                Integer skuIndex,
                                                                ProductCollectionTemuSku skuEntity,
                                                                int siteId,
                                                                String warehouseId,
                                                                int defaultStock,
                                                                int maxStock,
                                                                List<AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq> skuSpecReqDtos) {
        AddGloGoodsRequest.ProductSkuReq sku = new AddGloGoodsRequest.ProductSkuReq();
        sku.setCurrencyType("CNY");

        BigDecimal finalPrice = skuEntity == null ? null : skuEntity.getSupplyPrice();
        if (finalPrice == null && skuEntity != null) {
            finalPrice = skuEntity.getOriginPrice();
        }
        sku.setSiteSupplierPrices(new ArrayList<>(List.of(
                new AddGloGoodsRequest.ProductSkuReq.SiteSupplierPrice(siteId, priceToCents(finalPrice))
        )));
        sku.setProductSkuUsSuggestedPriceReq(null);

        int publishStock = normalizePublishStock(null, defaultStock, maxStock);
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
                skuEntity == null ? null : skuEntity.getImage(),
                pc == null ? null : pc.getProductMainImage()
        );
        sku.setThumbUrl(thumb);

        Integer weightG = skuEntity == null ? null : skuEntity.getWeightG();
        int weightMg = Math.max(30, weightG == null ? 150 : weightG) * 1000;
        int lenMm = cmToMmOrDefault(skuEntity == null ? null : skuEntity.getLengthCm(), 10);
        int widthMm = cmToMmOrDefault(skuEntity == null ? null : skuEntity.getWidthCm(), 5);
        int heightMm = cmToMmOrDefault(skuEntity == null ? null : skuEntity.getHeightCm(), 5);
        int[] dims = new int[]{Math.max(1, lenMm), Math.max(1, widthMm), Math.max(1, heightMm)};
        Arrays.sort(dims);

        AddGloGoodsRequest.ProductSkuReq.ProductSkuWeightReq weightReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuWeightReq();
        weightReq.setValue(weightMg);
        AddGloGoodsRequest.ProductSkuReq.ProductSkuVolumeReq volReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuVolumeReq();
        volReq.setLen(dims[2]);
        volReq.setWidth(dims[1]);
        volReq.setHeight(Math.max(2, dims[0]));
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
        sku.setExtCode(safeSkuExtCode(
                skuEntity == null ? null : skuEntity.getTemuSkuId(),
                skuEntity == null ? null : skuEntity.getOriginSkuId(),
                idx
        ));
        sku.setProductSkuSpecReqs(skuSpecReqDtos == null ? new ArrayList<>() : skuSpecReqDtos);
        return sku;
    }

    private LinkedHashMap<String, String> parseSpecJson(String specJson) {
        String json = firstNonBlank(specJson);
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
            LinkedHashMap<String, String> out = new LinkedHashMap<>();
            if (parsed != null) {
                for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                    out.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
                }
            }
            return out;
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String resolveSpecValue(ProductCollectionTemuSku sku,
                                    String fieldName) {
        LinkedHashMap<String, String> specMap = parseSpecJson(sku == null ? null : sku.getSpecJson());
        if (StringUtils.hasText(fieldName)) {
            String directValue = firstNonBlank(specMap.get(fieldName));
            if (StringUtils.hasText(directValue) && !"*".equals(directValue)) {
                return normalizeSpecValueForField(fieldName, directValue);
            }
        }
        List<String> values = new ArrayList<>();
        for (String value : specMap.values()) {
            String normalized = firstNonBlank(value);
            if (StringUtils.hasText(normalized) && !"*".equals(normalized)) {
                values.add(normalized);
            }
        }
        if (!values.isEmpty()) {
            return normalizeSpecValueForField(fieldName, String.join(" / ", values));
        }
        String fallback = firstNonBlank(sku == null ? null : sku.getSpecKey(),
                sku == null ? null : sku.getOriginSkuId(),
                sku == null ? null : sku.getTemuSkuId());
        return normalizeSpecValueForField(fieldName, fallback);
    }

    private String normalizeSpecValueForField(String fieldName,
                                              String value) {
        if (isSizeLikeFieldName(fieldName)) {
            String normalizedSize = normalizeSizeSpecValue(value);
            if (StringUtils.hasText(normalizedSize)) {
                return normalizedSize;
            }
        }
        return translateSpecValueIfNeeded(normalizeSpecValue(value));
    }

    private boolean isSizeLikeFieldName(String fieldName) {
        String normalized = normalizeSpecToken(fieldName);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return normalized.contains("尺码")
                || normalized.contains("鞋码")
                || normalized.equals("size")
                || normalized.contains("shoesize");
    }

    private String normalizeSizeSpecValue(String value) {
        String candidate = firstNonBlank(value);
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        java.util.regex.Matcher shoePairMatcher = java.util.regex.Pattern
                .compile("(?i)(?:shoe\\s*size|size|尺码|鞋码)\\s*(\\d+(?:\\.\\d+)?)\\s*[-/]\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(candidate);
        if (shoePairMatcher.find()) {
            return formatSizeNumber(shoePairMatcher.group(1)) + "-" + formatSizeNumber(shoePairMatcher.group(2));
        }
        java.util.regex.Matcher pairMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*[-/]\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(candidate);
        if (pairMatcher.find()) {
            return formatSizeNumber(pairMatcher.group(1)) + "-" + formatSizeNumber(pairMatcher.group(2));
        }
        java.util.regex.Matcher singleMatcher = java.util.regex.Pattern
                .compile("(?<!\\d)(\\d{1,3}(?:\\.\\d+)?)(?!\\d)")
                .matcher(candidate);
        if (singleMatcher.find()) {
            return formatSizeNumber(singleMatcher.group(1));
        }
        return translateSpecValueIfNeeded(normalizeSpecValue(candidate));
    }

    private String canonicalizeSpecValueForPublish(ParentSpec parentSpec,
                                                   String specValue,
                                                   Map<String, SpecValueMetadata> specValueMetadataByKey) {
        String normalized = firstNonBlank(specValue);
        if (!StringUtils.hasText(normalized) || parentSpec == null || parentSpec.parentSpecId() <= 0) {
            return normalized;
        }
        if (!isSizeLikeParentSpec(parentSpec)) {
            return normalized;
        }
        String midpoint = midpointSizeValue(normalized);
        if (!StringUtils.hasText(midpoint) || midpoint.equals(normalized)) {
            return normalized;
        }
        SpecValueMetadata exact = specValueMetadataByKey == null
                ? null
                : specValueMetadataByKey.get(specValueMetadataKey(parentSpec.parentSpecId(), normalized));
        SpecValueMetadata mapped = specValueMetadataByKey == null
                ? null
                : specValueMetadataByKey.get(specValueMetadataKey(parentSpec.parentSpecId(), midpoint));
        if (mapped == null) {
            return normalized;
        }
        if (exact == null || Objects.equals(exact.valueGroupId(), mapped.valueGroupId())) {
            return midpoint;
        }
        return normalized;
    }

    private String translateSpecValueIfNeeded(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return skuSpecTranslateService.translatePlainText(value);
    }

    private boolean isSizeLikeParentSpec(ParentSpec parentSpec) {
        if (parentSpec == null) {
            return false;
        }
        if (Objects.equals(parentSpec.parentSpecId(), 3001)) {
            return true;
        }
        return isSizeLikeFieldName(parentSpec.parentSpecName());
    }

    private String midpointSizeValue(String raw) {
        String value = firstNonBlank(raw);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        java.util.regex.Matcher pairMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*[-/]\\s*(\\d+(?:\\.\\d+)?)")
                .matcher(value);
        if (!pairMatcher.find()) {
            return value;
        }
        try {
            double start = Double.parseDouble(pairMatcher.group(1));
            double end = Double.parseDouble(pairMatcher.group(2));
            double midpoint = (start + end) / 2.0d;
            return formatSizeNumber(midpoint);
        } catch (NumberFormatException ignored) {
            return value;
        }
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
        if (Math.abs(value - Math.rint(value)) < 0.0001d) {
            return String.valueOf((int) Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private List<ParentSpec> collectParentSpecs(JsonNode node) {
        List<ParentSpec> out = new ArrayList<>();
        collectParentSpecsInto(node, out);
        if (out.isEmpty()) {
            return out;
        }
        Map<String, ParentSpec> unique = new LinkedHashMap<>();
        for (ParentSpec parentSpec : out) {
            if (parentSpec != null && StringUtils.hasText(parentSpec.parentSpecName())) {
                unique.putIfAbsent(parentSpec.parentSpecName(), parentSpec);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private void collectParentSpecsInto(JsonNode node,
                                        List<ParentSpec> out) {
        if (node == null || node.isNull() || out == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode idNode = node.get("parentSpecId");
            JsonNode nameNode = node.get("parentSpecName");
            if (idNode != null && !idNode.isNull() && nameNode != null && !nameNode.isNull()) {
                int id = idNode.asInt(0);
                String name = firstNonBlank(nameNode.asText(null));
                if (id > 0 && StringUtils.hasText(name)) {
                    out.add(new ParentSpec(id, name));
                }
            }
            node.fields().forEachRemaining(entry -> collectParentSpecsInto(entry.getValue(), out));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectParentSpecsInto(child, out);
            }
        }
    }

    private AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq emptyMainProductSkuSpecReq() {
        AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq item = new AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq();
        item.setParentSpecId(0);
        item.setParentSpecName("");
        item.setSpecId(0);
        item.setSpecName("");
        return item;
    }

    private int normalizePublishStock(Integer rawStock,
                                      int defaultStock,
                                      int maxStock) {
        int fallbackStock = defaultStock > 0 ? defaultStock : 100;
        int upperBound = maxStock > 0 ? maxStock : 10842;
        int candidate = rawStock == null || rawStock <= 0 ? fallbackStock : rawStock;
        if (candidate <= 0) {
            candidate = fallbackStock;
        }
        return Math.min(candidate, upperBound);
    }

    private int priceToCents(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private int cmToMmOrDefault(BigDecimal value,
                                int defaultCm) {
        BigDecimal candidate = value == null ? BigDecimal.valueOf(defaultCm) : value;
        return candidate.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String safeSkuExtCode(String temuSkuId,
                                  String originSkuId,
                                  int index) {
        String base = firstNonBlank(temuSkuId, originSkuId);
        if (StringUtils.hasText(base)) {
            return base;
        }
        return "SKU-" + (index + 1);
    }

    private String normalizeSpecValue(String value) {
        String candidate = firstNonBlank(value);
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        candidate = candidate
                .replace('【', ' ')
                .replace('】', ' ')
                .replace('[', ' ')
                .replace(']', ' ')
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('(', ' ')
                .replace(')', ' ')
                .replace('.', ' ')
                .replace('．', ' ')
                .replace('。', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        // Keep full-ish spec values so different package/size variants do not collapse
        // into the same truncated TEMU spec value during publish.
        if (candidate.length() > 120) {
            candidate = candidate.substring(0, 120);
        }
        return candidate;
    }

    private String normalizeSpecToken(String value) {
        String normalized = firstNonBlank(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private boolean isModelLikeParentSpec(String parentSpecName) {
        String normalized = firstNonBlank(parentSpecName);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return normalized.contains("型号")
                || normalized.contains("规格")
                || lower.contains("model")
                || lower.contains("spec");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record ParentSpec(int parentSpecId, String parentSpecName) {
    }

    private record SpecDimension(String fieldName, ParentSpec parentSpec, boolean varying) {
    }

    private record SpecValueMetadata(Integer vid, Integer valueGroupId, String valueGroupName) {
    }
}
