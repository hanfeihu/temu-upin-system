package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_main_sale_spec_rules",
        indexes = {
                @Index(name = "idx_temu_main_sale_spec_rules_enabled", columnList = "enabled"),
                @Index(name = "idx_temu_main_sale_spec_rules_sig", columnList = "sku_signature"),
                @Index(name = "idx_temu_main_sale_spec_rules_type", columnList = "rule_type")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_temu_main_sale_spec_rules_sig", columnNames = {"sku_signature"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuMainSaleSpecRule {

    public enum RuleType {
        MANUAL,
        LEARNED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 16)
    private RuleType ruleType;

    /**
     * Deterministic signature generated from SKU specJson (order independent).
     * Used for exact match to avoid repeated AI calls.
     */
    @Column(name = "sku_signature", nullable = false, length = 2000)
    private String skuSignature;

    /** Preferred parent spec name in TEMU, e.g. 颜色/尺码/型号/规格/数量 */
    @Column(name = "parent_spec_name", nullable = false, length = 64)
    private String parentSpecName;

    /** The inferred dimension key in specJson, e.g. 颜色/Size/型号 */
    @Column(name = "dim_key", length = 128)
    private String dimKey;

    /** Optional traceability back to TEMU publish run */
    @Column(name = "created_from_run_id")
    private Long createdFromRunId;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Explicit getters/setters (keep Lombok too)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public RuleType getRuleType() { return ruleType; }
    public void setRuleType(RuleType ruleType) { this.ruleType = ruleType; }
    public String getSkuSignature() { return skuSignature; }
    public void setSkuSignature(String skuSignature) { this.skuSignature = skuSignature; }
    public String getParentSpecName() { return parentSpecName; }
    public void setParentSpecName(String parentSpecName) { this.parentSpecName = parentSpecName; }
    public String getDimKey() { return dimKey; }
    public void setDimKey(String dimKey) { this.dimKey = dimKey; }
    public Long getCreatedFromRunId() { return createdFromRunId; }
    public void setCreatedFromRunId(Long createdFromRunId) { this.createdFromRunId = createdFromRunId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
