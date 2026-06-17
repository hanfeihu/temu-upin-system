package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_detail_records",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alibaba_1688_detail_record_offer_id", columnNames = "offer_id")
        },
        indexes = {
                @Index(name = "idx_alibaba_1688_detail_record_credential", columnList = "last_credential_id"),
                @Index(name = "idx_alibaba_1688_detail_record_collected_at", columnList = "last_collected_at"),
                @Index(name = "idx_alibaba_1688_detail_record_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688DetailRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_id", nullable = false, length = 64)
    private String offerId;

    @Column(name = "detail_url", length = 2000)
    private String detailUrl;

    @Column(name = "canonical_url", length = 2000)
    private String canonicalUrl;

    @Column(name = "product_name", columnDefinition = "TEXT")
    private String productName;

    @Column(name = "company_name", columnDefinition = "TEXT")
    private String companyName;

    @Column(name = "product_main_image", length = 2000)
    private String productMainImage;

    @Column(name = "min_price", precision = 18, scale = 4)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 18, scale = 4)
    private BigDecimal maxPrice;

    @Column(name = "repeat_customer_rate", precision = 10, scale = 4)
    private BigDecimal repeatCustomerRate;

    @Column(name = "service_score", precision = 10, scale = 4)
    private BigDecimal serviceScore;

    @Column(name = "on_time_delivery_rate", precision = 10, scale = 4)
    private BigDecimal onTimeDeliveryRate;

    @Column(name = "shop_positive_rate", precision = 10, scale = 4)
    private BigDecimal shopPositiveRate;

    @Column(name = "power_seller")
    private Boolean powerSeller;

    @Column(name = "settled_years_text", length = 255)
    private String settledYearsText;

    @Column(name = "main_business", columnDefinition = "TEXT")
    private String mainBusiness;

    @Column(name = "source_platform", length = 32)
    private String sourcePlatform;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_task_id")
    private Long lastTaskId;

    @Column(name = "last_credential_id")
    private Long lastCredentialId;

    @Column(name = "raw_html", columnDefinition = "TEXT")
    private String rawHtml;

    @Column(name = "extracted_json", columnDefinition = "TEXT")
    private String extractedJson;

    @Column(name = "parsed_json", columnDefinition = "TEXT")
    private String parsedJson;

    @Column(name = "last_collected_at")
    private LocalDateTime lastCollectedAt;

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
        if (status == null || status.isBlank()) {
            status = "READY";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
