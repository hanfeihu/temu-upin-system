package com.tminos.productscene.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SupplierProductSubmissionDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long id;
        private String supplierName;
        private String supplierPhone;
        private String supplierAddress;
        private String productName;
        private BigDecimal supplyPrice;
        private BigDecimal weightG;
        private BigDecimal lengthCm;
        private BigDecimal widthCm;
        private BigDecimal heightCm;
        @Builder.Default
        private List<String> imageUrls = new ArrayList<>();
        private String status;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveRequest {
        private String supplierName;
        private String supplierPhone;
        private String supplierAddress;
        private String productName;
        private BigDecimal supplyPrice;
        private BigDecimal weightG;
        private BigDecimal lengthCm;
        private BigDecimal widthCm;
        private BigDecimal heightCm;
        @Builder.Default
        private List<String> imageUrls = new ArrayList<>();
        private String status;
        private String remark;
    }
}
