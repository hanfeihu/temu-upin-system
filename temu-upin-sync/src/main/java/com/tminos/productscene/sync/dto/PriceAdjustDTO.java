package com.tminos.productscene.sync.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PriceAdjustDTO {

    @Data
    public static class ListRequest {
        private String shopId;
        private Integer status;
        private String reviewAction;
        private Integer page = 1;
        private Integer pageSize = 20;
    }

    @Data
    public static class AdjustOrderItem {
        private Long id;
        private String shopId;
        private String priceOrderSn;
        private Long skcId;
        private String productName;
        private Integer priceType;
        private String source;
        private String adjustReason;
        private String newSupplyPrice;
        private String priceCurrency;
        private String rejectReason;
        private Boolean trafficLowExpose;
        private Integer status;
        private String siteNamesJson;
        private List<String> siteNameList;
        private String reviewAction;
        private LocalDateTime reviewAt;
        private LocalDateTime syncedAt;
        private List<AdjustSkuItem> skuList;
        private List<AdjustSkuItem> skuInfoList;
    }

    @Data
    public static class AdjustSkuItem {
        private Long id;
        private Long productSkuId;
        private Integer price;
        private String spec;
        private String imageUrl;
        private String extCode;
        private String specInfo;
        private Integer currentSupplyPrice;
        private Integer purchasePrice;
        private Long salesQuantity;
        private Long aftersaleQuantity;
        private Long signedQuantity;
        private BigDecimal firstLegLogisticsFee;
        private Long logisticsRefreshOrderId;
    }

    @Data
    public static class BatchReviewRequest {
        private String shopId;
        private List<Long> orderIds;
        private String action;
        private String rejectReason;
    }
}
