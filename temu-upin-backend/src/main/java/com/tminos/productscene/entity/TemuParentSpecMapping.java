package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_parent_spec_mappings",
        indexes = {
                @Index(name = "idx_temu_parent_spec_mapping_enabled", columnList = "enabled"),
                @Index(name = "idx_temu_parent_spec_mapping_normalized", columnList = "normalized_source_field", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuParentSpecMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_field_name", nullable = false, length = 128)
    private String sourceFieldName;

    @Column(name = "normalized_source_field", nullable = false, length = 128)
    private String normalizedSourceField;

    @Column(name = "target_parent_spec_name", nullable = false, length = 128)
    private String targetParentSpecName;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String notes;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}