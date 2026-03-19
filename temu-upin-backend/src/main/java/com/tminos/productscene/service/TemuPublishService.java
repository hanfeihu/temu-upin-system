package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.dto.TemuPublishDTO;
import com.tminos.productscene.entity.ProductCollection;
import com.tminos.productscene.entity.ProductCollectionTemuSku;
import com.tminos.productscene.entity.TemuImageMeta;
import com.tminos.productscene.repository.ProductCollectionRepository;
import com.tminos.productscene.repository.ProductCollectionTemuSkuRepository;
import com.tminos.productscene.config.AITemuAttrFillerConfig;
import com.tminos.temu.openapi.client.goods.CategoryApiClient;
import com.tminos.temu.openapi.client.goods.GloGoodsApiClient;
import com.tminos.temu.openapi.client.goods.ImageApiClient;
import com.tminos.temu.openapi.config.TemuOpenApiGoodsConfig;
import com.tminos.temu.openapi.dto.AddGloGoodsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TemuPublishService {


    private final ProductCollectionService productCollectionService;
    private final ProductCollectionRepository productCollectionRepository;
    private final ProductCollectionTemuSkuRepository temuSkuRepository;
    private final PlatformConfigService platformConfigService;
    private final TemuImageNormalizeService imageNormalizeService;
    private final TemuImageMetaService temuImageMetaService;
    private final TemuPublishLogService publishLogService;
    private final ObjectMapper objectMapper;
    private final AITemuAttrFillerConfig aiTemuAttrFillerConfig;

    public TemuPublishService(ProductCollectionService productCollectionService,
                             ProductCollectionRepository productCollectionRepository,
                             ProductCollectionTemuSkuRepository temuSkuRepository,
                             PlatformConfigService platformConfigService,
                             TemuImageNormalizeService imageNormalizeService,
                             TemuImageMetaService temuImageMetaService,
                             TemuPublishLogService publishLogService,
                             ObjectMapper objectMapper,
                             AITemuAttrFillerConfig aiTemuAttrFillerConfig) {
        this.productCollectionService = productCollectionService;
        this.productCollectionRepository = productCollectionRepository;
        this.temuSkuRepository = temuSkuRepository;
        this.platformConfigService = platformConfigService;
        this.imageNormalizeService = imageNormalizeService;
        this.temuImageMetaService = temuImageMetaService;
        this.publishLogService = publishLogService;
        this.objectMapper = objectMapper;
        this.aiTemuAttrFillerConfig = aiTemuAttrFillerConfig;
    }

    @Transactional
    public TemuPublishDTO.PublishResponse publish(Long spuId) {
        com.tminos.productscene.entity.TemuPublishRun run = publishLogService.startRun(spuId);
        Long runId = run == null ? null : run.getId();
        publishLogService.info(runId, "START", "publish started");

        List<String> warnings = new ArrayList<>();
        ProductCollection pc = productCollectionService.get(spuId);

        // collectionStatus is used as publish status:
        // 0 未发布, 1 发布中, 2 发布失败
        try {
            pc.setCollectionStatus(1);
            pc.setLastPublishRunId(runId);
            pc.setUpdatedAt(LocalDateTime.now());
            productCollectionRepository.save(pc);
        } catch (Exception ignored) {
            // best-effort; publish should not be blocked by status persistence
        }

        {
            Map<String, Object> load = new LinkedHashMap<>();
            load.put("spuId", spuId);
            load.put("temuCatid", pc.getTemuCatid());
            load.put("temuCatname", pc.getTemuCatname());
            load.put("hasTemuAttributes", (pc.getTemuAttributes() != null && !pc.getTemuAttributes().isBlank()));
            publishLogService.data(runId, "LOAD", "loaded product", load);
        }

        // 1) validate preconditions
        if (!StringUtils.hasText(pc.getTemuCatid()) || !StringUtils.hasText(pc.getTemuCatname())) {
            publishLogService.error(runId, "VALIDATE", "missing temu category", null);
            publishLogService.finishFailed(runId, "missing temu category", null, null);
            markPublishFailed(pc, runId, "missing temu category");
            return new TemuPublishDTO.PublishResponse(false, "temu 类目为空，请先匹配并保存", runId, null, null, null, warnings);
        }
        if (!StringUtils.hasText(pc.getTemuAttributes())) {
            publishLogService.error(runId, "VALIDATE", "missing temu attributes", null);
            publishLogService.finishFailed(runId, "missing temu attributes", null, null);
            markPublishFailed(pc, runId, "missing temu attributes");
            return new TemuPublishDTO.PublishResponse(false, "TEMU 属性为空，请先填写并保存", runId, null, null, null, warnings);
        }
        List<ProductCollectionTemuSku> temuSkus = temuSkuRepository.findBySpuIdOrderByIdAsc(spuId);
        if (temuSkus == null || temuSkus.isEmpty()) {
            publishLogService.error(runId, "VALIDATE", "missing temu skus", null);
            publishLogService.finishFailed(runId, "missing temu skus", null, null);
            markPublishFailed(pc, runId, "missing temu skus");
            return new TemuPublishDTO.PublishResponse(false, "TEMU SKU 为空，请先做 SKU 转换", runId, null, null, null, warnings);
        }

        publishLogService.info(runId, "VALIDATE", "preconditions ok");

        // 2) load default config
        Map<String, String> cfg = platformConfigService.getDefaultConfigOrThrow();
        int siteId = parseInt(cfg.get(PlatformConfigService.KEY_DEFAULT_SITE_ID), 100);
        String warehouseId = firstNonBlank(cfg.get(PlatformConfigService.KEY_DEFAULT_WAREHOUSE_ID), "WH-03304781516934009");
        int defaultStock = parseInt(cfg.get(PlatformConfigService.KEY_SKU_DEFAULT_STOCK), 100);
        String originRegion1 = firstNonBlank(cfg.get(PlatformConfigService.KEY_ORIGIN_REGION1_SHORT), "CN");
        long originRegion2Id = parseLong(cfg.get(PlatformConfigService.KEY_ORIGIN_REGION2_ID), 43000000000016L);
        String freightTemplateId = firstNonBlank(cfg.get(PlatformConfigService.KEY_SHIPMENT_FREIGHT_TEMPLATE_ID), "HFT-14851213328261424009");
        int limitSecond = parseInt(cfg.get(PlatformConfigService.KEY_SHIPMENT_LIMIT_SECOND), 777600);

        publishLogService.data(runId, "CONFIG", "loaded default config", cfg);

        if (!TemuOpenApiGoodsConfig.isConfigComplete()) {
            publishLogService.error(runId, "CONFIG", "sdk config incomplete", null);
            publishLogService.finishFailed(runId, "sdk config incomplete", null, null);
            markPublishFailed(pc, runId, "sdk config incomplete");
            return new TemuPublishDTO.PublishResponse(false, "TEMU SDK 配置不完整（access token/app key/secret）", runId, null, null, null, warnings);
        }

        // 3) normalize images (800x800 + kwcdn)
        // Performance: batch fetch cached image meta so we can skip probe/upload when already normalized.
        publishLogService.info(runId, "IMAGES", "normalize images start");

        List<String> carousel = parseJsonStringList(pc.getCarouselImages());
        List<String> detail = parseJsonStringList(pc.getDetailImages());
        List<String> allImageUrls = new ArrayList<>();
        if (StringUtils.hasText(pc.getProductMainImage())) allImageUrls.add(pc.getProductMainImage().trim());
        for (String u : carousel) if (StringUtils.hasText(u)) allImageUrls.add(u.trim());
        for (String u : detail) if (StringUtils.hasText(u)) allImageUrls.add(u.trim());
        for (ProductCollectionTemuSku sku : temuSkus) {
            if (sku != null && StringUtils.hasText(sku.getImage())) {
                allImageUrls.add(sku.getImage().trim());
            }
        }
        Map<String, TemuImageMeta> imageMetaCache = temuImageMetaService.getByUrls(allImageUrls);

        String mainImage = normalizeOneStrict(pc.getProductMainImage(), "productMainImage", warnings, runId, imageMetaCache);
        if (carousel.isEmpty() && StringUtils.hasText(mainImage)) {
            carousel = List.of(mainImage);
            warnings.add("carouselImages empty, fallback to productMainImage");
        }
        if (!carousel.isEmpty()) {
            List<String> normalized = new ArrayList<>();
            for (int i = 0; i < carousel.size(); i++) {
                normalized.add(normalizeOneStrict(carousel.get(i), "carouselImages[" + i + "]", warnings, runId, imageMetaCache));
            }
            carousel = normalized;
        }
        if (!detail.isEmpty()) {
            List<String> normalized = new ArrayList<>();
            for (int i = 0; i < detail.size(); i++) {
                normalized.add(normalizeOneStrict(detail.get(i), "detailImages[" + i + "]", warnings, runId, imageMetaCache));
            }
            detail = normalized;
        }

        // update DB with replaced links (only when changed)
        boolean imagesChanged = false;
        if (StringUtils.hasText(mainImage) && !Objects.equals(mainImage, pc.getProductMainImage())) {
            pc.setProductMainImage(mainImage);
            imagesChanged = true;
        }
        String newCarouselJson = writeJson(carousel);
        if (newCarouselJson != null && !Objects.equals(newCarouselJson, pc.getCarouselImages())) {
            pc.setCarouselImages(newCarouselJson);
            imagesChanged = true;
        }
        String newDetailJson = writeJson(detail);
        if (newDetailJson != null && !Objects.equals(newDetailJson, pc.getDetailImages())) {
            pc.setDetailImages(newDetailJson);
            imagesChanged = true;
        }

        // sku image normalize too (thumb)
        for (ProductCollectionTemuSku sku : temuSkus) {
            if (sku == null) continue;
            if (!StringUtils.hasText(sku.getImage())) continue;
            String u = normalizeOneStrict(sku.getImage(), "temuSku.image", warnings, runId, imageMetaCache);
            if (StringUtils.hasText(u) && !Objects.equals(u, sku.getImage())) {
                sku.setImage(u);
                imagesChanged = true;
            }
        }
        if (imagesChanged) {
            productCollectionRepository.save(pc);
            temuSkuRepository.saveAll(temuSkus);
            publishLogService.info(runId, "IMAGES", "images updated in DB");
        }

        // 4) build request body (DTO form: AddGloGoodsRequest)
        GloGoodsApiClient.setDefaultSiteId(siteId);
        GloGoodsApiClient.setDefaultWarehouseId(warehouseId);

        AddGloGoodsRequest req = new AddGloGoodsRequest();
        String enTitle = sanitizeEnglishName(maybeTranslateTitleToEn(pc.getProductName(), warnings), warnings);
        req.setProductName(enTitle);
        req.setProductI18nReqs(new ArrayList<>(List.of(new AddGloGoodsRequest.ProductI18nReq("en", enTitle))));
        req.setIsRecommendedTag(true);

        String materialImg = firstNonBlank(mainImage, (carousel.isEmpty() ? null : carousel.get(0)));
        if (!StringUtils.hasText(materialImg)) {
            publishLogService.error(runId, "VALIDATE", "missing images", null);
            publishLogService.finishFailed(runId, "missing images", null, null);
            markPublishFailed(pc, runId, "missing images");
            return new TemuPublishDTO.PublishResponse(false, "发布失败：缺少可用图片（主图/轮播图）", runId, null, null, null, warnings);
        }
        req.setMaterialImgUrl(materialImg);
        if (carousel != null && !carousel.isEmpty()) {
            req.setCarouselImageUrls(new ArrayList<>(carousel));
        }

        // category path
        int[] catIds = parseCatIds(pc.getTemuCatid());
        req.setCat1Id(catIds[0]);
        req.setCat2Id(catIds[1]);
        req.setCat3Id(catIds[2]);
        req.setCat4Id(catIds[3]);
        req.setCat5Id(catIds[4]);
        req.setCat6Id(catIds[5]);
        req.setCat7Id(catIds[6]);
        req.setCat8Id(catIds[7]);
        req.setCat9Id(catIds[8]);
        req.setCat10Id(catIds[9]);

        // semi managed bind sites
        req.setProductSemiManagedReq(new AddGloGoodsRequest.ProductSemiManagedReq(
                null,
                new ArrayList<>(List.of(siteId)),
                null,
                null
        ));

        // route
        AddGloGoodsRequest.ProductWarehouseRouteReq routeReq = new AddGloGoodsRequest.ProductWarehouseRouteReq();
        routeReq.setTargetRouteList(new ArrayList<>(List.of(
                new AddGloGoodsRequest.ProductWarehouseRouteReq.RouteItem(new ArrayList<>(List.of(siteId)), warehouseId)
        )));
        req.setProductWarehouseRouteReq(routeReq);

        // origin + outer url
        AddGloGoodsRequest.ProductOrigin origin = new AddGloGoodsRequest.ProductOrigin();
        origin.setRegion1ShortName(originRegion1);
        origin.setRegion2Id(originRegion2Id);
        AddGloGoodsRequest.ProductWhExtAttrReq whExt = new AddGloGoodsRequest.ProductWhExtAttrReq();
        whExt.setOuterGoodsUrl(pc.getProductUrl());
        whExt.setProductOrigin(origin);
        req.setProductWhExtAttrReq(whExt);

        // shipment
        AddGloGoodsRequest.ProductShipmentReq shipment = new AddGloGoodsRequest.ProductShipmentReq();
        shipment.setFreightTemplateId(freightTemplateId);
        shipment.setShipmentLimitSecond(limitSecond);
        req.setProductShipmentReq(shipment);

        // product properties from saved temuAttributes
        Map<Integer, AttrTemplate> attrTemplateByPid = loadAttrTemplateByPid(spuId, warnings, runId);
        List<AddGloGoodsRequest.ProductPropertyReq> props = parseTemuAttributesAsProductPropertyReqs(pc.getTemuAttributes(), attrTemplateByPid, warnings, runId);
        if (props == null || props.isEmpty()) {
            publishLogService.error(runId, "VALIDATE", "parsed productPropertyReqs empty", null);
            publishLogService.finishFailed(runId, "productPropertyReqs empty", null, null);
            markPublishFailed(pc, runId, "productPropertyReqs empty");
            return new TemuPublishDTO.PublishResponse(false, "TEMU 属性解析为空，请重新保存属性后再发布", runId, null, null, null, warnings);
        }
        req.setProductPropertyReqs(props);

        // build SKU reqs first (spec ids will be assigned after we create specs)
        String fallbackThumb = !carousel.isEmpty() ? carousel.get(0) : mainImage;
        List<AddGloGoodsRequest.ProductSkuReq> skuReqs = new ArrayList<>();
        int idx = 0;
        for (ProductCollectionTemuSku s : temuSkus) {
            if (s == null) continue;
            AddGloGoodsRequest.ProductSkuReq sku = new AddGloGoodsRequest.ProductSkuReq();
            sku.setCurrencyType("CNY");

            int cents = priceToCents(s.getSupplyPrice());
            sku.setSiteSupplierPrices(new ArrayList<>(List.of(
                    new AddGloGoodsRequest.ProductSkuReq.SiteSupplierPrice(siteId, cents)
            )));

            AddGloGoodsRequest.ProductSkuReq.ProductSkuStockQuantityReq stockReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuStockQuantityReq();
            stockReq.setWarehouseStockQuantityReqs(new ArrayList<>(List.of(
                    new AddGloGoodsRequest.ProductSkuReq.ProductSkuStockQuantityReq.WarehouseStockQuantityReq(defaultStock, warehouseId, null)
            )));
            sku.setProductSkuStockQuantityReq(stockReq);

            String thumb = firstNonBlank(s.getImage(), fallbackThumb);
            sku.setThumbUrl(thumb);

            // wh ext attr: weight mg + volume mm
            int weightMg = Math.max(30, s.getWeightG() == null ? 150 : s.getWeightG()) * 1000;
            int lenMm = cmToMmOrDefault(s.getLengthCm(), 10);
            int widthMm = cmToMmOrDefault(s.getWidthCm(), 5);
            int heightMm = cmToMmOrDefault(s.getHeightCm(), 5);

            // TEMU constraint: longest >= middle >= shortest
            int[] dims = new int[]{Math.max(1, lenMm), Math.max(1, widthMm), Math.max(1, heightMm)};
            java.util.Arrays.sort(dims);
            // after sort ascending: [shortest, middle, longest]
            int longest = dims[2];
            int middle = dims[1];
            int shortest = dims[0];

            // Category constraint example: shortest side must be >= 0.2cm (2mm)
            if (shortest > 0 && shortest < 2) {
                shortest = 2;
            }

            AddGloGoodsRequest.ProductSkuReq.ProductSkuWeightReq weightReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuWeightReq();
            weightReq.setValue(weightMg);
            AddGloGoodsRequest.ProductSkuReq.ProductSkuVolumeReq volReq = new AddGloGoodsRequest.ProductSkuReq.ProductSkuVolumeReq();
            volReq.setLen(longest);
            volReq.setWidth(middle);
            volReq.setHeight(shortest);
            AddGloGoodsRequest.ProductSkuReq.ProductSkuSensitiveAttrReq senAttr = new AddGloGoodsRequest.ProductSkuReq.ProductSkuSensitiveAttrReq();
            senAttr.setIsSensitive(0);

            AddGloGoodsRequest.ProductSkuReq.ProductSkuWhExtAttrReq skuWhExt = new AddGloGoodsRequest.ProductSkuReq.ProductSkuWhExtAttrReq();
            skuWhExt.setProductSkuWeightReq(weightReq);
            skuWhExt.setProductSkuVolumeReq(volReq);
            skuWhExt.setProductSkuSensitiveLimitReq(new AddGloGoodsRequest.ProductSkuReq.ProductSkuSensitiveLimitReq());
            skuWhExt.setProductSkuSensitiveAttrReq(senAttr);
            sku.setProductSkuWhExtAttrReq(skuWhExt);

            sku.setExtCode(safeSkuExtCode(s.getTemuSkuId(), s.getOriginSkuId(), idx));
            skuReqs.add(sku);
            idx++;
        }
        if (skuReqs.isEmpty()) {
            publishLogService.error(runId, "VALIDATE", "no valid temu skus", null);
            publishLogService.finishFailed(runId, "no valid temu skus", null, null);
            return new TemuPublishDTO.PublishResponse(false, "TEMU SKU 为空或无效，请先做 SKU 转换", runId, null, null, null, warnings);
        }

        // Create parent spec + specId for each sku, and fill: productSpecPropertyReqs + SKU productSkuSpecReqs + SKC mainProductSkuSpecReqs
        SpecBundle specBundle;
        try {
            specBundle = buildSpecsForSkus(runId, skuReqs, temuSkus, warnings);
        } catch (Exception e) {
            String err = exceptionToString(e);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("error", safeErrMessage(e));
            data.put("exception", e.getClass().getName());
            publishLogService.error(runId, "SPEC", "build specs failed", data);
            publishLogService.finishFailed(runId, err, null, null);
            return new TemuPublishDTO.PublishResponse(false, "规格创建失败: " + safeErrMessage(e), runId, null, null, null, warnings);
        }
        req.setProductSpecPropertyReqs(specBundle.productSpecPropertyReqs);

        AddGloGoodsRequest.ProductSkcReq skc = new AddGloGoodsRequest.ProductSkcReq();
        // Make extCode deterministic so we can find it later by 1688 product id.
        // Format: SKC_<spuId>_<alibabaProductId>
        skc.setExtCode(safeSkcExtCode(spuId, pc == null ? null : pc.getAlibabaProductId(), pc == null ? null : pc.getProductId()));
        {
            String p = firstNonBlank(mainImage, fallbackThumb);
            if (StringUtils.hasText(p)) {
                skc.setPreviewImgUrls(new ArrayList<>(List.of(p)));
            } else {
                skc.setPreviewImgUrls(new ArrayList<>());
            }
        }
        // TEMU validates "main sales" spec strictly for some categories.
        // It must be consistent with productSpecPropertyReqs + SKU-level productSkuSpecReqs.
        if (specBundle.mainSkcSpec == null
                || specBundle.mainSkcSpec.getParentSpecId() == null
                || specBundle.mainSkcSpec.getParentSpecId() <= 0
                || specBundle.mainSkcSpec.getSpecId() == null
                || specBundle.mainSkcSpec.getSpecId() <= 0) {
            throw new IllegalStateException("invalid mainSkcSpec");
        }
        skc.setMainProductSkuSpecReqs(new ArrayList<>(List.of(specBundle.mainSkcSpec)));
        skc.setProductSkuReqs(skuReqs);
        req.setProductSkcReqs(new ArrayList<>(List.of(skc)));

        String requestJson = null;
        try {
            requestJson = objectMapper.writeValueAsString(req);
        } catch (Exception ignored) {
        }
        {
            Map<String, Object> reqLog = new LinkedHashMap<>();
            reqLog.put("requestJson", requestJson);
            reqLog.put("siteId", siteId);
            reqLog.put("warehouseId", warehouseId);
            reqLog.put("skuCount", skuReqs.size());
            publishLogService.data(runId, "REQUEST", "built request", reqLog);
        }

        // 5) call API
        String raw;
        try {
            raw = GloGoodsApiClient.addGloGoodsRaw(req);
        } catch (Exception e) {
            String err = exceptionToString(e);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("error", safeErrMessage(e));
            data.put("exception", e.getClass().getName());
            publishLogService.error(runId, "CALL", "addGloGoodsRaw threw", data);
            publishLogService.finishFailed(runId, err, requestJson, null);
            markPublishFailed(pc, runId, "call failed: " + safeErrMessage(e));
            return new TemuPublishDTO.PublishResponse(false, "发布调用失败: " + safeErrMessage(e), runId, null, null, null, warnings);
        }

        {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("raw", raw);
            publishLogService.data(runId, "RESPONSE", "raw response", data);
        }

        // 6) parse result and persist
        String goodsId = null;
        boolean success = false;
        try {
            JsonNode root = objectMapper.readTree(raw);
            success = root.path("success").asBoolean(false);
            if (success) {
                JsonNode r = root.path("result");
                if (r.has("goodsId")) {
                    goodsId = r.get("goodsId").asText();
                }
            }
        } catch (Exception e) {
            warnings.add("publish response parse failed: " + e.getMessage());
        }

        if (success) {
            pc.setTemuPublished(true);
            pc.setTemuGoodsId(goodsId);
            pc.setTemuPublishedAt(LocalDateTime.now());
            pc.setCollectionStatus(0);
        } else {
            pc.setTemuPublished(false);
            pc.setCollectionStatus(2);
        }
        pc.setTemuPublishRaw(raw);
        pc.setLastPublishRunId(runId);
        productCollectionRepository.save(pc);

        if (success) {
            publishLogService.finishSuccess(runId, goodsId, requestJson, raw);
        } else {
            // try to extract error
            String err = null;
            try {
                JsonNode root = objectMapper.readTree(raw);
                err = root.path("errorMsg").asText(null);
            } catch (Exception ignored) {}
            publishLogService.finishFailed(runId, err, requestJson, raw);
        }

        return new TemuPublishDTO.PublishResponse(success, success ? "OK" : "TEMU 返回失败", runId, goodsId, raw, null, warnings);
    }

    private void markPublishFailed(ProductCollection pc, Long runId, String reason) {
        if (pc == null) return;
        try {
            pc.setCollectionStatus(2);
            pc.setLastPublishRunId(runId);
            pc.setUpdatedAt(LocalDateTime.now());
            // keep temuPublishRaw as-is (may be null)
            productCollectionRepository.save(pc);
        } catch (Exception ignored) {
        }
    }

    private String normalizeOneStrict(String url, String name, List<String> warnings, Long runId) {
        return normalizeOneInternal(url, name, warnings, runId, true, null);
    }

    private String normalizeOneStrict(String url, String name, List<String> warnings, Long runId, Map<String, TemuImageMeta> metaCache) {
        return normalizeOneInternal(url, name, warnings, runId, true, metaCache);
    }

    private String normalizeOneInternal(String url, String name, List<String> warnings, Long runId, boolean strict, Map<String, TemuImageMeta> metaCache) {
        if (!StringUtils.hasText(url)) return null;
        String input = url.trim();

        // Fast-path: if we already normalized this URL to 800x800 and kwcdn, skip probe/upload.
        try {
            TemuImageMeta m = null;
            if (metaCache != null) {
                m = metaCache.get(input);
            }
            if (m != null
                    && Boolean.TRUE.equals(m.getKwcdn())
                    && m.getWidth() != null && m.getHeight() != null
                    && m.getWidth() == 800 && m.getHeight() == 800) {
                validateTemuImageUrlOrThrow(input, name);
                return input;
            }
        } catch (Exception ignored) {
        }

        try {
            TemuImageNormalizeService.Result r = imageNormalizeService.normalizeToTemu800(input);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("original", input);
            data.put("uploaded", (r == null ? null : r.getUploadedUrl()));
            data.put("width", (r == null ? null : r.getWidth()));
            data.put("height", (r == null ? null : r.getHeight()));
            data.put("reason", (r == null ? null : r.getReason()));
            publishLogService.data(runId, "IMAGES", "normalized " + name, data);

            String out = r == null ? input : firstNonBlank(r.getUploadedUrl(), input);
            validateTemuImageUrlOrThrow(out, name);

            // Record meta for the final URL so later publishes can fast-path.
            try {
                if (StringUtils.hasText(out)) {
                    Integer w = r == null ? null : r.getWidth();
                    Integer h = r == null ? null : r.getHeight();
                    temuImageMetaService.upsert(out, w, h);
                }
            } catch (Exception ignored) {
            }

            return out;
        } catch (Exception e) {
            String em = safeErrMessage(e);
            warnings.add("image normalize failed(" + name + "): " + em);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("error", em);
            data.put("original", input);
            publishLogService.error(runId, "IMAGES", "normalize failed " + name, data);
            if (strict) {
                throw new IllegalStateException("图片转换失败(" + name + "): " + em, e);
            }
            return input;
        }
    }

    private void validateTemuImageUrlOrThrow(String url, String name) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("image url empty");
        }
        String u = url.trim();
        if (!u.startsWith("http")) {
            throw new IllegalStateException("image url invalid: " + u);
        }
        try {
            java.net.URI uri = java.net.URI.create(u);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("image url host missing");
            }
            // publish requires TEMU kwcdn domain
            if (!"img.kwcdn.com".equalsIgnoreCase(host)) {
                throw new IllegalStateException("image url not kwcdn: host=" + host);
            }
        } catch (RuntimeException re) {
            throw new IllegalStateException("image url has illegal chars: " + u);
        }
    }

    private List<String> parseJsonStringList(String json) {
        if (!StringUtils.hasText(json)) return new ArrayList<>();
        try {
            JsonNode n = objectMapper.readTree(json);
            if (!n.isArray()) return new ArrayList<>();
            List<String> out = new ArrayList<>();
            for (JsonNode x : n) {
                if (x != null && x.isTextual()) {
                    String s = x.asText();
                    if (StringUtils.hasText(s)) out.add(s.trim());
                }
            }
            return out;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private String writeJson(List<String> list) {
        if (list == null) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    private List<AddGloGoodsRequest.ProductPropertyReq> parseTemuAttributesAsProductPropertyReqs(
            String savedJson,
            Map<Integer, AttrTemplate> templateByPid,
            List<String> warnings,
            Long runId
    ) {
        // Stored structure: { leafCatId, savedAt, properties: [ {pid,name,selectedVids,selectedValues,freeText,...} ] }
        List<AddGloGoodsRequest.ProductPropertyReq> out = new ArrayList<>();
        if (!StringUtils.hasText(savedJson)) return out;
        try {
            JsonNode root = objectMapper.readTree(savedJson);

            // Conditional skip rules for parent-child attributes.
            // Example: if "供电方式" is "无需供电使用", then "插头规格" must be empty (otherwise parent-child validation fails).
            // Template reference (commonly):
            // - 供电方式 pid=1425, value "无需供电使用" vid=53941, value "插头供电" vid=36781
            // - 插头规格 pid=1404
            Set<String> powerVids = extractSelectedVids(root, 1425);
            boolean powerIsPlug = powerVids.contains("36781");

            JsonNode props = root.path("properties");
            if (!props.isArray()) return out;
            for (JsonNode p : props) {
                if (p == null || p.isNull()) continue;
                int pid = p.path("pid").asInt(0);
                int templatePid = p.path("templatePid").asInt(0);
                int refPid = p.path("refPid").asInt(0);
                String propName = p.path("name").asText("");

                String valueUnit = p.path("valueUnit").asText("");
                String numberInputValue = p.path("numberInputValue").asText("");

                // NOTE: do not hard-skip any specific pid here.
                // Parent-child correctness should be enforced by template rules (templatePropertyValueParentList)
                // when saving attributes, otherwise the user needs to re-fill based on updated template.

                // Backward-compat: old saved payloads didn't include templatePid/refPid/valueUnit.
                if ((templatePid <= 0 || refPid <= 0 || !StringUtils.hasText(valueUnit) || !StringUtils.hasText(propName))
                        && templateByPid != null && pid > 0) {
                    AttrTemplate t = templateByPid.get(pid);
                    if (t != null) {
                        if (templatePid <= 0) templatePid = t.templatePid;
                        if (refPid <= 0) refPid = t.refPid;
                        if (!StringUtils.hasText(propName)) propName = t.name;
                        if (!StringUtils.hasText(valueUnit)) valueUnit = t.valueUnit;
                    }
                }

                String freeText = p.path("freeText").isNull() ? null : p.path("freeText").asText(null);
                if (StringUtils.hasText(freeText)) {
                    AddGloGoodsRequest.ProductPropertyReq item = new AddGloGoodsRequest.ProductPropertyReq();
                    item.setPid(pid);
                    item.setTemplatePid(templatePid);
                    item.setRefPid(refPid);
                    item.setVid(0);
                    item.setPropName(propName);
                    item.setPropValue(freeText);
                    item.setValueUnit(valueUnit == null ? "" : valueUnit);
                    // For numeric/text input properties, numberInputValue can help SDK sanitize.
                    item.setNumberInputValue(StringUtils.hasText(numberInputValue) ? numberInputValue : freeText);
                    out.add(item);
                    continue;
                }

                JsonNode selected = p.path("selectedValues");
                if (selected.isArray() && selected.size() > 0) {
                    // only take first selected value for now (TEMU supports multi-select for some props, but sdk uses vid per item)
                    JsonNode first = selected.get(0);
                    int vid = first.path("vid").asInt(0);
                    String value = first.path("value").asText("");
                    AddGloGoodsRequest.ProductPropertyReq item = new AddGloGoodsRequest.ProductPropertyReq();
                    item.setPid(pid);
                    item.setTemplatePid(templatePid);
                    item.setRefPid(refPid);
                    item.setVid(vid);
                    item.setPropName(propName);
                    item.setPropValue(value);
                    item.setValueUnit(valueUnit == null ? "" : valueUnit);
                    item.setNumberInputValue("");
                    out.add(item);
                } else {
                    // fallback: selectedVids
                    JsonNode vids = p.path("selectedVids");
                    if (vids.isArray() && vids.size() > 0) {
                        String v = vids.get(0).asText();
                        int vid = parseInt(v, 0);
                        AddGloGoodsRequest.ProductPropertyReq item = new AddGloGoodsRequest.ProductPropertyReq();
                        item.setPid(pid);
                        item.setTemplatePid(templatePid);
                        item.setRefPid(refPid);
                        item.setVid(vid);
                        item.setPropName(propName);
                        item.setPropValue(v);
                        item.setValueUnit(valueUnit == null ? "" : valueUnit);
                        item.setNumberInputValue("");
                        out.add(item);
                    }
                }
            }

            // Validate required fields are present (templatePid/refPid are required by TEMU)
            int missing = 0;
            for (AddGloGoodsRequest.ProductPropertyReq item : out) {
                if (item == null) continue;
                if (item.getPid() == null || item.getPid() <= 0) { missing++; continue; }
                if (item.getTemplatePid() == null || item.getTemplatePid() <= 0) missing++;
                if (item.getRefPid() == null || item.getRefPid() <= 0) missing++;
            }
            if (missing > 0) {
                publishLogService.warn(runId, "ATTR", "some product properties missing templatePid/refPid");
                warnings.add("some product properties missing templatePid/refPid: count=" + missing);
            }
        } catch (Exception e) {
            warnings.add("temuAttributes parse failed: " + e.getMessage());
        }
        return out;
    }

    private Set<String> extractSelectedVids(JsonNode root, int pid) {
        Set<String> out = new LinkedHashSet<>();
        if (root == null || pid <= 0) return out;
        JsonNode props = root.path("properties");
        if (!props.isArray()) return out;
        for (JsonNode p : props) {
            if (p == null || p.isNull()) continue;
            if (p.path("pid").asInt(0) != pid) continue;
            JsonNode vids = p.path("selectedVids");
            if (vids.isArray()) {
                for (JsonNode v : vids) {
                    if (v != null && v.isTextual()) {
                        String s = v.asText();
                        if (StringUtils.hasText(s)) out.add(s.trim());
                    } else if (v != null && v.isNumber()) {
                        out.add(String.valueOf(v.asInt()));
                    }
                }
            }
            JsonNode selected = p.path("selectedValues");
            if (selected.isArray()) {
                for (JsonNode sv : selected) {
                    String s = sv.path("vid").asText(null);
                    if (!StringUtils.hasText(s)) {
                        int n = sv.path("vid").asInt(0);
                        if (n > 0) s = String.valueOf(n);
                    }
                    if (StringUtils.hasText(s)) out.add(s.trim());
                }
            }
        }
        return out;
    }

    private Map<Integer, AttrTemplate> loadAttrTemplateByPid(Long spuId, List<String> warnings, Long runId) {
        Map<Integer, AttrTemplate> out = new LinkedHashMap<>();
        if (spuId == null) return out;
        try {
            String raw = productCollectionService.getTemuCategoryAttributesRaw(spuId);
            if (!StringUtils.hasText(raw)) return out;
            JsonNode root = objectMapper.readTree(raw);
            JsonNode props = root.path("result").path("properties");
            if (!props.isArray()) return out;
            for (JsonNode p : props) {
                int pid = p.path("pid").asInt(0);
                int templatePid = p.path("templatePid").asInt(0);
                int refPid = p.path("refPid").asInt(0);
                String name = p.path("name").asText("");
                String valueUnit = "";
                JsonNode vu = p.path("valueUnit");
                if (vu.isArray() && vu.size() > 0) {
                    valueUnit = vu.get(0).asText("");
                }
                if (pid > 0 && templatePid > 0 && refPid > 0) {
                    out.put(pid, new AttrTemplate(pid, templatePid, refPid, name, valueUnit));
                }
            }
            {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("count", out.size());
                publishLogService.data(runId, "ATTR", "loaded attribute template", data);
            }
        } catch (Exception e) {
            warnings.add("load category attributes template failed: " + safeErrMessage(e));
            publishLogService.warn(runId, "ATTR", "load category attributes template failed: " + safeErrMessage(e));
        }
        return out;
    }

    private static class AttrTemplate {
        final int pid;
        final int templatePid;
        final int refPid;
        final String name;
        final String valueUnit;

        AttrTemplate(int pid, int templatePid, int refPid, String name, String valueUnit) {
            this.pid = pid;
            this.templatePid = templatePid;
            this.refPid = refPid;
            this.name = name;
            this.valueUnit = valueUnit;
        }
    }

    private boolean shouldForceEmptyAttr(int pid, String propName) {
        // Hard rule: battery capacity should be omitted entirely (platform requires numberInputValue empty).
        if (pid == 1485) return true;
        // Hard rule: silver material net weight should be omitted entirely.
        if (pid == 2172) return true;
        if (StringUtils.hasText(propName)) {
            String n = propName.trim();
            if (n.contains("电池容量")) return true;
            if (n.contains("银材料净克重")) return true;
            if (n.contains("净克重")) return true;
            String nl = n.toLowerCase();
            if (nl.contains("battery") && nl.contains("capacity")) return true;
            if (nl.contains("mah")) return true;
            if (nl.contains("net") && nl.contains("weight")) return true;
        }
        // Config-driven force-empty list is optional. Do not hard-fail publish if Lombok getters are unavailable.
        try {
            if (pid > 0 && aiTemuAttrFillerConfig != null) {
                java.lang.reflect.Method m = aiTemuAttrFillerConfig.getClass().getMethod("getForceEmptyPids");
                Object v = m.invoke(aiTemuAttrFillerConfig);
                if (v instanceof java.util.List<?> list) {
                    for (Object o : list) {
                        if (o == null) continue;
                        if (String.valueOf(o).trim().equals(String.valueOf(pid))) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private SpecBundle buildSpecsForSkus(Long runId,
                                        List<AddGloGoodsRequest.ProductSkuReq> skuReqs,
                                        List<ProductCollectionTemuSku> temuSkus,
                                        List<String> warnings) throws Exception {
        Integer parentSpecId;
        String parentSpecName;

        String psJson = CategoryApiClient.getParentSpecList();
        JsonNode psRoot = objectMapper.readTree(psJson);
        if (!psRoot.path("success").asBoolean(false)) {
            throw new IllegalStateException("getParentSpecList failed: " + psRoot.path("errorMsg").asText("unknown"));
        }
        JsonNode dtos = psRoot.path("result").path("parentSpecDTOS");
        if (!dtos.isArray() || dtos.isEmpty()) {
            throw new IllegalStateException("parentSpecDTOS empty");
        }

        ParentSpec chosen = chooseParentSpecFromSkus(dtos, temuSkus);
        parentSpecId = chosen.parentSpecId;
        parentSpecName = chosen.parentSpecName;
        if (parentSpecId <= 0) {
            throw new IllegalStateException("invalid parentSpecId");
        }

        List<AddGloGoodsRequest.ProductSpecPropertyReq> topSpecProps = new ArrayList<>();
        AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq mainSkcSpec = null;

        Map<String, Integer> cache = new LinkedHashMap<>();
        for (int i = 0; i < skuReqs.size(); i++) {
            AddGloGoodsRequest.ProductSkuReq skuReq = skuReqs.get(i);
            ProductCollectionTemuSku skuEntity = (temuSkus != null && i < temuSkus.size()) ? temuSkus.get(i) : null;

            String specValueName = deriveSpecValueName(skuReq, skuEntity, i);
            Integer specId = cache.get(specValueName);
            String finalSpecName = specValueName;
            if (specId == null) {
                String csJson = CategoryApiClient.createSpec(parentSpecId, specValueName);
                JsonNode csRoot = objectMapper.readTree(csJson);
                if (!csRoot.path("success").asBoolean(false)) {
                    throw new IllegalStateException("createSpec failed(name=" + specValueName + "): " + csRoot.path("errorMsg").asText("unknown"));
                }
                JsonNode r = csRoot.path("result");
                specId = r.path("specId").asInt(0);
                String sn = r.path("specName").asText(null);
                if (StringUtils.hasText(sn)) finalSpecName = sn;
                if (specId == null || specId <= 0) {
                    throw new IllegalStateException("createSpec returned invalid specId");
                }
                cache.put(specValueName, specId);
            }

            AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq skuSpec = new AddGloGoodsRequest.ProductSkuReq.ProductSkuSpecReq();
            skuSpec.setParentSpecId(parentSpecId);
            skuSpec.setParentSpecName(parentSpecName == null ? "" : parentSpecName);
            skuSpec.setSpecId(specId);
            skuSpec.setSpecName(finalSpecName == null ? "" : finalSpecName);
            skuReq.setProductSkuSpecReqs(new ArrayList<>(List.of(skuSpec)));

            AddGloGoodsRequest.ProductSpecPropertyReq top = new AddGloGoodsRequest.ProductSpecPropertyReq();
            top.setVid(0);
            top.setSpecId(specId);
            top.setValueGroupId(0);
            top.setParentSpecId(parentSpecId);
            top.setValueGroupName("");
            top.setValueUnit("");
            top.setPid(0);
            top.setTemplatePid(0);
            top.setNumberInputValue("");
            top.setPropValue(finalSpecName == null ? "" : finalSpecName);
            top.setPropName(parentSpecName == null ? "" : parentSpecName);
            top.setRefPid(0);
            topSpecProps.add(top);

            if (mainSkcSpec == null) {
                AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq ms = new AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq();
                ms.setParentSpecId(parentSpecId);
                ms.setParentSpecName(parentSpecName == null ? "" : parentSpecName);
                ms.setSpecId(specId);
                ms.setSpecName(finalSpecName == null ? "" : finalSpecName);
                mainSkcSpec = ms;
            }
        }

        if (topSpecProps.isEmpty() || mainSkcSpec == null) {
            throw new IllegalStateException("spec props empty");
        }
        {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("parentSpecId", parentSpecId);
            data.put("parentSpecName", parentSpecName);
            data.put("specCount", topSpecProps.size());
            publishLogService.data(runId, "SPEC", "specs prepared", data);
        }
        return new SpecBundle(parentSpecId, parentSpecName, mainSkcSpec, topSpecProps);
    }

    private ParentSpec chooseParentSpecFromSkus(JsonNode parentSpecDtos, List<ProductCollectionTemuSku> temuSkus) {
        // Map parentSpecName -> id
        Map<String, Integer> nameToId = new LinkedHashMap<>();
        if (parentSpecDtos != null && parentSpecDtos.isArray()) {
            for (JsonNode dto : parentSpecDtos) {
                String n = dto.path("parentSpecName").asText("");
                int id = dto.path("parentSpecId").asInt(0);
                if (StringUtils.hasText(n) && id > 0) {
                    nameToId.put(n, id);
                }
            }
        }

        // Inspect sku specKey prefix
        int colorScore = 0;
        int sizeScore = 0;
        int qtyScore = 0;
        int modelScore = 0;
        if (temuSkus != null) {
            for (ProductCollectionTemuSku s : temuSkus) {
                if (s == null) continue;
                String k = s.getSpecKey();
                if (!StringUtils.hasText(k)) continue;
                String head = k.trim();
                if (head.contains(">")) head = head.substring(0, head.indexOf('>'));
                head = head.trim().toLowerCase();
                if (head.isBlank()) continue;

                if (head.contains("颜色") || head.contains("color")) colorScore++;
                if (head.contains("尺码") || head.contains("尺寸") || head.contains("size")) sizeScore++;
                if (head.contains("数量") || head.contains("件数") || head.contains("quantity") || head.contains("pack")) qtyScore++;
                if (head.contains("型号") || head.contains("model") || head.contains("规格") || head.contains("spec")) modelScore++;
            }
        }

        String desired;
        int max = Math.max(Math.max(colorScore, sizeScore), Math.max(qtyScore, modelScore));
        if (max <= 0) {
            // fallback order similar to your test class preference
            desired = firstExistingParentSpec(nameToId, "规格", "型号", "尺码", "尺寸", "数量", "颜色");
        } else if (max == colorScore) {
            desired = firstExistingParentSpec(nameToId, "颜色");
        } else if (max == sizeScore) {
            desired = firstExistingParentSpec(nameToId, "尺码", "尺寸");
        } else if (max == qtyScore) {
            desired = firstExistingParentSpec(nameToId, "数量");
        } else {
            desired = firstExistingParentSpec(nameToId, "型号", "规格");
        }

        if (!StringUtils.hasText(desired)) {
            // last resort: take the first dto
            if (parentSpecDtos != null && parentSpecDtos.isArray() && !parentSpecDtos.isEmpty()) {
                JsonNode first = parentSpecDtos.get(0);
                return new ParentSpec(first.path("parentSpecId").asInt(0), first.path("parentSpecName").asText(""));
            }
            return new ParentSpec(0, "");
        }
        return new ParentSpec(nameToId.getOrDefault(desired, 0), desired);
    }

    private String firstExistingParentSpec(Map<String, Integer> nameToId, String... candidates) {
        if (nameToId == null || nameToId.isEmpty() || candidates == null) return null;
        for (String c : candidates) {
            if (!StringUtils.hasText(c)) continue;
            if (nameToId.containsKey(c)) return c;
        }
        // try loose contains match
        for (String c : candidates) {
            if (!StringUtils.hasText(c)) continue;
            for (String k : nameToId.keySet()) {
                if (k != null && k.contains(c)) return k;
            }
        }
        return null;
    }

    private static class ParentSpec {
        final int parentSpecId;
        final String parentSpecName;

        ParentSpec(int parentSpecId, String parentSpecName) {
            this.parentSpecId = parentSpecId;
            this.parentSpecName = parentSpecName;
        }
    }

    private String deriveSpecValueName(AddGloGoodsRequest.ProductSkuReq skuReq, ProductCollectionTemuSku skuEntity, int index) {
        String candidate = null;
        if (skuEntity != null && StringUtils.hasText(skuEntity.getSpecKey())) {
            String k = skuEntity.getSpecKey().trim();
            if (k.contains(">")) {
                String[] parts = k.split(">");
                if (parts.length > 0) {
                    String last = parts[parts.length - 1];
                    if (StringUtils.hasText(last)) candidate = last.trim();
                }
            } else {
                candidate = k;
            }
        }
        if (!StringUtils.hasText(candidate)) {
            candidate = skuReq == null ? null : skuReq.getExtCode();
        }
        if (!StringUtils.hasText(candidate)) {
            candidate = "Default-" + (index + 1);
        }
        candidate = candidate.trim();
        // Avoid punctuation/brackets that break TEMU display.
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
        if (candidate.length() > 50) candidate = candidate.substring(0, 50);
        return candidate;
    }

    private static class SpecBundle {
        final Integer parentSpecId;
        final String parentSpecName;
        final AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq mainSkcSpec;
        final List<AddGloGoodsRequest.ProductSpecPropertyReq> productSpecPropertyReqs;

        SpecBundle(Integer parentSpecId,
                   String parentSpecName,
                   AddGloGoodsRequest.ProductSkcReq.MainProductSkuSpecReq mainSkcSpec,
                   List<AddGloGoodsRequest.ProductSpecPropertyReq> productSpecPropertyReqs) {
            this.parentSpecId = parentSpecId;
            this.parentSpecName = parentSpecName;
            this.mainSkcSpec = mainSkcSpec;
            this.productSpecPropertyReqs = productSpecPropertyReqs;
        }
    }

    private String safeErrMessage(Exception e) {
        if (e == null) return "unknown";
        String m = e.getMessage();
        if (!StringUtils.hasText(m)) return e.getClass().getSimpleName();
        return m;
    }

    private String exceptionToString(Exception e) {
        if (e == null) return null;
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        String msg = e.getMessage();
        if (!StringUtils.hasText(msg)) msg = e.getClass().getSimpleName();
        return e.getClass().getName() + ": " + msg + "\n" + sw;
    }

    private int[] parseCatIds(String csv) {
        int[] out = new int[10];
        Arrays.fill(out, 0);
        if (!StringUtils.hasText(csv)) return out;
        String[] parts = csv.split(",");
        int idx = 0;
        for (String p : parts) {
            if (idx >= 10) break;
            String s = p == null ? "" : p.trim();
            if (s.isEmpty()) continue;
            out[idx] = parseInt(s, 0);
            idx++;
        }
        return out;
    }

    private int priceToCents(BigDecimal price) {
        if (price == null) return 0;
        BigDecimal p = price.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP);
        try {
            return p.intValueExact();
        } catch (Exception ignored) {
            return p.intValue();
        }
    }

    private int cmToMmOrDefault(BigDecimal cm, int defCm) {
        BigDecimal v = cm == null ? new BigDecimal(String.valueOf(defCm)) : cm;
        BigDecimal mm = v.multiply(new BigDecimal("10")).setScale(0, RoundingMode.HALF_UP);
        return mm.intValue();
    }

    private String safeSkuExtCode(String temuSkuId, String originSkuId, int idx) {
        String base = firstNonBlank(temuSkuId, originSkuId, "SKU" + (idx + 1));
        // TEMU extCode seems to allow letters/numbers/_/-; keep it simple.
        return base.replaceAll("[^a-zA-Z0-9_\u4e00-\u9fa5-]", "_");
    }

    private String safeSkcExtCode(Long spuId, String alibabaProductId, String productId) {
        String ali = firstNonBlank(alibabaProductId, productId);
        String base = "SKC_" + (spuId == null ? "0" : String.valueOf(spuId)) + "_" + (ali == null ? "" : ali);
        // Keep deterministic and TEMU-safe.
        String out = base.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (out.length() > 120) {
            out = out.substring(0, 120);
        }
        // Avoid trailing underscore spam
        out = out.replaceAll("_+", "_");
        if (out.endsWith("_")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private int parseInt(String s, int def) {
        if (!StringUtils.hasText(s)) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return def;
        }
    }

    private long parseLong(String s, long def) {
        if (!StringUtils.hasText(s)) return def;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ignored) {
            return def;
        }
    }

    private String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (StringUtils.hasText(v)) return v.trim();
        }
        return null;
    }

    private String maybeTranslateTitleToEn(String title, List<String> warnings) {
        if (!StringUtils.hasText(title)) return title;
        String t = title.trim();

        // only translate when it contains CJK characters
        if (!containsCjk(t)) return t;

        // invoke AliyunTranslateUtil.translateToEn(String) via reflection to avoid compile-time coupling
        String[] candidates = {
                "com.tminos.erp.common.util.AliyunTranslateUtil",
                "com.tminos.temu.openapi.util.AliyunTranslateUtil",
                "com.tminos.temu.openapi.util.aliyun.AliyunTranslateUtil",
                "com.tminos.productscene.util.AliyunTranslateUtil",
                "AliyunTranslateUtil"
        };

        for (String clzName : candidates) {
            try {
                Class<?> clz = Class.forName(clzName);
                java.lang.reflect.Method m = clz.getMethod("translateToEn", String.class);
                Object out = m.invoke(null, t);
                if (out instanceof String s && StringUtils.hasText(s)) {
                    return s.trim();
                }
            } catch (Exception ignored) {
            }
        }

        warnings.add("title translation skipped: AliyunTranslateUtil.translateToEn not found");
        return t;
    }

    private String sanitizeEnglishName(String s, List<String> warnings) {
        if (!StringUtils.hasText(s)) return s;
        String input = s.trim();
        // Keep only ASCII letters/numbers/basic punctuation/spaces.
        // Remove emojis, CJK, and other symbols that TEMU rejects.
        String cleaned = input
                .replaceAll("[^A-Za-z0-9\\-\\_\\.\\,\\/\\(\\)\\[\\]\\+\\&\\%\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(cleaned)) {
            cleaned = "Product";
        }
        if (!cleaned.equals(input)) {
            if (warnings != null) warnings.add("english name sanitized");
        }
        // Conservative length cap
        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(0, 200).trim();
        }
        return cleaned;
    }

    private boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fff') return true;
        }
        return false;
    }
}
