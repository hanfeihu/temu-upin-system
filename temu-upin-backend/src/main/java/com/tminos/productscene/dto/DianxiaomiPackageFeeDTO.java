package com.tminos.productscene.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class DianxiaomiPackageFeeDTO {

    @Data
    public static class ImportRequest {
        private List<String> packageNumbers;
    }

    @Data
    @Builder
    public static class FeeDetailItem {
        private String feeKindCode;
        private String feeKindName;
        private BigDecimal amount;
        private BigDecimal currencyAmount;
        private String currencyCode;
        private String currencyName;
        private BigDecimal currencyRate;
        private String note;
        private String occurDate;
        private String billDate;
        private String createDate;
    }

    @Data
    @Builder
    public static class ListItem {
        private Long id;
        private String dianxiaomiPackageNumber;
        private BigDecimal totalFee;
        private BigDecimal packingFee;
        private BigDecimal totalFeeWithPacking;
        private String errorMessage;
        private List<FeeDetailItem> feeDetailItems;
        private LocalDateTime lastQueriedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    public static class Summary {
        private long totalCount;
        private long successCount;
        private long failedCount;
        private BigDecimal totalFee;
        private BigDecimal packingFee;
        private BigDecimal totalFeeWithPacking;
    }

    @Data
    @Builder
    public static class ImportFailure {
        private String dianxiaomiPackageNumber;
        private String message;
    }

    @Data
    @Builder
    public static class ImportResult {
        private int inputCount;
        private int acceptedCount;
        private int createdCount;
        private int updatedCount;
        private int successCount;
        private int failedCount;
        private List<ImportFailure> failures;
    }
}
