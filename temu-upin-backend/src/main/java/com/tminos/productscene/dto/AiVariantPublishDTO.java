package com.tminos.productscene.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class AiVariantPublishDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RawPublishRequest {
        private Long shopRecordId;
        private String sourceType;
        private String sourceBizType;
        private Long sourceBizId;
        private String sourceBizName;
        private String sourceNote;
        private String requestPayload;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateDraftRequest {
        private Long shopRecordId;
        private Long sourceSpuId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordSummary {
        private Long id;
        private Long shopRecordId;
        private String shopId;
        private String shopName;
        private String sourceType;
        private String sourceBizType;
        private Long sourceBizId;
        private String sourceBizName;
        private String sourceNote;
        private String status;
        private String goodsId;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private ParsedPreview preview;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordDetail {
        private Long id;
        private Long shopRecordId;
        private String shopId;
        private String shopName;
        private String sourceType;
        private String sourceBizType;
        private Long sourceBizId;
        private String sourceBizName;
        private String sourceNote;
        private String status;
        private String goodsId;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String requestPayload;
        private String responsePayload;
        private Boolean responseSuccess;
        private Integer responseErrorCode;
        private String responseErrorMsg;
        private String requestId;
        private ParsedPreview preview;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateDraftResponse {
        private Long shopRecordId;
        private String shopId;
        private String shopName;
        private Long sourceSpuId;
        private String sourceProductName;
        private String sourceProductMainImage;
        private String sourceType;
        private String sourceBizType;
        private Long sourceBizId;
        private String sourceBizName;
        private String sourceNote;
        private String requestPayload;
        private List<String> warnings;
        private ParsedPreview preview;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedPreview {
        private String productName;
        private String mainImageUrl;
        private List<String> carouselImageUrls;
        private List<String> detailImageUrls;
        private Integer skcCount;
        private Integer skuCount;
        private List<SkuPreview> skuList;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkuPreview {
        private String skcExtCode;
        private String skuExtCode;
        private String thumbUrl;
        private String supplierPriceText;
        private String siteSupplierPriceText;
        private List<String> specNames;
    }
}
