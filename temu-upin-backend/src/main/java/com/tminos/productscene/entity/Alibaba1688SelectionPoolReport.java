package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_selection_pool_reports",
        indexes = {
                @Index(name = "idx_alibaba_1688_selection_pool_report_pool", columnList = "pool_id"),
                @Index(name = "idx_alibaba_1688_selection_pool_report_type", columnList = "report_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688SelectionPoolReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pool_id", nullable = false)
    private Long poolId;

    @Column(name = "report_type", nullable = false, length = 32)
    private String reportType;

    @Column(name = "report_title", columnDefinition = "TEXT")
    private String reportTitle;

    @Column(name = "report_summary", columnDefinition = "TEXT")
    private String reportSummary;

    @Column(name = "report_content", columnDefinition = "TEXT")
    private String reportContent;

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "version_no")
    private Integer versionNo;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "score")
    private Integer score;

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
        if (versionNo == null || versionNo <= 0) {
            versionNo = 1;
        }
        if (sourceType == null || sourceType.isBlank()) {
            sourceType = "MANUAL";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
