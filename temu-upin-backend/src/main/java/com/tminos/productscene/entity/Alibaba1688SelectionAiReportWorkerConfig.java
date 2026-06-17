package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_selection_ai_report_worker_configs",
        indexes = {
                @Index(name = "idx_alibaba_1688_selection_ai_report_worker_configs_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688SelectionAiReportWorkerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_name", nullable = false, length = 128)
    private String configName;

    @Column(name = "thread_count", nullable = false)
    private Integer threadCount;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

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
        if (configName == null || configName.isBlank()) {
            configName = "默认配置";
        }
        if (threadCount == null || threadCount < 1) {
            threadCount = 1;
        }
        if (enabled == null) {
            enabled = false;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (configName == null || configName.isBlank()) {
            configName = "默认配置";
        }
        if (threadCount == null || threadCount < 1) {
            threadCount = 1;
        }
        if (enabled == null) {
            enabled = false;
        }
    }
}
