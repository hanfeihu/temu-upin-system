package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tminos.productscene.dto.TemuOrderDTO;
import com.tminos.productscene.entity.TemuOrderAftersale;
import com.tminos.productscene.entity.TemuOrder;
import com.tminos.productscene.entity.TemuOrderLogistics;
import com.tminos.productscene.entity.TemuOrderSyncState;
import com.tminos.productscene.entity.TemuOrderSyncType;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuOrderLogisticsRepository;
import com.tminos.productscene.repository.TemuOrderRepository;
import com.tminos.productscene.repository.TemuOrderSyncStateRepository;
import com.tminos.productscene.sync.entity.TemuGoodsSku;
import com.tminos.productscene.sync.entity.TemuGoodsSkuPrice;
import com.tminos.productscene.sync.entity.TemuGoodsSkuSitePrice;
import com.tminos.productscene.sync.entity.TemuGoodsSkuSpec;
import com.tminos.productscene.sync.repository.TemuGoodsSkuRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuPriceRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuSitePriceRepository;
import com.tminos.productscene.sync.repository.TemuGoodsSkuSpecRepository;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TemuOrderService {

    private static final Logger log = LoggerFactory.getLogger(TemuOrderService.class);
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_SYNC_PAGES = 500;
    private static final long DEFAULT_INCREMENTAL_HOURS = 24L;
    private static final long DEFAULT_FULL_SYNC_HOURS = 24L * 30L;
    private static final long CURSOR_OVERLAP_SECONDS = 3600L;

    private final TemuOrderRepository orderRepository;
    private final TemuOrderLogisticsRepository logisticsRepository;
    private final TemuOrderSyncStateRepository syncStateRepository;
    private final TemuGoodsSkuRepository goodsSkuRepository;
    private final TemuGoodsSkuSpecRepository goodsSkuSpecRepository;
    private final TemuGoodsSkuPriceRepository goodsSkuPriceRepository;
    private final TemuGoodsSkuSitePriceRepository goodsSkuSitePriceRepository;
    private final TemuShopService shopService;
    private final TemuOpenApiCredentialService credentialService;
    private final TemuOrderOpenApiService orderOpenApiService;
    private final TemuOrderMatchService orderMatchService;
    private final DianxiaomiPackageService dianxiaomiPackageService;
    private final HaoyuanLogisticsService haoyuanLogisticsService;
    private final ObjectMapper objectMapper;

    public TemuOrderService(TemuOrderRepository orderRepository,
                            TemuOrderLogisticsRepository logisticsRepository,
                            TemuOrderSyncStateRepository syncStateRepository,
                            TemuGoodsSkuRepository goodsSkuRepository,
                            TemuGoodsSkuSpecRepository goodsSkuSpecRepository,
                            TemuGoodsSkuPriceRepository goodsSkuPriceRepository,
                            TemuGoodsSkuSitePriceRepository goodsSkuSitePriceRepository,
                            TemuShopService shopService,
                            TemuOpenApiCredentialService credentialService,
                            TemuOrderOpenApiService orderOpenApiService,
                            TemuOrderMatchService orderMatchService,
                            DianxiaomiPackageService dianxiaomiPackageService,
                            HaoyuanLogisticsService haoyuanLogisticsService,
                            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.logisticsRepository = logisticsRepository;
        this.syncStateRepository = syncStateRepository;
        this.goodsSkuRepository = goodsSkuRepository;
        this.goodsSkuSpecRepository = goodsSkuSpecRepository;
        this.goodsSkuPriceRepository = goodsSkuPriceRepository;
        this.goodsSkuSitePriceRepository = goodsSkuSitePriceRepository;
        this.shopService = shopService;
        this.credentialService = credentialService;
        this.orderOpenApiService = orderOpenApiService;
        this.orderMatchService = orderMatchService;
        this.dianxiaomiPackageService = dianxiaomiPackageService;
        this.haoyuanLogisticsService = haoyuanLogisticsService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<TemuOrderDTO.ListItem> list(Long shopRecordId,
                                            String shopId,
                                            String keyword,
                                            String matchedTemuSkuIdLike,
                                            String cancelState,
                                            String aftersaleState,
                                            Integer orderStatus,
                                            String matchStatus,
                                            Long orderTimeStartMs,
                                            Long orderTimeEndMs,
                                            Long updateTimeStartMs,
                                            Long updateTimeEndMs,
                                            int page,
                                            int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        Page<TemuOrder> rows = orderRepository.findAll(
                buildListSpec(shopRecordId, shopId, keyword, matchedTemuSkuIdLike, cancelState, aftersaleState, orderStatus, matchStatus,
                        orderTimeStartMs, orderTimeEndMs, updateTimeStartMs, updateTimeEndMs),
                PageRequest.of(safePage - 1, safePageSize, Sort.by(
                        Sort.Order.desc("orderTimeMs").nullsLast(),
                        Sort.Order.desc("id")
                )));
        enrichDianxiaomiPackageNumbers(rows.getContent());
        Map<String, TemuOrderLogistics> logisticsMap = loadLatestLogistics(rows.getContent());
        Map<String, String> matchedSkuSpecNameMap = loadMatchedSkuSpecNameMap(rows.getContent());
        Map<String, MatchedSupplyPriceContext> matchedSupplyPriceMap = loadMatchedSupplyPriceMap(rows.getContent());
        Map<SkuStatsKey, SkuAftersaleStats> skuAftersaleStatsMap = loadSkuAftersaleStats(rows.getContent());
        return rows.map(row -> toListItem(
                row,
                logisticsMap.get(logisticsKey(row.getShopRecordId(), row.getParentOrderSn())),
                matchedSkuSpecNameMap,
                matchedSupplyPriceMap,
                skuAftersaleStatsMap
        ));
    }

    @Transactional(readOnly = true)
    public TemuOrderDTO.Detail getDetail(Long id) {
        TemuOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + id));
        enrichDianxiaomiPackageNumbers(List.of(order));
        TemuOrderLogistics logistics = logisticsRepository
                .findFirstByShopRecordIdAndParentOrderSnOrderByUpdatedAtDesc(order.getShopRecordId(), order.getParentOrderSn())
                .orElse(null);
        return toDetail(
                order,
                logistics,
                loadMatchedSkuSpecNameMap(List.of(order)),
                loadMatchedSupplyPriceMap(List.of(order)),
                loadSkuAftersaleStats(List.of(order))
        );
    }

    public List<TemuOrderDTO.SyncResponse> sync(TemuOrderDTO.SyncRequest request) {
        boolean fullSync = request != null && Boolean.TRUE.equals(request.getFullSync());
        Integer hoursBack = request == null ? null : request.getHoursBack();
        List<TemuShop> shops = resolveSyncShops(request == null ? null : request.getShopRecordId());
        List<TemuOrderDTO.SyncResponse> responses = new ArrayList<>();
        for (TemuShop shop : shops) {
            responses.add(syncShop(shop, fullSync, hoursBack));
        }
        return responses;
    }

    @Transactional
    public TemuOrderDTO.LogisticsSnapshot refreshLogistics(Long orderId, TemuOrderDTO.RefreshLogisticsRequest request) {
        TemuOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderId));
        TemuShop shop = shopService.getEnabledShopByIdOrThrow(order.getShopRecordId());
        String providerCode = request == null ? null : trim(request.getProviderCode());
        if (StringUtils.hasText(providerCode)
                && !Objects.equals(providerCode.toUpperCase(), LogisticsProviderConfigService.HAOYUAN_PROVIDER_CODE)) {
            throw new IllegalArgumentException("当前仅支持浩远国际物流刷新");
        }
        String shippingMethodNo = firstNonBlank(
                request == null ? null : request.getShippingMethodNo(),
                order.getDianxiaomiPackageNumber()
        );
        if (!StringUtils.hasText(shippingMethodNo) && StringUtils.hasText(order.getParentOrderSn())) {
            Map<String, String> resolvedPackageNumbers = dianxiaomiPackageService.resolvePackageNumbers(shop, List.of(order.getParentOrderSn()));
            shippingMethodNo = resolvedPackageNumbers.get(trim(order.getParentOrderSn()));
            if (StringUtils.hasText(shippingMethodNo)) {
                order.setDianxiaomiPackageNumber(shippingMethodNo);
            }
        }
        shippingMethodNo = trim(shippingMethodNo);
        if (!StringUtils.hasText(shippingMethodNo)
                && !StringUtils.hasText(request == null ? null : request.getReferenceNo())
                && !StringUtils.hasText(request == null ? null : request.getTrackingNumber())) {
            throw new IllegalStateException("未找到店小秘单号，请先在店铺管理配置店小秘 cookie，或手动填写浩远查询号");
        }
        if (StringUtils.hasText(shippingMethodNo)
                && !Objects.equals(trim(order.getParentOrderSn()), shippingMethodNo)
                && !Objects.equals(trim(order.getDianxiaomiPackageNumber()), shippingMethodNo)) {
            dianxiaomiPackageService.savePackageNumber(shop, order.getParentOrderSn(), shippingMethodNo);
            order.setDianxiaomiPackageNumber(shippingMethodNo);
        }
        TemuOrderLogistics logistics = haoyuanLogisticsService.refresh(
                order.getShopRecordId(),
                order.getShopId(),
                order.getShopName(),
                order.getParentOrderSn(),
                request == null ? null : request.getReferenceNo(),
                shippingMethodNo,
                request == null ? null : request.getTrackingNumber()
        );
        return toLogisticsSnapshot(logistics);
    }

    public int refreshRecentLogistics(Long shopRecordId, Integer hoursBack, Integer limit) {
        TemuShop shop = shopService.getEnabledShopByIdOrThrow(shopRecordId);
        return refreshRecentLogistics(shop, hoursBack, limit);
    }

    private TemuOrderDTO.SyncResponse syncShop(TemuShop shop, boolean fullSync, Integer hoursBack) {
        TemuOrderSyncState state = getOrCreateState(shop, TemuOrderSyncType.ORDER);
        state.setLastAttemptAt(LocalDateTime.now());
        syncStateRepository.save(state);

        try {
            TemuOpenApiCredentials credentials = credentialService.getOrderTemuOpenApiCredentialsByExactShopIdOrThrow(shop.getShopId());
            long nowSec = System.currentTimeMillis() / 1000L;
            long startSec = resolveStartSeconds(state, fullSync, hoursBack, nowSec);
            long maxCursorMs = state.getLastCursorMs() == null ? startSec * 1000L : state.getLastCursorMs();

            int totalCount = 0;
            int createdCount = 0;
            int updatedCount = 0;
            int matchedCount = 0;
            Map<String, Long> touchedParentOrders = new LinkedHashMap<>();

            for (int pageNumber = 1; pageNumber <= MAX_SYNC_PAGES; pageNumber++) {
                JsonNode root = orderOpenApiService.listOrders(credentials, pageNumber, DEFAULT_PAGE_SIZE, null, startSec, nowSec);
                JsonNode result = root.path("result");
                JsonNode pageItems = result.path("pageItems");
                if (!pageItems.isArray() || pageItems.isEmpty()) {
                    break;
                }

                int currentPageOrderCount = 0;
                for (JsonNode pageItem : pageItems) {
                    JsonNode parentOrderMap = pageItem.path("parentOrderMap");
                    JsonNode orderList = pageItem.path("orderList");
                    if (!orderList.isArray()) {
                        continue;
                    }
                    for (JsonNode orderNode : orderList) {
                        UpsertResult upsertResult = upsertOrder(shop, orderNode, parentOrderMap);
                        if (upsertResult == null) {
                            continue;
                        }
                        currentPageOrderCount++;
                        totalCount++;
                        if (upsertResult.created()) {
                            createdCount++;
                        } else {
                            updatedCount++;
                        }
                        if (upsertResult.matched()) {
                            matchedCount++;
                        }
                        maxCursorMs = Math.max(maxCursorMs, upsertResult.cursorMs());
                        rememberTouchedParentOrder(touchedParentOrders, upsertResult.parentOrderSn(), upsertResult.cursorMs());
                    }
                }

                if (currentPageOrderCount == 0 || !hasMore(result, pageItems.size(), pageNumber, DEFAULT_PAGE_SIZE)) {
                    break;
                }
            }

            int packageResolvedCount = touchedParentOrders.isEmpty()
                    ? 0
                    : dianxiaomiPackageService.resolvePackageNumbers(shop, touchedParentOrders.keySet()).size();
            int logisticsRefreshedCount = refreshTouchedLogistics(shop, touchedParentOrders, fullSync ? 30 : 60);
            state.setLastCursorMs(Math.max(maxCursorMs, nowSec * 1000L));
            state.setLastSuccessAt(LocalDateTime.now());
            state.setLastError(null);
            state.setLastSummary("同步订单 " + totalCount + " 条，新增 " + createdCount + "，更新 " + updatedCount + "，店小秘回填 " + packageResolvedCount + "，物流回填 " + logisticsRefreshedCount);
            syncStateRepository.save(state);
            log.info("TEMU 订单同步完成 shopId={}, total={}, created={}, updated={}, matched={}, dianxiaomiResolved={}, logisticsRefreshed={}, fullSync={}, hoursBack={}",
                    shop.getShopId(), totalCount, createdCount, updatedCount, matchedCount, packageResolvedCount, logisticsRefreshedCount, fullSync, hoursBack);

            return TemuOrderDTO.SyncResponse.builder()
                    .shopRecordId(shop.getId())
                    .shopId(shop.getShopId())
                    .shopName(shop.getShopName())
                    .success(true)
                    .totalCount(totalCount)
                    .createdCount(createdCount)
                    .updatedCount(updatedCount)
                    .matchedCount(matchedCount)
                    .logisticsRefreshedCount(logisticsRefreshedCount)
                    .message("同步完成")
                    .build();
        } catch (Exception e) {
            state.setLastError(e.getMessage());
            state.setLastSummary("订单同步失败");
            syncStateRepository.save(state);
            log.warn("TEMU 订单同步失败 shopId={}, fullSync={}, hoursBack={}, error={}",
                    shop.getShopId(), fullSync, hoursBack, e.getMessage());
            return TemuOrderDTO.SyncResponse.builder()
                    .shopRecordId(shop.getId())
                    .shopId(shop.getShopId())
                    .shopName(shop.getShopName())
                    .success(false)
                    .totalCount(0)
                    .createdCount(0)
                    .updatedCount(0)
                    .matchedCount(0)
                    .logisticsRefreshedCount(0)
                    .message(e.getMessage())
                    .build();
        }
    }

    private UpsertResult upsertOrder(TemuShop shop, JsonNode orderNode, JsonNode parentOrderMap) {
        String orderSn = text(orderNode, "orderSn");
        if (!StringUtils.hasText(orderSn)) {
            return null;
        }
        TemuOrder entity = orderRepository.findByShopRecordIdAndOrderSn(shop.getId(), orderSn)
                .orElseGet(TemuOrder::new);
        boolean created = entity.getId() == null;

        entity.setShopRecordId(shop.getId());
        entity.setShopId(trim(shop.getShopId()));
        entity.setShopName(trim(shop.getShopName()));
        entity.setOrderSn(orderSn);
        entity.setParentOrderSn(text(parentOrderMap, "parentOrderSn"));
        entity.setGoodsId(text(orderNode, "goodsId"));
        entity.setGoodsName(text(orderNode, "goodsName"));
        entity.setSpec(text(orderNode, "spec"));
        entity.setThumbUrl(text(orderNode, "thumbUrl"));
        entity.setQuantity(integerValue(orderNode, "quantity"));
        entity.setOrderStatus(integerValue(orderNode, "orderStatus"));
        entity.setParentOrderStatus(integerValue(parentOrderMap, "parentOrderStatus"));
        entity.setOrderPaymentType(text(orderNode, "orderPaymentType"));
        entity.setInventoryDeductionWarehouseId(text(orderNode, "inventoryDeductionWarehouseId"));
        entity.setInventoryDeductionWarehouseName(text(orderNode, "inventoryDeductionWarehouseName"));
        entity.setOrderTimeMs(toMillis(longValue(parentOrderMap, "parentOrderTime")));
        entity.setUpdateTimeMs(toMillis(longValue(parentOrderMap, "updateTime")));
        entity.setEarliestTimeGetShippingDocumentMs(toMillis(longValue(orderNode, "earliestTimeGetShippingDocument")));
        entity.setExpectShipLatestTimeMs(toMillis(longValue(parentOrderMap, "expectShipLatestTime")));
        entity.setRegionId(integerValue(parentOrderMap, "regionId"));
        entity.setSiteId(integerValue(parentOrderMap, "siteId"));

        JsonNode productList = orderNode.path("productList");
        entity.setProductSkusJson(productList.isMissingNode() || productList.isNull() ? null : productList.toString());

        ObjectNode rawNode = objectMapper.createObjectNode();
        rawNode.set("parentOrderMap", parentOrderMap == null ? objectMapper.nullNode() : parentOrderMap);
        rawNode.set("orderNode", orderNode == null ? objectMapper.nullNode() : orderNode);
        entity.setRawJson(rawNode.toString());

        TemuOrderMatchService.MatchResult matchResult = orderMatchService.applyMatch(entity);
        TemuOrder saved = orderRepository.save(entity);
        long cursorMs = firstPositive(saved.getUpdateTimeMs(), saved.getOrderTimeMs(), System.currentTimeMillis());
        return new UpsertResult(created, matchResult.matched(), cursorMs, saved.getParentOrderSn());
    }

    private Specification<TemuOrder> buildListSpec(Long shopRecordId,
                                                   String shopId,
                                                   String keyword,
                                                   String matchedTemuSkuIdLike,
                                                   String cancelState,
                                                   String aftersaleState,
                                                   Integer orderStatus,
                                                   String matchStatus,
                                                   Long orderTimeStartMs,
                                                   Long orderTimeEndMs,
                                                   Long updateTimeStartMs,
                                                   Long updateTimeEndMs) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (shopRecordId != null) {
                predicates.add(cb.equal(root.get("shopRecordId"), shopRecordId));
            }
            if (StringUtils.hasText(shopId)) {
                predicates.add(cb.equal(root.get("shopId"), shopId.trim()));
            }
            String normalizedCancelState = cancelState == null ? null : cancelState.trim().toUpperCase();
            if ("CANCELLED".equals(normalizedCancelState)) {
                predicates.add(cb.equal(root.get("orderStatus"), 3));
            } else if ("ACTIVE".equals(normalizedCancelState)) {
                predicates.add(cb.or(
                        cb.isNull(root.get("orderStatus")),
                        cb.notEqual(root.get("orderStatus"), 3)
                ));
            }
            String normalizedAftersaleState = aftersaleState == null ? null : aftersaleState.trim().toUpperCase();
            if ("REFUNDED".equals(normalizedAftersaleState) || "NOT_REFUNDED".equals(normalizedAftersaleState)) {
                Subquery<Long> refundedAftersaleSubquery = query.subquery(Long.class);
                var aftersaleRoot = refundedAftersaleSubquery.from(TemuOrderAftersale.class);
                refundedAftersaleSubquery.select(cb.literal(1L));
                refundedAftersaleSubquery.where(
                        cb.equal(aftersaleRoot.get("shopRecordId"), root.get("shopRecordId")),
                        cb.equal(aftersaleRoot.get("parentOrderSn"), root.get("parentOrderSn")),
                        cb.equal(aftersaleRoot.get("parentAfterSalesStatus"), 5)
                );
                if ("REFUNDED".equals(normalizedAftersaleState)) {
                    predicates.add(cb.exists(refundedAftersaleSubquery));
                } else {
                    predicates.add(cb.not(cb.exists(refundedAftersaleSubquery)));
                }
            }
            if (orderStatus != null) {
                predicates.add(cb.equal(root.get("orderStatus"), orderStatus));
            }
            if (StringUtils.hasText(matchStatus)) {
                predicates.add(cb.equal(root.get("matchStatus"), matchStatus.trim()));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderSn")), like),
                        cb.like(cb.lower(root.get("parentOrderSn")), like),
                        cb.like(cb.lower(root.get("dianxiaomiPackageNumber")), like),
                        cb.like(cb.lower(root.get("goodsId")), like),
                        cb.like(cb.lower(root.get("goodsName")), like),
                        cb.like(cb.lower(root.get("matchedProductName")), like)
                ));
            }
            if (StringUtils.hasText(matchedTemuSkuIdLike)) {
                String like = "%" + matchedTemuSkuIdLike.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("matchedTemuSkuId")), like));
            }
            if (orderTimeStartMs != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderTimeMs"), orderTimeStartMs));
            }
            if (orderTimeEndMs != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderTimeMs"), orderTimeEndMs));
            }
            if (updateTimeStartMs != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("updateTimeMs"), updateTimeStartMs));
            }
            if (updateTimeEndMs != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("updateTimeMs"), updateTimeEndMs));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<String, TemuOrderLogistics> loadLatestLogistics(List<TemuOrder> orders) {
        Map<String, TemuOrderLogistics> out = new LinkedHashMap<>();
        if (orders == null || orders.isEmpty()) {
            return out;
        }
        Map<Long, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (TemuOrder order : orders) {
            if (order == null || order.getShopRecordId() == null || !StringUtils.hasText(order.getParentOrderSn())) {
                continue;
            }
            grouped.computeIfAbsent(order.getShopRecordId(), key -> new LinkedHashSet<>()).add(order.getParentOrderSn().trim());
        }
        for (Map.Entry<Long, LinkedHashSet<String>> entry : grouped.entrySet()) {
            List<TemuOrderLogistics> rows = logisticsRepository.findByShopRecordIdAndParentOrderSnIn(entry.getKey(), entry.getValue());
            for (TemuOrderLogistics row : rows) {
                String key = logisticsKey(row.getShopRecordId(), row.getParentOrderSn());
                TemuOrderLogistics existing = out.get(key);
                if (existing == null || isAfter(row.getUpdatedAt(), existing.getUpdatedAt())) {
                    out.put(key, row);
                }
            }
        }
        return out;
    }

    private void enrichDianxiaomiPackageNumbers(Collection<TemuOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Map<Long, LinkedHashSet<String>> parentOrdersByShopRecordId = new LinkedHashMap<>();
        for (TemuOrder order : orders) {
            if (order == null
                    || order.getShopRecordId() == null
                    || StringUtils.hasText(order.getDianxiaomiPackageNumber())
                    || !StringUtils.hasText(order.getParentOrderSn())) {
                continue;
            }
            parentOrdersByShopRecordId
                    .computeIfAbsent(order.getShopRecordId(), key -> new LinkedHashSet<>())
                    .add(order.getParentOrderSn().trim());
        }
        if (parentOrdersByShopRecordId.isEmpty()) {
            return;
        }

        Map<String, String> resolvedMap = new LinkedHashMap<>();
        for (Map.Entry<Long, LinkedHashSet<String>> entry : parentOrdersByShopRecordId.entrySet()) {
            try {
                TemuShop shop = shopService.getEnabledShopByIdOrThrow(entry.getKey());
                Map<String, String> shopResolved = dianxiaomiPackageService.resolvePackageNumbers(shop, entry.getValue());
                for (Map.Entry<String, String> resolvedEntry : shopResolved.entrySet()) {
                    resolvedMap.put(packageLookupKey(entry.getKey(), resolvedEntry.getKey()), resolvedEntry.getValue());
                }
            } catch (Exception e) {
                log.debug("补充店小秘单号失败 shopRecordId={}, error={}", entry.getKey(), e.getMessage());
            }
        }
        if (resolvedMap.isEmpty()) {
            return;
        }

        for (TemuOrder order : orders) {
            if (order == null || order.getShopRecordId() == null || !StringUtils.hasText(order.getParentOrderSn())) {
                continue;
            }
            String resolvedPackageNumber = resolvedMap.get(packageLookupKey(order.getShopRecordId(), order.getParentOrderSn()));
            if (StringUtils.hasText(resolvedPackageNumber)) {
                order.setDianxiaomiPackageNumber(resolvedPackageNumber);
            }
        }
    }

    private List<TemuShop> resolveSyncShops(Long shopRecordId) {
        if (shopRecordId != null) {
            return List.of(shopService.getEnabledShopByIdOrThrow(shopRecordId));
        }
        return shopService.listEnabledShops();
    }

    private TemuOrderSyncState getOrCreateState(TemuShop shop, TemuOrderSyncType syncType) {
        return syncStateRepository.findByShopRecordIdAndSyncType(shop.getId(), syncType)
                .orElseGet(() -> {
                    TemuOrderSyncState state = new TemuOrderSyncState();
                    state.setShopRecordId(shop.getId());
                    state.setShopId(shop.getShopId());
                    state.setShopName(shop.getShopName());
                    state.setSyncType(syncType);
                    return state;
                });
    }

    private long resolveStartSeconds(TemuOrderSyncState state, boolean fullSync, Integer hoursBack, long nowSec) {
        if (fullSync) {
            long fullHours = hoursBack != null && hoursBack > 0 ? hoursBack.longValue() : 0L;
            if (fullHours > 0) {
                return Math.max(0L, nowSec - fullHours * 3600L);
            }
            return 0L;
        }
        if (hoursBack != null && hoursBack > 0) {
            return Math.max(0L, nowSec - hoursBack.longValue() * 3600L);
        }
        if (state.getLastCursorMs() != null && state.getLastCursorMs() > 0) {
            long cursorStart = state.getLastCursorMs() / 1000L - CURSOR_OVERLAP_SECONDS;
            long windowStart = nowSec - DEFAULT_INCREMENTAL_HOURS * 3600L;
            return Math.max(0L, Math.min(cursorStart, windowStart));
        }
        return Math.max(0L, nowSec - DEFAULT_INCREMENTAL_HOURS * 3600L);
    }

    private int refreshRecentLogistics(TemuShop shop, Integer hoursBack, Integer limit) {
        int safeLimit = Math.max(limit == null ? 0 : limit, 0);
        if (shop == null || shop.getId() == null || safeLimit <= 0) {
            return 0;
        }
        int safeHours = hoursBack == null || hoursBack <= 0 ? 24 : hoursBack;
        long sinceMs = System.currentTimeMillis() - safeHours * 3600_000L;
        int fetchSize = Math.min(Math.max(safeLimit * 20, 200), 2000);

        Page<TemuOrder> rows = orderRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("shopRecordId"), shop.getId()));
            predicates.add(cb.isNotNull(root.get("parentOrderSn")));
            predicates.add(cb.notEqual(cb.trim(root.get("parentOrderSn")), ""));
            predicates.add(cb.or(
                    cb.greaterThanOrEqualTo(root.get("updateTimeMs"), sinceMs),
                    cb.greaterThanOrEqualTo(root.get("orderTimeMs"), sinceMs)
            ));
            return cb.and(predicates.toArray(new Predicate[0]));
        }, PageRequest.of(0, fetchSize, Sort.by(
                Sort.Order.desc("updateTimeMs"),
                Sort.Order.desc("orderTimeMs"),
                Sort.Order.desc("id")
        )));

        if (rows.isEmpty()) {
            return 0;
        }

        Map<String, TemuOrderLogistics> logisticsMap = loadLatestLogistics(rows.getContent());
        LocalDateTime freshThreshold = LocalDateTime.now().minusHours(12);
        LinkedHashMap<String, Long> targets = new LinkedHashMap<>();
        for (TemuOrder order : rows.getContent()) {
            if (order == null || !StringUtils.hasText(order.getParentOrderSn())) {
                continue;
            }
            String parentOrderSn = order.getParentOrderSn().trim();
            TemuOrderLogistics logistics = logisticsMap.get(logisticsKey(order.getShopRecordId(), parentOrderSn));
            if (logistics != null && logistics.getLastSyncedAt() != null && logistics.getLastSyncedAt().isAfter(freshThreshold)) {
                continue;
            }
            targets.putIfAbsent(parentOrderSn, firstPositive(order.getUpdateTimeMs(), order.getOrderTimeMs(), 0L));
            if (targets.size() >= safeLimit) {
                break;
            }
        }
        return refreshTouchedLogistics(shop, targets, safeLimit);
    }

    private int refreshTouchedLogistics(TemuShop shop, Map<String, Long> touchedParentOrders, int maxCount) {
        if (shop == null || touchedParentOrders == null || touchedParentOrders.isEmpty() || maxCount <= 0) {
            return 0;
        }
        List<String> orderedParentOrderSns = touchedParentOrders.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getKey()))
                .sorted((left, right) -> Long.compare(
                        right.getValue() == null ? 0L : right.getValue(),
                        left.getValue() == null ? 0L : left.getValue()
                ))
                .limit(maxCount)
                .map(Map.Entry::getKey)
                .toList();
        if (orderedParentOrderSns.isEmpty()) {
            return 0;
        }

        Map<String, String> shippingMethodNoMap = dianxiaomiPackageService.resolvePackageNumbers(shop, orderedParentOrderSns);
        int refreshedCount = 0;
        for (String parentOrderSn : orderedParentOrderSns) {
            String shippingMethodNo = trim(shippingMethodNoMap.get(parentOrderSn));
            if (!StringUtils.hasText(shippingMethodNo)) {
                continue;
            }
            try {
                haoyuanLogisticsService.refresh(
                        shop.getId(),
                        shop.getShopId(),
                        shop.getShopName(),
                        parentOrderSn,
                        null,
                        shippingMethodNo,
                        null
                );
                refreshedCount++;
            } catch (Exception e) {
                log.debug("订单同步后刷新头程失败 shopId={}, parentOrderSn={}, error={}",
                        shop.getShopId(), parentOrderSn, e.getMessage());
            }
        }
        return refreshedCount;
    }

    private void rememberTouchedParentOrder(Map<String, Long> touchedParentOrders, String parentOrderSn, long cursorMs) {
        if (touchedParentOrders == null || !StringUtils.hasText(parentOrderSn)) {
            return;
        }
        touchedParentOrders.merge(parentOrderSn.trim(), cursorMs, Math::max);
    }

    private static boolean hasMore(JsonNode result, int currentItemSize, int pageNumber, int pageSize) {
        int total = result.path("total").asInt(-1);
        int currentPage = result.path("pageNumber").asInt(pageNumber);
        if (total >= 0) {
            return (long) currentPage * (long) pageSize < total;
        }
        return currentItemSize >= pageSize;
    }

    private TemuOrderDTO.ListItem toListItem(TemuOrder entity,
                                             TemuOrderLogistics logistics,
                                             Map<String, String> matchedSkuSpecNameMap,
                                             Map<String, MatchedSupplyPriceContext> matchedSupplyPriceMap,
                                             Map<SkuStatsKey, SkuAftersaleStats> skuAftersaleStatsMap) {
        SkuAftersaleStats skuAftersaleStats = resolveSkuAftersaleStats(entity, skuAftersaleStatsMap);
        return TemuOrderDTO.ListItem.builder()
                .id(entity.getId())
                .shopRecordId(entity.getShopRecordId())
                .shopId(entity.getShopId())
                .shopName(entity.getShopName())
                .orderSn(entity.getOrderSn())
                .parentOrderSn(entity.getParentOrderSn())
                .dianxiaomiPackageNumber(entity.getDianxiaomiPackageNumber())
                .goodsId(entity.getGoodsId())
                .goodsName(entity.getGoodsName())
                .spec(entity.getSpec())
                .thumbUrl(entity.getThumbUrl())
                .quantity(entity.getQuantity())
                .orderStatus(entity.getOrderStatus())
                .parentOrderStatus(entity.getParentOrderStatus())
                .orderPaymentType(entity.getOrderPaymentType())
                .orderTimeMs(entity.getOrderTimeMs())
                .updateTimeMs(entity.getUpdateTimeMs())
                .matchedSpuId(entity.getMatchedSpuId())
                .matchedTemuSkuId(entity.getMatchedTemuSkuId())
                .matchedOriginSkuId(entity.getMatchedOriginSkuId())
                .matchedSkuSpecName(resolveMatchedSkuSpecName(entity, matchedSkuSpecNameMap))
                .matchedProductName(entity.getMatchedProductName())
                .matchedSupplyPrice(resolveMatchedSupplyPrice(entity, matchedSupplyPriceMap))
                .salesQuantity(skuAftersaleStats == null ? null : skuAftersaleStats.salesQuantity())
                .aftersaleQuantity(skuAftersaleStats == null ? null : skuAftersaleStats.aftersaleQuantity())
                .aftersaleRate(skuAftersaleStats == null ? null : skuAftersaleStats.aftersaleRate())
                .signedQuantity(skuAftersaleStats == null ? null : skuAftersaleStats.signedQuantity())
                .signedAftersaleQuantity(skuAftersaleStats == null ? null : skuAftersaleStats.signedAftersaleQuantity())
                .matchStatus(entity.getMatchStatus())
                .matchMessage(entity.getMatchMessage())
                .logisticsTrackingNumber(logistics == null ? null : logistics.getTrackingNumber())
                .logisticsTrackStatusName(logistics == null ? null : logistics.getTrackStatusName())
                .firstLegLogisticsFee(logistics == null ? null : logistics.getFirstLegLogisticsFee())
                .chargeWeight(logistics == null ? null : logistics.getChargeWeight())
                .orderFeeDetailJson(logistics == null ? null : logistics.getOrderFeeDetailJson())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private TemuOrderDTO.Detail toDetail(TemuOrder entity,
                                         TemuOrderLogistics logistics,
                                         Map<String, String> matchedSkuSpecNameMap,
                                         Map<String, MatchedSupplyPriceContext> matchedSupplyPriceMap,
                                         Map<SkuStatsKey, SkuAftersaleStats> skuAftersaleStatsMap) {
        SkuAftersaleStats skuAftersaleStats = resolveSkuAftersaleStats(entity, skuAftersaleStatsMap);
        return TemuOrderDTO.Detail.builder()
                .id(entity.getId())
                .shopRecordId(entity.getShopRecordId())
                .shopId(entity.getShopId())
                .shopName(entity.getShopName())
                .orderSn(entity.getOrderSn())
                .parentOrderSn(entity.getParentOrderSn())
                .dianxiaomiPackageNumber(entity.getDianxiaomiPackageNumber())
                .goodsId(entity.getGoodsId())
                .goodsName(entity.getGoodsName())
                .spec(entity.getSpec())
                .thumbUrl(entity.getThumbUrl())
                .quantity(entity.getQuantity())
                .orderStatus(entity.getOrderStatus())
                .parentOrderStatus(entity.getParentOrderStatus())
                .orderPaymentType(entity.getOrderPaymentType())
                .inventoryDeductionWarehouseId(entity.getInventoryDeductionWarehouseId())
                .inventoryDeductionWarehouseName(entity.getInventoryDeductionWarehouseName())
                .orderTimeMs(entity.getOrderTimeMs())
                .updateTimeMs(entity.getUpdateTimeMs())
                .earliestTimeGetShippingDocumentMs(entity.getEarliestTimeGetShippingDocumentMs())
                .expectShipLatestTimeMs(entity.getExpectShipLatestTimeMs())
                .regionId(entity.getRegionId())
                .siteId(entity.getSiteId())
                .productSkusJson(entity.getProductSkusJson())
                .rawJson(entity.getRawJson())
                .matchedSpuId(entity.getMatchedSpuId())
                .matchedTemuSkuId(entity.getMatchedTemuSkuId())
                .matchedOriginSkuId(entity.getMatchedOriginSkuId())
                .matchedSkuSpecName(resolveMatchedSkuSpecName(entity, matchedSkuSpecNameMap))
                .matchedProductName(entity.getMatchedProductName())
                .matchedSupplyPrice(resolveMatchedSupplyPrice(entity, matchedSupplyPriceMap))
                .salesQuantity(skuAftersaleStats == null ? null : skuAftersaleStats.salesQuantity())
                .aftersaleQuantity(skuAftersaleStats == null ? null : skuAftersaleStats.aftersaleQuantity())
                .aftersaleRate(skuAftersaleStats == null ? null : skuAftersaleStats.aftersaleRate())
                .signedQuantity(skuAftersaleStats == null ? null : skuAftersaleStats.signedQuantity())
                .signedAftersaleQuantity(skuAftersaleStats == null ? null : skuAftersaleStats.signedAftersaleQuantity())
                .matchStatus(entity.getMatchStatus())
                .matchMessage(entity.getMatchMessage())
                .logistics(toLogisticsSnapshot(logistics))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private Map<SkuStatsKey, SkuAftersaleStats> loadSkuAftersaleStats(List<TemuOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyMap();
        }

        LinkedHashSet<Long> shopRecordIds = new LinkedHashSet<>();
        LinkedHashSet<String> matchedTemuSkuIds = new LinkedHashSet<>();
        for (TemuOrder order : orders) {
            if (order == null || order.getShopRecordId() == null) {
                continue;
            }
            String matchedTemuSkuId = trim(order.getMatchedTemuSkuId());
            if (!StringUtils.hasText(matchedTemuSkuId)) {
                continue;
            }
            shopRecordIds.add(order.getShopRecordId());
            matchedTemuSkuIds.add(matchedTemuSkuId);
        }
        if (shopRecordIds.isEmpty() || matchedTemuSkuIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<TemuOrderRepository.SkuQuantityAggregate> salesRows =
                orderRepository.aggregateSalesQuantityByMatchedSku(shopRecordIds, matchedTemuSkuIds);
        List<TemuOrderRepository.SkuQuantityAggregate> aftersaleRows =
                orderRepository.aggregateAftersaleQuantityByMatchedSku(shopRecordIds, matchedTemuSkuIds);
        List<TemuOrderRepository.SkuQuantityAggregate> signedRows =
                orderRepository.aggregateSignedQuantityByMatchedSku(shopRecordIds, matchedTemuSkuIds);
        List<TemuOrderRepository.SkuQuantityAggregate> signedAftersaleRows =
                orderRepository.aggregateSignedAftersaleQuantityByMatchedSku(shopRecordIds, matchedTemuSkuIds);

        Map<SkuStatsKey, Long> salesQuantityMap = new LinkedHashMap<>();
        for (TemuOrderRepository.SkuQuantityAggregate row : salesRows) {
            SkuStatsKey key = skuStatsKey(row.getShopRecordId(), row.getMatchedTemuSkuId());
            if (key != null) {
                salesQuantityMap.put(key, row.getQuantity() == null ? 0L : row.getQuantity());
            }
        }

        Map<SkuStatsKey, Long> aftersaleQuantityMap = new LinkedHashMap<>();
        for (TemuOrderRepository.SkuQuantityAggregate row : aftersaleRows) {
            SkuStatsKey key = skuStatsKey(row.getShopRecordId(), row.getMatchedTemuSkuId());
            if (key != null) {
                aftersaleQuantityMap.put(key, row.getQuantity() == null ? 0L : row.getQuantity());
            }
        }

        Map<SkuStatsKey, Long> signedQuantityMap = new LinkedHashMap<>();
        for (TemuOrderRepository.SkuQuantityAggregate row : signedRows) {
            SkuStatsKey key = skuStatsKey(row.getShopRecordId(), row.getMatchedTemuSkuId());
            if (key != null) {
                signedQuantityMap.put(key, row.getQuantity() == null ? 0L : row.getQuantity());
            }
        }

        Map<SkuStatsKey, Long> signedAftersaleQuantityMap = new LinkedHashMap<>();
        for (TemuOrderRepository.SkuQuantityAggregate row : signedAftersaleRows) {
            SkuStatsKey key = skuStatsKey(row.getShopRecordId(), row.getMatchedTemuSkuId());
            if (key != null) {
                signedAftersaleQuantityMap.put(key, row.getQuantity() == null ? 0L : row.getQuantity());
            }
        }

        LinkedHashSet<SkuStatsKey> allKeys = new LinkedHashSet<>();
        allKeys.addAll(salesQuantityMap.keySet());
        allKeys.addAll(aftersaleQuantityMap.keySet());
        allKeys.addAll(signedQuantityMap.keySet());
        allKeys.addAll(signedAftersaleQuantityMap.keySet());

        Map<SkuStatsKey, SkuAftersaleStats> out = new LinkedHashMap<>();
        for (SkuStatsKey key : allKeys) {
            long salesQuantity = salesQuantityMap.getOrDefault(key, 0L);
            long aftersaleQuantity = aftersaleQuantityMap.getOrDefault(key, 0L);
            long signedQuantity = signedQuantityMap.getOrDefault(key, 0L);
            long signedAftersaleQuantity = signedAftersaleQuantityMap.getOrDefault(key, 0L);
            BigDecimal aftersaleRate = salesQuantity <= 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(aftersaleQuantity)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(salesQuantity), 2, RoundingMode.HALF_UP);
            out.put(key, new SkuAftersaleStats(
                    salesQuantity,
                    aftersaleQuantity,
                    aftersaleRate,
                    signedQuantity,
                    signedAftersaleQuantity
            ));
        }
        return out;
    }

    private SkuAftersaleStats resolveSkuAftersaleStats(TemuOrder order,
                                                       Map<SkuStatsKey, SkuAftersaleStats> skuAftersaleStatsMap) {
        SkuStatsKey key = skuStatsKey(order == null ? null : order.getShopRecordId(), order == null ? null : order.getMatchedTemuSkuId());
        if (key == null) {
            return null;
        }
        if (skuAftersaleStatsMap == null || skuAftersaleStatsMap.isEmpty()) {
            return new SkuAftersaleStats(0L, 0L, BigDecimal.ZERO, 0L, 0L);
        }
        return skuAftersaleStatsMap.getOrDefault(key, new SkuAftersaleStats(0L, 0L, BigDecimal.ZERO, 0L, 0L));
    }

    private SkuStatsKey skuStatsKey(Long shopRecordId, String matchedTemuSkuId) {
        String normalizedMatchedTemuSkuId = trim(matchedTemuSkuId);
        if (shopRecordId == null || !StringUtils.hasText(normalizedMatchedTemuSkuId)) {
            return null;
        }
        return new SkuStatsKey(shopRecordId, normalizedMatchedTemuSkuId);
    }

    private Map<String, String> loadMatchedSkuSpecNameMap(List<TemuOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, LinkedHashSet<Long>> skuIdsByShopId = collectMatchedSkuIdsByShopId(orders);
        if (skuIdsByShopId.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, TemuGoodsSku> skuRowMap = new LinkedHashMap<>();
        List<Long> skuRowIds = new ArrayList<>();
        for (Map.Entry<String, LinkedHashSet<Long>> entry : skuIdsByShopId.entrySet()) {
            List<TemuGoodsSku> skuRows = goodsSkuRepository.findByShopIdAndProductSkuIdIn(entry.getKey(), List.copyOf(entry.getValue()));
            for (TemuGoodsSku skuRow : skuRows) {
                if (skuRow == null || skuRow.getId() == null || skuRow.getProductSkuId() == null || !StringUtils.hasText(skuRow.getShopId())) {
                    continue;
                }
                skuRowMap.put(matchedSupplyPriceKey(skuRow.getShopId(), skuRow.getProductSkuId()), skuRow);
                skuRowIds.add(skuRow.getId());
            }
        }
        if (skuRowMap.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<TemuGoodsSkuSpec>> specsBySkuRowId = new LinkedHashMap<>();
        if (!skuRowIds.isEmpty()) {
            List<TemuGoodsSkuSpec> specRows = goodsSkuSpecRepository.findBySkuIdIn(skuRowIds.stream().distinct().toList());
            for (TemuGoodsSkuSpec specRow : specRows) {
                if (specRow == null || specRow.getSkuId() == null) {
                    continue;
                }
                specsBySkuRowId.computeIfAbsent(specRow.getSkuId(), key -> new ArrayList<>()).add(specRow);
            }
        }

        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, TemuGoodsSku> entry : skuRowMap.entrySet()) {
            TemuGoodsSku skuRow = entry.getValue();
            out.put(entry.getKey(), buildMatchedSkuSpecName(specsBySkuRowId.get(skuRow.getId())));
        }
        return out;
    }

    private Map<String, MatchedSupplyPriceContext> loadMatchedSupplyPriceMap(List<TemuOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, LinkedHashSet<Long>> skuIdsByShopId = collectMatchedSkuIdsByShopId(orders);
        if (skuIdsByShopId.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, TemuGoodsSkuPrice> priceRowMap = new LinkedHashMap<>();
        List<Long> skuPriceIds = new ArrayList<>();
        for (Map.Entry<String, LinkedHashSet<Long>> entry : skuIdsByShopId.entrySet()) {
            List<TemuGoodsSkuPrice> rows = goodsSkuPriceRepository.findByShopIdAndProductSkuIdIn(entry.getKey(), List.copyOf(entry.getValue()));
            for (TemuGoodsSkuPrice row : rows) {
                if (row == null || row.getId() == null || row.getProductSkuId() == null || !StringUtils.hasText(row.getShopId())) {
                    continue;
                }
                priceRowMap.put(matchedSupplyPriceKey(row.getShopId(), row.getProductSkuId()), row);
                skuPriceIds.add(row.getId());
            }
        }
        if (priceRowMap.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Map<Integer, TemuGoodsSkuSitePrice>> sitePriceMap = new LinkedHashMap<>();
        if (!skuPriceIds.isEmpty()) {
            List<TemuGoodsSkuSitePrice> sitePriceRows = goodsSkuSitePriceRepository.findBySkuPriceIdIn(skuPriceIds.stream().distinct().toList());
            for (TemuGoodsSkuSitePrice row : sitePriceRows) {
                if (row == null || row.getSkuPriceId() == null || row.getSiteId() == null) {
                    continue;
                }
                sitePriceMap
                        .computeIfAbsent(row.getSkuPriceId(), key -> new LinkedHashMap<>())
                        .put(row.getSiteId(), row);
            }
        }

        Map<String, MatchedSupplyPriceContext> out = new LinkedHashMap<>();
        for (Map.Entry<String, TemuGoodsSkuPrice> entry : priceRowMap.entrySet()) {
            TemuGoodsSkuPrice priceRow = entry.getValue();
            out.put(entry.getKey(), new MatchedSupplyPriceContext(
                    priceRow,
                    sitePriceMap.getOrDefault(priceRow.getId(), Collections.emptyMap())
            ));
        }
        return out;
    }

    private Map<String, LinkedHashSet<Long>> collectMatchedSkuIdsByShopId(List<TemuOrder> orders) {
        Map<String, LinkedHashSet<Long>> skuIdsByShopId = new LinkedHashMap<>();
        for (TemuOrder order : orders) {
            if (order == null || !StringUtils.hasText(order.getShopId())) {
                continue;
            }
            Long productSkuId = toLong(order.getMatchedTemuSkuId());
            if (productSkuId == null) {
                continue;
            }
            skuIdsByShopId.computeIfAbsent(order.getShopId().trim(), key -> new LinkedHashSet<>()).add(productSkuId);
        }
        return skuIdsByShopId;
    }

    private String resolveMatchedSkuSpecName(TemuOrder order, Map<String, String> matchedSkuSpecNameMap) {
        if (order == null) {
            return null;
        }
        if (matchedSkuSpecNameMap != null && !matchedSkuSpecNameMap.isEmpty() && StringUtils.hasText(order.getShopId())) {
            Long productSkuId = toLong(order.getMatchedTemuSkuId());
            if (productSkuId != null) {
                String matched = trim(matchedSkuSpecNameMap.get(matchedSupplyPriceKey(order.getShopId(), productSkuId)));
                if (StringUtils.hasText(matched)) {
                    return matched;
                }
            }
        }
        return trim(order.getSpec());
    }

    private String buildMatchedSkuSpecName(List<TemuGoodsSkuSpec> specRows) {
        if (specRows == null || specRows.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (TemuGoodsSkuSpec specRow : specRows) {
            if (specRow == null) {
                continue;
            }
            String parentSpecName = trim(specRow.getParentSpecName());
            String specName = trim(specRow.getSpecName());
            if (StringUtils.hasText(parentSpecName) && StringUtils.hasText(specName)) {
                parts.add(parentSpecName + "=" + specName);
            } else if (StringUtils.hasText(specName)) {
                parts.add(specName);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" / ", parts);
    }

    private BigDecimal resolveMatchedSupplyPrice(TemuOrder order, Map<String, MatchedSupplyPriceContext> matchedSupplyPriceMap) {
        if (order == null || matchedSupplyPriceMap == null || matchedSupplyPriceMap.isEmpty() || !StringUtils.hasText(order.getShopId())) {
            return null;
        }
        Long productSkuId = toLong(order.getMatchedTemuSkuId());
        if (productSkuId == null) {
            return null;
        }

        MatchedSupplyPriceContext context = matchedSupplyPriceMap.get(matchedSupplyPriceKey(order.getShopId(), productSkuId));
        if (context == null || context.priceRow() == null) {
            return null;
        }

        Integer supplierPrice = null;
        if (order.getSiteId() != null && context.sitePriceMap() != null) {
            TemuGoodsSkuSitePrice sitePrice = context.sitePriceMap().get(order.getSiteId());
            if (sitePrice != null) {
                supplierPrice = sitePrice.getSupplierPrice();
            }
        }
        if (supplierPrice == null) {
            supplierPrice = context.priceRow().getSupplierPrice();
        }
        return supplierPrice == null ? null : BigDecimal.valueOf(supplierPrice.longValue()).movePointLeft(2);
    }

    private TemuOrderDTO.LogisticsSnapshot toLogisticsSnapshot(TemuOrderLogistics logistics) {
        if (logistics == null) {
            return null;
        }
        return TemuOrderDTO.LogisticsSnapshot.builder()
                .providerCode(logistics.getProviderCode())
                .providerName(logistics.getProviderName())
                .referenceNo(logistics.getReferenceNo())
                .shippingMethodNo(logistics.getShippingMethodNo())
                .trackingNumber(logistics.getTrackingNumber())
                .destinationCountry(logistics.getDestinationCountry())
                .trackStatus(logistics.getTrackStatus())
                .trackStatusName(logistics.getTrackStatusName())
                .grossWeight(logistics.getGrossWeight())
                .volumeWeight(logistics.getVolumeWeight())
                .chargeWeight(logistics.getChargeWeight())
                .firstLegLogisticsFee(logistics.getFirstLegLogisticsFee())
                .trackDetailsJson(logistics.getTrackDetailsJson())
                .orderFeeDetailJson(logistics.getOrderFeeDetailJson())
                .orderWeightInfoJson(logistics.getOrderWeightInfoJson())
                .lastSyncedAt(logistics.getLastSyncedAt())
                .build();
    }

    private static String logisticsKey(Long shopRecordId, String parentOrderSn) {
        return (shopRecordId == null ? "null" : String.valueOf(shopRecordId)) + ":" + (parentOrderSn == null ? "" : parentOrderSn.trim());
    }

    private static boolean isAfter(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return false;
        }
        if (right == null) {
            return true;
        }
        return left.isAfter(right);
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : trim(value.asText());
    }

    private static Integer integerValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static Long longValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private static long firstPositive(Long... values) {
        if (values == null) {
            return System.currentTimeMillis();
        }
        for (Long value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return System.currentTimeMillis();
    }

    private static Long toMillis(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp > 9_999_999_999L ? timestamp : timestamp * 1000L;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trim(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private Long toLong(String value) {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String matchedSupplyPriceKey(String shopId, Long productSkuId) {
        return (shopId == null ? "" : shopId.trim()) + ":" + (productSkuId == null ? "" : productSkuId);
    }

    private static String packageLookupKey(Long shopRecordId, String parentOrderSn) {
        return (shopRecordId == null ? "" : String.valueOf(shopRecordId))
                + ":"
                + (parentOrderSn == null ? "" : parentOrderSn.trim());
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private record UpsertResult(boolean created, boolean matched, long cursorMs, String parentOrderSn) {
    }

    private record MatchedSupplyPriceContext(TemuGoodsSkuPrice priceRow,
                                             Map<Integer, TemuGoodsSkuSitePrice> sitePriceMap) {
    }

    private record SkuStatsKey(Long shopRecordId, String matchedTemuSkuId) {
    }

    private record SkuAftersaleStats(long salesQuantity,
                                     long aftersaleQuantity,
                                     BigDecimal aftersaleRate,
                                     long signedQuantity,
                                     long signedAftersaleQuantity) {
    }
}
