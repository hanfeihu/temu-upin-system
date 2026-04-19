package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_order_sync_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_temu_order_sync_states_shop_type", columnNames = {"shop_record_id", "sync_type"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuOrderSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_record_id", nullable = false)
    private Long shopRecordId;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "shop_name", length = 128)
    private String shopName;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_type", nullable = false, length = 32)
    private TemuOrderSyncType syncType;

    @Column(name = "last_cursor_ms")
    private Long lastCursorMs;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "last_summary", length = 1000)
    private String lastSummary;

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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
