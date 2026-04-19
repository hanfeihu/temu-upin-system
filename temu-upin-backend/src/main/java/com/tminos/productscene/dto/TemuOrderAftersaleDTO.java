package com.tminos.productscene.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

public class TemuOrderAftersaleDTO {

    @Data
    @Builder
    public static class ListItem {
        private Long id;
        private Long shopRecordId;
        private String shopId;
        private String shopName;
        private String parentAfterSalesSn;
        private String parentOrderSn;
        private Integer afterSalesStatusGroup;
        private String afterSalesStatusGroupName;
        private Integer parentAfterSalesStatus;
        private String parentAfterSalesStatusName;
        private Integer afterSalesType;
        private String afterSalesTypeName;
        private Long createAtMs;
        private Long updateAtMs;
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
        private String message;
    }
}
