package com.tminos.productscene.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SupplierProductPackageDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long id;
        private Long submissionId;
        private String supplierName;
        private String productName;
        private String remark;
        private BigDecimal supplyPrice;
        private BigDecimal weightG;
        private BigDecimal lengthCm;
        private BigDecimal widthCm;
        private BigDecimal heightCm;
        @Builder.Default
        private List<String> sourceImageUrls = new ArrayList<>();
        private String aiTitle;
        private String aiTitleZh;
        @Builder.Default
        private List<String> generatedImageUrls = new ArrayList<>();
        private String status;
        private String lastError;
        private Long productCollectionId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PushRequest {
        @Builder.Default
        private List<String> targetShopIds = new ArrayList<>();
    }
}
