package com.tminos.productscene.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class Alibaba1688SelectionPoolDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListItem {
        private Long id;
        private Long detailRecordId;
        private String offerId;
        private String detailUrl;
        private String canonicalUrl;
        private String sourcePlatform;
        private String productTitleSnapshot;
        private String productName;
        private String mainImageSnapshot;
        private String productMainImage;
        private String companyNameSnapshot;
        private String companyName;
        private BigDecimal repeatCustomerRateSnapshot;
        private BigDecimal serviceScoreSnapshot;
        private BigDecimal onTimeDeliveryRateSnapshot;
        private BigDecimal shopPositiveRateSnapshot;
        private Boolean powerSellerSnapshot;
        private String settledYearsTextSnapshot;
        private String mainBusinessSnapshot;
        private String categorySnapshot;
        private BigDecimal baseFreightSnapshot;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private Integer moqSnapshot;
        private Integer startBatchQtySnapshot;
        private String monthlySalesSnapshot;
        private Integer overallScore;
        private String poolStatus;
        private String status;
        private List<String> targetShopIds;
        private List<String> targetShopNames;
        private List<String> tags;
        private Integer skuCount;
        private Integer reportCount;
        private Integer temuCompeteScore;
        private Integer aiSelectionScore;
        private String aiSelectionDecision;
        private Boolean aiContainsLiquid;
        private Boolean aiContainsBattery;
        private Boolean aiFragile;
        private Boolean aiPotentialBrandInfringement;
        private BigDecimal aiMaxWeightG;
        private BigDecimal aiMaxDimensionCm;
        private String aiMaxDimensionSource;
        private String aiDimensionEvidence;
        private Integer aiDetectedMoq;
        private Integer aiDetectedSalesVolume;
        private String aiDetectedSalesText;
        private Long pushedProductCollectionId;
        private String pushStatus;
        private LocalDateTime pushedAt;
        private String pushError;
        private Boolean temuSiteExceptionBlocked;
        private String temuSiteExceptionReason;
        private String note;
        private String latestReportStatus;
        private LocalDateTime latestReportAt;
        private LocalDateTime publishedAt1688;
        private LocalDateTime detailLastCollectedAt;
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
        private Long detailRecordId;
        private String offerId;
        private String detailUrl;
        private String canonicalUrl;
        private String sourcePlatform;
        private String productTitleSnapshot;
        private String productName;
        private String mainImageSnapshot;
        private String productMainImage;
        private List<String> carouselImageUrls;
        private List<String> detailImageUrls;
        private String companyNameSnapshot;
        private String companyName;
        private BigDecimal repeatCustomerRateSnapshot;
        private BigDecimal serviceScoreSnapshot;
        private BigDecimal onTimeDeliveryRateSnapshot;
        private BigDecimal shopPositiveRateSnapshot;
        private Boolean powerSellerSnapshot;
        private String settledYearsTextSnapshot;
        private String mainBusinessSnapshot;
        private String shippingLocationSnapshot;
        private String categorySnapshot;
        private LocalDateTime publishedAt1688;
        private BigDecimal baseFreightSnapshot;
        private Integer moqSnapshot;
        private Integer startBatchQtySnapshot;
        private String moqTextSnapshot;
        private String monthlySalesSnapshot;
        private String salesTrendSnapshotJson;
        private String priceStepsSnapshotJson;
        private Integer overallScore;
        private String scoreDetailJson;
        private List<String> targetShopIds;
        private List<String> targetShopNames;
        private List<String> tags;
        private String selectedReason;
        private String rejectReason;
        private String remark;
        private String note;
        private String poolStatus;
        private String status;
        private LocalDateTime firstSeenAt;
        private LocalDateTime detailLastCollectedAt;
        private String assistantExtraJson;
        private String temuCompeteAnalysisStatus;
        private String temuCompeteAnalysisSummary;
        private String temuCompeteReportTitle;
        private String temuCompeteReportContent;
        private String temuCompeteReportJson;
        private Integer temuCompeteScore;
        private LocalDateTime temuCompeteAnalysisAt;
        private String aiSelectionAnalysisStatus;
        private String aiSelectionAnalysisSummary;
        private String aiSelectionReportTitle;
        private String aiSelectionReportContent;
        private String aiSelectionReportJson;
        private Integer aiSelectionScore;
        private String aiSelectionDecision;
        private Boolean aiContainsLiquid;
        private Boolean aiContainsBattery;
        private Boolean aiFragile;
        private Boolean aiPotentialBrandInfringement;
        private BigDecimal aiMaxWeightG;
        private BigDecimal aiMaxDimensionCm;
        private String aiMaxDimensionSource;
        private String aiDimensionEvidence;
        private Integer aiDetectedMoq;
        private Integer aiDetectedSalesVolume;
        private String aiDetectedSalesText;
        private LocalDateTime aiSelectionAnalysisAt;
        private Long pushedProductCollectionId;
        private String pushStatus;
        private LocalDateTime pushedAt;
        private String pushError;
        private String latestReportStatus;
        private LocalDateTime latestReportAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<SkuItem> skus;
        private List<SkuItem> skuRows;
        private List<ReportItem> reports;
        private List<ReportItem> reportList;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkuItem {
        private Long id;
        private Long poolId;
        private String sourceSkuId;
        private String skuId;
        private String skuSpecText;
        private String specKey;
        private String skuSpecJson;
        private String specJson;
        private String skuImage;
        private String image;
        private String skuMainImage;
        private BigDecimal skuPriceSnapshot;
        private BigDecimal price;
        private Integer pageStockSnapshot;
        private Integer stock;
        private Integer manualStockQty;
        private String stockCheckedBy;
        private LocalDateTime stockCheckedAt;
        private BigDecimal weightValue;
        private String weightSource;
        private BigDecimal dimensionValue;
        private String dimensionSource;
        private String dimensionEvidence;
        private BigDecimal estimatedPurchasePrice;
        private BigDecimal estimatedFirstLegFee;
        private BigDecimal estimatedTemuPrice;
        private BigDecimal estimatedUnitProfit;
        private BigDecimal temuFinalPrice;
        private String selectionStatus;
        private Boolean isPrimarySku;
        private String stockRiskLevel;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReportItem {
        private Long id;
        private Long poolId;
        private String reportType;
        private String reportTitle;
        private String title;
        private String reportSummary;
        private String summary;
        private String reportContent;
        private String reportJson;
        private String dataJson;
        private String contentJson;
        private String status;
        private Integer versionNo;
        private String sourceType;
        private String modelName;
        private Integer score;
        private String note;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportRequest {
        private Long detailRecordId;
        private Boolean forceRefresh;
        private List<String> targetShopIds;
        private List<String> tags;
        private String selectedReason;
        private String remark;
        private String note;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportResponse {
        private Long id;
        private Boolean created;
        private Boolean refreshed;
        private Boolean skipped;
        private String message;
        private String poolStatus;
        private Integer skuCount;
        private String offerId;
        private String duplicateReason;
        private String duplicateTitle;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchImportRequest {
        private List<Long> detailRecordIds;
        private Boolean allMatching;
        private String keyword;
        private Long detailRecordId;
        private String note;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchImportResponse {
        private Integer total;
        private Integer successCount;
        private Integer createdCount;
        private Integer refreshedCount;
        private Integer skippedCount;
        private Integer failedCount;
        private List<BatchImportFailure> failures;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchImportFailure {
        private Long detailRecordId;
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PushToProductCollectionRequest {
        private Boolean forceCreate;
        private List<String> targetShopIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PushToProductCollectionResponse {
        private Long poolId;
        private String offerId;
        private Long productCollectionId;
        private String productId;
        private String productName;
        private Boolean created;
        private Boolean existing;
        private String pushStatus;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private Integer overallScore;
        private List<String> targetShopIds;
        private List<String> tags;
        private String selectedReason;
        private String rejectReason;
        private String remark;
        private String poolStatus;
        private String scoreDetailJson;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateSkuRequest {
        private Integer manualStockQty;
        private String stockCheckedBy;
        private BigDecimal weightValue;
        private String weightSource;
        private BigDecimal dimensionValue;
        private String dimensionSource;
        private String dimensionEvidence;
        private BigDecimal estimatedPurchasePrice;
        private BigDecimal estimatedFirstLegFee;
        private BigDecimal estimatedTemuPrice;
        private BigDecimal temuFinalPrice;
        private String selectionStatus;
        private Boolean isPrimarySku;
        private String stockRiskLevel;
        private String remark;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveReportRequest {
        private String reportType;
        private String reportTitle;
        private String reportSummary;
        private String reportContent;
        private String reportJson;
        private String status;
        private Integer score;
        private String sourceType;
        private String modelName;
    }
}
