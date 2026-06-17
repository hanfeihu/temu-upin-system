package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_selection_pool_filter_categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alibaba_1688_selection_pool_filter_category_name", columnNames = "category_name")
        },
        indexes = {
                @Index(name = "idx_alibaba_1688_selection_pool_filter_category_enabled", columnList = "enabled"),
                @Index(name = "idx_alibaba_1688_selection_pool_filter_category_updated_at", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688SelectionPoolFilterCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_name", nullable = false, length = 255)
    private String categoryName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

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
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        if (source == null || source.isBlank()) {
            source = "MANUAL";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (source == null || source.isBlank()) {
            source = "MANUAL";
        }
    }
}
