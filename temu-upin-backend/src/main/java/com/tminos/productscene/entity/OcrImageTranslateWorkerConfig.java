package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ocr_image_translate_worker_configs",
        indexes = {
                @Index(name = "idx_ocr_image_translate_worker_configs_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrImageTranslateWorkerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_name", nullable = false, length = 128)
    private String configName;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "max_chinese_image_count", nullable = false)
    private Integer maxChineseImageCount;

    @Column(name = "batch_size", nullable = false)
    private Integer batchSize;

    @Column(name = "poll_ms", nullable = false)
    private Long pollMs;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "quality", length = 32)
    private String quality;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

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
        if (configName == null || configName.isBlank()) configName = "默认配置";
        if (enabled == null) enabled = false;
        if (maxChineseImageCount == null || maxChineseImageCount < 1) maxChineseImageCount = 5;
        if (batchSize == null || batchSize < 1) batchSize = 1;
        if (pollMs == null || pollMs < 10_000L) pollMs = 60_000L;
        if (provider == null || provider.isBlank()) provider = "ai";
        if (model == null || model.isBlank()) model = "gpt-image-2";
        if (quality == null || quality.isBlank()) quality = "medium";
    }
}
