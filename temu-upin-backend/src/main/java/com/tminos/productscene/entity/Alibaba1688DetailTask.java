package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_detail_tasks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alibaba_1688_detail_task_credential_offer", columnNames = {"credential_id", "offer_id"})
        },
        indexes = {
                @Index(name = "idx_alibaba_1688_detail_task_status", columnList = "status"),
                @Index(name = "idx_alibaba_1688_detail_task_credential", columnList = "credential_id"),
                @Index(name = "idx_alibaba_1688_detail_task_offer_id", columnList = "offer_id"),
                @Index(name = "idx_alibaba_1688_detail_task_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688DetailTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    @Column(name = "offer_id", nullable = false, length = 64)
    private String offerId;

    @Column(name = "detail_url", nullable = false, length = 2000)
    private String detailUrl;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "worker_name", length = 255)
    private String workerName;

    @Column(name = "claim_token", length = 128)
    private String claimToken;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "detail_record_id")
    private Long detailRecordId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

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
            status = "PENDING";
        }
        if (sourceType == null || sourceType.isBlank()) {
            sourceType = "MANUAL_DIALOG";
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
