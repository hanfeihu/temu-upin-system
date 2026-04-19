package com.tminos.productscene.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tminos.productscene.dto.TemuOrderAftersaleDTO;
import com.tminos.productscene.entity.TemuOrderAftersale;
import com.tminos.productscene.entity.TemuOrderSyncState;
import com.tminos.productscene.entity.TemuOrderSyncType;
import com.tminos.productscene.entity.TemuShop;
import com.tminos.productscene.repository.TemuOrderAftersaleRepository;
import com.tminos.productscene.repository.TemuOrderSyncStateRepository;
import com.tminos.temu.upin.sdk.v2.common.TemuOpenApiCredentials;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class TemuOrderAftersaleService {

    private static final Logger log = LoggerFactory.getLogger(TemuOrderAftersaleService.class);
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_FILTER_PAGE_SIZE = 200;
    private static final int MAX_SYNC_PAGES = 500;
    private static final long DEFAULT_INCREMENTAL_HOURS = 24L;
    private static final long DEFAULT_FULL_SYNC_HOURS = 24L * 90L;
    private static final long CURSOR_OVERLAP_SECONDS = 3600L;
    private static final long FULL_SYNC_WINDOW_SECONDS = 15L * 24L * 3600L;
    private static final long FULL_SYNC_MIN_WINDOW_SECONDS = 24L * 3600L;
    private static final int OPEN_STATUS_REFRESH_BATCH_SIZE = 200;
    private static final List<Integer> OPEN_AFTERSALE_STATUS_GROUPS = List.of(1, 2);
    private static final Map<Integer, String> AFTERSALE_STATUS_GROUP_LABELS = Map.ofEntries(
            Map.entry(1, "待处理"),
            Map.entry(2, "已申请"),
            Map.entry(3, "包裹已寄回"),
            Map.entry(4, "平台审核中"),
            Map.entry(5, "已退款"),
            Map.entry(6, "已拒绝"),
            Map.entry(7, "已取消")
    );
    private static final Map<Integer, String> PARENT_AFTERSALE_STATUS_LABELS = Map.ofEntries(
            Map.entry(1, "买家申请退款，待处理"),
            Map.entry(2, "买家已寄出退货包裹"),
            Map.entry(3, "已收到退货，待商家处理"),
            Map.entry(4, "已发起退款，系统处理中"),
            Map.entry(5, "已退款完成"),
            Map.entry(6, "买家已取消售后"),
            Map.entry(7, "退款申请已拒绝"),
            Map.entry(8, "买家使用商家面单退货，待商家审核并上传面单"),
            Map.entry(9, "已发起退款，系统处理中"),
            Map.entry(10, "买家已申请退货"),
            Map.entry(11, "平台审核中")
    );
    private static final Map<Integer, String> AFTERSALE_TYPE_LABELS = Map.ofEntries(
            Map.entry(1, "仅退款"),
            Map.entry(2, "退货退款")
    );

    private final TemuOrderAftersaleRepository aftersaleRepository;
    private final TemuOrderSyncStateRepository syncStateRepository;
    private final TemuShopService shopService;
    private final TemuOpenApiCredentialService credentialService;
    private final TemuOrderOpenApiService orderOpenApiService;

    public TemuOrderAftersaleService(TemuOrderAftersaleRepository aftersaleRepository,
                                     TemuOrderSyncStateRepository syncStateRepository,
                                     TemuShopService shopService,
                                     TemuOpenApiCredentialService credentialService,
                                     TemuOrderOpenApiService orderOpenApiService) {
        this.aftersaleRepository = aftersaleRepository;
        this.syncStateRepository = syncStateRepository;
        this.shopService = shopService;
        this.credentialService = credentialService;
        this.orderOpenApiService = orderOpenApiService;
    }

    @Transactional(readOnly = true)
    public Page<TemuOrderAftersaleDTO.ListItem> list(Long shopRecordId,
                                                     String shopId,
                                                     String keyword,
                                                     Integer afterSalesStatusGroup,
                                                     Long createAtStartMs,
                                                     Long createAtEndMs,
                                                     Long updateAtStartMs,
                                                     Long updateAtEndMs,
                                                     int page,
                                                     int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        Page<TemuOrderAftersale> rows = aftersaleRepository.findAll(
                buildSpec(shopRecordId, shopId, keyword, afterSalesStatusGroup,
                        createAtStartMs, createAtEndMs, updateAtStartMs, updateAtEndMs),
                PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return rows.map(this::toListItem);
    }

    public List<TemuOrderAftersaleDTO.SyncResponse> sync(TemuOrderAftersaleDTO.SyncRequest request) {
        boolean fullSync = request != null && Boolean.TRUE.equals(request.getFullSync());
        List<TemuShop> shops = request != null && request.getShopRecordId() != null
                ? List.of(shopService.getEnabledShopByIdOrThrow(request.getShopRecordId()))
                : shopService.listEnabledShops();
        Integer hoursBack = request == null ? null : request.getHoursBack();

        List<TemuOrderAftersaleDTO.SyncResponse> out = new ArrayList<>();
        for (TemuShop shop : shops) {
            out.add(syncShop(shop, fullSync, hoursBack));
        }
        return out;
    }

    public TemuOrderAftersaleDTO.SyncResponse refreshOpenStatus(Long shopRecordId) {
        return refreshOpenStatus(shopService.getEnabledShopByIdOrThrow(shopRecordId));
    }

    TemuOrderAftersaleDTO.SyncResponse refreshOpenStatus(TemuShop shop) {
        try {
            TemuOpenApiCredentials credentials = credentialService.getOrderTemuOpenApiCredentialsByExactShopIdOrThrow(shop.getShopId());
            List<TemuOrderAftersale> openAftersales = aftersaleRepository
                    .findByShopRecordIdAndAfterSalesStatusGroupInOrderByUpdateAtMsAscIdAsc(shop.getId(), OPEN_AFTERSALE_STATUS_GROUPS);
            if (openAftersales.isEmpty()) {
                return TemuOrderAftersaleDTO.SyncResponse.builder()
                        .shopRecordId(shop.getId())
                        .shopId(shop.getShopId())
                        .shopName(shop.getShopName())
                        .success(true)
                        .totalCount(0)
                        .createdCount(0)
                        .updatedCount(0)
                        .message("无待刷新售后状态")
                        .build();
            }

            int totalCount = 0;
            int createdCount = 0;
            int updatedCount = 0;
            long nowSec = System.currentTimeMillis() / 1000L;
            for (int start = 0; start < openAftersales.size(); start += OPEN_STATUS_REFRESH_BATCH_SIZE) {
                int end = Math.min(openAftersales.size(), start + OPEN_STATUS_REFRESH_BATCH_SIZE);
                List<String> parentAfterSalesSnList = collectParentAfterSalesSnBatch(openAftersales, start, end);
                if (parentAfterSalesSnList.isEmpty()) {
                    continue;
                }
                long updateAtStartSec = resolveOpenStatusRefreshStartSec(openAftersales, start, end, nowSec);
                WindowSyncResult batchResult = syncAftersalesByParentAfterSalesSnList(
                        credentials,
                        shop,
                        parentAfterSalesSnList,
                        updateAtStartSec,
                        nowSec
                );
                totalCount += batchResult.totalCount();
                createdCount += batchResult.createdCount();
                updatedCount += batchResult.updatedCount();
            }

            return TemuOrderAftersaleDTO.SyncResponse.builder()
                    .shopRecordId(shop.getId())
                    .shopId(shop.getShopId())
                    .shopName(shop.getShopName())
                    .success(true)
                    .totalCount(totalCount)
                    .createdCount(createdCount)
                    .updatedCount(updatedCount)
                    .message("开放售后状态刷新完成")
                    .build();
        } catch (Exception e) {
            log.warn("TEMU 开放售后状态刷新失败 shopId={}, error={}", shop.getShopId(), e.getMessage());
            return TemuOrderAftersaleDTO.SyncResponse.builder()
                    .shopRecordId(shop.getId())
                    .shopId(shop.getShopId())
                    .shopName(shop.getShopName())
                    .success(false)
                    .totalCount(0)
                    .createdCount(0)
                    .updatedCount(0)
                    .message(e.getMessage())
                    .build();
        }
    }

    private TemuOrderAftersaleDTO.SyncResponse syncShop(TemuShop shop, boolean fullSync, Integer hoursBack) {
        TemuOrderSyncState state = getOrCreateState(shop);
        state.setLastAttemptAt(LocalDateTime.now());
        syncStateRepository.save(state);

        try {
            TemuOpenApiCredentials credentials = credentialService.getOrderTemuOpenApiCredentialsByExactShopIdOrThrow(shop.getShopId());
            long nowSec = System.currentTimeMillis() / 1000L;
            long startSec = resolveStartSeconds(state, fullSync, hoursBack, nowSec);
            long maxCursorMs = state.getLastCursorMs() == null ? startSec * 1000L : state.getLastCursorMs();

            WindowSyncResult syncResult = fullSync
                    ? syncFullHistoryAftersales(credentials, shop, startSec, nowSec, maxCursorMs)
                    : syncAftersalesByUpdateWindow(credentials, shop, startSec, nowSec, maxCursorMs);

            int totalCount = syncResult.totalCount();
            int createdCount = syncResult.createdCount();
            int updatedCount = syncResult.updatedCount();
            maxCursorMs = syncResult.maxCursorMs();

            state.setLastCursorMs(Math.max(maxCursorMs, nowSec * 1000L));
            state.setLastSuccessAt(LocalDateTime.now());
            state.setLastError(null);
            state.setLastSummary("同步售后 " + totalCount + " 条，新增 " + createdCount + "，更新 " + updatedCount);
            syncStateRepository.save(state);
            log.info("TEMU 售后同步完成 shopId={}, total={}, created={}, updated={}, fullSync={}, hoursBack={}",
                    shop.getShopId(), totalCount, createdCount, updatedCount, fullSync, hoursBack);

            return TemuOrderAftersaleDTO.SyncResponse.builder()
                    .shopRecordId(shop.getId())
                    .shopId(shop.getShopId())
                    .shopName(shop.getShopName())
                    .success(true)
                    .totalCount(totalCount)
                    .createdCount(createdCount)
                    .updatedCount(updatedCount)
                    .message("同步完成")
                    .build();
        } catch (Exception e) {
            state.setLastError(e.getMessage());
            state.setLastSummary("售后同步失败");
            syncStateRepository.save(state);
            log.warn("TEMU 售后同步失败 shopId={}, fullSync={}, hoursBack={}, error={}",
                    shop.getShopId(), fullSync, hoursBack, e.getMessage());
            return TemuOrderAftersaleDTO.SyncResponse.builder()
                    .shopRecordId(shop.getId())
                    .shopId(shop.getShopId())
                    .shopName(shop.getShopName())
                    .success(false)
                    .totalCount(0)
                    .createdCount(0)
                    .updatedCount(0)
                    .message(e.getMessage())
                    .build();
        }
    }

    private WindowSyncResult syncFullHistoryAftersales(TemuOpenApiCredentials credentials,
                                                       TemuShop shop,
                                                       long startSec,
                                                       long endSec,
                                                       long initialMaxCursorMs) {
        WindowSyncResult totalResult = new WindowSyncResult(0, 0, 0, initialMaxCursorMs);
        long windowStart = startSec;
        while (windowStart <= endSec) {
            long windowEnd = Math.min(endSec, windowStart + FULL_SYNC_WINDOW_SECONDS - 1);
            WindowSyncResult windowResult = syncAftersalesByCreateWindowAdaptive(
                    credentials,
                    shop,
                    windowStart,
                    windowEnd,
                    FULL_SYNC_MIN_WINDOW_SECONDS,
                    totalResult.maxCursorMs()
            );
            totalResult = totalResult.merge(windowResult);
            windowStart = windowEnd + 1;
        }
        return totalResult;
    }

    private WindowSyncResult syncAftersalesByCreateWindowAdaptive(TemuOpenApiCredentials credentials,
                                                                  TemuShop shop,
                                                                  long startSec,
                                                                  long endSec,
                                                                  long minWindowSeconds,
                                                                  long initialMaxCursorMs) {
        try {
            return syncAftersalesWindow(credentials, shop, startSec, endSec, null, null, initialMaxCursorMs);
        } catch (IllegalStateException ex) {
            long currentWindowSeconds = Math.max(0L, endSec - startSec);
            if (!isReadTimeout(ex) || currentWindowSeconds <= minWindowSeconds) {
                throw ex;
            }
            long mid = startSec + currentWindowSeconds / 2L;
            if (mid <= startSec || mid >= endSec) {
                throw ex;
            }
            log.info("TEMU 售后窗口超时，拆分重试 shopId={}, startSec={}, endSec={}, midSec={}",
                    shop.getShopId(), startSec, endSec, mid);
            WindowSyncResult leftResult = syncAftersalesByCreateWindowAdaptive(
                    credentials,
                    shop,
                    startSec,
                    mid,
                    minWindowSeconds,
                    initialMaxCursorMs
            );
            WindowSyncResult rightResult = syncAftersalesByCreateWindowAdaptive(
                    credentials,
                    shop,
                    mid + 1,
                    endSec,
                    minWindowSeconds,
                    leftResult.maxCursorMs()
            );
            return leftResult.merge(rightResult);
        }
    }

    private WindowSyncResult syncAftersalesByUpdateWindow(TemuOpenApiCredentials credentials,
                                                          TemuShop shop,
                                                          long startSec,
                                                          long endSec,
                                                          long initialMaxCursorMs) {
        return syncAftersalesWindow(credentials, shop, null, null, startSec, endSec, initialMaxCursorMs);
    }

    private WindowSyncResult syncAftersalesByParentAfterSalesSnList(TemuOpenApiCredentials credentials,
                                                                    TemuShop shop,
                                                                    List<String> parentAfterSalesSnList,
                                                                    long updateAtStartSec,
                                                                    long updateAtEndSec) {
        int totalCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int safePageSize = Math.min(MAX_FILTER_PAGE_SIZE, Math.max(1, parentAfterSalesSnList.size()));

        for (int pageNo = 1; pageNo <= MAX_SYNC_PAGES; pageNo++) {
            JsonNode root = orderOpenApiService.listParentAftersales(
                    credentials,
                    pageNo,
                    safePageSize,
                    null,
                    null,
                    updateAtStartSec,
                    updateAtEndSec,
                    null,
                    null,
                    parentAfterSalesSnList
            );
            JsonNode result = root.path("result");
            JsonNode data = result.path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }

            for (JsonNode item : data) {
                UpsertResult upsertResult = upsertAftersale(shop, item);
                totalCount++;
                if (upsertResult.created()) {
                    createdCount++;
                } else {
                    updatedCount++;
                }
            }

            if (!hasMore(result, data.size(), pageNo, safePageSize)) {
                break;
            }
        }

        return new WindowSyncResult(totalCount, createdCount, updatedCount, System.currentTimeMillis());
    }

    private WindowSyncResult syncAftersalesWindow(TemuOpenApiCredentials credentials,
                                                  TemuShop shop,
                                                  Long createAtStartSec,
                                                  Long createAtEndSec,
                                                  Long updateAtStartSec,
                                                  Long updateAtEndSec,
                                                  long initialMaxCursorMs) {
        int totalCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        long maxCursorMs = initialMaxCursorMs;

        for (int pageNo = 1; pageNo <= MAX_SYNC_PAGES; pageNo++) {
            JsonNode root = orderOpenApiService.listParentAftersales(
                    credentials,
                    pageNo,
                    DEFAULT_PAGE_SIZE,
                    createAtStartSec,
                    createAtEndSec,
                    updateAtStartSec,
                    updateAtEndSec,
                    null
            );
            JsonNode result = root.path("result");
            JsonNode data = result.path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }

            for (JsonNode item : data) {
                UpsertResult upsertResult = upsertAftersale(shop, item);
                totalCount++;
                if (upsertResult.created()) {
                    createdCount++;
                } else {
                    updatedCount++;
                }
                maxCursorMs = Math.max(maxCursorMs, upsertResult.cursorMs());
            }

            if (!hasMore(result, data.size(), pageNo, DEFAULT_PAGE_SIZE)) {
                break;
            }
        }

        return new WindowSyncResult(totalCount, createdCount, updatedCount, maxCursorMs);
    }

    private UpsertResult upsertAftersale(TemuShop shop, JsonNode item) {
        String parentAfterSalesSn = text(item, "parentAfterSalesSn");
        if (!StringUtils.hasText(parentAfterSalesSn)) {
            throw new IllegalArgumentException("售后记录缺少 parentAfterSalesSn");
        }
        TemuOrderAftersale entity = aftersaleRepository.findByShopRecordIdAndParentAfterSalesSn(shop.getId(), parentAfterSalesSn)
                .orElseGet(TemuOrderAftersale::new);
        boolean created = entity.getId() == null;

        entity.setShopRecordId(shop.getId());
        entity.setShopId(trim(shop.getShopId()));
        entity.setShopName(trim(shop.getShopName()));
        entity.setParentAfterSalesSn(parentAfterSalesSn);
        entity.setParentOrderSn(text(item, "parentOrderSn"));
        Integer afterSalesStatusGroup = integerValue(item, "afterSalesStatusGroup");
        Integer parentAfterSalesStatus = integerValue(item, "parentAfterSalesStatus");
        Integer afterSalesType = integerValue(item, "afterSalesType");
        entity.setAfterSalesStatusGroup(afterSalesStatusGroup);
        entity.setAfterSalesStatusGroupName(resolveAfterSalesStatusGroupName(afterSalesStatusGroup, text(item, "afterSalesStatusGroupName")));
        entity.setParentAfterSalesStatus(parentAfterSalesStatus);
        entity.setParentAfterSalesStatusName(resolveParentAfterSalesStatusName(parentAfterSalesStatus, text(item, "parentAfterSalesStatusName")));
        entity.setAfterSalesType(afterSalesType);
        entity.setAfterSalesTypeName(resolveAfterSalesTypeName(afterSalesType, text(item, "afterSalesTypeName")));

        JsonNode availableOperateList = item.path("availableOperateList");
        entity.setAvailableOperateListJson(availableOperateList.isMissingNode() || availableOperateList.isNull() ? null : availableOperateList.toString());
        JsonNode availableOperateNames = item.path("availableOperateNames");
        entity.setAvailableOperateNamesJson(availableOperateNames.isMissingNode() || availableOperateNames.isNull() ? null : availableOperateNames.toString());

        entity.setReturnDeliveryType(integerValue(item, "returnDeliveryType"));
        entity.setReturnDeliveryTypeName(text(item, "returnDeliveryTypeName"));
        entity.setOperateExpireTimeMs(toMillis(longValue(item, "operateExpireTime")));
        entity.setCreateAtMs(toMillis(longValue(item, "createAt")));
        entity.setUpdateAtMs(toMillis(longValue(item, "updateAt")));
        entity.setRawJson(item.toString());
        aftersaleRepository.save(entity);
        return new UpsertResult(created, firstPositive(entity.getUpdateAtMs(), entity.getCreateAtMs(), System.currentTimeMillis()));
    }

    private Specification<TemuOrderAftersale> buildSpec(Long shopRecordId,
                                                        String shopId,
                                                        String keyword,
                                                        Integer afterSalesStatusGroup,
                                                        Long createAtStartMs,
                                                        Long createAtEndMs,
                                                        Long updateAtStartMs,
                                                        Long updateAtEndMs) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (shopRecordId != null) {
                predicates.add(cb.equal(root.get("shopRecordId"), shopRecordId));
            }
            if (StringUtils.hasText(shopId)) {
                predicates.add(cb.equal(root.get("shopId"), shopId.trim()));
            }
            if (afterSalesStatusGroup != null) {
                predicates.add(cb.equal(root.get("afterSalesStatusGroup"), afterSalesStatusGroup));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("parentAfterSalesSn")), like),
                        cb.like(cb.lower(root.get("parentOrderSn")), like),
                        cb.like(cb.lower(root.get("afterSalesStatusGroupName")), like),
                        cb.like(cb.lower(root.get("parentAfterSalesStatusName")), like)
                ));
            }
            if (createAtStartMs != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createAtMs"), createAtStartMs));
            }
            if (createAtEndMs != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createAtMs"), createAtEndMs));
            }
            if (updateAtStartMs != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("updateAtMs"), updateAtStartMs));
            }
            if (updateAtEndMs != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("updateAtMs"), updateAtEndMs));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private TemuOrderSyncState getOrCreateState(TemuShop shop) {
        return syncStateRepository.findByShopRecordIdAndSyncType(shop.getId(), TemuOrderSyncType.AFTERSALE)
                .orElseGet(() -> {
                    TemuOrderSyncState state = new TemuOrderSyncState();
                    state.setShopRecordId(shop.getId());
                    state.setShopId(shop.getShopId());
                    state.setShopName(shop.getShopName());
                    state.setSyncType(TemuOrderSyncType.AFTERSALE);
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

    private static boolean hasMore(JsonNode result, int currentSize, int currentPage, int pageSize) {
        int total = result.path("total").asInt(-1);
        int pageNo = result.path("pageNumber").asInt(currentPage);
        if (total >= 0) {
            return (long) pageNo * (long) pageSize < total;
        }
        return currentSize >= pageSize;
    }

    private TemuOrderAftersaleDTO.ListItem toListItem(TemuOrderAftersale entity) {
        String afterSalesStatusGroupName = resolveAfterSalesStatusGroupName(entity.getAfterSalesStatusGroup(), entity.getAfterSalesStatusGroupName());
        String parentAfterSalesStatusName = resolveParentAfterSalesStatusName(entity.getParentAfterSalesStatus(), entity.getParentAfterSalesStatusName());
        String afterSalesTypeName = resolveAfterSalesTypeName(entity.getAfterSalesType(), entity.getAfterSalesTypeName());
        return TemuOrderAftersaleDTO.ListItem.builder()
                .id(entity.getId())
                .shopRecordId(entity.getShopRecordId())
                .shopId(entity.getShopId())
                .shopName(entity.getShopName())
                .parentAfterSalesSn(entity.getParentAfterSalesSn())
                .parentOrderSn(entity.getParentOrderSn())
                .afterSalesStatusGroup(entity.getAfterSalesStatusGroup())
                .afterSalesStatusGroupName(afterSalesStatusGroupName)
                .parentAfterSalesStatus(entity.getParentAfterSalesStatus())
                .parentAfterSalesStatusName(parentAfterSalesStatusName)
                .afterSalesType(entity.getAfterSalesType())
                .afterSalesTypeName(afterSalesTypeName)
                .createAtMs(entity.getCreateAtMs())
                .updateAtMs(entity.getUpdateAtMs())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
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

    private static String resolveAfterSalesStatusGroupName(Integer code, String currentName) {
        return firstNonBlank(currentName, AFTERSALE_STATUS_GROUP_LABELS.get(code));
    }

    private static String resolveParentAfterSalesStatusName(Integer code, String currentName) {
        return firstNonBlank(currentName, PARENT_AFTERSALE_STATUS_LABELS.get(code));
    }

    private static String resolveAfterSalesTypeName(Integer code, String currentName) {
        return firstNonBlank(currentName, AFTERSALE_TYPE_LABELS.get(code));
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

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private List<String> collectParentAfterSalesSnBatch(List<TemuOrderAftersale> aftersales, int fromIndex, int toExclusive) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int index = fromIndex; index < toExclusive; index++) {
            String parentAfterSalesSn = trim(aftersales.get(index).getParentAfterSalesSn());
            if (StringUtils.hasText(parentAfterSalesSn)) {
                out.add(parentAfterSalesSn);
            }
        }
        return new ArrayList<>(out);
    }

    private long resolveOpenStatusRefreshStartSec(List<TemuOrderAftersale> aftersales,
                                                  int fromIndex,
                                                  int toExclusive,
                                                  long nowSec) {
        long earliestMs = Long.MAX_VALUE;
        for (int index = fromIndex; index < toExclusive; index++) {
            TemuOrderAftersale aftersale = aftersales.get(index);
            Long candidateMs = firstPositive(aftersale.getUpdateAtMs(), aftersale.getCreateAtMs());
            if (candidateMs != null && candidateMs > 0 && candidateMs < earliestMs) {
                earliestMs = candidateMs;
            }
        }
        if (earliestMs == Long.MAX_VALUE) {
            return Math.max(0L, nowSec - DEFAULT_INCREMENTAL_HOURS * 3600L);
        }
        return Math.max(0L, earliestMs / 1000L - CURSOR_OVERLAP_SECONDS);
    }

    private boolean isReadTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("read timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record UpsertResult(boolean created, long cursorMs) {
    }

    private record WindowSyncResult(int totalCount,
                                    int createdCount,
                                    int updatedCount,
                                    long maxCursorMs) {
        private WindowSyncResult merge(WindowSyncResult other) {
            if (other == null) {
                return this;
            }
            return new WindowSyncResult(
                    totalCount + other.totalCount,
                    createdCount + other.createdCount,
                    updatedCount + other.updatedCount,
                    Math.max(maxCursorMs, other.maxCursorMs)
            );
        }
    }
}
