package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_sync_task",
        indexes = {
                @Index(name = "idx_sync_task_shop", columnList = "shop_id"),
                @Index(name = "idx_sync_task_type", columnList = "sync_type"),
                @Index(name = "idx_sync_task_status", columnList = "status"),
                @Index(name = "idx_sync_task_started", columnList = "started_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuSyncTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "sync_type", nullable = false, length = 32)
    private String syncType;

        @Column(name = "sync_scope", length = 32)
        private String syncScope;

    @Builder.Default
    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType = "MANUAL";

    @Builder.Default
    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Builder.Default
    @Column(name = "current_phase", length = 16)
    private String currentPhase = "DOWNLOAD";

    @Column(name = "download_total")
    private Integer downloadTotal;

    @Builder.Default
    @Column(name = "download_completed")
    private Integer downloadCompleted = 0;

    @Column(name = "download_failed")
    private Integer downloadFailed;

    @Column(name = "persist_total")
    private Integer persistTotal;

    @Builder.Default
    @Column(name = "persist_completed")
    private Integer persistCompleted = 0;

    @Column(name = "persist_failed")
    private Integer persistFailed;

    @Column(name = "total_batches")
    private Integer totalBatches;

    @Builder.Default
    @Column(name = "persisted_batches")
    private Integer persistedBatches = 0;

    @Column(name = "failed_batch_index")
    private Integer failedBatchIndex;

    @Column(name = "last_error_msg", columnDefinition = "text")
    private String lastErrorMsg;

    @Builder.Default
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
