package com.tminos.productscene.sync.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class TemuGoodsDTO {

    @Data
    public static class ListRequest {
        private String shopId;
        private String keyword;
        private Long productSkcId;
        private Long productId;
        private Integer selectStatus;
        private Integer leafCatId;
        private Integer skcSiteStatus;
        private Boolean matchJitMode;
        private Integer page = 1;
        private Integer pageSize = 20;
    }

    @Data
    public static class GoodsListItem {
        private Long id;
        private String shopId;
        private Long productId;
        private Long productSkcId;
        private String productName;
        private String extCode;
        private String mainImageUrl;
        private Integer skcSiteStatus;
        private String leafCatName;
        private Integer leafCatId;
        private Boolean matchJitMode;
        private Integer selectStatus;
        private Integer skuCount;
        private LocalDateTime syncedAt;
    }

    @Data
    public static class GoodsDetail {
        private Long id;
        private String shopId;
        private Long productId;
        private Long productSkcId;
        private String productName;
        private String extCode;
        private String mainImageUrl;
        private Integer skcSiteStatus;
        private String leafCatName;
        private Integer leafCatId;
        private String categoriesJson;
        private Boolean isSupportPersonalization;
        private Boolean matchJitMode;
        private Boolean matchSkcJitMode;
        private String freightTemplateId;
        private Integer shipmentLimitSecond;
        private Long temuCreatedAt;
        private LocalDateTime syncedAt;

        private Integer selectStatus;
        private Integer applyJitStatus;
        private Boolean suggestCloseJit;

        private List<SkuItem> skuList;
        private List<SiteItem> siteList;
        private List<PropertyItem> propertyList;
        private List<SkuPriceChangeItem> priceChangeList;
    }

    @Data
    public static class SkuItem {
        private Long id;
        private Long productSkuId;
        private String extCode;
        private String imageUrl;
        private Integer virtualStock;
        private Integer weightMg;
        private Integer lengthMm;
        private Integer widthMm;
        private Integer heightMm;
        private Boolean isSensitive;
        private Boolean isFragile;
        private Integer shippingMode;
        private List<SkuSpecItem> specList;
        private SkuPriceItem price;
    }

    @Data
    public static class SkuSpecItem {
        private Integer specId;
        private String specName;
        private Integer parentSpecId;
        private String parentSpecName;
    }

    @Data
    public static class SkuPriceItem {
        private Integer supplierPrice;
        private String currencyType;
        private List<SitePriceItem> sitePrices;
    }

    @Data
    public static class SitePriceItem {
        private Integer siteId;
        private Integer supplierPrice;
        private Integer priceReviewStatus;
    }

    @Data
    public static class SkuPriceChangeItem {
        private Long id;
        private Long productSkuId;
        private String imageUrl;
        private Integer siteId;
        private String siteName;
        private Integer oldSupplierPrice;
        private Integer newSupplierPrice;
        private LocalDateTime changedAt;
    }

    @Data
    public static class SiteItem {
        private Integer siteId;
        private String siteName;
    }

    @Data
    public static class PropertyItem {
        private Integer pid;
        private String propName;
        private Integer vid;
        private String propValue;
        private String valueUnit;
    }
}
