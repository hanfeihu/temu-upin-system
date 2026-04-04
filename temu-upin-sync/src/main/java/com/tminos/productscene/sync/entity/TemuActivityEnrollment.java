package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_activity_enrollment",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_enrollment", columnNames = {"shop_id", "enroll_id"}),
        indexes = {
                @Index(name = "idx_temu_enrollment_product", columnList = "product_id"),
                @Index(name = "idx_temu_enrollment_status", columnList = "enroll_status"),
                @Index(name = "idx_temu_enrollment_type", columnList = "activity_type")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuActivityEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "enroll_id", nullable = false)
    private Long enrollId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "goods_id")
    private Long goodsId;

    @Column(name = "activity_type")
    private Integer activityType;

    @Column(name = "activity_type_name", length = 128)
    private String activityTypeName;

    @Column(name = "activity_thematic_id")
    private Long activityThematicId;

    @Column(name = "activity_thematic_name", length = 512)
    private String activityThematicName;

    @Column(name = "enroll_status")
    private Integer enrollStatus;

    @Column(name = "enroll_time")
    private Long enrollTime;

    @Column(name = "activity_stock")
    private Integer activityStock;

    @Column(name = "remaining_activity_stock")
    private Integer remainingActivityStock;

    @Column(name = "is_apparel")
    private Integer isApparel;

    @Column(name = "currency", length = 16)
    private String currency;

    @Column(name = "sold_status")
    private Integer soldStatus;

    @Column(name = "session_start_time")
    private Long sessionStartTime;

    @Column(name = "session_end_time")
    private Long sessionEndTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assign_sessions_json", columnDefinition = "jsonb")
    private String assignSessionsJson;

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
