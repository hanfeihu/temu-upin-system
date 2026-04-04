package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_sync_step_log",
        indexes = {
                @Index(name = "idx_step_log_task", columnList = "task_id"),
                @Index(name = "idx_step_log_created", columnList = "created_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuSyncStepLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "phase", nullable = false, length = 16)
    private String phase;

    @Column(name = "level", nullable = false, length = 8)
    private String level;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
