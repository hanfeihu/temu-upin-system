package com.tminos.productscene.sync.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class PriceReviewDTO {

    @Data
    public static class ListRequest {
        private String shopId;
        private Integer orderStatus;
        private String reviewAction;
        private Integer page = 1;
        private Integer pageSize = 20;
    }

    @Data
    public static class ReviewOrderItem {
        private Long id;
        private String shopId;
        private Long orderId;
        private Integer orderStatus;
        private Integer supplyPrice;
        private String priceCurrency;
        private Integer suggestSupplyPrice;
        private String suggestPriceCurrency;
        private Boolean canBargain;
        private String siteIdsJson;
        private String siteNamesJson;
        private String reviewAction;
        private LocalDateTime reviewAt;
        private String rejectReasonsJson;
        private LocalDateTime syncedAt;
        private List<ReviewSkuItem> skuList;
    }

    @Data
    public static class ReviewSkuItem {
        private Long id;
        private Long productSkuId;
        private Integer newPrice;
        // 关联的SKU信息（从goods_sku表联查）
        private String imageUrl;
        private String extCode;
        private String specInfo;
        private Integer currentSupplyPrice;
    }

    @Data
    public static class BatchReviewRequest {
        private String shopId;
        private List<Long> orderIds;
        private String action; // APPROVE or REJECT
        private List<BargainReasonItem> bargainReasonList;
        private List<RejectPriceItem> rejectPrices;
    }

    @Data
    public static class BargainReasonItem {
        private List<RejectReasonComponent> componentList;
        private List<String> externalLinkList;
    }

    @Data
    public static class RejectReasonComponent {
        private String reason;
        private Integer type;
    }

    @Data
    public static class RejectPriceItem {
        private Long orderId;
        private Long productSkuId;
        private Integer newPrice;
    }
}
