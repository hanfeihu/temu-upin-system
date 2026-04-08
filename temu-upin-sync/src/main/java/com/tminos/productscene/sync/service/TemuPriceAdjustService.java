package com.tminos.productscene.sync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.service.TemuOpenApiCredentialService;
import com.tminos.productscene.sync.dto.PriceAdjustDTO;
import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.entity.TemuGoodsSku;
import com.tminos.productscene.sync.entity.TemuGoodsSkuPrice;
import com.tminos.productscene.sync.entity.TemuGoodsSkuSpec;
import com.tminos.productscene.sync.entity.TemuPriceAdjustOrder;
import com.tminos.productscene.sync.entity.TemuPriceAdjustSku;
import com.tminos.productscene.sync.repository.TemuGoodsRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuPriceRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuSpecRepository;
import com.tminos.productscene.sync.repository.TemuPriceAdjustOrderRepository;
import com.tminos.productscene.sync.repository.TemuPriceAdjustSkuRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TemuPriceAdjustService {

    private static final Logger log = LoggerFactory.getLogger(TemuPriceAdjustService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TemuGoodsRepository goodsRepository;
    private final TemuGoodsSkuRepository goodsSkuRepository;
    private final TemuGoodsSkuSpecRepository goodsSkuSpecRepository;
    private final TemuGoodsSkuPriceRepository goodsSkuPriceRepository;
    private final TemuPriceAdjustOrderRepository adjustOrderRepository;
    private final TemuPriceAdjustSkuRepository adjustSkuRepository;
    private final TemuOpenApiCredentialService credentialService;

    public TemuPriceAdjustService(TemuGoodsRepository goodsRepository,
                                  TemuGoodsSkuRepository goodsSkuRepository,
                                  TemuGoodsSkuSpecRepository goodsSkuSpecRepository,
                                  TemuGoodsSkuPriceRepository goodsSkuPriceRepository,
                                  TemuPriceAdjustOrderRepository adjustOrderRepository,
                                  TemuPriceAdjustSkuRepository adjustSkuRepository,
                                  TemuOpenApiCredentialService credentialService) {
        this.goodsRepository = goodsRepository;
        this.goodsSkuRepository = goodsSkuRepository;
        this.goodsSkuSpecRepository = goodsSkuSpecRepository;
        this.goodsSkuPriceRepository = goodsSkuPriceRepository;
        this.adjustOrderRepository = adjustOrderRepository;
        this.adjustSkuRepository = adjustSkuRepository;
        this.credentialService = credentialService;
    }

    // ==================== 查询 ====================

    public Page<PriceAdjustDTO.AdjustOrderItem> listOrders(String shopId, Integer status, String reviewAction, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        if (reviewAction != null && !reviewAction.isBlank()) {
            String normalizedAction = reviewAction.trim().toUpperCase(Locale.ROOT);
            if ("PENDING".equals(normalizedAction)) {
                return toListPage(status != null
                        ? adjustOrderRepository.findPendingByShopIdAndStatus(shopId, status, pageRequest)
                        : adjustOrderRepository.findPendingByShopId(shopId, pageRequest));
            }
            if ("APPROVED".equals(normalizedAction) || "APPROVE".equals(normalizedAction)) {
                return toListPage(status != null
                        ? adjustOrderRepository.findByShopIdAndStatusAndReviewActionIn(shopId, status, List.of("APPROVE", "APPROVED"), pageRequest)
                        : adjustOrderRepository.findByShopIdAndReviewActionIn(shopId, List.of("APPROVE", "APPROVED"), pageRequest));
            }
            if ("REJECTED".equals(normalizedAction) || "REJECT".equals(normalizedAction)) {
                return toListPage(status != null
                        ? adjustOrderRepository.findByShopIdAndStatusAndReviewActionIn(shopId, status, List.of("REJECT", "REJECTED"), pageRequest)
                        : adjustOrderRepository.findByShopIdAndReviewActionIn(shopId, List.of("REJECT", "REJECTED"), pageRequest));
            }
            return toListPage(status != null
                    ? adjustOrderRepository.findByShopIdAndStatusAndReviewAction(shopId, status, normalizedAction, pageRequest)
                    : adjustOrderRepository.findByShopIdAndReviewAction(shopId, normalizedAction, pageRequest));
        }
        if (status != null) {
            return toListPage(adjustOrderRepository.findByShopIdAndStatus(shopId, status, pageRequest));
        }
        return toListPage(adjustOrderRepository.findByShopId(shopId, pageRequest));
    }

    public PriceAdjustDTO.AdjustOrderItem getOrderDetail(Long id) {
        Long safeId = Objects.requireNonNull(id, "调价单ID不能为空");
        TemuPriceAdjustOrder order = adjustOrderRepository.findById(safeId)
                .orElseThrow(() -> new IllegalArgumentException("调价单不存在: " + id));
        List<TemuPriceAdjustSku> skus = adjustSkuRepository.findByAdjustOrderId(safeId);
        return toDetailItem(order, skus, buildSkuContext(order.getShopId(), skus));
    }

    private PriceAdjustDTO.AdjustOrderItem toDetailItem(TemuPriceAdjustOrder order,
                                                        List<TemuPriceAdjustSku> skus,
                                                        AdjustSkuContext skuContext) {
        PriceAdjustDTO.AdjustOrderItem item = new PriceAdjustDTO.AdjustOrderItem();
        item.setId(order.getId());
        item.setShopId(order.getShopId());
        item.setPriceOrderSn(order.getPriceOrderSn());
        item.setSkcId(order.getSkcId());
        item.setProductName(order.getProductName());
        item.setPriceType(order.getPriceType());
        item.setSource(order.getSource());
        item.setAdjustReason(order.getAdjustReason());
        item.setNewSupplyPrice(order.getNewSupplyPrice());
        item.setPriceCurrency(order.getPriceCurrency());
        item.setRejectReason(order.getRejectReason());
        item.setTrafficLowExpose(order.getTrafficLowExpose());
        item.setStatus(order.getStatus());
        item.setSiteNamesJson(order.getSiteNamesJson());
        item.setSiteNameList(parseSiteNames(order.getSiteNamesJson()));
        item.setReviewAction(order.getReviewAction());
        item.setReviewAt(order.getReviewAt());
        item.setSyncedAt(order.getSyncedAt());

        List<PriceAdjustDTO.AdjustSkuItem> skuItems = buildSkuItems(skus, skuContext);
        item.setSkuList(skuItems);
        item.setSkuInfoList(skuItems);

        return item;
    }

    private Page<PriceAdjustDTO.AdjustOrderItem> toListPage(Page<TemuPriceAdjustOrder> orderPage) {
        List<TemuPriceAdjustOrder> orders = orderPage.getContent();
        if (orders.isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), orderPage.getPageable(), orderPage.getTotalElements());
        }

        List<Long> orderIds = orders.stream()
                .map(TemuPriceAdjustOrder::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<TemuPriceAdjustSku>> skuMap = adjustSkuRepository.findByAdjustOrderIdIn(orderIds).stream()
                .filter(sku -> sku.getAdjustOrderId() != null)
                .collect(Collectors.groupingBy(TemuPriceAdjustSku::getAdjustOrderId, LinkedHashMap::new, Collectors.toList()));
        List<TemuPriceAdjustSku> allSkus = skuMap.values().stream().flatMap(Collection::stream).toList();
        AdjustSkuContext skuContext = buildSkuContext(orders.get(0).getShopId(), allSkus);

        List<PriceAdjustDTO.AdjustOrderItem> items = orders.stream()
                .map(order -> toDetailItem(order, skuMap.getOrDefault(order.getId(), List.of()), skuContext))
                .toList();
        return new PageImpl<>(new ArrayList<>(items), orderPage.getPageable(), orderPage.getTotalElements());
    }

    private List<PriceAdjustDTO.AdjustSkuItem> buildSkuItems(List<TemuPriceAdjustSku> skus, AdjustSkuContext skuContext) {
        return skus.stream().map(sku -> {
            PriceAdjustDTO.AdjustSkuItem si = new PriceAdjustDTO.AdjustSkuItem();
            si.setId(sku.getId());
            si.setProductSkuId(sku.getProductSkuId());
            si.setPrice(sku.getPrice());
            si.setSpec(sku.getSpec());

            TemuGoodsSku goodsSku = skuContext.goodsSkuByProductSkuId().get(sku.getProductSkuId());
            if (goodsSku != null) {
                si.setImageUrl(skuContext.imageUrlByProductSkuId().get(sku.getProductSkuId()));
                si.setExtCode(goodsSku.getExtCode());
                si.setCurrentSupplyPrice(skuContext.currentSupplyPriceByProductSkuId().get(sku.getProductSkuId()));
            }
            String specInfo = skuContext.specInfoByProductSkuId().get(sku.getProductSkuId());
            si.setSpecInfo((specInfo == null || specInfo.isBlank()) ? sku.getSpec() : specInfo);
            return si;
        }).collect(Collectors.toList());
    }

    private AdjustSkuContext buildSkuContext(String shopId, List<TemuPriceAdjustSku> adjustSkus) {
        if (shopId == null || shopId.isBlank() || adjustSkus == null || adjustSkus.isEmpty()) {
            return AdjustSkuContext.empty();
        }

        List<Long> productSkuIds = adjustSkus.stream()
                .map(TemuPriceAdjustSku::getProductSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productSkuIds.isEmpty()) {
            return AdjustSkuContext.empty();
        }

        Map<Long, TemuGoodsSku> goodsSkuMap = goodsSkuRepository.findByShopIdAndProductSkuIdIn(shopId, productSkuIds)
                .stream()
                .filter(sku -> sku.getProductSkuId() != null)
                .collect(Collectors.toMap(TemuGoodsSku::getProductSkuId, sku -> sku, (left, right) -> left, LinkedHashMap::new));

        List<Long> goodsIds = goodsSkuMap.values().stream()
                .map(TemuGoodsSku::getGoodsId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> goodsMainImageMap = new LinkedHashMap<>();
        for (TemuGoods goods : goodsRepository.findAllById(new ArrayList<>(goodsIds))) {
            if (goods.getId() != null) {
                goodsMainImageMap.putIfAbsent(goods.getId(), goods.getMainImageUrl());
            }
        }

        Map<Long, String> imageUrlMap = new LinkedHashMap<>();
        for (TemuGoodsSku sku : goodsSkuMap.values()) {
            if (sku.getProductSkuId() == null) {
                continue;
            }
            imageUrlMap.putIfAbsent(sku.getProductSkuId(), resolveSkuImageUrl(sku, goodsMainImageMap.get(sku.getGoodsId())));
        }

        List<Long> skuIds = goodsSkuMap.values().stream()
                .map(TemuGoodsSku::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, String> specInfoBySkuId = goodsSkuSpecRepository.findBySkuIdIn(skuIds).stream()
                .filter(spec -> spec.getSkuId() != null)
                .collect(Collectors.groupingBy(
                        TemuGoodsSkuSpec::getSkuId,
                        LinkedHashMap::new,
                        Collectors.mapping(spec -> spec.getParentSpecName() + ": " + spec.getSpecName(), Collectors.joining(" / "))));

        Map<Long, String> specInfoByProductSkuId = new LinkedHashMap<>();
        for (TemuGoodsSku sku : goodsSkuMap.values()) {
            if (sku.getProductSkuId() == null || sku.getId() == null) {
                continue;
            }
            specInfoByProductSkuId.putIfAbsent(sku.getProductSkuId(), specInfoBySkuId.get(sku.getId()));
        }

        Map<Long, Integer> currentSupplyPriceMap = new LinkedHashMap<>();
        for (TemuGoodsSkuPrice price : goodsSkuPriceRepository.findByShopIdAndProductSkuIdIn(shopId, productSkuIds)) {
            if (price.getProductSkuId() == null) {
                continue;
            }
            currentSupplyPriceMap.putIfAbsent(price.getProductSkuId(), price.getSupplierPrice());
        }

        return new AdjustSkuContext(goodsSkuMap, specInfoByProductSkuId, currentSupplyPriceMap, imageUrlMap);
    }

    private String resolveSkuImageUrl(TemuGoodsSku goodsSku, String fallbackImageUrl) {
        if (goodsSku == null) {
            return fallbackImageUrl;
        }
        String rawJson = goodsSku.getRawJson();
        if (rawJson == null || rawJson.isBlank()) {
            return fallbackImageUrl;
        }
        try {
            Map<String, Object> raw = OBJECT_MAPPER.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
            if (raw == null || raw.isEmpty()) {
                return fallbackImageUrl;
            }
            for (String key : List.of("thumbUrl", "specShowImageUrl", "mainImageUrl", "imageUrl", "image")) {
                Object value = raw.get(key);
                if (value instanceof String image && !image.isBlank()) {
                    return image.trim();
                }
            }
            Object skuImageList = firstNonNull(raw.get("skuImageList"), raw.get("imageList"), raw.get("images"));
            if (skuImageList instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String image && !image.isBlank()) {
                        return image.trim();
                    }
                    if (item instanceof Map<?, ?> imageMap) {
                        for (String key : List.of("thumbUrl", "imageUrl", "url")) {
                            Object value = imageMap.get(key);
                            if (value instanceof String image && !image.isBlank()) {
                                return image.trim();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析调价单 SKU 图片失败, productSkuId={}, message={}", goodsSku.getProductSkuId(), e.getMessage());
        }
        return fallbackImageUrl;
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

    private record AdjustSkuContext(Map<Long, TemuGoodsSku> goodsSkuByProductSkuId,
                                    Map<Long, String> specInfoByProductSkuId,
                                    Map<Long, Integer> currentSupplyPriceByProductSkuId,
                                    Map<Long, String> imageUrlByProductSkuId) {

        private static AdjustSkuContext empty() {
            return new AdjustSkuContext(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private List<String> parseSiteNames(String siteNamesJson) {
        if (siteNamesJson == null || siteNamesJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> siteNames = OBJECT_MAPPER.readValue(siteNamesJson, new TypeReference<List<String>>() {});
            return siteNames == null ? List.of() : siteNames;
        } catch (Exception e) {
            log.warn("解析调价单站点名称失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Transactional
    public Map<String, Object> clearLocalData(String shopId) {
        if (shopId == null || shopId.isBlank()) {
            return Map.of("success", false, "message", "shopId不能为空");
        }

        long orderCount = adjustOrderRepository.countByShopId(shopId);
        if (orderCount <= 0) {
            return Map.of("success", true, "message", "当前店铺没有可删除的本地调价单数据", "deletedOrders", 0, "deletedSkus", 0);
        }

        List<Long> orderIds = adjustOrderRepository.findIdsByShopId(shopId).stream()
                .filter(Objects::nonNull)
                .toList();

        long deletedSkus = 0;
        if (!orderIds.isEmpty()) {
            deletedSkus = adjustSkuRepository.countByAdjustOrderIdIn(orderIds);
            adjustSkuRepository.deleteByAdjustOrderIdIn(orderIds);
        }
        int deletedOrders = adjustOrderRepository.deleteAllByShopId(shopId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("deletedOrders", deletedOrders);
        result.put("deletedSkus", deletedSkus);
        result.put("message", String.format("已清空本地调价单数据：%d 条主单，%d 条 SKU 明细", deletedOrders, deletedSkus));
        return result;
    }

    // ==================== 批量审批 ====================

    @Transactional
    public Map<String, Object> batchReview(PriceAdjustDTO.BatchReviewRequest request) {
        String shopId = request.getShopId();
        String action = request.getAction() != null ? request.getAction().trim().toUpperCase(Locale.ROOT) : null;
        List<Long> orderIds = request.getOrderIds();
        String rejectReason = request.getRejectReason() == null ? null : request.getRejectReason().trim();

        if (shopId == null || shopId.isBlank()) {
            return Map.of("success", false, "message", "shopId不能为空");
        }
        if (action == null || (!"APPROVE".equals(action) && !"APPROVED".equals(action) && !"REJECT".equals(action) && !"REJECTED".equals(action))) {
            return Map.of("success", false, "message", "调价单审核当前仅支持 APPROVE 或 REJECT");
        }
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of("success", false, "message", "请选择至少一条调价单");
        }

        List<TemuPriceAdjustOrder> orders = adjustOrderRepository.findByShopIdAndIdIn(shopId, orderIds);
        if (orders.isEmpty()) {
            return Map.of("success", false, "message", "未找到指定的调价单");
        }

        try {
            TemuOpenApiCredentials creds = credentialService.getTemuOpenApiCredentialsByShopIdOrThrow(shopId);
            TemuOpenApiClient client = new TemuOpenApiClient(creds);
            List<String> submitOrders = new ArrayList<>();
            for (TemuPriceAdjustOrder order : orders) {
                if (order.getPriceOrderSn() == null || order.getPriceOrderSn().isBlank()) {
                    continue;
                }
                submitOrders.add(order.getPriceOrderSn().trim());
            }

            if (submitOrders.isEmpty()) {
                return Map.of("success", false, "message", "没有可审核的调价单号");
            }

            ReviewApiAttemptResult reviewAttempt = callPriceAdjustReview(client, action, submitOrders, rejectReason);
            TemuOpenApiClient.ApiResult result = reviewAttempt.result();
            Map<String, Object> remoteResult = result.resultAsMap();
            Set<String> successOrderSns = resolveSuccessOrderSns(remoteResult, submitOrders, result.success);
            Map<String, String> failedOrders = parseFailedOrders(remoteResult == null ? null : remoteResult.get("failedOrders"));

            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("success", result.success);
            resultMap.put("total", orders.size());
            resultMap.put("attempts", reviewAttempt.attemptSummaries());

            if (result.success) {
                LocalDateTime reviewTime = LocalDateTime.now();
                int successCount = 0;
                for (TemuPriceAdjustOrder order : orders) {
                    String priceOrderSn = order.getPriceOrderSn() == null ? null : order.getPriceOrderSn().trim();
                    if (priceOrderSn == null || !successOrderSns.contains(priceOrderSn)) {
                        continue;
                    }
                    order.setReviewAction("REJECT".equals(action) || "REJECTED".equals(action) ? "REJECT" : "APPROVE");
                    order.setReviewAt(reviewTime);
                    order.setRejectReason("REJECT".equals(action) || "REJECTED".equals(action) ? defaultIfBlank(rejectReason, "") : "");
                    successCount++;
                }
                if (successCount > 0) {
                    for (TemuPriceAdjustOrder order : orders) {
                        String priceOrderSn = order.getPriceOrderSn() == null ? null : order.getPriceOrderSn().trim();
                        if (priceOrderSn != null && successOrderSns.contains(priceOrderSn)) {
                            adjustOrderRepository.save(order);
                        }
                    }
                }

                int failCount = Math.max(0, submitOrders.size() - successCount);
                resultMap.put("successCount", successCount);
                resultMap.put("failCount", failCount);
                if (!failedOrders.isEmpty()) {
                    resultMap.put("failedOrders", failedOrders);
                }
                resultMap.put("success", failCount == 0);
                resultMap.put("message", buildBatchReviewMessage(action, successCount, failCount, failedOrders));
            } else {
                resultMap.put("successCount", 0);
                resultMap.put("failCount", submitOrders.size());
                resultMap.put("message", buildReviewFailureMessage(reviewAttempt));
            }
            return resultMap;
        } catch (Exception e) {
            log.error("批量审批调价单失败", e);
            return Map.of("success", false, "message", "API调用失败: " + defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private ReviewApiAttemptResult callPriceAdjustReview(TemuOpenApiClient client,
                                                         String action,
                                                         List<String> submitOrders,
                                                         String rejectReason) throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("batchResult", resolveBatchResult(action));
        params.put("submitOrders", submitOrders);
        if ("REJECT".equals(action) || "REJECTED".equals(action)) {
            params.put("rejectReasons", buildRejectReasons(submitOrders, rejectReason));
        }

        TemuOpenApiClient.ApiResult apiResult = client.callApiParsed(TemuOpenApiClient.API_PRICE_ADJUST_REVIEW, params);
        ReviewApiAttemptSummary summary = toAttemptSummary(1, "PARTNER", params, apiResult);
        if (!apiResult.success) {
            log.warn("调价单审核调用失败, attempt={}, router={}, params={}, errorMsg={}, raw={}",
                    summary.attemptNo(),
                    summary.routerName(),
                    summary.requestSummary(),
                    summary.errorMessage(),
                    summary.rawResponse());
        }
        return new ReviewApiAttemptResult(apiResult, List.of(summary));
    }

    private Map<String, String> buildRejectReasons(List<String> submitOrders, String rejectReason) {
        if (submitOrders == null || submitOrders.isEmpty()) {
            return Map.of();
        }
        Map<String, String> rejectReasons = new LinkedHashMap<>();
        String reasonText = defaultIfBlank(rejectReason, "");
        for (String priceOrderSn : submitOrders) {
            rejectReasons.put(priceOrderSn, reasonText);
        }
        return rejectReasons;
    }

    private Set<String> resolveSuccessOrderSns(Map<String, Object> remoteResult,
                                               List<String> submitOrders,
                                               boolean apiSuccess) {
        List<String> successOrders = parseStringList(remoteResult == null ? null : remoteResult.get("successOrders"));
        Map<String, String> failedOrders = parseFailedOrders(remoteResult == null ? null : remoteResult.get("failedOrders"));

        if (!successOrders.isEmpty()) {
            return new LinkedHashSet<>(successOrders);
        }
        if (!failedOrders.isEmpty()) {
            return submitOrders.stream()
                    .filter(orderSn -> !failedOrders.containsKey(orderSn))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return apiSuccess
                ? new LinkedHashSet<>(submitOrders)
                : new LinkedHashSet<>();
    }

    private List<String> parseStringList(Object rawValue) {
        if (!(rawValue instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values;
    }

    private Map<String, String> parseFailedOrders(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey()).trim();
            if (key.isEmpty()) {
                continue;
            }
            values.put(key, entry.getValue() == null ? "TEMU接口返回失败" : String.valueOf(entry.getValue()).trim());
        }
        return values;
    }

    private String buildBatchReviewMessage(String action,
                                           int successCount,
                                           int failCount,
                                           Map<String, String> failedOrders) {
        String actionText = "REJECT".equals(action) || "REJECTED".equals(action) ? "审核拒绝" : "审核通过";
        if (failCount <= 0) {
            return actionText + "成功，共 " + successCount + " 条调价单";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(actionText)
                .append("完成，成功 ")
                .append(successCount)
                .append(" 条，失败 ")
                .append(failCount)
                .append(" 条");
        if (!failedOrders.isEmpty()) {
            String details = failedOrders.entrySet().stream()
                    .limit(3)
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(Collectors.joining("；"));
            if (!details.isBlank()) {
                builder.append("；").append(details);
            }
            if (failedOrders.size() > 3) {
                builder.append("；其余 ").append(failedOrders.size() - 3).append(" 条请查看明细");
            }
        }
        return builder.toString();
    }

    private String buildReviewFailureMessage(ReviewApiAttemptResult attemptResult) {
        if (attemptResult == null || attemptResult.attemptSummaries().isEmpty()) {
            return "TEMU接口调用失败";
        }

        ReviewApiAttemptSummary lastAttempt = attemptResult.attemptSummaries().get(attemptResult.attemptSummaries().size() - 1);
        StringBuilder builder = new StringBuilder();
        builder.append("TEMU接口调用失败");
        if (lastAttempt.errorCode() != null) {
            builder.append("，errorCode=").append(lastAttempt.errorCode());
        }
        if (lastAttempt.errorMessage() != null && !lastAttempt.errorMessage().isBlank()) {
            builder.append("，errorMsg=").append(lastAttempt.errorMessage());
        }
        builder.append("，请求=").append(lastAttempt.requestSummary());

        return builder.toString();
    }

    private ReviewApiAttemptSummary toAttemptSummary(int attemptNo,
                                                     String routerUrl,
                                                     Map<String, Object> params,
                                                     TemuOpenApiClient.ApiResult result) {
        Map<String, Object> rawMap = parseRawResponse(result == null ? null : result.raw);
        return new ReviewApiAttemptSummary(
                attemptNo,
            routerUrl,
                summarizeReviewParams(params),
                rawMap.get("errorCode") == null ? null : String.valueOf(rawMap.get("errorCode")),
                result == null ? null : result.errorMsg,
                result == null ? null : truncate(result.raw, 1000)
        );
    }

    private Integer resolveBatchResult(String action) {
        return ("REJECT".equals(action) || "REJECTED".equals(action)) ? 2 : 1;
    }

    private Map<String, Object> parseRawResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String summarizeReviewParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        Object submitOrdersRaw = params.get("submitOrders");
        int orderCount = submitOrdersRaw instanceof List<?> list ? list.size() : 0;
        String orderPreview = submitOrdersRaw instanceof List<?> list
                ? list.stream().filter(Objects::nonNull).map(String::valueOf).limit(3).collect(Collectors.joining(","))
                : "";
        Object batchResultRaw = params.get("batchResult");
        Object rejectReasonsRaw = params.get("rejectReasons");
        String rejectReasonsState;
        if (!params.containsKey("rejectReasons")) {
            rejectReasonsState = "omitted";
        } else if (rejectReasonsRaw instanceof Map<?, ?> map) {
            rejectReasonsState = "map(" + map.size() + ")";
        } else {
            rejectReasonsState = String.valueOf(rejectReasonsRaw);
        }
        return "batchResult=" + batchResultRaw + ", submitOrders=" + orderCount + "[" + orderPreview + "]" + ", rejectReasons=" + rejectReasonsState;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private record ReviewApiAttemptResult(TemuOpenApiClient.ApiResult result,
                                          List<ReviewApiAttemptSummary> attemptSummaries) {
    }

    private record ReviewApiAttemptSummary(int attemptNo,
                                           String routerName,
                                           String requestSummary,
                                           String errorCode,
                                           String errorMessage,
                                           String rawResponse) {
    }

}
