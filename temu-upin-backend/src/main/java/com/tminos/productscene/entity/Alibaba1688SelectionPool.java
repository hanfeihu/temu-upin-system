package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_selection_pools",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alibaba_1688_selection_pool_detail_record", columnNames = "detail_record_id"),
                @UniqueConstraint(name = "uk_alibaba_1688_selection_pool_offer_id", columnNames = "offer_id")
        },
        indexes = {
                @Index(name = "idx_alibaba_1688_selection_pool_status", columnList = "pool_status"),
                @Index(name = "idx_alibaba_1688_selection_pool_title_key", columnList = "product_title_key"),
                @Index(name = "idx_alibaba_1688_selection_pool_score", columnList = "overall_score"),
                @Index(name = "idx_alibaba_1688_selection_pool_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688SelectionPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "detail_record_id", nullable = false)
    private Long detailRecordId;

    @Column(name = "offer_id", nullable = false, length = 64)
    private String offerId;

    @Column(name = "detail_url", columnDefinition = "TEXT")
    private String detailUrl;

    @Column(name = "source_platform", length = 32)
    private String sourcePlatform;

    @Column(name = "product_title_snapshot", columnDefinition = "TEXT")
    private String productTitleSnapshot;

    @Column(name = "product_title_key", length = 512)
    private String productTitleKey;

    @Column(name = "main_image_snapshot", length = 2000)
    private String mainImageSnapshot;

    @Column(name = "carousel_image_urls_json", columnDefinition = "TEXT")
    private String carouselImageUrlsJson;

    @Column(name = "detail_image_urls_json", columnDefinition = "TEXT")
    private String detailImageUrlsJson;

    @Column(name = "company_name_snapshot", columnDefinition = "TEXT")
    private String companyNameSnapshot;

    @Column(name = "repeat_customer_rate_snapshot", precision = 10, scale = 4)
    private BigDecimal repeatCustomerRateSnapshot;

    @Column(name = "service_score_snapshot", precision = 10, scale = 4)
    private BigDecimal serviceScoreSnapshot;

    @Column(name = "on_time_delivery_rate_snapshot", precision = 10, scale = 4)
    private BigDecimal onTimeDeliveryRateSnapshot;

    @Column(name = "shop_positive_rate_snapshot", precision = 10, scale = 4)
    private BigDecimal shopPositiveRateSnapshot;

    @Column(name = "power_seller_snapshot")
    private Boolean powerSellerSnapshot;

    @Column(name = "settled_years_text_snapshot", length = 255)
    private String settledYearsTextSnapshot;

    @Column(name = "main_business_snapshot", columnDefinition = "TEXT")
    private String mainBusinessSnapshot;

    @Column(name = "shipping_location_snapshot", columnDefinition = "TEXT")
    private String shippingLocationSnapshot;

    @Column(name = "category_snapshot", columnDefinition = "TEXT")
    private String categorySnapshot;

    @Column(name = "published_at_1688")
    private LocalDateTime publishedAt1688;

    @Column(name = "base_freight_snapshot", precision = 18, scale = 4)
    private BigDecimal baseFreightSnapshot;

    @Column(name = "moq_snapshot")
    private Integer moqSnapshot;

    @Column(name = "start_batch_qty_snapshot")
    private Integer startBatchQtySnapshot;

    @Column(name = "moq_text_snapshot", columnDefinition = "TEXT")
    private String moqTextSnapshot;

    @Column(name = "monthly_sales_snapshot", columnDefinition = "TEXT")
    private String monthlySalesSnapshot;

    @Column(name = "sales_trend_snapshot_json", columnDefinition = "TEXT")
    private String salesTrendSnapshotJson;

    @Column(name = "price_steps_snapshot_json", columnDefinition = "TEXT")
    private String priceStepsSnapshotJson;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "score_detail_json", columnDefinition = "TEXT")
    private String scoreDetailJson;

    @Column(name = "target_shop_ids_json", columnDefinition = "TEXT")
    private String targetShopIdsJson;

    @Column(name = "target_shop_names_json", columnDefinition = "TEXT")
    private String targetShopNamesJson;

    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "selected_reason", columnDefinition = "TEXT")
    private String selectedReason;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "pool_status", nullable = false, length = 32)
    private String poolStatus;

    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    @Column(name = "detail_last_collected_at")
    private LocalDateTime detailLastCollectedAt;

    @Column(name = "assistant_extra_json", columnDefinition = "TEXT")
    private String assistantExtraJson;

    @Column(name = "temu_compete_analysis_status", length = 32)
    private String temuCompeteAnalysisStatus;

    @Column(name = "temu_compete_analysis_summary", columnDefinition = "TEXT")
    private String temuCompeteAnalysisSummary;

    @Column(name = "temu_compete_report_title", columnDefinition = "TEXT")
    private String temuCompeteReportTitle;

    @Column(name = "temu_compete_report_content", columnDefinition = "TEXT")
    private String temuCompeteReportContent;

    @Column(name = "temu_compete_report_json", columnDefinition = "TEXT")
    private String temuCompeteReportJson;

    @Column(name = "temu_compete_score")
    private Integer temuCompeteScore;

    @Column(name = "temu_compete_analysis_at")
    private LocalDateTime temuCompeteAnalysisAt;

    @Column(name = "ai_selection_analysis_status", length = 32)
    private String aiSelectionAnalysisStatus;

    @Column(name = "ai_selection_analysis_summary", columnDefinition = "TEXT")
    private String aiSelectionAnalysisSummary;

    @Column(name = "ai_selection_report_title", columnDefinition = "TEXT")
    private String aiSelectionReportTitle;

    @Column(name = "ai_selection_report_content", columnDefinition = "TEXT")
    private String aiSelectionReportContent;

    @Column(name = "ai_selection_report_json", columnDefinition = "TEXT")
    private String aiSelectionReportJson;

    @Column(name = "ai_selection_score")
    private Integer aiSelectionScore;

    @Column(name = "ai_selection_decision", length = 32)
    private String aiSelectionDecision;

    @Column(name = "ai_contains_liquid")
    private Boolean aiContainsLiquid;

    @Column(name = "ai_contains_battery")
    private Boolean aiContainsBattery;

    @Column(name = "ai_fragile")
    private Boolean aiFragile;

    @Column(name = "ai_potential_brand_infringement")
    private Boolean aiPotentialBrandInfringement;

    @Column(name = "ai_max_weight_g", precision = 18, scale = 4)
    private BigDecimal aiMaxWeightG;

    @Column(name = "ai_max_dimension_cm", precision = 18, scale = 4)
    private BigDecimal aiMaxDimensionCm;

    @Column(name = "ai_max_dimension_source", length = 64)
    private String aiMaxDimensionSource;

    @Column(name = "ai_dimension_evidence", columnDefinition = "TEXT")
    private String aiDimensionEvidence;

    @Column(name = "ai_detected_moq")
    private Integer aiDetectedMoq;

    @Column(name = "ai_detected_sales_volume")
    private Integer aiDetectedSalesVolume;

    @Column(name = "ai_detected_sales_text", columnDefinition = "TEXT")
    private String aiDetectedSalesText;

    @Column(name = "ai_selection_analysis_at")
    private LocalDateTime aiSelectionAnalysisAt;

    @Column(name = "pushed_product_collection_id")
    private Long pushedProductCollectionId;

    @Column(name = "push_status", length = 32)
    private String pushStatus;

    @Column(name = "pushed_at")
    private LocalDateTime pushedAt;

    @Column(name = "push_error", columnDefinition = "TEXT")
    private String pushError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (poolStatus == null || poolStatus.isBlank()) {
            poolStatus = "NEW";
        }
        if (sourcePlatform == null || sourcePlatform.isBlank()) {
            sourcePlatform = "1688";
        }
        if (temuCompeteAnalysisStatus == null || temuCompeteAnalysisStatus.isBlank()) {
            temuCompeteAnalysisStatus = "NOT_STARTED";
        }
        if (aiSelectionAnalysisStatus == null || aiSelectionAnalysisStatus.isBlank()) {
            aiSelectionAnalysisStatus = "NOT_STARTED";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
