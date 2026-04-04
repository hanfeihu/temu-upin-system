package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_activity",
        indexes = {
                @Index(name = "idx_temu_activity_shop", columnList = "shop_id"),
                @Index(name = "idx_temu_activity_type", columnList = "activity_type")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "activity_type", nullable = false)
    private Integer activityType;

    @Column(name = "activity_name", length = 512)
    private String activityName;

    @Column(name = "activity_content", columnDefinition = "TEXT")
    private String activityContent;

    @Column(name = "activity_label_tag")
    private Integer activityLabelTag;

    @Column(name = "session_assign_type")
    private Integer sessionAssignType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefit_labels_json", columnDefinition = "jsonb")
    private String benefitLabelsJson;

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
