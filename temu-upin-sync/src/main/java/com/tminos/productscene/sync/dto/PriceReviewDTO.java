package com.tminos.productscene.sync.dto;

import lombok.Data;

import java.math.BigDecimal;
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
        private Integer purchasePrice;
        private Integer collectedPrice;
        private String collectedPriceSource;
        private Long collectedProductCollectionId;
        private String collectedProductId;
        private String collectedProductName;
        private String collectedProductUrl;
        private String collectedSkuId;
        private String collectedSkuSpec;
        private BigDecimal collectedBaseFreight;
        private BigDecimal collectedMaxWeightG;
        private LocalDateTime collectedPublishedAt1688;
        private LocalDateTime collectedPushedAt;
        private String collectedCompanyName;
        private String collectedCompanyLocation;
        private String collectedShippingLocation;
        private Long collectedSelectionPoolId;
        private String collectedMerchantRepeatCustomerRate;
        private String collectedMerchantServiceScore;
        private String collectedMerchantOnTimeDeliveryRate;
        private String collectedMerchantShopPositiveRate;
        private Boolean collectedMerchantPowerSeller;
        private String collectedMerchantSettledYears;
        private String collectedMerchantMainBusiness;
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
    public static class BatchLocalCompleteRequest {
        private String shopId;
        private List<Long> orderIds;
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

    @Data
    public static class LowPriceRejectWorkerConfigView {
        private Long id;
        private String configName;
        private Boolean enabled;
        private Integer maxSuggestSupplyPrice;
        private Long pollMs;
        private Integer batchSize;
        private Integer reasonType;
        private String reasonText;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class UpdateLowPriceRejectWorkerConfigRequest {
        private String configName;
        private Integer maxSuggestSupplyPrice;
        private Long pollMs;
        private Integer batchSize;
        private Integer reasonType;
        private String reasonText;
    }

    @Data
    public static class LowPriceRejectWorkerStatusView {
        private Boolean running;
        private Boolean enabled;
        private Integer maxSuggestSupplyPrice;
        private Long pollMs;
        private Integer batchSize;
        private Integer reasonType;
        private String reasonText;
        private Long successCount;
        private Long failureCount;
        private Long skippedCount;
        private Long lastOrderId;
        private String lastShopId;
        private LocalDateTime startedAt;
        private LocalDateTime stoppedAt;
        private LocalDateTime lastScanAt;
        private LocalDateTime lastWorkAt;
        private LocalDateTime lastErrorAt;
        private String lastError;
    }
}
