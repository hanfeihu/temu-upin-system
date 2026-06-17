package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_selection_auto_push_configs",
        indexes = {
                @Index(name = "idx_alibaba_1688_selection_auto_push_configs_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688SelectionAutoPushConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_name", nullable = false, length = 128)
    private String configName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "target_shop_ids_json", columnDefinition = "TEXT")
    private String targetShopIdsJson;

    @Column(name = "target_shop_names_json", columnDefinition = "TEXT")
    private String targetShopNamesJson;

    @Column(name = "batch_size", nullable = false)
    private Integer batchSize;

    @Column(name = "poll_ms", nullable = false)
    private Long pollMs;

    @Column(name = "force_create", nullable = false)
    private Boolean forceCreate;

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
        if (batchSize == null || batchSize < 1) batchSize = 1;
        if (pollMs == null || pollMs < 10_000L) pollMs = 60_000L;
        if (forceCreate == null) forceCreate = false;
    }
}
