package com.tminos.productscene.sync.service;

import com.google.gson.Gson;
import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.entity.TemuGoodsProperty;
import com.tminos.productscene.sync.entity.TemuGoodsSite;
import com.tminos.productscene.sync.entity.TemuGoodsSku;
import com.tminos.productscene.sync.entity.TemuGoodsSkuBarcode;
import com.tminos.productscene.sync.entity.TemuGoodsSkuSpec;
import com.tminos.productscene.sync.repository.TemuGoodsPropertyRepository;
import com.tminos.productscene.sync.repository.TemuGoodsRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSiteRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuBarcodeRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuSpecRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TemuGoodsAggregateService {

    private static final Logger log = LoggerFactory.getLogger(TemuGoodsAggregateService.class);

    private final Gson gson = new Gson();
    private final TemuGoodsRepository goodsRepository;
    private final TemuGoodsPropertyRepository propertyRepository;
    private final TemuGoodsSiteRepository siteRepository;
    private final TemuGoodsSkuRepository skuRepository;
    private final TemuGoodsSkuSpecRepository skuSpecRepository;
    private final TemuGoodsSkuBarcodeRepository skuBarcodeRepository;

    public TemuGoodsAggregateService(TemuGoodsRepository goodsRepository,
                                     TemuGoodsPropertyRepository propertyRepository,
                                     TemuGoodsSiteRepository siteRepository,
                                     TemuGoodsSkuRepository skuRepository,
                                     TemuGoodsSkuSpecRepository skuSpecRepository,
                                     TemuGoodsSkuBarcodeRepository skuBarcodeRepository) {
        this.goodsRepository = goodsRepository;
        this.propertyRepository = propertyRepository;
        this.siteRepository = siteRepository;
        this.skuRepository = skuRepository;
        this.skuSpecRepository = skuSpecRepository;
        this.skuBarcodeRepository = skuBarcodeRepository;
    }

    @Transactional
    public void upsertGoodsAggregate(String shopId, Map<String, Object> raw) {
        Map<String, Object> normalizedRaw = normalizeGoodsRaw(raw);
        Long productSkcId = toLong(normalizedRaw.get("productSkcId"));
        if (productSkcId == null) {
            return;
        }

        Map<String, Object> leafCat = toMap(normalizedRaw.get("leafCat"));
        Map<String, Object> productJitMode = toMap(normalizedRaw.get("productJitMode"));
        Map<String, Object> productSemiManaged = toMap(normalizedRaw.get("productSemiManaged"));
        Map<String, Object> productShipment = productSemiManaged == null ? null : toMap(productSemiManaged.get("productShipment"));

        TemuGoods goods = goodsRepository.findByShopIdAndProductSkcId(shopId, productSkcId)
                .orElse(new TemuGoods());

        goods.setShopId(shopId);
        goods.setProductId(toLong(normalizedRaw.get("productId")));
        goods.setProductSkcId(productSkcId);
        goods.setProductName(toScalarStr(normalizedRaw.get("productName")));
        goods.setExtCode(toScalarStr(normalizedRaw.get("extCode")));
        goods.setMainImageUrl(normalizeMainImageUrl(firstUrl(normalizedRaw, "mainImageUrl", "thumbUrl")));
        goods.setSkcSiteStatus(toInt(normalizedRaw.get("skcSiteStatus")));
        goods.setIsSupportPersonalization(toBool(normalizedRaw.get("isSupportPersonalization")));
        goods.setMatchSkcJitMode(toBool(normalizedRaw.get("matchSkcJitMode")));
        goods.setMatchJitMode(toBool(getMapValue(productJitMode, "matchJitMode")));
        goods.setSignLatestJitVersion(toBool(getMapValue(productJitMode, "signLatestJitVersion")));
        goods.setQuickSellAgtSignStatus(toInt(getMapValue(productJitMode, "quickSellAgtSignStatus")));
        goods.setLeafCatId(toInt(getMapValue(leafCat, "catId", "leafCatId")));
        goods.setLeafCatName(firstScalarStr(leafCat, "catName", "leafCatName"));
        goods.setCatType(toInt(normalizedRaw.get("catType")));
        goods.setCategoriesJson(toJson(normalizedRaw.get("categories")));
        goods.setFreightTemplateId(firstScalarStr(productShipment, "freightTemplateId"));
        goods.setShipmentLimitSecond(toInt(getMapValue(productShipment, "shipmentLimitSecond")));
        goods.setLongTransport(toBool(getMapValue(productShipment, "longTransport")));
        goods.setWarehouseRegionIdsJson(toJson(getMapValue(productShipment, "warehouseRegionId1List", "warehouseRegionIds")));
        goods.setProductOriginJson(toJson(extractProductOrigin(normalizedRaw)));
        goods.setTemuCreatedAt(toLong(normalizedRaw.get("createdAt")));
        goods.setRawJson(gson.toJson(normalizedRaw));
        goods.setSyncedAt(LocalDateTime.now());

        goods = goodsRepository.save(goods);
        rebuildGoodsChildren(goods, normalizedRaw);
    }

    private Map<String, Object> normalizeGoodsRaw(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> productOrigin = toMap(raw.get("productOrigin"));
        if (productOrigin != null && !productOrigin.isEmpty() && toLong(productOrigin.get("productSkcId")) != null) {
            return productOrigin;
        }
        return raw;
    }

    private Object extractProductOrigin(Map<String, Object> raw) {
        Object explicitOrigin = getMapValue(raw, "productOrigin", "productWhExtAttr");
        if (explicitOrigin != null) {
            return explicitOrigin;
        }
        return raw == null || raw.isEmpty() ? null : raw;
    }

    public boolean repairSingleGoodsFromRaw(String shopId, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return false;
        }
        Map<String, Object> raw = parseJsonMap(rawJson);
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        upsertGoodsAggregate(shopId, raw);
        return true;
    }

    private void rebuildGoodsChildren(TemuGoods goods, Map<String, Object> raw) {
        Long goodsId = goods.getId();
        List<Long> existingSkuIds = skuRepository.findByGoodsId(goodsId).stream()
                .map(TemuGoodsSku::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!existingSkuIds.isEmpty()) {
            skuSpecRepository.deleteBySkuIdIn(existingSkuIds);
            skuSpecRepository.flush();
            skuBarcodeRepository.deleteBySkuIdIn(existingSkuIds);
            skuBarcodeRepository.flush();
        }
        skuRepository.deleteByGoodsId(goodsId);
        skuRepository.flush();
        siteRepository.deleteByGoodsId(goodsId);
        siteRepository.flush();
        propertyRepository.deleteByGoodsId(goodsId);
        propertyRepository.flush();

        List<Map<String, Object>> mergedSites = new ArrayList<>(getList(raw.get("bindSites"), null));
        Map<String, Object> productSemiManaged = toMap(raw.get("productSemiManaged"));
        if (productSemiManaged != null) {
            mergedSites.addAll(getList(productSemiManaged.get("bindSites"), null));
        }
        saveGoodsSites(goodsId, mergedSites);
        saveGoodsProperties(goodsId, getList(raw.get("productProperties"), null));
        saveGoodsSkus(goods, getList(raw.get("productSkuSummaries"), null));
    }

    private void saveGoodsSites(Long goodsId, List<Map<String, Object>> siteList) {
        if (siteList == null || siteList.isEmpty()) {
            return;
        }
        Map<Integer, TemuGoodsSite> deduplicatedSites = new LinkedHashMap<>();
        for (Map<String, Object> siteRaw : siteList) {
            Integer siteId = toInt(siteRaw.get("siteId"));
            if (siteId == null) {
                continue;
            }
            String siteName = toStr(siteRaw.get("siteName"));
            TemuGoodsSite existing = deduplicatedSites.get(siteId);
            if (existing == null) {
                deduplicatedSites.put(siteId, TemuGoodsSite.builder()
                        .goodsId(goodsId)
                        .siteId(siteId)
                        .siteName(siteName)
                        .build());
                continue;
            }
            if ((existing.getSiteName() == null || existing.getSiteName().isBlank())
                    && siteName != null && !siteName.isBlank()) {
                existing.setSiteName(siteName);
            }
        }
        if (!deduplicatedSites.isEmpty()) {
            siteRepository.saveAll(new ArrayList<>(deduplicatedSites.values()));
        }
    }

    private void saveGoodsProperties(Long goodsId, List<Map<String, Object>> propertyList) {
        if (propertyList == null || propertyList.isEmpty()) {
            return;
        }
        List<TemuGoodsProperty> properties = new ArrayList<>();
        for (Map<String, Object> propertyRaw : propertyList) {
            Integer pid = toInt(propertyRaw.get("pid"));
            if (pid == null) {
                continue;
            }
            properties.add(TemuGoodsProperty.builder()
                    .goodsId(goodsId)
                    .pid(pid)
                    .templatePid(toInt(propertyRaw.get("templatePid")))
                    .refPid(toInt(propertyRaw.get("refPid")))
                    .propName(toStr(propertyRaw.get("propName")))
                    .vid(toInt(propertyRaw.get("vid")))
                    .propValue(toStr(propertyRaw.get("propValue")))
                    .valueUnit(toStr(propertyRaw.get("valueUnit")))
                    .valueExtendInfo(toStr(propertyRaw.get("valueExtendInfo")))
                    .numberInputValue(toStr(propertyRaw.get("numberInputValue")))
                    .language(toStr(propertyRaw.get("language")))
                    .build());
        }
        if (!properties.isEmpty()) {
            propertyRepository.saveAll(properties);
        }
    }

    @SuppressWarnings("null")
    private void saveGoodsSkus(TemuGoods goods, List<Map<String, Object>> skuList) {
        if (skuList == null || skuList.isEmpty()) {
            return;
        }
        for (Map<String, Object> skuRaw : skuList) {
            Long productSkuId = toLong(skuRaw.get("productSkuId"));
            if (productSkuId == null) {
                continue;
            }

            Map<String, Object> whExtAttr = toMap(skuRaw.get("productSkuWhExtAttr"));
            Map<String, Object> saleExtAttr = toMap(skuRaw.get("productSkuSaleExtAttr"));
            Map<String, Object> weight = whExtAttr == null ? null : toMap(whExtAttr.get("productSkuWeight"));
            Map<String, Object> wmsWeight = whExtAttr == null ? null : toMap(whExtAttr.get("productSkuWmsWeight"));
            Map<String, Object> volume = whExtAttr == null ? null : toMap(whExtAttr.get("productSkuVolume"));
            Map<String, Object> wmsVolume = whExtAttr == null ? null : toMap(whExtAttr.get("productSkuWmsVolume"));
            Map<String, Object> sensitiveAttr = whExtAttr == null ? null : toMap(whExtAttr.get("productSkuSensitiveAttr"));
            Map<String, Object> fragileLabels = whExtAttr == null ? null : toMap(whExtAttr.get("productSkuFragileLabels"));
            Map<String, Object> volumeLabel = whExtAttr == null ? null : toMap(whExtAttr.get("productSkuVolumeLabel"));
            Map<String, Object> newSensitiveAttr = whExtAttr == null ? null : toMap(whExtAttr.get("productSkuNewSensitiveAttr"));

            TemuGoodsSku sku = TemuGoodsSku.builder()
                    .goodsId(goods.getId())
                    .shopId(goods.getShopId())
                    .productSkuId(productSkuId)
                    .extCode(toStr(skuRaw.get("extCode")))
                    .virtualStock(toInt(skuRaw.get("virtualStock")))
                    .weightMg(toInt(getMapValue(weight, "value")))
                    .wmsWeightMg(toInt(getMapValue(wmsWeight, "value")))
                    .lengthMm(toInt(getMapValue(volume, "len")))
                    .widthMm(toInt(getMapValue(volume, "width")))
                    .heightMm(toInt(getMapValue(volume, "height")))
                    .wmsLengthMm(toInt(getMapValue(wmsVolume, "len")))
                    .wmsWidthMm(toInt(getMapValue(wmsVolume, "width")))
                    .wmsHeightMm(toInt(getMapValue(wmsVolume, "height")))
                    .wmsCollectionSourceType(toInt(firstNonNull(
                        getMapValue(wmsVolume, "wmsCollectionSourceType"),
                        getMapValue(wmsWeight, "wmsCollectionSourceType")
                    )))
                    .isSensitive(toInt(getMapValue(sensitiveAttr, "isSensitive")))
                    .sensitiveTypesJson(toJson(firstNonNull(
                        getMapValue(sensitiveAttr, "sensitiveTypes"),
                        getMapValue(newSensitiveAttr, "sensitiveList")
                    )))
                    .isFragile(toBool(getMapValue(fragileLabels, "isFragile")))
                    .isSideOverLength(toBool(getMapValue(volumeLabel, "isSideOverLength")))
                    .isVolumeOverSize(toBool(getMapValue(volumeLabel, "isVolumeOverSize")))
                    .isForceToNormal(toBool(getMapValue(newSensitiveAttr, "isForce2Normal")))
                    .shippingMode(toInt(getMapValue(saleExtAttr, "productSkuShippingMode")))
                    .individuallyPacked(toInt(getMapValue(saleExtAttr, "productSkuIndividuallyPacked")))
                    .subSellMode(toInt(getMapValue(whExtAttr, "productSkuSubSellMode")))
                    .sensitiveLimitJson(toJson(getMapValue(whExtAttr, "productSkuSensitiveLimit")))
                    .rawJson(toJson(skuRaw))
                    .build();
            sku = skuRepository.save(sku);

            saveGoodsSkuSpecs(sku.getId(), getList(skuRaw.get("productSkuSpecList"), null));
            if (whExtAttr != null) {
                saveGoodsSkuBarcodes(sku.getId(), getList(whExtAttr.get("productSkuBarCodes"), null));
            }
        }
    }

    private void saveGoodsSkuSpecs(Long skuId, List<Map<String, Object>> specList) {
        if (specList == null || specList.isEmpty()) {
            return;
        }
        List<TemuGoodsSkuSpec> specs = new ArrayList<>();
        for (Map<String, Object> specRaw : specList) {
            Integer specId = toInt(specRaw.get("specId"));
            Integer parentSpecId = toInt(specRaw.get("parentSpecId"));
            String specName = toStr(specRaw.get("specName"));
            String parentSpecName = toStr(specRaw.get("parentSpecName"));
            if (specId == null || parentSpecId == null || specName == null || parentSpecName == null) {
                continue;
            }
            specs.add(TemuGoodsSkuSpec.builder()
                    .skuId(skuId)
                    .specId(specId)
                    .specName(specName)
                    .parentSpecId(parentSpecId)
                    .parentSpecName(parentSpecName)
                    .build());
        }
        if (!specs.isEmpty()) {
            skuSpecRepository.saveAll(specs);
        }
    }

    private void saveGoodsSkuBarcodes(Long skuId, List<Map<String, Object>> barcodeList) {
        if (barcodeList == null || barcodeList.isEmpty()) {
            return;
        }
        List<TemuGoodsSkuBarcode> barcodes = new ArrayList<>();
        for (Map<String, Object> barcodeRaw : barcodeList) {
            String code = toStr(barcodeRaw.get("code"));
            Integer codeType = toInt(barcodeRaw.get("codeType"));
            if (code == null || codeType == null) {
                continue;
            }
            barcodes.add(TemuGoodsSkuBarcode.builder()
                    .skuId(skuId)
                    .code(code)
                    .codeType(codeType)
                    .build());
        }
        if (!barcodes.isEmpty()) {
            skuBarcodeRepository.saveAll(barcodes);
        }
    }

    private Map<String, Object> parseJsonMap(String rawJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = gson.fromJson(rawJson, Map.class);
            return parsed;
        } catch (Exception e) {
            log.warn("解析商品 raw_json 失败: {}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> getList(Object value, Object fallback) {
        Object source = value != null ? value : fallback;
        if (!(source instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            Map<String, Object> itemMap = toMap(item);
            if (itemMap != null) {
                result.add(itemMap);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object raw) {
        if (raw instanceof Map<?, ?> rawMap) {
            return (Map<String, Object>) rawMap;
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object getMapValue(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstScalarStr(Map<String, Object> source, String... keys) {
        return toScalarStr(getMapValue(source, keys));
    }

    private String firstUrl(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = toScalarStr(source.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String toJson(Object value) {
        return value == null ? null : gson.toJson(value);
    }

    private String toStr(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? null : str;
    }

    private String toScalarStr(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
            return null;
        }
        return toStr(value);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String str = toStr(value);
        if (str == null) {
            return null;
        }
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private Integer toInt(Object value) {
        Long longValue = toLong(value);
        return longValue == null ? null : longValue.intValue();
    }

    private Boolean toBool(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String str = toStr(value);
        if (str == null) {
            return null;
        }
        if ("1".equals(str) || "true".equalsIgnoreCase(str)) {
            return true;
        }
        if ("0".equals(str) || "false".equalsIgnoreCase(str)) {
            return false;
        }
        return null;
    }

    private String normalizeMainImageUrl(String url) {
        if (url == null || url.length() <= 1900) {
            return url;
        }
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            String withoutQuery = url.substring(0, queryIndex);
            if (withoutQuery.length() <= 1900) {
                return withoutQuery;
            }
            url = withoutQuery;
        }
        return url.substring(0, 1900);
    }
}