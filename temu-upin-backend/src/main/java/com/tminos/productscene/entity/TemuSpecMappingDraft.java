package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_spec_mapping_drafts",
        indexes = {
                @Index(name = "idx_temu_spec_mapping_drafts_spu", columnList = "spu_id"),
                @Index(name = "idx_temu_spec_mapping_drafts_active", columnList = "active")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuSpecMappingDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spu_id", nullable = false)
    private Long spuId;

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "draft_name", length = 128)
    private String draftName;

    @Column(name = "target_parent_spec_name", length = 64)
    private String targetParentSpecName;

    @Column(name = "selected_main_field", length = 128)
    private String selectedMainField;

    @Lob
    @Column(name = "selected_sku_fields_json", columnDefinition = "TEXT")
    private String selectedSkuFieldsJson;

    @Lob
    @Column(name = "field_mappings_json", columnDefinition = "TEXT")
    private String fieldMappingsJson;

    @Lob
    @Column(name = "value_rules_json", columnDefinition = "TEXT")
    private String valueRulesJson;

    @Builder.Default
    @Column(name = "auto_ignore_constant_fields", nullable = false)
    private Boolean autoIgnoreConstantFields = true;

    @Lob
    @Column(name = "source_snapshot_json", columnDefinition = "TEXT")
    private String sourceSnapshotJson;

    @Lob
    @Column(name = "preview_json", columnDefinition = "TEXT")
    private String previewJson;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = false;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}