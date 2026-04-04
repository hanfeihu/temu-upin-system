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

    private final TemuActivityRepository activityRepository;
    private final TemuActivityThematicRepository thematicRepository;
    private final TemuActivitySessionRepository sessionRepository;
    private final TemuActivityEnrollmentRepository enrollmentRepository;
    private final TemuActivityEnrollPriceRepository enrollPriceRepository;
    private final TemuGoodsRepository goodsRepository;
    private final TemuGoodsSkuRepository goodsSkuRepository;
    private final TemuGoodsSkuSpecRepository goodsSkuSpecRepository;
    private final TemuOpenApiCredentialService credentialService;

    public TemuActivityService(TemuActivityRepository activityRepository,
                               TemuActivityThematicRepository thematicRepository,
                               TemuActivitySessionRepository sessionRepository,
                               TemuActivityEnrollmentRepository enrollmentRepository,
                               TemuActivityEnrollPriceRepository enrollPriceRepository,
                               TemuGoodsRepository goodsRepository,
                               TemuGoodsSkuRepository goodsSkuRepository,
                               TemuGoodsSkuSpecRepository goodsSkuSpecRepository,
                               TemuOpenApiCredentialService credentialService) {
        this.activityRepository = activityRepository;
        this.thematicRepository = thematicRepository;
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollPriceRepository = enrollPriceRepository;
        this.goodsRepository = goodsRepository;
        this.goodsSkuRepository = goodsSkuRepository;
        this.goodsSkuSpecRepository = goodsSkuSpecRepository;
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
        return activities.stream().map(a -> {
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

            return item;
        }).collect(Collectors.toList());
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

    public Page<TemuActivityEnrollment> listEnrollments(String shopId, Integer activityType, Integer enrollStatus, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        if (enrollStatus != null) {
            return enrollmentRepository.findByShopIdAndEnrollStatus(shopId, enrollStatus, pageRequest);
        }
        if (activityType != null) {
            return enrollmentRepository.findByShopIdAndActivityType(shopId, activityType, pageRequest);
        }
        return enrollmentRepository.findByShopId(shopId, pageRequest);
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

            List<Long> productIds = request.getProductList().stream()
                    .map(ActivityDTO.EnrollProductItem::getProductId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            String refreshWarning = null;
            if (!productIds.isEmpty() && successCount > 0) {
                try {
                    refreshEnrollmentRecords(request.getShopId(), request.getActivityType(), request.getActivityThematicId(), productIds);
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

    private void refreshEnrollmentRecords(String shopId, Integer activityType, Long activityThematicId, List<Long> productIds) throws Exception {
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

    private <T> List<List<T>> partition(List<T> source, int batchSize) {
        List<List<T>> result = new ArrayList<>();
        for (int index = 0; index < source.size(); index += batchSize) {
            result.add(new ArrayList<>(source.subList(index, Math.min(index + batchSize, source.size()))));
        }
        return result;
    }

}
