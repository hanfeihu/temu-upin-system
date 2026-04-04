package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_activity_thematic",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_activity_thematic", columnNames = {"activity_id", "activity_thematic_id"}),
        indexes = @Index(name = "idx_temu_activity_thematic_act", columnList = "activity_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuActivityThematic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "activity_thematic_id", nullable = false)
    private Long activityThematicId;

    @Column(name = "activity_thematic_name", length = 512)
    private String activityThematicName;

    @Column(name = "enroll_source")
    private Integer enrollSource;

    @Column(name = "enroll_start_at")
    private Long enrollStartAt;

    @Column(name = "enroll_dead_line")
    private Long enrollDeadLine;

    @Column(name = "start_time")
    private Long startTime;

    @Column(name = "end_time")
    private Long endTime;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "sale_promotion_label", length = 256)
    private String salePromotionLabel;

    @Column(name = "activity_label_tag")
    private Integer activityLabelTag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefit_labels_json", columnDefinition = "jsonb")
    private String benefitLabelsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sites_json", columnDefinition = "jsonb")
    private String sitesJson;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
