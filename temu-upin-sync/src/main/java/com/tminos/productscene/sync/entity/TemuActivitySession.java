package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_activity_session",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_activity_session", columnNames = {"shop_id", "session_id"}),
        indexes = {
                @Index(name = "idx_temu_activity_session_type", columnList = "activity_type"),
                @Index(name = "idx_temu_activity_session_status", columnList = "session_status")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuActivitySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "activity_type", nullable = false)
    private Integer activityType;

    @Column(name = "activity_thematic_id")
    private Long activityThematicId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "session_name", length = 256)
    private String sessionName;

    @Column(name = "session_status")
    private Integer sessionStatus;

    @Column(name = "site_id")
    private Integer siteId;

    @Column(name = "site_name", length = 128)
    private String siteName;

    @Column(name = "start_time")
    private Long startTime;

    @Column(name = "end_time")
    private Long endTime;

    @Column(name = "start_date_str", length = 32)
    private String startDateStr;

    @Column(name = "end_date_str", length = 32)
    private String endDateStr;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
