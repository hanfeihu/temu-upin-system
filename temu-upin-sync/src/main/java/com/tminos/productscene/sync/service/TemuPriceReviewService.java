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
import com.tminos.productscene.sync.repository.TemuShopSkuPurchasePriceRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TemuPriceReviewService {

    private static final Logger log = LoggerFactory.getLogger(TemuPriceReviewService.class);
    private static final Gson GSON = new Gson();
    private static final String MATCH_COLLECTION_PRICE_SQL = """
            with ranked_matches as (
                select
                    lower(btrim(pcs.sku_id)) as sku_key,
                    cast(round(pcs.price * 100) as integer) as collected_price,
                    pc.source_platform as source_platform,
                    pc.id as product_collection_id,
                    pc.product_id as product_id,
                    pc.product_name as product_name,
                    pc.product_url as product_url,
                    pcs.sku_id as collected_sku_id,
                    pcs.spec_key as collected_sku_spec,
                    coalesce(pool.base_freight_snapshot, pc.base_freight) as base_freight,
                    coalesce(pool_sku.weight_value, pool.ai_max_weight_g, pc.packaging_weight, pc.net_weight) as max_weight_g,
                    pool.published_at_1688 as published_at_1688,
                    coalesce(pool.pushed_at, pc.temu_published_at, pc.collection_time) as pushed_at,
                    coalesce(pool.company_name_snapshot, pc.company_name) as company_name,
                    pc.company_location as company_location,
                    coalesce(pool.shipping_location_snapshot, pc.shipping_location) as shipping_location,
                    pool.id as selection_pool_id,
                    pool.repeat_customer_rate_snapshot as merchant_repeat_customer_rate,
                    pool.service_score_snapshot as merchant_service_score,
                    pool.on_time_delivery_rate_snapshot as merchant_on_time_delivery_rate,
                    pool.shop_positive_rate_snapshot as merchant_shop_positive_rate,
                    pool.power_seller_snapshot as merchant_power_seller,
                    pool.settled_years_text_snapshot as merchant_settled_years,
                    pool.main_business_snapshot as merchant_main_business,
                    row_number() over (
                        partition by lower(btrim(pcs.sku_id))
                        order by coalesce(pool.pushed_at, pc.temu_published_at, pc.updated_at, pc.created_at) desc, pcs.id desc
                    ) as rn
                from product_collection_sku pcs
                join product_collection pc on pc.id = pcs.spu_id
                left join alibaba_1688_selection_pools pool on (
                    pool.pushed_product_collection_id = pc.id
                    or (pc.alibaba_product_id is not null and pc.alibaba_product_id <> '' and pool.offer_id = pc.alibaba_product_id)
                    or (pc.product_id is not null and pc.product_id <> '' and pool.offer_id = pc.product_id)
                )
                left join alibaba_1688_selection_pool_skus pool_sku on pool_sku.pool_id = pool.id
                    and lower(btrim(pool_sku.source_sku_id)) = lower(btrim(pcs.sku_id))
                where pc.deleted = false
                  and pcs.price is not null
                  and pcs.sku_id is not null
                  and btrim(pcs.sku_id) <> ''
                  and lower(btrim(pcs.sku_id)) in (:extCodes)
            )
            select *
            from ranked_matches
            where rn = 1
            """;

    private final TemuGoodsRepository goodsRepository;
    private final TemuPriceReviewOrderRepository reviewOrderRepository;
    private final TemuPriceReviewSkuRepository reviewSkuRepository;
    private final TemuGoodsSkuRepository goodsSkuRepository;
    private final TemuGoodsSkuSpecRepository goodsSkuSpecRepository;
    private final TemuGoodsSkuPriceRepository goodsSkuPriceRepository;
    private final TemuShopSkuPurchasePriceRepository purchasePriceRepository;
    private final TemuOpenApiCredentialService credentialService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public TemuPriceReviewService(TemuGoodsRepository goodsRepository,
                                  TemuPriceReviewOrderRepository reviewOrderRepository,
                                  TemuPriceReviewSkuRepository reviewSkuRepository,
                                  TemuGoodsSkuRepository goodsSkuRepository,
                                  TemuGoodsSkuSpecRepository goodsSkuSpecRepository,
                                  TemuGoodsSkuPriceRepository goodsSkuPriceRepository,
                                  TemuShopSkuPurchasePriceRepository purchasePriceRepository,
                                  TemuOpenApiCredentialService credentialService,
                                  NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.goodsRepository = goodsRepository;
        this.reviewOrderRepository = reviewOrderRepository;
        this.reviewSkuRepository = reviewSkuRepository;
        this.goodsSkuRepository = goodsSkuRepository;
        this.goodsSkuSpecRepository = goodsSkuSpecRepository;
        this.goodsSkuPriceRepository = goodsSkuPriceRepository;
        this.purchasePriceRepository = purchasePriceRepository;
        this.credentialService = credentialService;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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
                CollectedPriceMatch collectedPriceMatch = skuContext.collectedPriceMatchByExtCode().get(normalizeExtCode(goodsSku.getExtCode()));
                if (collectedPriceMatch != null) {
                    si.setCollectedPrice(collectedPriceMatch.collectedPrice());
                    si.setCollectedPriceSource(collectedPriceMatch.sourcePlatform());
                    applyCollectedPriceMatch(si, collectedPriceMatch);
                }
            }
            si.setPurchasePrice(skuContext.purchasePriceByProductSkuId().get(sku.getProductSkuId()));
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
                .filter(Objects::nonNull)
                .filter(sku -> sku.getProductSkuId() != null)
                .collect(Collectors.toMap(TemuGoodsSku::getProductSkuId, sku -> sku, (left, right) -> left, LinkedHashMap::new));

        List<Long> goodsIds = goodsSkuMap.values().stream()
                .map(TemuGoodsSku::getGoodsId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> goodsMainImageMap = new LinkedHashMap<>();
        if (!goodsIds.isEmpty()) {
            for (TemuGoods goods : goodsRepository.findAllById(new ArrayList<>(goodsIds))) {
                if (goods == null || goods.getId() == null) {
                    continue;
                }
                String mainImageUrl = goods.getMainImageUrl();
                if (mainImageUrl != null && !mainImageUrl.isBlank()) {
                    goodsMainImageMap.putIfAbsent(goods.getId(), mainImageUrl);
                }
            }
        }

        Map<Long, String> imageUrlMap = new LinkedHashMap<>();
        for (TemuGoodsSku sku : goodsSkuMap.values()) {
            if (sku == null || sku.getProductSkuId() == null) {
                continue;
            }
            String resolvedImageUrl = resolveSkuImageUrl(sku, goodsMainImageMap.get(sku.getGoodsId()));
            if (resolvedImageUrl != null && !resolvedImageUrl.isBlank()) {
                imageUrlMap.putIfAbsent(sku.getProductSkuId(), resolvedImageUrl);
            }
        }

        List<Long> skuIds = goodsSkuMap.values().stream()
                .map(TemuGoodsSku::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, String> specInfoMap = goodsSkuSpecRepository.findBySkuIdIn(skuIds)
                .stream()
                .filter(Objects::nonNull)
                .filter(spec -> spec.getSkuId() != null)
                .collect(Collectors.groupingBy(
                        TemuGoodsSkuSpec::getSkuId,
                        LinkedHashMap::new,
                        Collectors.mapping(spec -> spec.getParentSpecName() + ": " + spec.getSpecName(), Collectors.joining(" / "))));

        Map<Long, Integer> currentSupplyPriceMap = new LinkedHashMap<>();
        for (TemuGoodsSkuPrice price : goodsSkuPriceRepository.findByShopIdAndProductSkuIdIn(shopId, productSkuIds)) {
            if (price == null || price.getProductSkuId() == null || price.getSupplierPrice() == null) {
                continue;
            }
            currentSupplyPriceMap.putIfAbsent(price.getProductSkuId(), price.getSupplierPrice());
        }

        Map<Long, Integer> purchasePriceMap = purchasePriceRepository.findByShopIdAndProductSkuIdIn(shopId, productSkuIds)
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getProductSkuId() != null && item.getPurchasePrice() != null)
                .collect(Collectors.toMap(item -> item.getProductSkuId(), item -> item.getPurchasePrice(), (left, right) -> left, LinkedHashMap::new));

        Map<String, CollectedPriceMatch> collectedPriceMatchByExtCode = loadCollectedPriceMatchByExtCode(
                goodsSkuMap.values().stream()
                        .map(TemuGoodsSku::getExtCode)
                        .filter(Objects::nonNull)
                        .toList());

        return new ReviewSkuContext(goodsSkuMap, specInfoMap, currentSupplyPriceMap, imageUrlMap, purchasePriceMap, collectedPriceMatchByExtCode);
    }

    private Map<String, CollectedPriceMatch> loadCollectedPriceMatchByExtCode(Collection<String> extCodes) {
        List<String> normalizedExtCodes = extCodes == null ? List.of() : extCodes.stream()
                .map(this::normalizeExtCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedExtCodes.isEmpty()) {
            return Map.of();
        }

        Map<String, CollectedPriceMatch> result = new LinkedHashMap<>();
        MapSqlParameterSource params = new MapSqlParameterSource("extCodes", normalizedExtCodes);
        namedParameterJdbcTemplate.query(MATCH_COLLECTION_PRICE_SQL, params, rs -> {
            String skuKey = normalizeExtCode(rs.getString("sku_key"));
            Integer collectedPrice = toCollectedPrice(rs.getObject("collected_price"));
            if (skuKey == null || collectedPrice == null) {
                return;
            }
            result.putIfAbsent(skuKey, new CollectedPriceMatch(
                    skuKey,
                    collectedPrice,
                    rs.getString("source_platform"),
                    toLong(rs.getObject("product_collection_id")),
                    rs.getString("product_id"),
                    rs.getString("product_name"),
                    rs.getString("product_url"),
                    rs.getString("collected_sku_id"),
                    rs.getString("collected_sku_spec"),
                    toBigDecimal(rs.getObject("base_freight")),
                    toBigDecimal(rs.getObject("max_weight_g")),
                    rs.getTimestamp("published_at_1688") == null ? null : rs.getTimestamp("published_at_1688").toLocalDateTime(),
                    rs.getTimestamp("pushed_at") == null ? null : rs.getTimestamp("pushed_at").toLocalDateTime(),
                    rs.getString("company_name"),
                    rs.getString("company_location"),
                    rs.getString("shipping_location"),
                    toLong(rs.getObject("selection_pool_id")),
                    toPlainString(rs.getObject("merchant_repeat_customer_rate")),
                    toPlainString(rs.getObject("merchant_service_score")),
                    toPlainString(rs.getObject("merchant_on_time_delivery_rate")),
                    toPlainString(rs.getObject("merchant_shop_positive_rate")),
                    rs.getObject("merchant_power_seller") == null ? null : rs.getBoolean("merchant_power_seller"),
                    rs.getString("merchant_settled_years"),
                    rs.getString("merchant_main_business")
            ));
        });
        return result;
    }

    private void applyCollectedPriceMatch(PriceReviewDTO.ReviewSkuItem si, CollectedPriceMatch match) {
        si.setCollectedProductCollectionId(match.productCollectionId());
        si.setCollectedProductId(match.productId());
        si.setCollectedProductName(match.productName());
        si.setCollectedProductUrl(match.productUrl());
        si.setCollectedSkuId(match.collectedSkuId());
        si.setCollectedSkuSpec(match.collectedSkuSpec());
        si.setCollectedBaseFreight(match.baseFreight());
        si.setCollectedMaxWeightG(match.maxWeightG());
        si.setCollectedPublishedAt1688(match.publishedAt1688());
        si.setCollectedPushedAt(match.pushedAt());
        si.setCollectedCompanyName(match.companyName());
        si.setCollectedCompanyLocation(match.companyLocation());
        si.setCollectedShippingLocation(match.shippingLocation());
        si.setCollectedSelectionPoolId(match.selectionPoolId());
        si.setCollectedMerchantRepeatCustomerRate(match.merchantRepeatCustomerRate());
        si.setCollectedMerchantServiceScore(match.merchantServiceScore());
        si.setCollectedMerchantOnTimeDeliveryRate(match.merchantOnTimeDeliveryRate());
        si.setCollectedMerchantShopPositiveRate(match.merchantShopPositiveRate());
        si.setCollectedMerchantPowerSeller(match.merchantPowerSeller());
        si.setCollectedMerchantSettledYears(match.merchantSettledYears());
        si.setCollectedMerchantMainBusiness(match.merchantMainBusiness());
    }

    private Integer toCollectedPrice(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.intValue();
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).intValue();
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toPlainString(Object value) {
        BigDecimal decimal = toBigDecimal(value);
        return decimal == null ? null : decimal.stripTrailingZeros().toPlainString();
    }

    private String normalizeExtCode(String extCode) {
        if (extCode == null) {
            return null;
        }
        String trimmed = extCode.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
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
                                    Map<Long, String> imageUrlByProductSkuId,
                                    Map<Long, Integer> purchasePriceByProductSkuId,
                                    Map<String, CollectedPriceMatch> collectedPriceMatchByExtCode) {
        private static ReviewSkuContext empty() {
            return new ReviewSkuContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private record CollectedPriceMatch(String extCode,
                                       Integer collectedPrice,
                                       String sourcePlatform,
                                       Long productCollectionId,
                                       String productId,
                                       String productName,
                                       String productUrl,
                                       String collectedSkuId,
                                       String collectedSkuSpec,
                                       BigDecimal baseFreight,
                                       BigDecimal maxWeightG,
                                       LocalDateTime publishedAt1688,
                                       LocalDateTime pushedAt,
                                       String companyName,
                                       String companyLocation,
                                       String shippingLocation,
                                       Long selectionPoolId,
                                       String merchantRepeatCustomerRate,
                                       String merchantServiceScore,
                                       String merchantOnTimeDeliveryRate,
                                       String merchantShopPositiveRate,
                                       Boolean merchantPowerSeller,
                                       String merchantSettledYears,
                                       String merchantMainBusiness) {
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
        if ("REJECT".equals(action)) {
            String rejectValidationMessage = validateRejectRequest(request);
            if (rejectValidationMessage != null) {
                return Map.of("success", false, "message", rejectValidationMessage);
            }
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
            int autoCompletedCount = 0;
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
                    } else if (shouldAutoCompleteRejectedOrder(action, result)) {
                        markLocalCompleted(order);
                        autoCompletedCount++;
                        log.warn("核价单拒绝失败后已自动标记本地完成, shopId={}, localOrderId={}, orderId={}, action={}, apiType={}, params={}, raw={}",
                                shopId,
                                order.getId(),
                                order.getOrderId(),
                                action,
                                apiType,
                                summarizeReviewParams(params),
                                truncate(result == null ? null : result.raw, 1500));
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
            resultMap.put("autoCompletedCount", autoCompletedCount);
            resultMap.put("failCount", failCount);
            if (!errors.isEmpty()) {
                resultMap.put("errors", errors);
                resultMap.put("message", buildBatchReviewMessage(successCount, autoCompletedCount, failCount, errors));
            } else {
                resultMap.put("message", buildBatchReviewSuccessMessage(successCount, autoCompletedCount));
            }
            return resultMap;
        } catch (Exception e) {
            log.error("批量审批核价单失败", e);
            return Map.of("success", false, "message", "API调用失败: " + defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    @Transactional
    public Map<String, Object> batchLocalComplete(PriceReviewDTO.BatchLocalCompleteRequest request) {
        String shopId = request == null ? null : request.getShopId();
        List<Long> orderIds = request == null ? null : request.getOrderIds();
        if (shopId == null || shopId.isBlank()) {
            return Map.of("success", false, "message", "shopId不能为空");
        }
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of("success", false, "message", "请选择至少一条核价单");
        }

        List<TemuPriceReviewOrder> orders = reviewOrderRepository.findByShopIdAndIdIn(shopId, orderIds);
        if (orders.isEmpty()) {
            return Map.of("success", false, "message", "未找到指定的核价单");
        }

        int updatedCount = 0;
        int skippedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (TemuPriceReviewOrder order : orders) {
            String normalizedAction = order.getReviewAction() == null ? null : order.getReviewAction().trim().toUpperCase(Locale.ROOT);
            boolean isPending = normalizedAction == null || normalizedAction.isBlank() || "PENDING".equals(normalizedAction);
            if (!isPending) {
                skippedCount++;
                continue;
            }
            order.setReviewAction("COMPLETED");
            order.setReviewAt(now);
            updatedCount++;
        }
        if (updatedCount > 0) {
            reviewOrderRepository.saveAll(orders);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("total", orders.size());
        result.put("updatedCount", updatedCount);
        result.put("skippedCount", skippedCount);
        if (updatedCount == 0) {
            result.put("message", skippedCount > 0 ? "选中的核价单已存在本地处理状态，无需重复标记" : "没有可标记的核价单");
        } else if (skippedCount > 0) {
            result.put("message", "已标记完成 " + updatedCount + " 条，跳过 " + skippedCount + " 条已有处理状态的数据");
        } else {
            result.put("message", "已标记完成 " + updatedCount + " 条");
        }
        return result;
    }

    private String buildBatchReviewMessage(int successCount, int autoCompletedCount, int failCount, List<String> errors) {
        StringBuilder builder = new StringBuilder();
        if (successCount > 0) {
            builder.append("成功 ").append(successCount).append(" 条");
        }
        if (autoCompletedCount > 0) {
            if (builder.length() > 0) {
                builder.append("，");
            }
            builder.append("自动标记已处理 ").append(autoCompletedCount).append(" 条");
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

    private String buildBatchReviewSuccessMessage(int successCount, int autoCompletedCount) {
        StringBuilder builder = new StringBuilder("批量核价完成");
        if (successCount > 0 || autoCompletedCount > 0) {
            builder.append("，");
        }
        if (successCount > 0) {
            builder.append("成功 ").append(successCount).append(" 条");
        }
        if (autoCompletedCount > 0) {
            if (successCount > 0) {
                builder.append("，");
            }
            builder.append("自动标记已处理 ").append(autoCompletedCount).append(" 条");
        }
        return builder.toString();
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

    private boolean shouldAutoCompleteRejectedOrder(String action, TemuOpenApiClient.ApiResult result) {
        if (!"REJECT".equals(action) || result == null || result.success) {
            return false;
        }
        Map<String, Object> rawMap = parseRawResponse(result.raw);
        Long errorCode = toLong(rawMap.get("errorCode"));
        return Objects.equals(errorCode, 400000010L);
    }

    private void markLocalCompleted(TemuPriceReviewOrder order) {
        order.setReviewAction("COMPLETED");
        order.setReviewAt(LocalDateTime.now());
        reviewOrderRepository.save(order);
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

    private String validateRejectRequest(PriceReviewDTO.BatchReviewRequest request) {
        if (request == null || request.getBargainReasonList() == null || request.getBargainReasonList().isEmpty()) {
            return null;
        }
        for (PriceReviewDTO.BargainReasonItem item : request.getBargainReasonList()) {
            if (item == null) {
                continue;
            }
            boolean hasExternalLinks = item.getExternalLinkList() != null
                    && item.getExternalLinkList().stream().anyMatch(link -> link != null && !link.isBlank());
            boolean hasValidComponent = item.getComponentList() != null
                    && item.getComponentList().stream().anyMatch(this::isValidRejectReasonComponent);
            boolean hasInvalidType = item.getComponentList() != null
                    && item.getComponentList().stream().filter(Objects::nonNull).anyMatch(component ->
                    component.getReason() != null
                            && !component.getReason().isBlank()
                            && (component.getType() == null || component.getType() < 0 || component.getType() > 8));
            if (hasInvalidType) {
                return "拒绝原因类型仅支持 0-8";
            }
            if (hasExternalLinks && !hasValidComponent) {
                return "填写外部链接时，请至少填写一条拒绝原因";
            }
        }
        return null;
    }

    private boolean isValidRejectReasonComponent(PriceReviewDTO.RejectReasonComponent component) {
        return component != null
                && component.getReason() != null
                && !component.getReason().isBlank()
                && component.getType() != null
                && component.getType() >= 0
                && component.getType() <= 8;
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
                    if (!isValidRejectReasonComponent(component)) {
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

            if (componentList.isEmpty()) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("componentList", componentList);
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
