package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_sync_log",
        indexes = {
                @Index(name = "idx_sync_log_shop", columnList = "shop_id"),
                @Index(name = "idx_sync_log_type", columnList = "sync_type"),
                @Index(name = "idx_sync_log_status", columnList = "status"),
                @Index(name = "idx_sync_log_started", columnList = "started_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "sync_type", nullable = false, length = 32)
    private String syncType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "fail_count")
    private Integer failCount;

    @Column(name = "error_msg", columnDefinition = "text")
    private String errorMsg;

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
