package com.tminos.productscene.sync.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tminos.productscene.repository.TemuShopRepository;
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
import com.tminos.productscene.sync.repository.TemuShopSkuPurchasePriceRepository;
import com.tminos.temu.upin.sdk.v2.client.TemuOpenApiClient;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TemuPriceAdjustService {

    private static final Logger log = LoggerFactory.getLogger(TemuPriceAdjustService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int PRICE_ADJUST_QUERY_PAGE_SIZE = 100;
    private static final String SALES_QUANTITY_SQL = """
            select o.matched_temu_sku_id as product_sku_id, coalesce(sum(o.quantity), 0) as quantity
            from temu_orders o
            where o.shop_record_id = :shopRecordId
              and o.matched_temu_sku_id in (:skuIds)
              and (o.order_status is null or o.order_status <> 3)
            group by o.matched_temu_sku_id
            """;
    private static final String AFTERSALE_QUANTITY_SQL = """
            select o.matched_temu_sku_id as product_sku_id, coalesce(sum(o.quantity), 0) as quantity
            from temu_orders o
            where o.shop_record_id = :shopRecordId
              and o.matched_temu_sku_id in (:skuIds)
              and (o.order_status is null or o.order_status <> 3)
              and exists (
                  select 1
                  from temu_order_aftersales a
                  where a.shop_record_id = o.shop_record_id
                    and a.parent_order_sn = o.parent_order_sn
                    and a.parent_after_sales_status = 5
              )
            group by o.matched_temu_sku_id
            """;
    private static final String SIGNED_QUANTITY_SQL = """
            select o.matched_temu_sku_id as product_sku_id, coalesce(sum(o.quantity), 0) as quantity
            from temu_orders o
            where o.shop_record_id = :shopRecordId
              and o.matched_temu_sku_id in (:skuIds)
              and o.order_status in (5, 51)
            group by o.matched_temu_sku_id
            """;
    private static final String LATEST_SINGLE_ITEM_FIRST_LEG_FEE_SQL = """
            with latest_logistics as (
                select distinct on (l.shop_record_id, l.parent_order_sn)
                    l.shop_record_id,
                    l.parent_order_sn,
                    l.first_leg_logistics_fee,
                    l.updated_at,
                    l.id
                from temu_order_logistics l
                where l.shop_record_id = :shopRecordId
                order by l.shop_record_id, l.parent_order_sn, l.updated_at desc, l.id desc
            ),
            qualified_orders as (
                select
                    o.matched_temu_sku_id as product_sku_id,
                    logistics.first_leg_logistics_fee as first_leg_logistics_fee,
                    row_number() over (
                        partition by o.matched_temu_sku_id
                        order by coalesce(o.order_time_ms, o.update_time_ms, 0) desc, o.id desc
                    ) as rn
                from temu_orders o
                join latest_logistics logistics
                  on logistics.shop_record_id = o.shop_record_id
                 and logistics.parent_order_sn = o.parent_order_sn
                where o.shop_record_id = :shopRecordId
                  and o.matched_temu_sku_id in (:skuIds)
                  and o.quantity = 1
                  and logistics.first_leg_logistics_fee is not null
                  and not exists (
                      select 1
                      from temu_orders sibling
                      where sibling.shop_record_id = o.shop_record_id
                        and sibling.parent_order_sn = o.parent_order_sn
                        and sibling.id <> o.id
                  )
            )
            select product_sku_id, first_leg_logistics_fee
            from qualified_orders
            where rn = 1
            """;
    private static final String LATEST_REFRESHABLE_LOGISTICS_ORDER_SQL = """
            with qualified_orders as (
                select
                    o.id as order_id,
                    o.matched_temu_sku_id as product_sku_id,
                    row_number() over (
                        partition by o.matched_temu_sku_id
                        order by
                            case
                                when o.order_status in (5, 51) then 0
                                when o.order_status in (4, 41, 2) then 1
                                else 2
                            end,
                            coalesce(o.order_time_ms, o.update_time_ms, 0) desc,
                            o.id desc
                    ) as rn
                from temu_orders o
                where o.shop_record_id = :shopRecordId
                  and o.matched_temu_sku_id in (:skuIds)
                  and o.quantity = 1
                  and not exists (
                      select 1
                      from temu_orders sibling
                      where sibling.shop_record_id = o.shop_record_id
                        and sibling.parent_order_sn = o.parent_order_sn
                        and sibling.id <> o.id
                  )
            )
            select product_sku_id, order_id
            from qualified_orders
            where rn = 1
            """;

    private final TemuGoodsRepository goodsRepository;
    private final TemuGoodsSkuRepository goodsSkuRepository;
    private final TemuGoodsSkuSpecRepository goodsSkuSpecRepository;
    private final TemuGoodsSkuPriceRepository goodsSkuPriceRepository;
    private final TemuPriceAdjustOrderRepository adjustOrderRepository;
    private final TemuPriceAdjustSkuRepository adjustSkuRepository;
    private final TemuShopSkuPurchasePriceRepository purchasePriceRepository;
    private final TemuShopRepository shopRepository;
    private final TemuOpenApiCredentialService credentialService;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public TemuPriceAdjustService(TemuGoodsRepository goodsRepository,
                                  TemuGoodsSkuRepository goodsSkuRepository,
                                  TemuGoodsSkuSpecRepository goodsSkuSpecRepository,
                                  TemuGoodsSkuPriceRepository goodsSkuPriceRepository,
                                  TemuPriceAdjustOrderRepository adjustOrderRepository,
                                  TemuPriceAdjustSkuRepository adjustSkuRepository,
                                  TemuShopSkuPurchasePriceRepository purchasePriceRepository,
                                  TemuShopRepository shopRepository,
                                  TemuOpenApiCredentialService credentialService,
                                  NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.goodsRepository = goodsRepository;
        this.goodsSkuRepository = goodsSkuRepository;
        this.goodsSkuSpecRepository = goodsSkuSpecRepository;
        this.goodsSkuPriceRepository = goodsSkuPriceRepository;
        this.adjustOrderRepository = adjustOrderRepository;
        this.adjustSkuRepository = adjustSkuRepository;
        this.purchasePriceRepository = purchasePriceRepository;
        this.shopRepository = shopRepository;
        this.credentialService = credentialService;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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
            si.setPurchasePrice(skuContext.purchasePriceByProductSkuId().get(sku.getProductSkuId()));
            si.setSalesQuantity(skuContext.salesQuantityByProductSkuId().getOrDefault(sku.getProductSkuId(), 0L));
            si.setAftersaleQuantity(skuContext.aftersaleQuantityByProductSkuId().getOrDefault(sku.getProductSkuId(), 0L));
            si.setSignedQuantity(skuContext.signedQuantityByProductSkuId().getOrDefault(sku.getProductSkuId(), 0L));
            si.setFirstLegLogisticsFee(skuContext.firstLegLogisticsFeeByProductSkuId().get(sku.getProductSkuId()));
            si.setLogisticsRefreshOrderId(skuContext.logisticsRefreshOrderIdByProductSkuId().get(sku.getProductSkuId()));
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

        Map<Long, Integer> purchasePriceMap = purchasePriceRepository.findByShopIdAndProductSkuIdIn(shopId, productSkuIds)
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getProductSkuId() != null && item.getPurchasePrice() != null)
                .collect(Collectors.toMap(item -> item.getProductSkuId(), item -> item.getPurchasePrice(), (left, right) -> left, LinkedHashMap::new));

        AdjustSkuOrderStats orderStats = loadAdjustSkuOrderStats(shopId, productSkuIds);

        return new AdjustSkuContext(
                goodsSkuMap,
                specInfoByProductSkuId,
                currentSupplyPriceMap,
                imageUrlMap,
                purchasePriceMap,
                orderStats.salesQuantityByProductSkuId(),
                orderStats.aftersaleQuantityByProductSkuId(),
                orderStats.signedQuantityByProductSkuId(),
                orderStats.firstLegLogisticsFeeByProductSkuId(),
                orderStats.logisticsRefreshOrderIdByProductSkuId()
        );
    }

    private AdjustSkuOrderStats loadAdjustSkuOrderStats(String shopId, List<Long> productSkuIds) {
        if (!StringUtils.hasText(shopId) || productSkuIds == null || productSkuIds.isEmpty()) {
            return AdjustSkuOrderStats.empty();
        }

        Long shopRecordId = shopRepository.findByShopId(shopId.trim())
                .map(com.tminos.productscene.entity.TemuShop::getId)
                .orElse(null);
        if (shopRecordId == null) {
            return AdjustSkuOrderStats.empty();
        }

        List<String> skuIds = productSkuIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .toList();
        if (skuIds.isEmpty()) {
            return AdjustSkuOrderStats.empty();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("shopRecordId", shopRecordId)
                .addValue("skuIds", skuIds);

        return new AdjustSkuOrderStats(
                loadSkuQuantityMap(SALES_QUANTITY_SQL, params),
                loadSkuQuantityMap(AFTERSALE_QUANTITY_SQL, params),
                loadSkuQuantityMap(SIGNED_QUANTITY_SQL, params),
                loadSkuFeeMap(LATEST_SINGLE_ITEM_FIRST_LEG_FEE_SQL, params),
                loadSkuOrderIdMap(LATEST_REFRESHABLE_LOGISTICS_ORDER_SQL, params)
        );
    }

    private Map<Long, Long> loadSkuQuantityMap(String sql, MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.query(sql, params, rs -> {
            Map<Long, Long> quantityMap = new LinkedHashMap<>();
            while (rs.next()) {
                Long productSkuId = toLong(rs.getString("product_sku_id"));
                if (productSkuId == null) {
                    continue;
                }
                quantityMap.put(productSkuId, rs.getLong("quantity"));
            }
            return quantityMap;
        });
    }

    private Map<Long, BigDecimal> loadSkuFeeMap(String sql, MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.query(sql, params, rs -> {
            Map<Long, BigDecimal> feeMap = new LinkedHashMap<>();
            while (rs.next()) {
                Long productSkuId = toLong(rs.getString("product_sku_id"));
                if (productSkuId == null) {
                    continue;
                }
                feeMap.put(productSkuId, rs.getBigDecimal("first_leg_logistics_fee"));
            }
            return feeMap;
        });
    }

    private Map<Long, Long> loadSkuOrderIdMap(String sql, MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.query(sql, params, rs -> {
            Map<Long, Long> orderIdMap = new LinkedHashMap<>();
            while (rs.next()) {
                Long productSkuId = toLong(rs.getString("product_sku_id"));
                if (productSkuId == null) {
                    continue;
                }
                orderIdMap.put(productSkuId, rs.getLong("order_id"));
            }
            return orderIdMap;
        });
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
                                    Map<Long, String> imageUrlByProductSkuId,
                                    Map<Long, Integer> purchasePriceByProductSkuId,
                                    Map<Long, Long> salesQuantityByProductSkuId,
                                    Map<Long, Long> aftersaleQuantityByProductSkuId,
                                    Map<Long, Long> signedQuantityByProductSkuId,
                                    Map<Long, BigDecimal> firstLegLogisticsFeeByProductSkuId,
                                    Map<Long, Long> logisticsRefreshOrderIdByProductSkuId) {

        private static AdjustSkuContext empty() {
            return new AdjustSkuContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private record AdjustSkuOrderStats(Map<Long, Long> salesQuantityByProductSkuId,
                                       Map<Long, Long> aftersaleQuantityByProductSkuId,
                                       Map<Long, Long> signedQuantityByProductSkuId,
                                       Map<Long, BigDecimal> firstLegLogisticsFeeByProductSkuId,
                                       Map<Long, Long> logisticsRefreshOrderIdByProductSkuId) {

        private static AdjustSkuOrderStats empty() {
            return new AdjustSkuOrderStats(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
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
            Map<String, PriceAdjustOrderRefreshResult> refreshedFailedOrders = refreshFailedOrdersIfNeeded(shopId, client, failedOrders);
            Map<String, String> displayFailedOrders = mergeFailedOrdersWithRefresh(failedOrders, refreshedFailedOrders);

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
                if (!displayFailedOrders.isEmpty()) {
                    resultMap.put("failedOrders", displayFailedOrders);
                }
                if (!refreshedFailedOrders.isEmpty()) {
                    resultMap.put("refreshedFailedOrders", toRefreshResultPayload(refreshedFailedOrders));
                }
                resultMap.put("success", failCount == 0);
                resultMap.put("message", buildBatchReviewMessage(action, successCount, failCount, displayFailedOrders));
            } else {
                resultMap.put("successCount", 0);
                resultMap.put("failCount", submitOrders.size());
                if (!displayFailedOrders.isEmpty()) {
                    resultMap.put("failedOrders", displayFailedOrders);
                    resultMap.put("message", buildBatchReviewMessage(action, 0, submitOrders.size(), displayFailedOrders));
                } else {
                    resultMap.put("message", buildReviewFailureMessage(reviewAttempt));
                }
                if (!refreshedFailedOrders.isEmpty()) {
                    resultMap.put("refreshedFailedOrders", toRefreshResultPayload(refreshedFailedOrders));
                }
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

    private Map<String, PriceAdjustOrderRefreshResult> refreshFailedOrdersIfNeeded(String shopId,
                                                                                   TemuOpenApiClient client,
                                                                                   Map<String, String> failedOrders) {
        if (failedOrders == null || failedOrders.isEmpty()) {
            return Map.of();
        }

        List<String> refreshTargets = failedOrders.entrySet().stream()
                .filter(entry -> shouldRefreshLatestState(entry.getValue()))
                .map(Map.Entry::getKey)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (refreshTargets.isEmpty()) {
            return Map.of();
        }

        try {
            return syncLatestOrdersByPriceOrderSn(shopId, client, refreshTargets);
        } catch (Exception e) {
            log.warn("按调价单号同步最新状态失败, shopId={}, orderSns={}, message={}",
                    shopId,
                    refreshTargets,
                    e.getMessage());
            Map<String, PriceAdjustOrderRefreshResult> fallback = new LinkedHashMap<>();
            for (String orderSn : refreshTargets) {
                fallback.put(orderSn, PriceAdjustOrderRefreshResult.syncFailed(orderSn, "同步最新状态失败: " + defaultIfBlank(e.getMessage(), "未知异常")));
            }
            return fallback;
        }
    }

    private boolean shouldRefreshLatestState(String failureMessage) {
        if (failureMessage == null || failureMessage.isBlank()) {
            return false;
        }
        String normalized = failureMessage.trim();
        return normalized.contains("非待确认状态")
                || normalized.contains("刷新页面重试")
                || normalized.contains("请刷新页面");
    }

    private Map<String, PriceAdjustOrderRefreshResult> syncLatestOrdersByPriceOrderSn(String shopId,
                                                                                       TemuOpenApiClient client,
                                                                                       Collection<String> priceOrderSns) throws Exception {
        List<String> targetOrderSns = priceOrderSns == null
                ? List.of()
                : priceOrderSns.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (targetOrderSns.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("priceOrderSn", targetOrderSns);
        params.put("pageNo", 1);
        params.put("pageSize", Math.min(PRICE_ADJUST_QUERY_PAGE_SIZE, Math.max(targetOrderSns.size(), 20)));

        TemuOpenApiClient.ApiResult apiResult = client.callApiParsed(TemuOpenApiClient.API_PRICE_ADJUST_QUERY, params);
        if (!apiResult.success) {
            throw new IllegalStateException(defaultIfBlank(apiResult.errorMsg, "TEMU调价单查询失败"));
        }

        Map<String, Object> resultMap = apiResult.resultAsMap();
        List<Map<String, Object>> orderList = extractMapList(resultMap, "priceAdjustOrderList", "orderList");
        Map<String, PriceAdjustOrderRefreshResult> refreshedOrders = new LinkedHashMap<>();
        Set<String> notSynced = new LinkedHashSet<>(targetOrderSns);
        if (orderList != null) {
            for (Map<String, Object> rawOrder : orderList) {
                String priceOrderSn = trimToNull(toStr(rawOrder.get("priceOrderSn")));
                if (priceOrderSn == null || !notSynced.contains(priceOrderSn)) {
                    continue;
                }
                refreshedOrders.put(priceOrderSn, syncAdjustOrderFromRemote(shopId, rawOrder));
                notSynced.remove(priceOrderSn);
            }
        }

        for (String priceOrderSn : notSynced) {
            refreshedOrders.put(priceOrderSn, PriceAdjustOrderRefreshResult.notFound(priceOrderSn));
        }
        return refreshedOrders;
    }

    private PriceAdjustOrderRefreshResult syncAdjustOrderFromRemote(String shopId, Map<String, Object> raw) {
        String priceOrderSn = trimToNull(toStr(raw.get("priceOrderSn")));
        if (priceOrderSn == null) {
            throw new IllegalArgumentException("TEMU返回的调价单缺少 priceOrderSn");
        }

        TemuPriceAdjustOrder order = adjustOrderRepository.findByShopIdAndPriceOrderSn(shopId, priceOrderSn)
                .orElse(new TemuPriceAdjustOrder());

        order.setShopId(shopId);
        order.setPriceOrderSn(priceOrderSn);
        order.setSkcId(toLong(raw.get("skcId")));
        order.setProductName(toStr(raw.get("productName")));
        order.setPriceType(toInt(raw.get("priceType")));
        order.setSource(toStr(raw.get("source")));
        order.setAdjustReason(toStr(raw.get("adjustReason")));
        order.setNewSupplyPrice(toStr(raw.get("newSupplyPrice")));
        order.setPriceCurrency(toStr(raw.get("priceCurrency")));
        order.setRejectReason(toStr(raw.get("rejectReason")));
        order.setTrafficLowExpose(Boolean.TRUE.equals(toBool(raw.get("trafficLowExpose"))));
        order.setStatus(toInt(raw.get("status")));
        order.setSiteNamesJson(toJsonString(getFirst(raw, "siteNameList", "siteNames")));
        order.setSyncedAt(LocalDateTime.now());

        adjustOrderRepository.save(order);
        adjustSkuRepository.deleteByAdjustOrderId(order.getId());
        List<TemuPriceAdjustSku> skuRows = buildAdjustSkuRows(order.getId(), raw);
        if (!skuRows.isEmpty()) {
            adjustSkuRepository.saveAll(skuRows);
        }

        return PriceAdjustOrderRefreshResult.synced(priceOrderSn, order.getId(), order.getStatus(), order.getReviewAction());
    }

    private List<TemuPriceAdjustSku> buildAdjustSkuRows(Long adjustOrderId, Map<String, Object> raw) {
        Object skuInfoList = getFirst(raw, "skuInfoList", "skuList");
        if (!(skuInfoList instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }

        List<TemuPriceAdjustSku> rows = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> skuRawMap)) {
                continue;
            }
            Long productSkuId = toLong(skuRawMap.get("productSkuId"));
            if (productSkuId == null) {
                continue;
            }
            Integer price = toInt(skuRawMap.get("price"));
            String spec = toStr(skuRawMap.get("spec"));
            String dedupeKey = productSkuId + "|" + price + "|" + spec;
            if (!seenKeys.add(dedupeKey)) {
                continue;
            }
            rows.add(TemuPriceAdjustSku.builder()
                    .adjustOrderId(adjustOrderId)
                    .productSkuId(productSkuId)
                    .price(price)
                    .spec(spec)
                    .build());
        }
        return rows;
    }

    private Map<String, String> mergeFailedOrdersWithRefresh(Map<String, String> failedOrders,
                                                             Map<String, PriceAdjustOrderRefreshResult> refreshedFailedOrders) {
        if (failedOrders == null || failedOrders.isEmpty()) {
            return Map.of();
        }
        if (refreshedFailedOrders == null || refreshedFailedOrders.isEmpty()) {
            return new LinkedHashMap<>(failedOrders);
        }

        Map<String, String> merged = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : failedOrders.entrySet()) {
            String priceOrderSn = entry.getKey();
            String failureMessage = defaultIfBlank(entry.getValue(), "TEMU接口返回失败");
            PriceAdjustOrderRefreshResult refreshResult = refreshedFailedOrders.get(priceOrderSn);
            if (refreshResult != null && refreshResult.message() != null && !refreshResult.message().isBlank()) {
                merged.put(priceOrderSn, failureMessage + "（" + refreshResult.message() + "）");
            } else {
                merged.put(priceOrderSn, failureMessage);
            }
        }
        return merged;
    }

    private Map<String, Object> toRefreshResultPayload(Map<String, PriceAdjustOrderRefreshResult> refreshedFailedOrders) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, PriceAdjustOrderRefreshResult> entry : refreshedFailedOrders.entrySet()) {
            PriceAdjustOrderRefreshResult value = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("priceOrderSn", value.priceOrderSn());
            item.put("orderId", value.orderId());
            item.put("status", value.status());
            item.put("reviewAction", value.reviewAction());
            item.put("message", value.message());
            payload.put(entry.getKey(), item);
        }
        return payload;
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractMapList(Map<String, Object> resultMap, String... preferredKeys) {
        if (resultMap == null || preferredKeys == null) {
            return null;
        }
        for (String key : preferredKeys) {
            Object val = resultMap.get(key);
            if (val instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                return (List<Map<String, Object>>) list;
            }
        }
        for (Object value : resultMap.values()) {
            if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                return (List<Map<String, Object>>) list;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toStr(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private Boolean toBool(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(obj));
    }

    private Object getFirst(Map<String, Object> raw, String... keys) {
        if (raw == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key != null && raw.containsKey(key)) {
                Object value = raw.get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("序列化调价单字段失败: {}", e.getMessage());
            return null;
        }
    }

    private static String formatAdjustStatus(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        return switch (status) {
            case 0 -> "待核价";
            case 1 -> "待供应商确认";
            case 2 -> "调价成功";
            case 3 -> "调价失败";
            default -> "状态" + status;
        };
    }

    private record PriceAdjustOrderRefreshResult(String priceOrderSn,
                                                 Long orderId,
                                                 Integer status,
                                                 String reviewAction,
                                                 String message) {

        private static PriceAdjustOrderRefreshResult synced(String priceOrderSn,
                                                            Long orderId,
                                                            Integer status,
                                                            String reviewAction) {
            return new PriceAdjustOrderRefreshResult(
                    priceOrderSn,
                    orderId,
                    status,
                    reviewAction,
                    "已同步本地最新状态为" + formatAdjustStatus(status)
            );
        }

        private static PriceAdjustOrderRefreshResult notFound(String priceOrderSn) {
            return new PriceAdjustOrderRefreshResult(priceOrderSn, null, null, null, "已调用TEMU查询，但未查到该调价单");
        }

        private static PriceAdjustOrderRefreshResult syncFailed(String priceOrderSn, String message) {
            return new PriceAdjustOrderRefreshResult(priceOrderSn, null, null, null, message);
        }
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
