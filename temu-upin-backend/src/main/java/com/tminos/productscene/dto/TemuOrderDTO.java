package com.tminos.productscene.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TemuOrderDTO {

    @Data
    @Builder
    public static class ListItem {
        private Long id;
        private Long shopRecordId;
        private String shopId;
        private String shopName;
        private String orderSn;
        private String parentOrderSn;
        private String dianxiaomiPackageNumber;
        private String goodsId;
        private String goodsName;
        private String spec;
        private String thumbUrl;
        private Integer quantity;
        private Integer orderStatus;
        private Integer parentOrderStatus;
        private String orderPaymentType;
        private Long orderTimeMs;
        private Long updateTimeMs;
        private Long matchedSpuId;
        private String matchedTemuSkuId;
        private String matchedOriginSkuId;
        private String matchedSkuSpecName;
        private String matchedProductName;
        private BigDecimal matchedSupplyPrice;
        private Long salesQuantity;
        private Long aftersaleQuantity;
        private BigDecimal aftersaleRate;
        private Long signedQuantity;
        private Long signedAftersaleQuantity;
        private String matchStatus;
        private String matchMessage;
        private String logisticsTrackingNumber;
        private String logisticsTrackStatusName;
        private BigDecimal firstLegLogisticsFee;
        private BigDecimal chargeWeight;
        private String orderFeeDetailJson;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    public static class Detail {
        private Long id;
        private Long shopRecordId;
        private String shopId;
        private String shopName;
        private String orderSn;
        private String parentOrderSn;
        private String dianxiaomiPackageNumber;
        private String goodsId;
        private String goodsName;
        private String spec;
        private String thumbUrl;
        private Integer quantity;
        private Integer orderStatus;
        private Integer parentOrderStatus;
        private String orderPaymentType;
        private String inventoryDeductionWarehouseId;
        private String inventoryDeductionWarehouseName;
        private Long orderTimeMs;
        private Long updateTimeMs;
        private Long earliestTimeGetShippingDocumentMs;
        private Long expectShipLatestTimeMs;
        private Integer regionId;
        private Integer siteId;
        private String productSkusJson;
        private String rawJson;
        private Long matchedSpuId;
        private String matchedTemuSkuId;
        private String matchedOriginSkuId;
        private String matchedSkuSpecName;
        private String matchedProductName;
        private BigDecimal matchedSupplyPrice;
        private Long salesQuantity;
        private Long aftersaleQuantity;
        private BigDecimal aftersaleRate;
        private Long signedQuantity;
        private Long signedAftersaleQuantity;
        private String matchStatus;
        private String matchMessage;
        private LogisticsSnapshot logistics;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class SyncRequest {
        private Long shopRecordId;
        private Boolean fullSync;
        private Integer hoursBack;
    }

    @Data
    @Builder
    public static class SyncResponse {
        private Long shopRecordId;
        private String shopId;
        private String shopName;
        private boolean success;
        private int totalCount;
        private int createdCount;
        private int updatedCount;
        private int matchedCount;
        private int logisticsRefreshedCount;
        private String message;
    }

    @Data
    public static class RefreshLogisticsRequest {
        private String providerCode;
        private String referenceNo;
        private String shippingMethodNo;
        private String trackingNumber;
    }

    @Data
    @Builder
    public static class LogisticsSnapshot {
        private String providerCode;
        private String providerName;
        private String referenceNo;
        private String shippingMethodNo;
        private String trackingNumber;
        private String destinationCountry;
        private String trackStatus;
        private String trackStatusName;
        private BigDecimal grossWeight;
        private BigDecimal volumeWeight;
        private BigDecimal chargeWeight;
        private BigDecimal firstLegLogisticsFee;
        private String trackDetailsJson;
        private String orderFeeDetailJson;
        private String orderWeightInfoJson;
        private LocalDateTime lastSyncedAt;
    }
}
