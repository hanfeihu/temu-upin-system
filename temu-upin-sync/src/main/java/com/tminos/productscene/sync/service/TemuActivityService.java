package com.tminos.productscene.sync.service;

import com.google.gson.Gson;
import com.tminos.productscene.service.TemuOpenApiCredentialService;
import com.tminos.productscene.sync.dto.ActivityDTO;
import com.tminos.productscene.sync.entity.*;
import com.tminos.productscene.sync.repository.*;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TemuActivityService {

    private static final Logger log = LoggerFactory.getLogger(TemuActivityService.class);
    private static final Gson GSON = new Gson();
    private static final int ENROLL_PAGE_SIZE = 50;
    private static final int ENROLL_SUBMIT_BATCH_SIZE = 20;
    private static final String ACTIVITY_RECOMMEND_LOCAL_CANDIDATES_SQL = """
            with sku_stats as (
                select
                    g.id as goods_id,
                    g.product_id,
                    g.product_name,
                    g.main_image_url,
                    g.ext_code,
                    g.temu_created_at,
                    coalesce(sum(case when o.order_status is null or o.order_status <> 3 then o.quantity else 0 end), 0) as sales_quantity,
                    min(purchase.purchase_price) as min_purchase_price,
                    max(purchase.purchase_price) as max_purchase_price,
                    min(coalesce(site_price.supplier_price, price.supplier_price, 0)) as min_supplier_price,
                    max(coalesce(site_price.supplier_price, price.supplier_price, 0)) as max_supplier_price
                from temu_goods g
                join temu_goods_sku sku
                  on sku.goods_id = g.id
                 and sku.shop_id = g.shop_id
                left join temu_goods_sku_price price
                  on price.shop_id = sku.shop_id
                 and price.product_sku_id = sku.product_sku_id
                left join temu_goods_sku_site_price site_price
                  on site_price.sku_price_id = price.id
                 and site_price.site_id = 100
                left join temu_shop_sku_purchase_price purchase
                  on purchase.shop_id = sku.shop_id
                 and purchase.product_sku_id = sku.product_sku_id
                left join temu_shops shop
                  on shop.shop_id = g.shop_id
                left join temu_orders o
                  on o.shop_record_id = shop.id
                 and o.matched_temu_sku_id = cast(sku.product_sku_id as text)
                where g.shop_id = :shopId
                  and coalesce(g.skc_site_status, 0) = 1
                  and not exists (
                      select 1 from temu_activity_blacklist blacklist
                      where blacklist.shop_id = g.shop_id
                        and blacklist.product_id = g.product_id
                  )
                  and (:hasMinSupplierPrice = false or coalesce(site_price.supplier_price, price.supplier_price, 0) >= :minSupplierPrice)
                  and (:hasMaxSupplierPrice = false or coalesce(site_price.supplier_price, price.supplier_price, 0) <= :maxSupplierPrice)
                  and (
                    :excludeEnrolled = false
                    or not exists (
                        select 1 from temu_activity_enrollment e
                        where e.shop_id = g.shop_id
                          and e.product_id = g.product_id
                          and e.activity_type = :activityType
                          and (:hasActivityThematicId = false or e.activity_thematic_id = :activityThematicId)
                          and coalesce(e.enroll_status, 0) in (1, 3, 4)
                    )
                  )
                group by g.id, g.product_id, g.product_name, g.main_image_url, g.ext_code, g.temu_created_at
            )
            select *
            from sku_stats
            where (:hasMaxSalesQuantity = false or sales_quantity <= :maxSalesQuantity)
              and (:hasMinListedDays = false or temu_created_at is null or temu_created_at <= :listedBeforeSeconds)
            order by max_supplier_price desc nulls last, sales_quantity asc, product_id desc
            limit :limit
            """;

    private final TemuActivityRepository activityRepository;
    private final TemuActivityThematicRepository thematicRepository;
    private final TemuActivitySessionRepository sessionRepository;
    private final TemuActivityEnrollmentRepository enrollmentRepository;
    private final TemuActivityEnrollPriceRepository enrollPriceRepository;
    private final TemuGoodsRepository goodsRepository;
    private final TemuGoodsSkuRepository goodsSkuRepository;
    private final TemuGoodsSkuSpecRepository goodsSkuSpecRepository;
    private final TemuActivityBlacklistRepository activityBlacklistRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final TemuOpenApiCredentialService credentialService;

    public TemuActivityService(TemuActivityRepository activityRepository,
                               TemuActivityThematicRepository thematicRepository,
                               TemuActivitySessionRepository sessionRepository,
                               TemuActivityEnrollmentRepository enrollmentRepository,
                               TemuActivityEnrollPriceRepository enrollPriceRepository,
                               TemuGoodsRepository goodsRepository,
                               TemuGoodsSkuRepository goodsSkuRepository,
                               TemuGoodsSkuSpecRepository goodsSkuSpecRepository,
                               TemuActivityBlacklistRepository activityBlacklistRepository,
                               NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                               TemuOpenApiCredentialService credentialService) {
        this.activityRepository = activityRepository;
        this.thematicRepository = thematicRepository;
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollPriceRepository = enrollPriceRepository;
        this.goodsRepository = goodsRepository;
        this.goodsSkuRepository = goodsSkuRepository;
        this.goodsSkuSpecRepository = goodsSkuSpecRepository;
        this.activityBlacklistRepository = activityBlacklistRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.credentialService = credentialService;
    }

    // ==================== 活动列表 ====================

    public List<ActivityDTO.ActivityItem> listActivities(String shopId, Integer activityType) {
        List<TemuActivity> activities;
        if (activityType != null) {
            activities = activityRepository.findByShopIdAndActivityType(shopId, activityType);
        } else {
            activities = activityRepository.findByShopId(shopId);
        }
        Map<String, ActivityDTO.ActivityItem> deduped = new LinkedHashMap<>();
        for (TemuActivity a : activities) {
            ActivityDTO.ActivityItem item = new ActivityDTO.ActivityItem();
            item.setId(a.getId());
            item.setShopId(a.getShopId());
            item.setActivityType(a.getActivityType());
            item.setActivityName(a.getActivityName());
            item.setActivityContent(a.getActivityContent());
            item.setActivityLabelTag(a.getActivityLabelTag());
            item.setSessionAssignType(a.getSessionAssignType());
            item.setBenefitLabelsJson(a.getBenefitLabelsJson());

            List<TemuActivityThematic> thematics = thematicRepository.findByActivityId(a.getId());
            item.setThematicList(thematics.stream().map(t -> {
                ActivityDTO.ThematicItem ti = new ActivityDTO.ThematicItem();
                ti.setId(t.getId());
                ti.setActivityThematicId(t.getActivityThematicId());
                ti.setActivityThematicName(t.getActivityThematicName());
                ti.setEnrollSource(t.getEnrollSource());
                ti.setEnrollStartAt(t.getEnrollStartAt());
                ti.setEnrollDeadLine(t.getEnrollDeadLine());
                ti.setStartTime(t.getStartTime());
                ti.setEndTime(t.getEndTime());
                ti.setDurationDays(t.getDurationDays());
                ti.setSalePromotionLabel(t.getSalePromotionLabel());
                ti.setBenefitLabelsJson(t.getBenefitLabelsJson());
                ti.setSitesJson(t.getSitesJson());
                return ti;
            }).collect(Collectors.toList()));

            String key = item.getActivityType() + "|" + (item.getActivityName() == null ? "" : item.getActivityName());
            ActivityDTO.ActivityItem existing = deduped.get(key);
            if (existing == null || thematicSize(item) > thematicSize(existing) || (thematicSize(item) == thematicSize(existing) && item.getId() > existing.getId())) {
                deduped.put(key, item);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private int thematicSize(ActivityDTO.ActivityItem item) {
        return item.getThematicList() == null ? 0 : item.getThematicList().size();
    }

    public ActivityDTO.ActivityDetail getActivityDetail(String shopId, Integer activityType, Long activityThematicId) {
        if (activityType == null) {
            throw new IllegalArgumentException("activityType不能为空");
        }
        TemuOpenApiClient client = buildClient(shopId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("activityType", activityType);
        if (activityThematicId != null) {
            params.put("activityThematicId", activityThematicId);
        }
        Map<String, Object> resultMap = callResultMap(client, TemuOpenApiClient.API_ACTIVITY_DETAIL, params, "查询活动详情失败");

        ActivityDTO.ActivityDetail detail = new ActivityDTO.ActivityDetail();
        detail.setCanEnroll(toBool(resultMap.get("canEnroll")));
        detail.setActivityInfo(mapActivityInfo(toMap(resultMap.get("activityInfo"))));
        detail.setThematicInfo(mapThematicInfo(toMap(resultMap.get("thematicInfo"))));
        detail.setRequirements(mapRequirementList(resultMap.get("requirements")));
        detail.setMallAptitude(mapRequirementList(resultMap.get("mallAptitude")));
        return detail;
    }

    // ==================== 活动场次 ====================

    public List<ActivityDTO.SessionItem> listSessions(String shopId, Integer activityType, Integer sessionStatus) {
        List<TemuActivitySession> sessions;
        if (sessionStatus != null) {
            sessions = sessionRepository.findByShopIdAndActivityTypeAndSessionStatus(shopId, activityType, sessionStatus);
        } else {
            sessions = sessionRepository.findByShopIdAndActivityType(shopId, activityType);
        }
        return sessions.stream().map(s -> {
            ActivityDTO.SessionItem si = new ActivityDTO.SessionItem();
            si.setId(s.getId());
            si.setSessionId(s.getSessionId());
            si.setSessionName(s.getSessionName());
            si.setSessionStatus(s.getSessionStatus());
            si.setSiteId(s.getSiteId());
            si.setSiteName(s.getSiteName());
            si.setStartTime(s.getStartTime());
            si.setEndTime(s.getEndTime());
            si.setDurationDays(s.getDurationDays());
            return si;
        }).collect(Collectors.toList());
    }

    public ActivityDTO.ProductMatchResponse matchProducts(ActivityDTO.ProductMatchRequest request) {
        if (request == null || request.getActivityType() == null) {
            throw new IllegalArgumentException("activityType不能为空");
        }
        if (request.getProductIds() == null || request.getProductIds().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个商品");
        }

        TemuOpenApiClient client = buildClient(request.getShopId());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("activityType", request.getActivityType());
        params.put("rowCount", request.getRowCount() != null && request.getRowCount() > 0
                ? request.getRowCount()
                : Math.max(request.getProductIds().size(), 20));
        params.put("productIds", request.getProductIds());
        if (request.getActivityThematicId() != null) {
            params.put("activityThematicId", request.getActivityThematicId());
        }
        if (request.getSearchScrollContext() != null && !request.getSearchScrollContext().isBlank()) {
            params.put("searchScrollContext", request.getSearchScrollContext());
        }

        Map<String, Object> resultMap = callResultMap(client, TemuOpenApiClient.API_ACTIVITY_PRODUCT, params, "查询可报名商品失败");
        return mapProductMatchResponse(request.getShopId(), resultMap);
    }

    public ActivityDTO.RecommendationResponse recommendProducts(ActivityDTO.RecommendationRequest request) {
        if (request == null || request.getActivityType() == null) {
            throw new IllegalArgumentException("activityType不能为空");
        }
        if (request.getShopId() == null || request.getShopId().isBlank()) {
            throw new IllegalArgumentException("shopId不能为空");
        }

        int rowCount = clamp(request.getRowCount(), 1, 100, 20);
        List<LocalActivityCandidate> localCandidates = loadLocalActivityCandidates(request, Math.min(rowCount * 4, 200));
        ActivityDTO.RecommendationResponse response = new ActivityDTO.RecommendationResponse();
        response.setLocalCandidateCount(localCandidates.size());
        if (localCandidates.isEmpty()) {
            response.setMatchedCount(0);
            response.setList(List.of());
            return response;
        }
        Map<Long, LocalActivityCandidate> localByProductId = localCandidates.stream()
                .collect(Collectors.toMap(LocalActivityCandidate::productId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<ActivityDTO.RecommendedProductItem> recommended = new ArrayList<>();
        int matchedCount = 0;
        for (List<LocalActivityCandidate> batch : partition(localCandidates, 50)) {
            ActivityDTO.ProductMatchRequest matchRequest = new ActivityDTO.ProductMatchRequest();
            matchRequest.setShopId(request.getShopId());
            matchRequest.setActivityType(request.getActivityType());
            matchRequest.setActivityThematicId(request.getActivityThematicId());
            matchRequest.setRowCount(batch.size());
            matchRequest.setProductIds(batch.stream().map(LocalActivityCandidate::productId).toList());

            ActivityDTO.ProductMatchResponse matchResponse = matchProducts(matchRequest);
            List<ActivityDTO.MatchedProductItem> matchList = matchResponse.getMatchList() == null ? List.of() : matchResponse.getMatchList();
            matchedCount += matchList.size();
            for (ActivityDTO.MatchedProductItem matched : matchList) {
                LocalActivityCandidate local = localByProductId.get(matched.getProductId());
                if (local == null) {
                    continue;
                }
                ActivityDTO.RecommendedProductItem item = buildRecommendedProduct(local, matched, request);
                if (!"不建议".equals(item.getDecision())) {
                    recommended.add(item);
                }
                if (recommended.size() >= rowCount) {
                    break;
                }
            }
            if (recommended.size() >= rowCount) {
                break;
            }
        }
        response.setMatchedCount(matchedCount);
        response.setList(recommended);
        return response;
    }

    public ActivityDTO.SessionQueryResponse querySessions(ActivityDTO.SessionQueryRequest request) {
        if (request == null || request.getActivityType() == null) {
            throw new IllegalArgumentException("activityType不能为空");
        }
        if (request.getProductIds() == null || request.getProductIds().isEmpty()) {
            throw new IllegalArgumentException("productIds不能为空");
        }

        TemuOpenApiClient client = buildClient(request.getShopId());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("activityType", request.getActivityType());
        params.put("productIds", request.getProductIds());
        if (request.getActivityThematicId() != null) {
            params.put("activityThematicId", request.getActivityThematicId());
        }
        if (request.getStartTime() != null) {
            params.put("startTime", request.getStartTime());
        }
        if (request.getEndTime() != null) {
            params.put("endTime", request.getEndTime());
        }

        Map<String, Object> resultMap = callResultMap(client, TemuOpenApiClient.API_ACTIVITY_SESSION_LIST, params, "查询活动场次失败");
        ActivityDTO.SessionQueryResponse response = new ActivityDTO.SessionQueryResponse();
        response.setSiteIds(toIntList(resultMap.get("siteIds")));
        response.setList(mapSessionItems(resultMap.get("list")));

        Map<String, List<ActivityDTO.SessionItem>> sessionMap = new LinkedHashMap<>();
        Map<String, Object> productCanEnrollSessionMap = toMap(resultMap.get("productCanEnrollSessionMap"));
        if (productCanEnrollSessionMap != null) {
            for (Map.Entry<String, Object> entry : productCanEnrollSessionMap.entrySet()) {
                sessionMap.put(entry.getKey(), mapSessionItems(entry.getValue()));
            }
        }
        response.setProductCanEnrollSessionMap(sessionMap);
        return response;
    }

    // ==================== 报名记录 ====================

    public Page<TemuActivityEnrollment> listEnrollments(String shopId, Integer activityType, Integer enrollStatus, Long productId, List<Long> productIds, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        List<Long> safeProductIds = productIds == null ? List.of() : productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (safeProductIds.isEmpty() && productId != null) {
            safeProductIds = List.of(productId);
        }
        if (!safeProductIds.isEmpty() && activityType != null && enrollStatus != null) {
            return enrollmentRepository.findByShopIdAndProductIdInAndActivityTypeAndEnrollStatus(shopId, safeProductIds, activityType, enrollStatus, pageRequest);
        }
        if (!safeProductIds.isEmpty() && activityType != null) {
            return enrollmentRepository.findByShopIdAndProductIdInAndActivityType(shopId, safeProductIds, activityType, pageRequest);
        }
        if (!safeProductIds.isEmpty() && enrollStatus != null) {
            return enrollmentRepository.findByShopIdAndProductIdInAndEnrollStatus(shopId, safeProductIds, enrollStatus, pageRequest);
        }
        if (!safeProductIds.isEmpty()) {
            return enrollmentRepository.findByShopIdAndProductIdIn(shopId, safeProductIds, pageRequest);
        }
        if (productId != null && activityType != null && enrollStatus != null) {
            return enrollmentRepository.findByShopIdAndProductIdAndActivityTypeAndEnrollStatus(shopId, productId, activityType, enrollStatus, pageRequest);
        }
        if (productId != null && activityType != null) {
            return enrollmentRepository.findByShopIdAndProductIdAndActivityType(shopId, productId, activityType, pageRequest);
        }
        if (productId != null && enrollStatus != null) {
            return enrollmentRepository.findByShopIdAndProductIdAndEnrollStatus(shopId, productId, enrollStatus, pageRequest);
        }
        if (productId != null) {
            return enrollmentRepository.findByShopIdAndProductId(shopId, productId, pageRequest);
        }
        if (enrollStatus != null) {
            return enrollmentRepository.findByShopIdAndEnrollStatus(shopId, enrollStatus, pageRequest);
        }
        if (activityType != null) {
            return enrollmentRepository.findByShopIdAndActivityType(shopId, activityType, pageRequest);
        }
        return enrollmentRepository.findByShopId(shopId, pageRequest);
    }

    @Transactional
    public Map<String, Object> refreshEnrollmentRecords(String shopId, Integer activityType, Long activityThematicId, List<Long> productIds) {
        try {
            int beforeCount = productIds == null || productIds.isEmpty()
                    ? 0
                    : enrollmentRepository.findByShopIdAndProductIdIn(shopId, productIds).size();
            doRefreshEnrollmentRecords(shopId, activityType, activityThematicId, productIds);
            int afterCount = productIds == null || productIds.isEmpty()
                    ? 0
                    : enrollmentRepository.findByShopIdAndProductIdIn(shopId, productIds).size();
            return Map.of(
                    "success", true,
                    "beforeCount", beforeCount,
                    "afterCount", afterCount,
                    "message", afterCount > beforeCount ? "已刷新到新的报名记录" : "已请求 TEMU 刷新，暂未发现新的报名记录"
            );
        } catch (Exception e) {
            log.warn("手动刷新活动报名记录失败: {}", e.getMessage());
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    public ActivityDTO.EnrollmentItem getEnrollmentDetail(Long id) {
        Long safeId = Objects.requireNonNull(id, "报名记录ID不能为空");
        TemuActivityEnrollment enrollment = enrollmentRepository.findById(safeId)
                .orElseThrow(() -> new IllegalArgumentException("报名记录不存在: " + id));
        List<TemuActivityEnrollPrice> prices = enrollPriceRepository.findByEnrollmentId(safeId);
        return toEnrollmentItem(enrollment, prices);
    }

    private ActivityDTO.EnrollmentItem toEnrollmentItem(TemuActivityEnrollment e, List<TemuActivityEnrollPrice> prices) {
        ActivityDTO.EnrollmentItem item = new ActivityDTO.EnrollmentItem();
        item.setId(e.getId());
        item.setShopId(e.getShopId());
        item.setEnrollId(e.getEnrollId());
        item.setProductId(e.getProductId());
        item.setGoodsId(e.getGoodsId());
        item.setActivityType(e.getActivityType());
        item.setActivityTypeName(e.getActivityTypeName());
        item.setActivityThematicId(e.getActivityThematicId());
        item.setActivityThematicName(e.getActivityThematicName());
        item.setEnrollStatus(e.getEnrollStatus());
        item.setEnrollTime(e.getEnrollTime());
        item.setActivityStock(e.getActivityStock());
        item.setRemainingActivityStock(e.getRemainingActivityStock());
        item.setCurrency(e.getCurrency());
        item.setSoldStatus(e.getSoldStatus());
        item.setSessionStartTime(e.getSessionStartTime());
        item.setSessionEndTime(e.getSessionEndTime());
        item.setAssignSessionsJson(e.getAssignSessionsJson());
        item.setSyncedAt(e.getSyncedAt());

        item.setPriceList(prices.stream().map(p -> {
            ActivityDTO.EnrollPriceItem pi = new ActivityDTO.EnrollPriceItem();
            pi.setId(p.getId());
            pi.setLevel(p.getLevel());
            pi.setSkcId(p.getSkcId());
            pi.setSkuId(p.getSkuId());
            pi.setSiteId(p.getSiteId());
            pi.setSiteName(p.getSiteName());
            pi.setDailyPrice(p.getDailyPrice());
            pi.setActivityPrice(p.getActivityPrice());
            pi.setActivityDiscount(p.getActivityDiscount());
            pi.setCurrency(p.getCurrency());
            return pi;
        }).collect(Collectors.toList()));

        return item;
    }

    // ==================== 批量报名 ====================

    @Transactional
    public Map<String, Object> batchEnroll(ActivityDTO.BatchEnrollRequest request) {
        if (request == null || request.getActivityType() == null) {
            return Map.of("success", false, "message", "activityType不能为空");
        }
        if (request.getProductList() == null || request.getProductList().isEmpty()) {
            return Map.of("success", false, "message", "productList不能为空");
        }
        List<Long> requestedProductIds = request.getProductList().stream()
                .map(ActivityDTO.EnrollProductItem::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Long> blacklistedProductIds = requestedProductIds.isEmpty()
                ? List.of()
                : activityBlacklistRepository.findByShopIdAndProductIdIn(request.getShopId(), requestedProductIds).stream()
                .map(TemuActivityBlacklist::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!blacklistedProductIds.isEmpty()) {
            return Map.of("success", false, "message", "存在活动黑名单商品，已阻止报名: " + blacklistedProductIds);
        }
        try {
            TemuOpenApiClient client = buildClient(request.getShopId());
            List<List<ActivityDTO.EnrollProductItem>> batches = partition(request.getProductList(), ENROLL_SUBMIT_BATCH_SIZE);
            int successCount = 0;
            int failCount = 0;
            List<Object> failList = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (List<ActivityDTO.EnrollProductItem> batch : batches) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("activityType", request.getActivityType());
                if (request.getActivityThematicId() != null) {
                    params.put("activityThematicId", request.getActivityThematicId());
                }
                params.put("productList", batch.stream().map(this::toEnrollProductMap).collect(Collectors.toList()));

                try {
                    TemuOpenApiClient.ApiResult result = client.callApiParsed(TemuOpenApiClient.API_ACTIVITY_ENROLL_SUBMIT, params);
                    if (!result.success) {
                        failCount += batch.size();
                        errors.add(result.errorMsg != null ? result.errorMsg : "活动报名提交失败");
                        continue;
                    }

                    Map<String, Object> resultMap = result.resultAsMap();
                    int apiSuccessCount = toInt(resultMap == null ? null : resultMap.get("successCount"), batch.size());
                    int apiFailCount = toInt(resultMap == null ? null : resultMap.get("failCount"), 0);
                    successCount += apiSuccessCount;
                    failCount += apiFailCount;
                    if (resultMap != null) {
                        List<Object> currentFailList = toObjectList(resultMap.get("failList"));
                        if (currentFailList != null && !currentFailList.isEmpty()) {
                            failList.addAll(currentFailList);
                        }
                    }
                } catch (Exception e) {
                    failCount += batch.size();
                    errors.add(e.getMessage());
                }
            }

            List<Long> productIds = requestedProductIds;
            String refreshWarning = null;
            if (!productIds.isEmpty() && successCount > 0) {
                try {
                    doRefreshEnrollmentRecords(request.getShopId(), request.getActivityType(), request.getActivityThematicId(), productIds);
                } catch (Exception e) {
                    log.warn("活动报名成功但刷新报名记录失败: {}", e.getMessage());
                    refreshWarning = e.getMessage();
                }
            }

            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("success", failCount == 0 && errors.isEmpty());
            resultMap.put("total", request.getProductList().size());
            resultMap.put("successCount", successCount);
            resultMap.put("failCount", failCount);
            if (!failList.isEmpty()) {
                resultMap.put("failList", failList);
            }
            if (!errors.isEmpty()) {
                resultMap.put("errors", errors);
            }
            if (refreshWarning != null) {
                resultMap.put("refreshWarning", refreshWarning);
            }
            if (Boolean.FALSE.equals(resultMap.get("success"))) {
                resultMap.put("message", errors.isEmpty() ? "存在报名失败商品" : errors.get(0));
            }
            return resultMap;
        } catch (Exception e) {
            log.error("批量报名失败", e);
            return Map.of("success", false, "message", "API调用失败: " + e.getMessage());
        }
    }

    @Transactional
    public TemuActivityBlacklist addActivityBlacklist(ActivityDTO.BlacklistRequest request) {
        if (request == null || request.getShopId() == null || request.getShopId().isBlank()) {
            throw new IllegalArgumentException("shopId不能为空");
        }
        if (request.getProductId() == null) {
            throw new IllegalArgumentException("productId不能为空");
        }
        String reason = request.getReason();
        if (reason == null || reason.isBlank()) {
            reason = "建议活动价太低";
        }
        TemuActivityBlacklist row = activityBlacklistRepository.findByShopIdAndProductId(request.getShopId(), request.getProductId())
                .orElseGet(() -> TemuActivityBlacklist.builder()
                        .shopId(request.getShopId())
                        .productId(request.getProductId())
                        .build());
        row.setGoodsId(request.getGoodsId());
        row.setProductName(request.getProductName());
        row.setReason(reason.trim());
        return activityBlacklistRepository.save(row);
    }

    private void doRefreshEnrollmentRecords(String shopId, Integer activityType, Long activityThematicId, List<Long> productIds) throws Exception {
        if (shopId == null || shopId.isBlank() || productIds == null || productIds.isEmpty()) {
            return;
        }
        TemuOpenApiClient client = buildClient(shopId);
        int pageNo = 1;
        int totalPages = 1;
        do {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("pageNo", pageNo);
            params.put("pageSize", ENROLL_PAGE_SIZE);
            params.put("productIds", productIds);
            if (activityType != null) {
                params.put("activityType", activityType);
            }
            if (activityThematicId != null) {
                params.put("activityThematicId", activityThematicId);
            }
            Map<String, Object> resultMap = callResultMap(client, TemuOpenApiClient.API_ACTIVITY_ENROLL_LIST, params, "刷新活动报名记录失败");
            int total = toInt(resultMap.get("total"), 0);
            totalPages = Math.max(1, (int) Math.ceil((double) total / ENROLL_PAGE_SIZE));
            persistEnrollmentList(shopId, resultMap.get("list"));
            pageNo++;
        } while (pageNo <= totalPages);
    }

    @Transactional
    protected void persistEnrollmentList(String shopId, Object listObject) {
        List<Map<String, Object>> records = toMapList(listObject);
        if (records.isEmpty()) {
            return;
        }

        Map<Long, TemuGoods> goodsMap = goodsRepository.findByShopIdAndProductIdIn(
                        shopId,
                        records.stream().map(item -> toLong(item.get("productId"))).filter(Objects::nonNull).distinct().toList())
                .stream()
                .filter(goods -> goods.getProductId() != null)
                .collect(Collectors.toMap(TemuGoods::getProductId, goods -> goods, (left, right) -> left, LinkedHashMap::new));

        for (Map<String, Object> raw : records) {
            Long enrollId = toLong(raw.get("enrollId"));
            Long productId = toLong(raw.get("productId"));
            if (enrollId == null || productId == null) {
                continue;
            }

            TemuActivityEnrollment enrollment = enrollmentRepository.findByShopIdAndEnrollId(shopId, enrollId)
                    .orElseGet(TemuActivityEnrollment::new);
            enrollment.setShopId(shopId);
            enrollment.setEnrollId(enrollId);
            enrollment.setProductId(productId);
            enrollment.setGoodsId(toLong(raw.get("goodsId")));
            if (enrollment.getGoodsId() == null) {
                TemuGoods goods = goodsMap.get(productId);
                if (goods != null) {
                    enrollment.setGoodsId(goods.getId());
                }
            }
            enrollment.setActivityType(toInt(raw.get("activityType")));
            enrollment.setActivityTypeName(toStr(raw.get("activityTypeName")));
            enrollment.setActivityThematicId(toLong(raw.get("activityThematicId")));
            enrollment.setActivityThematicName(toStr(raw.get("activityThematicName")));
            enrollment.setEnrollStatus(toInt(raw.get("enrollStatus")));
            enrollment.setEnrollTime(toLong(raw.get("enrollTime")));
            enrollment.setActivityStock(toInt(raw.get("activityStock")));
            enrollment.setRemainingActivityStock(toInt(raw.get("remainingActivityStock")));
            enrollment.setIsApparel(toInt(raw.get("isApparel")));
            enrollment.setCurrency(toStr(raw.get("currency")));
            enrollment.setSoldStatus(toInt(raw.get("soldStatus")));
            enrollment.setSessionStartTime(toLong(raw.get("sessionStartTime")));
            enrollment.setSessionEndTime(toLong(raw.get("sessionEndTime")));
            enrollment.setAssignSessionsJson(toJson(raw.get("assignSessionList")));
            enrollment.setSyncedAt(java.time.LocalDateTime.now());
            enrollment = enrollmentRepository.save(enrollment);

            enrollPriceRepository.deleteByEnrollmentId(enrollment.getId());
            List<TemuActivityEnrollPrice> priceRows = buildEnrollPriceRows(enrollment.getId(), raw.get("skcList"));
            if (!priceRows.isEmpty()) {
                enrollPriceRepository.saveAll(priceRows);
            }
        }
    }

    private List<TemuActivityEnrollPrice> buildEnrollPriceRows(Long enrollmentId, Object skcListObject) {
        List<TemuActivityEnrollPrice> rows = new ArrayList<>();
        for (Map<String, Object> skcRaw : toMapList(skcListObject)) {
            Long skcId = toLong(skcRaw.get("skcId"));
            String currency = toStr(firstNonNull(skcRaw.get("currency"), skcRaw.get("priceCurrency")));
            List<Map<String, Object>> skcSitePrices = toMapList(skcRaw.get("sitePriceList"));
            if (skcSitePrices.isEmpty()) {
                rows.add(buildEnrollPrice(enrollmentId, "SKC", skcId, null, null, null,
                        toInt(skcRaw.get("dailyPrice")), toInt(skcRaw.get("activityPrice")), null, currency));
            } else {
                for (Map<String, Object> sitePrice : skcSitePrices) {
                    rows.add(buildEnrollPrice(enrollmentId, "SKC", skcId, null,
                            toInt(sitePrice.get("siteId")), toStr(sitePrice.get("siteName")),
                            toInt(sitePrice.get("dailyPrice")), toInt(sitePrice.get("activityPrice")),
                            toInt(sitePrice.get("activityDiscount")), currency));
                }
            }

            for (Map<String, Object> skuRaw : toMapList(skcRaw.get("skuList"))) {
                Long skuId = toLong(skuRaw.get("skuId"));
                String skuCurrency = toStr(firstNonNull(skuRaw.get("currency"), currency));
                List<Map<String, Object>> skuSitePrices = toMapList(skuRaw.get("sitePriceList"));
                if (skuSitePrices.isEmpty()) {
                    rows.add(buildEnrollPrice(enrollmentId, "SKU", skcId, skuId, null, null,
                            toInt(skuRaw.get("dailyPrice")), toInt(skuRaw.get("activityPrice")), null, skuCurrency));
                } else {
                    for (Map<String, Object> sitePrice : skuSitePrices) {
                        rows.add(buildEnrollPrice(enrollmentId, "SKU", skcId, skuId,
                                toInt(sitePrice.get("siteId")), toStr(sitePrice.get("siteName")),
                                toInt(sitePrice.get("dailyPrice")), toInt(sitePrice.get("activityPrice")),
                                toInt(sitePrice.get("activityDiscount")), skuCurrency));
                    }
                }
            }
        }
        return rows;
    }

    private TemuActivityEnrollPrice buildEnrollPrice(Long enrollmentId,
                                                     String level,
                                                     Long skcId,
                                                     Long skuId,
                                                     Integer siteId,
                                                     String siteName,
                                                     Integer dailyPrice,
                                                     Integer activityPrice,
                                                     Integer activityDiscount,
                                                     String currency) {
        return TemuActivityEnrollPrice.builder()
                .enrollmentId(enrollmentId)
                .level(level)
                .skcId(skcId)
                .skuId(skuId)
                .siteId(siteId)
                .siteName(siteName)
                .dailyPrice(dailyPrice)
                .activityPrice(activityPrice)
                .activityDiscount(activityDiscount)
                .currency(currency)
                .build();
    }

    private Map<String, Object> toEnrollProductMap(ActivityDTO.EnrollProductItem product) {
        Map<String, Object> productMap = new LinkedHashMap<>();
        productMap.put("productId", product.getProductId());
        productMap.put("activityStock", product.getActivityStock());
        if (product.getSessionIds() != null && !product.getSessionIds().isEmpty()) {
            productMap.put("sessionIds", product.getSessionIds());
        }
        productMap.put("skcList", product.getSkcList() == null ? List.of() : product.getSkcList().stream().map(skc -> {
            Map<String, Object> skcMap = new LinkedHashMap<>();
            skcMap.put("skcId", skc.getSkcId());
            if (skc.getActivityPrice() != null) {
                skcMap.put("activityPrice", skc.getActivityPrice());
            }
            if (skc.getSiteActivityPriceList() != null && !skc.getSiteActivityPriceList().isEmpty()) {
                skcMap.put("siteActivityPriceList", skc.getSiteActivityPriceList().stream().map(this::toSiteActivityPriceMap).collect(Collectors.toList()));
            }
            skcMap.put("skuList", skc.getSkuList() == null ? List.of() : skc.getSkuList().stream().map(sku -> {
                Map<String, Object> skuMap = new LinkedHashMap<>();
                skuMap.put("skuId", sku.getSkuId());
                if (sku.getActivityPrice() != null) {
                    skuMap.put("activityPrice", sku.getActivityPrice());
                }
                if (sku.getSiteActivityPriceList() != null && !sku.getSiteActivityPriceList().isEmpty()) {
                    skuMap.put("siteActivityPriceList", sku.getSiteActivityPriceList().stream().map(this::toSiteActivityPriceMap).collect(Collectors.toList()));
                }
                return skuMap;
            }).collect(Collectors.toList()));
            return skcMap;
        }).collect(Collectors.toList()));
        return productMap;
    }

    private Map<String, Object> toSiteActivityPriceMap(ActivityDTO.EnrollSitePriceItem sitePrice) {
        Map<String, Object> sitePriceMap = new LinkedHashMap<>();
        sitePriceMap.put("siteId", sitePrice.getSiteId());
        if (sitePrice.getActivityPrice() != null) {
            sitePriceMap.put("activityPrice", sitePrice.getActivityPrice());
        }
        return sitePriceMap;
    }

    private ActivityDTO.ProductMatchResponse mapProductMatchResponse(String shopId, Map<String, Object> resultMap) {
        ActivityDTO.ProductMatchResponse response = new ActivityDTO.ProductMatchResponse();
        response.setSearchScrollContext(toStr(resultMap.get("searchScrollContext")));
        response.setHasMore(toBool(resultMap.get("hasMore")));

        List<Map<String, Object>> rawProducts = toMapList(resultMap.get("matchList"));
        List<Long> rawProductIds = rawProducts.stream().map(item -> toLong(item.get("productId"))).filter(Objects::nonNull).distinct().toList();
        Set<Long> blacklistedProductIds = rawProductIds.isEmpty()
                ? Set.of()
                : activityBlacklistRepository.findByShopIdAndProductIdIn(shopId, rawProductIds).stream()
                .map(TemuActivityBlacklist::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!blacklistedProductIds.isEmpty()) {
            rawProducts = rawProducts.stream()
                    .filter(item -> {
                        Long productId = toLong(item.get("productId"));
                        return productId == null || !blacklistedProductIds.contains(productId);
                    })
                    .toList();
        }
        Map<Long, TemuGoods> goodsByProductId = goodsRepository.findByShopIdAndProductIdIn(
                        shopId,
                        rawProducts.stream().map(item -> toLong(item.get("productId"))).filter(Objects::nonNull).distinct().toList())
                .stream()
                .filter(goods -> goods.getProductId() != null)
                .collect(Collectors.toMap(TemuGoods::getProductId, goods -> goods, (left, right) -> left, LinkedHashMap::new));

        List<Long> goodsIds = goodsByProductId.values().stream().map(TemuGoods::getId).filter(Objects::nonNull).toList();
        Map<Long, List<TemuGoodsSku>> goodsSkuByGoodsId = goodsIds.isEmpty()
                ? Map.of()
                : goodsSkuRepository.findByGoodsIdIn(goodsIds).stream().collect(Collectors.groupingBy(TemuGoodsSku::getGoodsId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, TemuGoodsSku> goodsSkuByProductSkuId = goodsSkuByGoodsId.values().stream()
                .flatMap(Collection::stream)
                .filter(goodsSku -> goodsSku.getProductSkuId() != null)
                .collect(Collectors.toMap(TemuGoodsSku::getProductSkuId, goodsSku -> goodsSku, (left, right) -> left, LinkedHashMap::new));
        List<Long> localSkuIds = goodsSkuByProductSkuId.values().stream().map(TemuGoodsSku::getId).filter(Objects::nonNull).toList();
        Map<Long, String> specInfoBySkuId = localSkuIds.isEmpty()
                ? Map.of()
                : goodsSkuSpecRepository.findBySkuIdIn(localSkuIds).stream().collect(Collectors.groupingBy(
                        TemuGoodsSkuSpec::getSkuId,
                        LinkedHashMap::new,
                        Collectors.mapping(spec -> spec.getParentSpecName() + ": " + spec.getSpecName(), Collectors.joining(" / "))));

        List<ActivityDTO.MatchedProductItem> items = new ArrayList<>();
        for (Map<String, Object> rawProduct : rawProducts) {
            Long productId = toLong(rawProduct.get("productId"));
            TemuGoods goods = goodsByProductId.get(productId);

            ActivityDTO.MatchedProductItem item = new ActivityDTO.MatchedProductItem();
            item.setProductId(productId);
            item.setProductName(toStr(rawProduct.get("productName")));
            item.setCurrency(toStr(rawProduct.get("currency")));
            item.setIsApparel(toInt(rawProduct.get("isApparel")));
            item.setTargetActivityStock(toInt(rawProduct.get("targetActivityStock")));
            item.setSuggestActivityStock(toInt(rawProduct.get("suggestActivityStock")));
            item.setEnrollSessionIdList(toLongList(rawProduct.get("enrollSessionIdList")));
            item.setSites(mapSiteItems(rawProduct.get("sites")));
            item.setMainImageUrl(goods == null ? null : goods.getMainImageUrl());
            item.setExtCode(goods == null ? null : goods.getExtCode());
            item.setSkcList(mapMatchedSkcItems(rawProduct.get("skcList"), goods == null ? List.of() : goodsSkuByGoodsId.getOrDefault(goods.getId(), List.of()), goodsSkuByProductSkuId, specInfoBySkuId, goods == null ? null : goods.getMainImageUrl()));
            items.add(item);
        }
        response.setMatchList(items);
        return response;
    }

    private List<ActivityDTO.MatchedSkcItem> mapMatchedSkcItems(Object skcListObject,
                                                                List<TemuGoodsSku> localSkus,
                                                                Map<Long, TemuGoodsSku> goodsSkuByProductSkuId,
                                                                Map<Long, String> specInfoBySkuId,
                                                                String fallbackImageUrl) {
        List<ActivityDTO.MatchedSkcItem> items = new ArrayList<>();
        for (Map<String, Object> skcRaw : toMapList(skcListObject)) {
            ActivityDTO.MatchedSkcItem item = new ActivityDTO.MatchedSkcItem();
            item.setSkcId(toLong(skcRaw.get("skcId")));
            item.setDailyPrice(toInt(skcRaw.get("dailyPrice")));
            item.setActivityPrice(toInt(skcRaw.get("activityPrice")));
            item.setSuggestActivityPrice(toInt(skcRaw.get("suggestActivityPrice")));
            item.setCurrency(toStr(firstNonNull(skcRaw.get("currency"), skcRaw.get("priceCurrency"))));
            item.setSitePriceList(mapSitePriceItems(skcRaw.get("sitePriceList")));

            List<ActivityDTO.MatchedSkuItem> skuItems = new ArrayList<>();
            for (Map<String, Object> skuRaw : toMapList(skcRaw.get("skuList"))) {
                Long skuId = toLong(skuRaw.get("skuId"));
                TemuGoodsSku localSku = goodsSkuByProductSkuId.get(skuId);
                if (localSku == null && !localSkus.isEmpty()) {
                    localSku = localSkus.stream().filter(candidate -> Objects.equals(candidate.getProductSkuId(), skuId)).findFirst().orElse(null);
                }
                ActivityDTO.MatchedSkuItem skuItem = new ActivityDTO.MatchedSkuItem();
                skuItem.setSkuId(skuId);
                skuItem.setDailyPrice(toInt(skuRaw.get("dailyPrice")));
                skuItem.setActivityPrice(toInt(skuRaw.get("activityPrice")));
                skuItem.setSuggestActivityPrice(toInt(skuRaw.get("suggestActivityPrice")));
                skuItem.setCurrency(toStr(firstNonNull(skuRaw.get("currency"), item.getCurrency())));
                skuItem.setSitePriceList(mapSitePriceItems(skuRaw.get("sitePriceList")));
                skuItem.setExtCode(localSku == null ? null : localSku.getExtCode());
                skuItem.setSpecInfo(localSku == null ? null : specInfoBySkuId.get(localSku.getId()));
                skuItem.setImageUrl(resolveSkuImageUrl(localSku, fallbackImageUrl));
                skuItems.add(skuItem);
            }
            item.setSkuList(skuItems);
            items.add(item);
        }
        return items;
    }

    private ActivityDTO.ActivityInfo mapActivityInfo(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        ActivityDTO.ActivityInfo item = new ActivityDTO.ActivityInfo();
        item.setBenefitLabelName(toStringList(raw.get("benefitLabelName")));
        item.setActivityContent(toStr(raw.get("activityContent")));
        item.setSessionAssignType(toInt(raw.get("sessionAssignType")));
        item.setActivityName(toStr(raw.get("activityName")));
        item.setActivityLabelTag(toInt(raw.get("activityLabelTag")));
        item.setActivityType(toInt(raw.get("activityType")));
        return item;
    }

    private ActivityDTO.ThematicInfo mapThematicInfo(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        ActivityDTO.ThematicInfo item = new ActivityDTO.ThematicInfo();
        item.setBenefitLabelName(toStringList(raw.get("benefitLabelName")));
        item.setDurationDays(toInt(raw.get("durationDays")));
        item.setEnrollSource(toInt(raw.get("enrollSource")));
        item.setSalePromotionLabel(toStr(raw.get("salePromotionLabel")));
        item.setEnrollDeadLine(toLong(raw.get("enrollDeadLine")));
        item.setActivityLabelTag(toInt(raw.get("activityLabelTag")));
        item.setEnrollStartAt(toLong(raw.get("enrollStartAt")));
        item.setStartTime(toLong(raw.get("startTime")));
        item.setEndTime(toLong(raw.get("endTime")));
        item.setActivityThematicName(toStr(raw.get("activityThematicName")));
        item.setActivityThematicId(toLong(raw.get("activityThematicId")));
        item.setSites(mapSiteItems(raw.get("sites")));
        return item;
    }

    private List<ActivityDTO.RequirementItem> mapRequirementList(Object requirements) {
        List<ActivityDTO.RequirementItem> items = new ArrayList<>();
        for (Map<String, Object> raw : toMapList(requirements)) {
            ActivityDTO.RequirementItem item = new ActivityDTO.RequirementItem();
            item.setCheckStatus(toInt(raw.get("checkStatus")));
            item.setCheckStatusDesc(toStr(raw.get("checkStatusDesc")));
            item.setRequirementCode(toInt(raw.get("requirementCode")));
            item.setRequirementType(toStr(raw.get("requirementType")));
            item.setRequirementDesc(toStr(raw.get("requirementDesc")));
            items.add(item);
        }
        return items;
    }

    private List<ActivityDTO.SessionItem> mapSessionItems(Object sessions) {
        List<ActivityDTO.SessionItem> items = new ArrayList<>();
        for (Map<String, Object> raw : toMapList(sessions)) {
            ActivityDTO.SessionItem item = new ActivityDTO.SessionItem();
            item.setSessionId(toLong(raw.get("sessionId")));
            item.setSessionName(toStr(raw.get("sessionName")));
            item.setSessionStatus(toInt(raw.get("sessionStatus")));
            item.setSiteId(toInt(raw.get("siteId")));
            item.setSiteName(toStr(raw.get("siteName")));
            item.setStartTime(toLong(raw.get("startTime")));
            item.setEndTime(toLong(raw.get("endTime")));
            item.setDurationDays(toInt(raw.get("durationDays")));
            item.setStartDateStr(toStr(raw.get("startDateStr")));
            item.setEndDateStr(toStr(raw.get("endDateStr")));
            items.add(item);
        }
        return items;
    }

    private List<ActivityDTO.SiteItem> mapSiteItems(Object sites) {
        List<ActivityDTO.SiteItem> items = new ArrayList<>();
        for (Map<String, Object> raw : toMapList(sites)) {
            ActivityDTO.SiteItem item = new ActivityDTO.SiteItem();
            item.setSiteId(toInt(raw.get("siteId")));
            item.setSiteName(toStr(raw.get("siteName")));
            items.add(item);
        }
        return items;
    }

    private List<ActivityDTO.SitePriceItem> mapSitePriceItems(Object sitePrices) {
        List<ActivityDTO.SitePriceItem> items = new ArrayList<>();
        for (Map<String, Object> raw : toMapList(sitePrices)) {
            ActivityDTO.SitePriceItem item = new ActivityDTO.SitePriceItem();
            item.setSiteId(toInt(raw.get("siteId")));
            item.setSiteName(toStr(raw.get("siteName")));
            item.setDailyPrice(toInt(raw.get("dailyPrice")));
            item.setSuggestActivityPrice(toInt(raw.get("suggestActivityPrice")));
            item.setActivityPrice(toInt(raw.get("activityPrice")));
            item.setActivityDiscount(toInt(raw.get("activityDiscount")));
            items.add(item);
        }
        return items;
    }

    private List<LocalActivityCandidate> loadLocalActivityCandidates(ActivityDTO.RecommendationRequest request, int limit) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        Integer minListedDays = request.getMinListedDays();
        Long listedBeforeSeconds = minListedDays == null || minListedDays <= 0
                ? null
                : nowSeconds - minListedDays.longValue() * 24 * 60 * 60;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("shopId", request.getShopId())
                .addValue("activityType", request.getActivityType())
                .addValue("activityThematicId", request.getActivityThematicId())
                .addValue("hasActivityThematicId", request.getActivityThematicId() != null)
                .addValue("minSupplierPrice", request.getMinSupplierPrice())
                .addValue("hasMinSupplierPrice", request.getMinSupplierPrice() != null)
                .addValue("maxSupplierPrice", request.getMaxSupplierPrice())
                .addValue("hasMaxSupplierPrice", request.getMaxSupplierPrice() != null)
                .addValue("excludeEnrolled", !Boolean.FALSE.equals(request.getExcludeEnrolled()))
                .addValue("maxSalesQuantity", request.getMaxSalesQuantity())
                .addValue("hasMaxSalesQuantity", request.getMaxSalesQuantity() != null)
                .addValue("minListedDays", minListedDays)
                .addValue("hasMinListedDays", minListedDays != null && minListedDays > 0)
                .addValue("listedBeforeSeconds", listedBeforeSeconds)
                .addValue("limit", limit);
        return namedParameterJdbcTemplate.query(ACTIVITY_RECOMMEND_LOCAL_CANDIDATES_SQL, params, rs -> {
            List<LocalActivityCandidate> list = new ArrayList<>();
            while (rs.next()) {
                Long productId = toLong(rs.getObject("product_id"));
                if (productId == null) {
                    continue;
                }
                Long temuCreatedAt = toLong(rs.getObject("temu_created_at"));
                Integer listedDays = temuCreatedAt == null || temuCreatedAt <= 0
                        ? null
                        : Math.max(0, (int) ((nowSeconds - temuCreatedAt) / (24 * 60 * 60)));
                list.add(new LocalActivityCandidate(
                        rs.getLong("goods_id"),
                        productId,
                        rs.getString("product_name"),
                        rs.getString("main_image_url"),
                        rs.getString("ext_code"),
                        toInt(rs.getObject("min_supplier_price")),
                        toInt(rs.getObject("max_supplier_price")),
                        toInt(rs.getObject("min_purchase_price")),
                        toInt(rs.getObject("max_purchase_price")),
                        rs.getLong("sales_quantity"),
                        listedDays
                ));
            }
            return list;
        });
    }

    private ActivityDTO.RecommendedProductItem buildRecommendedProduct(LocalActivityCandidate local,
                                                                       ActivityDTO.MatchedProductItem matched,
                                                                       ActivityDTO.RecommendationRequest request) {
        Integer suggestActivityPrice = resolveSuggestActivityPrice(matched);
        Integer purchasePrice = local.maxPurchasePrice();
        Integer estimatedProfit = suggestActivityPrice != null && purchasePrice != null
                ? suggestActivityPrice - purchasePrice
                : null;
        Double estimatedProfitRate = estimatedProfit != null && suggestActivityPrice != null && suggestActivityPrice > 0
                ? estimatedProfit * 100.0 / suggestActivityPrice
                : null;
        Integer minProfitCents = request.getMinProfitCents() == null ? 0 : request.getMinProfitCents();
        Integer minProfitRatePercent = request.getMinProfitRatePercent() == null ? 0 : request.getMinProfitRatePercent();

        String decision = "可报名";
        String reason = "利润和销量条件符合，适合小库存测试活动";
        if (suggestActivityPrice == null) {
            decision = "需确认";
            reason = "TEMU 未返回建议活动价，需要人工确认活动价";
        } else if (purchasePrice == null) {
            decision = "需确认";
            reason = "本地没有采购价，无法自动判断利润";
        } else if (estimatedProfit == null || estimatedProfit < minProfitCents) {
            decision = "不建议";
            reason = "建议活动价低于最低利润要求";
        } else if (estimatedProfitRate != null && estimatedProfitRate < minProfitRatePercent) {
            decision = "不建议";
            reason = "建议活动价利润率低于要求";
        } else if (local.salesQuantity() != null && request.getMaxSalesQuantity() != null && local.salesQuantity() > request.getMaxSalesQuantity()) {
            decision = "需确认";
            reason = "销量超过当前筛选条件，建议人工确认是否仍要报活动";
        }

        ActivityDTO.RecommendedProductItem item = new ActivityDTO.RecommendedProductItem();
        item.setProductId(local.productId());
        item.setGoodsId(local.goodsId());
        item.setProductName(local.productName());
        item.setMainImageUrl(local.mainImageUrl());
        item.setExtCode(local.extCode());
        item.setCurrentSupplyPrice(local.maxSupplierPrice());
        item.setSuggestActivityPrice(suggestActivityPrice);
        item.setMinPurchasePrice(local.minPurchasePrice());
        item.setMaxPurchasePrice(local.maxPurchasePrice());
        item.setEstimatedProfit(estimatedProfit);
        item.setEstimatedProfitRate(estimatedProfitRate);
        item.setSalesQuantity(local.salesQuantity());
        item.setListedDays(local.listedDays());
        int defaultStock = clamp(request.getDefaultActivityStock(), 1, 99999, 5);
        int suggestedStock = Math.max(
                matched.getSuggestActivityStock() == null ? 0 : matched.getSuggestActivityStock(),
                matched.getTargetActivityStock() == null ? 0 : matched.getTargetActivityStock()
        );
        item.setActivityStock(Math.max(defaultStock, suggestedStock));
        item.setDecision(decision);
        item.setReason(reason);
        item.setMatchedProduct(matched);
        return item;
    }

    private Integer resolveSuggestActivityPrice(ActivityDTO.MatchedProductItem matched) {
        if (matched == null || matched.getSkcList() == null) {
            return null;
        }
        Integer best = null;
        for (ActivityDTO.MatchedSkcItem skc : matched.getSkcList()) {
            best = minNonNull(best, firstPrice(skc.getSuggestActivityPrice(), skc.getActivityPrice()));
            if (skc.getSitePriceList() != null) {
                for (ActivityDTO.SitePriceItem sitePrice : skc.getSitePriceList()) {
                    best = minNonNull(best, firstPrice(sitePrice.getSuggestActivityPrice(), sitePrice.getActivityPrice()));
                }
            }
            if (skc.getSkuList() != null) {
                for (ActivityDTO.MatchedSkuItem sku : skc.getSkuList()) {
                    best = minNonNull(best, firstPrice(sku.getSuggestActivityPrice(), sku.getActivityPrice()));
                    if (sku.getSitePriceList() != null) {
                        for (ActivityDTO.SitePriceItem sitePrice : sku.getSitePriceList()) {
                            best = minNonNull(best, firstPrice(sitePrice.getSuggestActivityPrice(), sitePrice.getActivityPrice()));
                        }
                    }
                }
            }
        }
        return best;
    }

    private Integer firstPrice(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private Integer minNonNull(Integer left, Integer right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.min(left, right);
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int normalized = value == null ? fallback : value;
        return Math.max(min, Math.min(max, normalized));
    }

    private <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(new ArrayList<>(list.subList(i, Math.min(i + batchSize, list.size()))));
        }
        return batches;
    }

    private TemuOpenApiClient buildClient(String shopId) {
        TemuOpenApiCredentials creds = credentialService.getTemuOpenApiCredentialsByShopIdOrThrow(shopId);
        return new TemuOpenApiClient(creds);
    }

    private Map<String, Object> callResultMap(TemuOpenApiClient client, String apiType, Map<String, Object> params, String errorPrefix) {
        try {
            TemuOpenApiClient.ApiResult result = client.callApiParsed(apiType, params);
            if (!result.success) {
                throw new IllegalStateException(errorPrefix + ": " + result.errorMsg);
            }
            Map<String, Object> resultMap = result.resultAsMap();
            return resultMap == null ? Map.of() : resultMap;
        } catch (Exception e) {
            throw new IllegalStateException(errorPrefix + ": " + e.getMessage(), e);
        }
    }

    private String resolveSkuImageUrl(TemuGoodsSku goodsSku, String fallbackImageUrl) {
        if (goodsSku == null || goodsSku.getRawJson() == null || goodsSku.getRawJson().isBlank()) {
            return fallbackImageUrl;
        }
        try {
            Map<String, Object> raw = toMap(GSON.fromJson(goodsSku.getRawJson(), Map.class));
            if (raw == null) {
                return fallbackImageUrl;
            }
            for (String key : List.of("thumbUrl", "specShowImageUrl", "mainImageUrl", "imageUrl", "image")) {
                String value = toStr(raw.get(key));
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        } catch (Exception ignored) {
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

    private String toJson(Object value) {
        return value == null ? null : GSON.toJson(value);
    }

    private List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String str = toStr(item);
                if (str != null) {
                    result.add(str);
                }
            }
        }
        return result;
    }

    private List<Integer> toIntList(Object value) {
        List<Integer> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Integer number = toInt(item);
                if (number != null) {
                    result.add(number);
                }
            }
        }
        return result;
    }

    private List<Long> toLongList(Object value) {
        List<Long> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Long number = toLong(item);
                if (number != null) {
                    result.add(number);
                }
            }
        }
        return result;
    }

    private List<Object> toObjectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = toMap(item);
            if (map != null) {
                result.add(map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private String toStr(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? null : str;
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

    private Integer toInt(Object value, int defaultValue) {
        Integer number = toInt(value);
        return number == null ? defaultValue : number;
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

    private record LocalActivityCandidate(Long goodsId,
                                          Long productId,
                                          String productName,
                                          String mainImageUrl,
                                          String extCode,
                                          Integer minSupplierPrice,
                                          Integer maxSupplierPrice,
                                          Integer minPurchasePrice,
                                          Integer maxPurchasePrice,
                                          Long salesQuantity,
                                          Integer listedDays) {
    }

}
