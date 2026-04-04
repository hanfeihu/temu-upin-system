package com.tminos.productscene.sync.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class ActivityDTO {

    @Data
    public static class ListRequest {
        private String shopId;
        private Integer activityType;
        private Integer enrollStatus;
        private Integer page = 1;
        private Integer pageSize = 20;
    }

    @Data
    public static class ActivityItem {
        private Long id;
        private String shopId;
        private Integer activityType;
        private String activityName;
        private String activityContent;
        private Integer activityLabelTag;
        private Integer sessionAssignType;
        private String benefitLabelsJson;
        private List<ThematicItem> thematicList;
    }

    @Data
    public static class ActivityDetail {
        private Boolean canEnroll;
        private ActivityInfo activityInfo;
        private ThematicInfo thematicInfo;
        private List<RequirementItem> requirements;
        private List<RequirementItem> mallAptitude;
    }

    @Data
    public static class ActivityInfo {
        private List<String> benefitLabelName;
        private String activityContent;
        private Integer sessionAssignType;
        private String activityName;
        private Integer activityLabelTag;
        private Integer activityType;
    }

    @Data
    public static class ThematicInfo {
        private List<String> benefitLabelName;
        private Integer durationDays;
        private Integer enrollSource;
        private String salePromotionLabel;
        private Long enrollDeadLine;
        private Integer activityLabelTag;
        private Long enrollStartAt;
        private Long startTime;
        private Long endTime;
        private String activityThematicName;
        private Long activityThematicId;
        private List<SiteItem> sites;
    }

    @Data
    public static class RequirementItem {
        private Integer checkStatus;
        private String checkStatusDesc;
        private Integer requirementCode;
        private String requirementType;
        private String requirementDesc;
    }

    @Data
    public static class ThematicItem {
        private Long id;
        private Long activityThematicId;
        private String activityThematicName;
        private Integer enrollSource;
        private Long enrollStartAt;
        private Long enrollDeadLine;
        private Long startTime;
        private Long endTime;
        private Integer durationDays;
        private String salePromotionLabel;
        private String benefitLabelsJson;
        private String sitesJson;
    }

    @Data
    public static class SessionItem {
        private Long id;
        private Long sessionId;
        private String sessionName;
        private Integer sessionStatus;
        private Integer siteId;
        private String siteName;
        private Long startTime;
        private Long endTime;
        private Integer durationDays;
        private String startDateStr;
        private String endDateStr;
    }

    @Data
    public static class SiteItem {
        private Integer siteId;
        private String siteName;
    }

    @Data
    public static class SitePriceItem {
        private Integer siteId;
        private String siteName;
        private Integer dailyPrice;
        private Integer suggestActivityPrice;
        private Integer activityPrice;
        private Integer activityDiscount;
    }

    @Data
    public static class ProductMatchRequest {
        private String shopId;
        private Integer activityType;
        private Long activityThematicId;
        private String searchScrollContext;
        private Integer rowCount;
        private List<Long> productIds;
    }

    @Data
    public static class ProductMatchResponse {
        private String searchScrollContext;
        private Boolean hasMore;
        private List<MatchedProductItem> matchList;
    }

    @Data
    public static class MatchedProductItem {
        private Long productId;
        private String productName;
        private String currency;
        private Integer isApparel;
        private Integer targetActivityStock;
        private Integer suggestActivityStock;
        private List<Long> enrollSessionIdList;
        private List<SiteItem> sites;
        private List<MatchedSkcItem> skcList;
        private String mainImageUrl;
        private String extCode;
    }

    @Data
    public static class MatchedSkcItem {
        private Long skcId;
        private Integer dailyPrice;
        private Integer activityPrice;
        private Integer suggestActivityPrice;
        private String currency;
        private List<SitePriceItem> sitePriceList;
        private List<MatchedSkuItem> skuList;
    }

    @Data
    public static class MatchedSkuItem {
        private Long skuId;
        private Integer dailyPrice;
        private Integer activityPrice;
        private Integer suggestActivityPrice;
        private String currency;
        private List<SitePriceItem> sitePriceList;
        private String extCode;
        private String specInfo;
        private String imageUrl;
    }

    @Data
    public static class SessionQueryRequest {
        private String shopId;
        private Integer activityType;
        private Long activityThematicId;
        private Long startTime;
        private Long endTime;
        private List<Long> productIds;
    }

    @Data
    public static class SessionQueryResponse {
        private List<Integer> siteIds;
        private List<SessionItem> list;
        private java.util.Map<String, List<SessionItem>> productCanEnrollSessionMap;
    }

    @Data
    public static class EnrollmentItem {
        private Long id;
        private String shopId;
        private Long enrollId;
        private Long productId;
        private Long goodsId;
        private Integer activityType;
        private String activityTypeName;
        private Long activityThematicId;
        private String activityThematicName;
        private Integer enrollStatus;
        private Long enrollTime;
        private Integer activityStock;
        private Integer remainingActivityStock;
        private String currency;
        private Integer soldStatus;
        private Long sessionStartTime;
        private Long sessionEndTime;
        private String assignSessionsJson;
        private LocalDateTime syncedAt;
        private List<EnrollPriceItem> priceList;
    }

    @Data
    public static class EnrollPriceItem {
        private Long id;
        private String level;
        private Long skcId;
        private Long skuId;
        private Integer siteId;
        private String siteName;
        private Integer dailyPrice;
        private Integer activityPrice;
        private Integer activityDiscount;
        private String currency;
    }

    @Data
    public static class BatchEnrollRequest {
        private String shopId;
        private Integer activityType;
        private Long activityThematicId;
        private List<EnrollProductItem> productList;
    }

    @Data
    public static class EnrollProductItem {
        private Long productId;
        private Integer activityStock;
        private List<Long> sessionIds;
        private List<EnrollSkcItem> skcList;
    }

    @Data
    public static class EnrollSkcItem {
        private Long skcId;
        private Integer activityPrice;
        private List<EnrollSitePriceItem> siteActivityPriceList;
        private List<EnrollSkuItem> skuList;
    }

    @Data
    public static class EnrollSkuItem {
        private Long skuId;
        private Integer activityPrice;
        private List<EnrollSitePriceItem> siteActivityPriceList;
    }

    @Data
    public static class EnrollSitePriceItem {
        private Integer siteId;
        private Integer activityPrice;
    }
}
