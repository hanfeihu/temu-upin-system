package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_price_review_low_price_reject_worker_configs",
        indexes = @Index(name = "idx_price_review_low_price_reject_worker_updated_at", columnList = "updated_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuPriceReviewLowPriceRejectWorkerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_name", nullable = false, length = 128)
    private String configName;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "max_suggest_supply_price", nullable = false)
    private Integer maxSuggestSupplyPrice;

    @Column(name = "poll_ms", nullable = false)
    private Long pollMs;

    @Column(name = "batch_size", nullable = false)
    private Integer batchSize;

    @Column(name = "reason_type", nullable = false)
    private Integer reasonType;

    @Column(name = "reason_text", nullable = false, length = 500)
    private String reasonText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        normalizeDefaults();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        normalizeDefaults();
    }

    private void normalizeDefaults() {
        if (configName == null || configName.isBlank()) configName = "低价自动拒绝";
        if (enabled == null) enabled = false;
        if (maxSuggestSupplyPrice == null || maxSuggestSupplyPrice < 1) maxSuggestSupplyPrice = 2000;
        if (pollMs == null || pollMs < 10_000L) pollMs = 300_000L;
        if (batchSize == null || batchSize < 1) batchSize = 20;
        if (reasonType == null || reasonType < 0 || reasonType > 8) reasonType = 2;
        if (reasonText == null || reasonText.isBlank()) reasonText = "价格太低";
    }
}
