package com.tminos.productscene.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.config.TemuParentSpecMappingTable;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductCollectionTemuSku;
import com.tminos.temu.upin.sdk.v2.category.CategoryApiClient;
import com.tminos.temu.upin.sdk.v2.dto.AddGloGoodsRequest;
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

    private final ObjectMapper objectMapper;
    private final TemuParentSpecMappingTable parentSpecMappingTable;

    public TemuSkuPublishDraftConverter(ObjectMapper objectMapper,
                                        TemuParentSpecMappingTable parentSpecMappingTable) {
        this.objectMapper = objectMapper;
        this.parentSpecMappingTable = parentSpecMappingTable;
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
                String mainValue = resolveSpecValue(sku, mainDimension.fieldName());
                if (!StringUtils.hasText(mainValue)) {
                    warnings.add("SKU " + firstNonBlank(sku.getTemuSkuId(), sku.getOriginSkuId(), String.valueOf(sku.getId())) + " 缺少主销售属性值，已跳过");
                    continue;
                }
                grouped.computeIfAbsent(mainValue, key -> new ArrayList<>()).add(sku);
            }

            for (Map.Entry<String, List<ProductCollectionTemuSku>> entry : grouped.entrySet()) {
                String mainValue = entry.getKey();
                Integer tempSpecId = ensureTempSpec(uniqueSpecs, tempSpecIdByKey, nextTempSpecId, mainDimension.parentSpec(), mainValue);

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
                String specValue = resolveSpecValue(sku, dimension.fieldName());
                if (!StringUtils.hasText(specValue)) {
                    continue;
                }
                Integer tempSpecId = ensureTempSpec(uniqueSpecs, tempSpecIdByKey, nextTempSpecId, dimension.parentSpec(), specValue);
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
        LinkedHashSet<String> seenParentNames = new LinkedHashSet<>();
        List<SpecDimension> out = new ArrayList<>();
        for (String fieldName : orderedSpecKeys) {
            ParentSpec parentSpec = resolveParentSpec(fieldName, allowedParentSpecs, warnings);
            if (parentSpec == null || parentSpec.parentSpecId() <= 0) {
                continue;
            }
            if (!seenParentNames.add(parentSpec.parentSpecName())) {
                warnings.add("规格维度“" + fieldName + "”映射到重复的父规格“" + parentSpec.parentSpecName() + "”，已忽略后续重复维度");
                continue;
            }
            boolean varying = isVaryingDimension(temuSkus, fieldName);
            out.add(new SpecDimension(fieldName, parentSpec, varying));
        }
        return out;
    }

    private ParentSpec resolveParentSpec(String fieldName,
                                         List<ParentSpec> allowedParentSpecs,
                                         List<String> warnings) {
        ParentSpec directMatch = findParentSpec(fieldName, allowedParentSpecs);
        if (directMatch != null) {
            return directMatch;
        }

        String mappedName = parentSpecMappingTable.getMappedParentSpecName(fieldName);
        ParentSpec mappedMatch = findParentSpec(mappedName, allowedParentSpecs);
        if (mappedMatch != null) {
            return mappedMatch;
        }

        ParentSpec fallback = allowedParentSpecs.isEmpty() ? null : allowedParentSpecs.get(0);
        if (fallback != null) {
            warnings.add("规格维度“" + fieldName + "”未命中父规格映射，已回退到“" + fallback.parentSpecName() + "”");
        }
        return fallback;
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

    private String resolveMainFieldName(List<SpecDimension> dimensions) {
        List<SpecDimension> varyingDimensions = dimensions.stream()
                .filter(SpecDimension::varying)
                .toList();
        if (varyingDimensions.size() <= 1) {
            return null;
        }
        return varyingDimensions.get(0).fieldName();
    }

    private Integer ensureTempSpec(Map<String, AddGloGoodsRequest.ProductSpecPropertyReq> uniqueSpecs,
                                   Map<String, Integer> tempSpecIdByKey,
                                   int[] nextTempSpecId,
                                   ParentSpec parentSpec,
                                   String specValue) {
        String key = parentSpec.parentSpecId() + "\u0001" + normalizeSpecToken(specValue);
        Integer tempSpecId = tempSpecIdByKey.get(key);
        if (tempSpecId != null) {
            return tempSpecId;
        }
        tempSpecId = nextTempSpecId[0]++;
        tempSpecIdByKey.put(key, tempSpecId);

        AddGloGoodsRequest.ProductSpecPropertyReq top = new AddGloGoodsRequest.ProductSpecPropertyReq();
        top.setVid(0);
        top.setSpecId(tempSpecId);
        top.setValueGroupId(0);
        top.setParentSpecId(parentSpec.parentSpecId());
        top.setValueGroupName("");
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
                return normalizeSpecValue(directValue);
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
            return normalizeSpecValue(String.join(" / ", values));
        }
        String fallback = firstNonBlank(sku == null ? null : sku.getSpecKey(),
                sku == null ? null : sku.getOriginSkuId(),
                sku == null ? null : sku.getTemuSkuId());
        return normalizeSpecValue(fallback);
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
        if (candidate.length() > 50) {
            candidate = candidate.substring(0, 50);
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
}