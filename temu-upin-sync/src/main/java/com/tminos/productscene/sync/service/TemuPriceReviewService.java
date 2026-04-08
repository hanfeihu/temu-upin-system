package com.tminos.productscene.sync.service;

import com.google.gson.Gson;
import com.tminos.productscene.service.TemuOpenApiCredentialService;
import com.tminos.productscene.sync.dto.PriceReviewDTO;
import com.tminos.productscene.sync.entity.TemuGoods;
import com.tminos.productscene.sync.entity.TemuGoodsSku;
import com.tminos.productscene.sync.entity.TemuGoodsSkuPrice;
import com.tminos.productscene.sync.entity.TemuGoodsSkuSpec;
import com.tminos.productscene.sync.entity.TemuPriceReviewOrder;
import com.tminos.productscene.sync.entity.TemuPriceReviewSku;
import com.tminos.productscene.sync.repository.TemuGoodsSkuPriceRepository;
import com.tminos.productscene.sync.repository.TemuGoodsRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuSpecRepository;
import com.tminos.productscene.sync.repository.TemuPriceReviewOrderRepository;
import com.tminos.productscene.sync.repository.TemuPriceReviewSkuRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TemuPriceReviewService {

    private static final Logger log = LoggerFactory.getLogger(TemuPriceReviewService.class);
    private static final Gson GSON = new Gson();

    private final TemuGoodsRepository goodsRepository;
    private final TemuPriceReviewOrderRepository reviewOrderRepository;
    private final TemuPriceReviewSkuRepository reviewSkuRepository;
    private final TemuGoodsSkuRepository goodsSkuRepository;
    private final TemuGoodsSkuSpecRepository goodsSkuSpecRepository;
    private final TemuGoodsSkuPriceRepository goodsSkuPriceRepository;
    private final TemuOpenApiCredentialService credentialService;

    public TemuPriceReviewService(TemuGoodsRepository goodsRepository,
                                  TemuPriceReviewOrderRepository reviewOrderRepository,
                                  TemuPriceReviewSkuRepository reviewSkuRepository,
                                  TemuGoodsSkuRepository goodsSkuRepository,
                                  TemuGoodsSkuSpecRepository goodsSkuSpecRepository,
                                  TemuGoodsSkuPriceRepository goodsSkuPriceRepository,
                                  TemuOpenApiCredentialService credentialService) {
        this.goodsRepository = goodsRepository;
        this.reviewOrderRepository = reviewOrderRepository;
        this.reviewSkuRepository = reviewSkuRepository;
        this.goodsSkuRepository = goodsSkuRepository;
        this.goodsSkuSpecRepository = goodsSkuSpecRepository;
        this.goodsSkuPriceRepository = goodsSkuPriceRepository;
        this.credentialService = credentialService;
    }

    // ==================== 查询 ====================

    public Page<PriceReviewDTO.ReviewOrderItem> listOrders(String shopId, Integer orderStatus, String reviewAction, int page, int pageSize) {
        Page<TemuPriceReviewOrder> orderPage = queryOrders(shopId, orderStatus, reviewAction, page, pageSize);
        List<TemuPriceReviewOrder> orders = orderPage.getContent();
        if (orders.isEmpty()) {
            return orderPage.map(order -> toReviewOrderItem(order, List.of(), ReviewSkuContext.empty()));
        }

        List<TemuPriceReviewSku> reviewSkus = loadReviewSkusForOrders(orders);
        Map<Long, List<TemuPriceReviewSku>> skuMap = reviewSkus.stream()
                .filter(sku -> sku.getReviewOrderId() != null)
                .collect(Collectors.groupingBy(TemuPriceReviewSku::getReviewOrderId, LinkedHashMap::new, Collectors.toList()));
        ReviewSkuContext skuContext = buildSkuContext(shopId, reviewSkus);

        return orderPage.map(order -> toReviewOrderItem(order, skuMap.getOrDefault(order.getId(), List.of()), skuContext));
    }

    private Page<TemuPriceReviewOrder> queryOrders(String shopId, Integer orderStatus, String reviewAction, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        if (reviewAction != null && !reviewAction.isBlank()) {
            String normalizedAction = reviewAction.trim().toUpperCase(Locale.ROOT);
            if ("PENDING".equals(normalizedAction)) {
                return orderStatus != null
                        ? reviewOrderRepository.findPendingByShopIdAndOrderStatus(shopId, orderStatus, pageRequest)
                        : reviewOrderRepository.findPendingByShopId(shopId, pageRequest);
            }
            if ("APPROVED".equals(normalizedAction) || "APPROVE".equals(normalizedAction)) {
                return orderStatus != null
                        ? reviewOrderRepository.findByShopIdAndOrderStatusAndReviewActionIn(shopId, orderStatus, List.of("APPROVE", "APPROVED"), pageRequest)
                        : reviewOrderRepository.findByShopIdAndReviewActionIn(shopId, List.of("APPROVE", "APPROVED"), pageRequest);
            }
            if ("REJECTED".equals(normalizedAction) || "REJECT".equals(normalizedAction)) {
                return orderStatus != null
                        ? reviewOrderRepository.findByShopIdAndOrderStatusAndReviewActionIn(shopId, orderStatus, List.of("REJECT", "REJECTED"), pageRequest)
                        : reviewOrderRepository.findByShopIdAndReviewActionIn(shopId, List.of("REJECT", "REJECTED"), pageRequest);
            }
            return orderStatus != null
                    ? reviewOrderRepository.findByShopIdAndOrderStatusAndReviewAction(shopId, orderStatus, normalizedAction, pageRequest)
                    : reviewOrderRepository.findByShopIdAndReviewAction(shopId, normalizedAction, pageRequest);
        }
        if (orderStatus != null) {
            return reviewOrderRepository.findByShopIdAndOrderStatus(shopId, orderStatus, pageRequest);
        }
        return reviewOrderRepository.findByShopId(shopId, pageRequest);
    }

    public PriceReviewDTO.ReviewOrderItem getOrderDetail(Long id) {
        Long safeId = Objects.requireNonNull(id, "核价单ID不能为空");
        TemuPriceReviewOrder order = reviewOrderRepository.findById(safeId)
                .orElseThrow(() -> new IllegalArgumentException("核价单不存在: " + id));
        List<TemuPriceReviewSku> skus = loadReviewSkus(order);
        return toReviewOrderItem(order, skus, buildSkuContext(order.getShopId(), skus));
    }

    private List<TemuPriceReviewSku> loadReviewSkusForOrders(List<TemuPriceReviewOrder> orders) {
        List<Long> reviewOrderIds = orders.stream()
                .map(TemuPriceReviewOrder::getId)
                .filter(Objects::nonNull)
                .toList();
        if (reviewOrderIds.isEmpty()) {
            return List.of();
        }

        List<TemuPriceReviewSku> reviewSkus = new ArrayList<>(reviewSkuRepository.findByReviewOrderIdIn(reviewOrderIds));
        Set<Long> loadedOrderIds = reviewSkus.stream()
                .map(TemuPriceReviewSku::getReviewOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (TemuPriceReviewOrder order : orders) {
            if (order.getId() == null || loadedOrderIds.contains(order.getId())) {
                continue;
            }
            List<TemuPriceReviewSku> repaired = loadReviewSkus(order);
            if (!repaired.isEmpty()) {
                reviewSkus.addAll(repaired);
                loadedOrderIds.add(order.getId());
            }
        }
        return reviewSkus;
    }

    private List<TemuPriceReviewSku> loadReviewSkus(TemuPriceReviewOrder order) {
        List<TemuPriceReviewSku> skus = reviewSkuRepository.findByReviewOrderId(order.getId());
        if (!skus.isEmpty()) {
            return skus;
        }
        List<TemuPriceReviewSku> repairedSkus = tryRepairReviewSkus(order);
        return repairedSkus.isEmpty() ? List.of() : repairedSkus;
    }

    private List<TemuPriceReviewSku> tryRepairReviewSkus(TemuPriceReviewOrder order) {
        if (order.getOrderId() == null || order.getShopId() == null || order.getShopId().isBlank()) {
            return List.of();
        }

        try {
            TemuOpenApiCredentials creds = credentialService.getDefaultTemuOpenApiCredentialsOrThrow();
            TemuOpenApiClient client = new TemuOpenApiClient(creds);
            int pageNo = 1;
            int pageSize = 50;
            int totalPages = 1;

            while (pageNo <= totalPages) {
                Map<String, Object> params = new HashMap<>();
                params.put("pageNo", pageNo);
                params.put("pageSize", pageSize);

                TemuOpenApiClient.ApiResult result = client.callApiParsed(TemuOpenApiClient.API_PRICE_REVIEW_QUERY, params);
                if (!result.success) {
                    log.warn("回补核价单SKU失败, orderId={}, error={}", order.getOrderId(), result.errorMsg);
                    return List.of();
                }

                Map<String, Object> resultMap = result.resultAsMap();
                if (resultMap == null) {
                    return List.of();
                }

                Integer total = toInt(resultMap.get("total"));
                if (total != null) {
                    totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
                }

                List<Map<String, Object>> orders = extractOrderList(resultMap);
                for (Map<String, Object> raw : orders) {
                    Long remoteOrderId = toLong(raw.get("orderId"));
                    if (!Objects.equals(remoteOrderId, order.getOrderId())) {
                        continue;
                    }
                    List<TemuPriceReviewSku> repaired = buildReviewSkuRows(order.getId(), raw);
                    if (repaired.isEmpty()) {
                        return List.of();
                    }
                    reviewSkuRepository.deleteByReviewOrderId(order.getId());
                    return reviewSkuRepository.saveAll(repaired);
                }
                pageNo++;
            }
        } catch (Exception e) {
            log.warn("回补核价单SKU异常, orderId={}, message={}", order.getOrderId(), e.getMessage());
        }
        return List.of();
    }

    private PriceReviewDTO.ReviewOrderItem toReviewOrderItem(TemuPriceReviewOrder order,
                                                             List<TemuPriceReviewSku> skus,
                                                             ReviewSkuContext skuContext) {
        PriceReviewDTO.ReviewOrderItem item = new PriceReviewDTO.ReviewOrderItem();
        item.setId(order.getId());
        item.setShopId(order.getShopId());
        item.setOrderId(order.getOrderId());
        item.setOrderStatus(order.getOrderStatus());
        item.setSupplyPrice(order.getSupplyPrice());
        item.setPriceCurrency(order.getPriceCurrency());
        item.setSuggestSupplyPrice(order.getSuggestSupplyPrice());
        item.setSuggestPriceCurrency(order.getSuggestPriceCurrency());
        item.setCanBargain(order.getCanBargain());
        item.setSiteIdsJson(order.getSiteIdsJson());
        item.setSiteNamesJson(order.getSiteNamesJson());
        item.setReviewAction(order.getReviewAction());
        item.setReviewAt(order.getReviewAt());
        item.setRejectReasonsJson(order.getRejectReasonsJson());
        item.setSyncedAt(order.getSyncedAt());

        item.setSkuList(skus.stream().map(sku -> {
            PriceReviewDTO.ReviewSkuItem si = new PriceReviewDTO.ReviewSkuItem();
            si.setId(sku.getId());
            si.setProductSkuId(sku.getProductSkuId());
            si.setNewPrice(sku.getNewPrice());

            TemuGoodsSku goodsSku = skuContext.goodsSkuByProductSkuId().get(sku.getProductSkuId());
            if (goodsSku != null) {
                si.setImageUrl(skuContext.imageUrlByProductSkuId().get(sku.getProductSkuId()));
                si.setExtCode(goodsSku.getExtCode());
                si.setSpecInfo(skuContext.specInfoBySkuId().get(goodsSku.getId()));
                si.setCurrentSupplyPrice(skuContext.currentSupplyPriceByProductSkuId().get(sku.getProductSkuId()));
            }
            return si;
        }).collect(Collectors.toList()));

        return item;
    }

    private ReviewSkuContext buildSkuContext(String shopId, List<TemuPriceReviewSku> reviewSkus) {
        if (shopId == null || shopId.isBlank() || reviewSkus == null || reviewSkus.isEmpty()) {
            return ReviewSkuContext.empty();
        }

        List<Long> productSkuIds = reviewSkus.stream()
                .map(TemuPriceReviewSku::getProductSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productSkuIds.isEmpty()) {
            return ReviewSkuContext.empty();
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
        Map<Long, String> goodsMainImageMap = goodsRepository.findAllById(new ArrayList<>(goodsIds))
            .stream()
            .filter(goods -> goods.getId() != null)
            .collect(Collectors.toMap(TemuGoods::getId, TemuGoods::getMainImageUrl, (left, right) -> left, LinkedHashMap::new));

        Map<Long, String> imageUrlMap = goodsSkuMap.values().stream()
            .filter(sku -> sku.getProductSkuId() != null)
            .collect(Collectors.toMap(
                TemuGoodsSku::getProductSkuId,
                sku -> resolveSkuImageUrl(sku, goodsMainImageMap.get(sku.getGoodsId())),
                (left, right) -> left,
                LinkedHashMap::new));

        List<Long> skuIds = goodsSkuMap.values().stream()
                .map(TemuGoodsSku::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, String> specInfoMap = goodsSkuSpecRepository.findBySkuIdIn(skuIds)
                .stream()
                .filter(spec -> spec.getSkuId() != null)
                .collect(Collectors.groupingBy(
                        TemuGoodsSkuSpec::getSkuId,
                        LinkedHashMap::new,
                        Collectors.mapping(spec -> spec.getParentSpecName() + ": " + spec.getSpecName(), Collectors.joining(" / "))));

        Map<Long, Integer> currentSupplyPriceMap = goodsSkuPriceRepository.findByShopIdAndProductSkuIdIn(shopId, productSkuIds)
                .stream()
                .filter(price -> price.getProductSkuId() != null)
                .collect(Collectors.toMap(TemuGoodsSkuPrice::getProductSkuId, TemuGoodsSkuPrice::getSupplierPrice, (left, right) -> left, LinkedHashMap::new));

        return new ReviewSkuContext(goodsSkuMap, specInfoMap, currentSupplyPriceMap, imageUrlMap);
    }

    @SuppressWarnings("unchecked")
    private String resolveSkuImageUrl(TemuGoodsSku goodsSku, String fallbackImageUrl) {
        if (goodsSku == null) {
            return fallbackImageUrl;
        }
        String rawJson = goodsSku.getRawJson();
        if (rawJson == null || rawJson.isBlank()) {
            return fallbackImageUrl;
        }
        try {
            Map<String, Object> raw = GSON.fromJson(rawJson, Map.class);
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
            log.debug("解析核价单 SKU 图片失败, productSkuId={}, message={}", goodsSku.getProductSkuId(), e.getMessage());
        }
        return fallbackImageUrl;
    }

    private record ReviewSkuContext(Map<Long, TemuGoodsSku> goodsSkuByProductSkuId,
                                    Map<Long, String> specInfoBySkuId,
                                    Map<Long, Integer> currentSupplyPriceByProductSkuId,
                                    Map<Long, String> imageUrlByProductSkuId) {
        private static ReviewSkuContext empty() {
            return new ReviewSkuContext(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractOrderList(Map<String, Object> resultMap) {
        Object value = resultMap.get("reviewSamplePriceList");
        if (value instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                }
            }
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return List.of();
    }

    private List<TemuPriceReviewSku> buildReviewSkuRows(Long reviewOrderId, Map<String, Object> raw) {
        Object productSkuIdList = raw.get("productSkuIdList");
        if (!(productSkuIdList instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }

        List<TemuPriceReviewSku> rows = new ArrayList<>();
        for (Long productSkuId : new LinkedHashSet<>(rawList.stream().map(this::toLong).filter(Objects::nonNull).toList())) {
            rows.add(TemuPriceReviewSku.builder()
                    .reviewOrderId(reviewOrderId)
                    .productSkuId(productSkuId)
                    .build());
        }
        return rows;
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

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
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

    // ==================== 批量审批 ====================

    @Transactional
    public Map<String, Object> batchReview(PriceReviewDTO.BatchReviewRequest request) {
        String shopId = request.getShopId();
        String action = request.getAction() != null ? request.getAction().trim().toUpperCase(Locale.ROOT) : null;
        List<Long> orderIds = request.getOrderIds();

        if (shopId == null || shopId.isBlank()) {
            return Map.of("success", false, "message", "shopId不能为空");
        }
        if (action == null || (!"APPROVE".equals(action) && !"REJECT".equals(action))) {
            return Map.of("success", false, "message", "action仅支持 APPROVE 或 REJECT");
        }
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of("success", false, "message", "请选择至少一条核价单");
        }

        List<TemuPriceReviewOrder> orders = reviewOrderRepository.findByShopIdAndIdIn(shopId, orderIds);
        if (orders.isEmpty()) {
            return Map.of("success", false, "message", "未找到指定的核价单");
        }

        // 调用 TEMU API 提交审批结果
        try {
            TemuOpenApiCredentials creds = credentialService.getTemuOpenApiCredentialsByShopIdOrThrow(shopId);
            TemuOpenApiClient client = new TemuOpenApiClient(creds);

            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();

            for (TemuPriceReviewOrder order : orders) {
                try {
                    String apiType;
                    Map<String, Object> params = new HashMap<>();
                    params.put("orderId", order.getOrderId());

                    if ("APPROVE".equals(action)) {
                        apiType = TemuOpenApiClient.API_PRICE_REVIEW_CONFIRM;
                    } else {
                        apiType = TemuOpenApiClient.API_PRICE_REVIEW_REJECT;
                        List<Map<String, Object>> bargainReasonList = buildBargainReasonList(request.getBargainReasonList());
                        if (!bargainReasonList.isEmpty()) {
                            params.put("bargainReasonList", bargainReasonList);
                        }

                        List<Map<String, Object>> priceItems = buildRejectPriceItems(request.getRejectPrices(), order);
                        if (!priceItems.isEmpty()) {
                            params.put("priceItemList", priceItems);
                        }
                    }

                    TemuOpenApiClient.ApiResult result = client.callApiParsed(apiType, params);
                    if (result.success) {
                        updateLocalReviewResult(order, action, request);
                        successCount++;
                    } else {
                        failCount++;
                        String failureDetail = buildReviewApiFailureDetail(result);
                        log.warn("核价单审批失败, shopId={}, localOrderId={}, orderId={}, action={}, apiType={}, params={}, detail={}, raw={}",
                                shopId,
                                order.getId(),
                                order.getOrderId(),
                                action,
                                apiType,
                                summarizeReviewParams(params),
                                failureDetail,
                                truncate(result == null ? null : result.raw, 1500));
                        errors.add("订单" + order.getOrderId() + ": " + failureDetail);
                    }
                } catch (Exception e) {
                    failCount++;
                    log.warn("核价单审批异常, shopId={}, localOrderId={}, orderId={}, action={}, message={}",
                            shopId,
                            order.getId(),
                            order.getOrderId(),
                            action,
                            e.getMessage(),
                            e);
                    errors.add("订单" + order.getOrderId() + ": " + defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
                }
            }

            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("success", failCount == 0);
            resultMap.put("total", orders.size());
            resultMap.put("successCount", successCount);
            resultMap.put("failCount", failCount);
            if (!errors.isEmpty()) {
                resultMap.put("errors", errors);
                resultMap.put("message", buildBatchReviewMessage(successCount, failCount, errors));
            } else {
                resultMap.put("message", "批量核价完成，共成功 " + successCount + " 条");
            }
            return resultMap;
        } catch (Exception e) {
            log.error("批量审批核价单失败", e);
            return Map.of("success", false, "message", "API调用失败: " + defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private String buildBatchReviewMessage(int successCount, int failCount, List<String> errors) {
        StringBuilder builder = new StringBuilder();
        if (successCount > 0) {
            builder.append("成功 ").append(successCount).append(" 条");
        }
        if (failCount > 0) {
            if (builder.length() > 0) {
                builder.append("，");
            }
            builder.append("失败 ").append(failCount).append(" 条");
        }
        if (errors != null && !errors.isEmpty()) {
            builder.append("；");
            builder.append(errors.stream().limit(3).collect(Collectors.joining("；")));
            if (errors.size() > 3) {
                builder.append("；其余 ").append(errors.size() - 3).append(" 条请查看明细");
            }
        }
        return builder.length() > 0 ? builder.toString() : "批量核价失败";
    }

    private String buildReviewApiFailureDetail(TemuOpenApiClient.ApiResult result) {
        String errorMsg = defaultIfBlank(result == null ? null : result.errorMsg, "TEMU接口返回失败");
        Map<String, Object> rawMap = parseRawResponse(result == null ? null : result.raw);
        String errorCode = rawMap.get("errorCode") == null ? null : String.valueOf(rawMap.get("errorCode"));
        String rawSnippet = truncate(result == null ? null : result.raw, 500);

        StringBuilder builder = new StringBuilder(errorMsg);
        if (errorCode != null && !errorCode.isBlank()) {
            builder.append(" (errorCode=").append(errorCode).append(')');
        }
        if (rawSnippet != null && !rawSnippet.isBlank() && !rawSnippet.equals(errorMsg)) {
            builder.append(" raw=").append(rawSnippet);
        }
        return builder.toString();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Map<String, Object> parseRawResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return GSON.fromJson(raw, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String summarizeReviewParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        Object orderId = params.get("orderId");
        Object bargainReasonList = params.get("bargainReasonList");
        Object priceItemList = params.get("priceItemList");
        int bargainReasonCount = bargainReasonList instanceof List<?> list ? list.size() : 0;
        int priceItemCount = priceItemList instanceof List<?> list ? list.size() : 0;
        return "orderId=" + orderId + ", bargainReasonList=" + bargainReasonCount + ", priceItemList=" + priceItemCount;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private List<Map<String, Object>> buildBargainReasonList(List<PriceReviewDTO.BargainReasonItem> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (PriceReviewDTO.BargainReasonItem item : source) {
            if (item == null) continue;

            List<Map<String, Object>> componentList = new ArrayList<>();
            if (item.getComponentList() != null) {
                for (PriceReviewDTO.RejectReasonComponent component : item.getComponentList()) {
                    if (component == null || component.getType() == null || component.getReason() == null || component.getReason().isBlank()) {
                        continue;
                    }
                    componentList.add(Map.of(
                            "reason", component.getReason().trim(),
                            "type", component.getType()
                    ));
                }
            }

            List<String> externalLinks = item.getExternalLinkList() == null
                    ? List.of()
                    : item.getExternalLinkList().stream().filter(link -> link != null && !link.isBlank()).map(String::trim).toList();

            if (componentList.isEmpty() && externalLinks.isEmpty()) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            if (!componentList.isEmpty()) {
                row.put("componentList", componentList);
            }
            if (!externalLinks.isEmpty()) {
                row.put("externalLinkList", externalLinks);
            }
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> buildRejectPriceItems(List<PriceReviewDTO.RejectPriceItem> rejectPrices, TemuPriceReviewOrder order) {
        if (rejectPrices == null || rejectPrices.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (PriceReviewDTO.RejectPriceItem item : rejectPrices) {
            if (item == null || item.getProductSkuId() == null || item.getNewPrice() == null) {
                continue;
            }
            if (!Objects.equals(item.getOrderId(), order.getId()) && !Objects.equals(item.getOrderId(), order.getOrderId())) {
                continue;
            }
            result.add(Map.of(
                    "productSkuId", item.getProductSkuId(),
                    "price", String.valueOf(item.getNewPrice())
            ));
        }
        return result;
    }

    private void updateLocalReviewResult(TemuPriceReviewOrder order,
                                         String action,
                                         PriceReviewDTO.BatchReviewRequest request) {
        order.setReviewAction(action);
        order.setReviewAt(LocalDateTime.now());
        if ("REJECT".equals(action)) {
            List<Map<String, Object>> bargainReasonList = buildBargainReasonList(request.getBargainReasonList());
            order.setRejectReasonsJson(bargainReasonList.isEmpty() ? null : GSON.toJson(bargainReasonList));
            updateRejectPrices(order.getId(), request.getRejectPrices());
        } else {
            order.setRejectReasonsJson(null);
        }
        reviewOrderRepository.save(order);
    }

    private void updateRejectPrices(Long localOrderId, List<PriceReviewDTO.RejectPriceItem> rejectPrices) {
        if (localOrderId == null || rejectPrices == null || rejectPrices.isEmpty()) {
            return;
        }
        Map<Long, Integer> priceMap = rejectPrices.stream()
                .filter(item -> item != null && Objects.equals(item.getOrderId(), localOrderId) && item.getProductSkuId() != null && item.getNewPrice() != null)
                .collect(Collectors.toMap(PriceReviewDTO.RejectPriceItem::getProductSkuId, PriceReviewDTO.RejectPriceItem::getNewPrice, (left, right) -> right));
        if (priceMap.isEmpty()) {
            return;
        }
        List<TemuPriceReviewSku> skus = reviewSkuRepository.findByReviewOrderId(localOrderId);
        boolean changed = false;
        for (TemuPriceReviewSku sku : skus) {
            Integer newPrice = priceMap.get(sku.getProductSkuId());
            if (newPrice != null) {
                sku.setNewPrice(newPrice);
                changed = true;
            }
        }
        if (changed) {
            reviewSkuRepository.saveAll(new ArrayList<>(skus));
        }
    }

}
