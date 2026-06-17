package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_selection_auto_push_logs",
        indexes = {
                @Index(name = "idx_alibaba_1688_selection_auto_push_logs_pool", columnList = "pool_id"),
                @Index(name = "idx_alibaba_1688_selection_auto_push_logs_status", columnList = "status"),
                @Index(name = "idx_alibaba_1688_selection_auto_push_logs_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688SelectionAutoPushLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pool_id")
    private Long poolId;

    @Column(name = "offer_id", length = 64)
    private String offerId;

    @Column(name = "product_collection_id")
    private Long productCollectionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "target_shop_ids_json", columnDefinition = "TEXT")
    private String targetShopIdsJson;

    @Column(name = "target_shop_names_json", columnDefinition = "TEXT")
    private String targetShopNamesJson;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null || status.isBlank()) status = "UNKNOWN";
    }
}
