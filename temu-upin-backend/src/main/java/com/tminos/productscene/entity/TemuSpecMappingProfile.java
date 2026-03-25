package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_spec_mapping_profiles",
        indexes = {
                @Index(name = "idx_temu_spec_mapping_profiles_enabled", columnList = "enabled"),
                @Index(name = "idx_temu_spec_mapping_profiles_target_cat", columnList = "target_category_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuSpecMappingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "source_category_path", length = 1000)
    private String sourceCategoryPath;

    @Column(name = "target_category_id", length = 64)
    private String targetCategoryId;

    @Column(name = "target_category_name", length = 255)
    private String targetCategoryName;

    @Column(name = "target_parent_spec_name", length = 64)
    private String targetParentSpecName;

    @Column(name = "source_signature", length = 1000)
    private String sourceSignature;

    @Lob
    @Column(name = "field_mappings_json", columnDefinition = "TEXT")
    private String fieldMappingsJson;

    @Lob
    @Column(name = "value_rules_json", columnDefinition = "TEXT")
    private String valueRulesJson;

    @Lob
    @Column(name = "main_field_candidates_json", columnDefinition = "TEXT")
    private String mainFieldCandidatesJson;

    @Builder.Default
    @Column(name = "auto_ignore_constant_fields", nullable = false)
    private Boolean autoIgnoreConstantFields = true;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}