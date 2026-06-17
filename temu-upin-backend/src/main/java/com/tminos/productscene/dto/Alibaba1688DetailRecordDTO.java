package com.tminos.productscene.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Alibaba1688DetailRecordDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListItem {
        private Long id;
        private String offerId;
        private String detailUrl;
        private String canonicalUrl;
        private String productName;
        private String companyName;
        private String productMainImage;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private BigDecimal repeatCustomerRate;
        private BigDecimal serviceScore;
        private BigDecimal onTimeDeliveryRate;
        private BigDecimal shopPositiveRate;
        private Boolean powerSeller;
        private String settledYearsText;
        private String mainBusiness;
        private String sourcePlatform;
        private String status;
        private String lastError;
        private Long lastTaskId;
        private Long lastCredentialId;
        private String credentialName;
        private LocalDateTime lastCollectedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetailResponse {
        private Long id;
        private String offerId;
        private String detailUrl;
        private String canonicalUrl;
        private String productName;
        private String companyName;
        private String productMainImage;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private BigDecimal repeatCustomerRate;
        private BigDecimal serviceScore;
        private BigDecimal onTimeDeliveryRate;
        private BigDecimal shopPositiveRate;
        private Boolean powerSeller;
        private String settledYearsText;
        private String mainBusiness;
        private String sourcePlatform;
        private String status;
        private String lastError;
        private Long lastTaskId;
        private Long lastCredentialId;
        private String credentialName;
        private String rawHtml;
        private String extractedJson;
        private String parsedJson;
        private LocalDateTime lastCollectedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
