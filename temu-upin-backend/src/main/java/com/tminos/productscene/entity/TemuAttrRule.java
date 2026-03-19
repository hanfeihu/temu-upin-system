package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_attr_rules",
        indexes = {
                @Index(name = "idx_temu_attr_rules_enabled", columnList = "enabled"),
                @Index(name = "idx_temu_attr_rules_leaf", columnList = "leaf_cat_id"),
                @Index(name = "idx_temu_attr_rules_type", columnList = "rule_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuAttrRule {

    public enum RuleType {
        GENERAL,
        FIXED_CATEGORY
    }

    public enum FillMode {
        FORCE_EMPTY,
        FIXED_VALUE,
        SKIP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    /** Leaf category ID (only required when ruleType=FIXED_CATEGORY) */
    @Column(name = "leaf_cat_id")
    private String leafCatId;

    /** Attribute display name in Chinese, matched by exact trimmed string */
    @Column(name = "attr_name", nullable = false)
    private String attrName;

    @Enumerated(EnumType.STRING)
    @Column(name = "fill_mode", nullable = false)
    private FillMode fillMode;

    /** Used when fillMode=FIXED_VALUE */
    @Column(name = "fixed_value")
    private String fixedValue;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
