package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "alibaba_1688_selection_pool_skus",
        indexes = {
                @Index(name = "idx_alibaba_1688_selection_pool_sku_pool", columnList = "pool_id"),
                @Index(name = "idx_alibaba_1688_selection_pool_sku_status", columnList = "selection_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alibaba1688SelectionPoolSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pool_id", nullable = false)
    private Long poolId;

    @Column(name = "source_sku_id", length = 128)
    private String sourceSkuId;

    @Column(name = "sku_spec_text", columnDefinition = "TEXT")
    private String skuSpecText;

    @Column(name = "sku_spec_json", columnDefinition = "TEXT")
    private String skuSpecJson;

    @Column(name = "sku_image", length = 2000)
    private String skuImage;

    @Column(name = "sku_main_image", length = 2000)
    private String skuMainImage;

    @Column(name = "sku_price_snapshot", precision = 18, scale = 4)
    private BigDecimal skuPriceSnapshot;

    @Column(name = "page_stock_snapshot")
    private Integer pageStockSnapshot;

    @Column(name = "manual_stock_qty")
    private Integer manualStockQty;

    @Column(name = "stock_checked_by", length = 128)
    private String stockCheckedBy;

    @Column(name = "stock_checked_at")
    private LocalDateTime stockCheckedAt;

    @Column(name = "weight_value", precision = 18, scale = 4)
    private BigDecimal weightValue;

    @Column(name = "weight_source", length = 32)
    private String weightSource;

    @Column(name = "dimension_value", precision = 18, scale = 4)
    private BigDecimal dimensionValue;

    @Column(name = "dimension_source", length = 64)
    private String dimensionSource;

    @Column(name = "dimension_evidence", columnDefinition = "TEXT")
    private String dimensionEvidence;

    @Column(name = "estimated_purchase_price", precision = 18, scale = 4)
    private BigDecimal estimatedPurchasePrice;

    @Column(name = "estimated_first_leg_fee", precision = 18, scale = 4)
    private BigDecimal estimatedFirstLegFee;

    @Column(name = "estimated_temu_price", precision = 18, scale = 4)
    private BigDecimal estimatedTemuPrice;

    @Column(name = "estimated_unit_profit", precision = 18, scale = 4)
    private BigDecimal estimatedUnitProfit;

    @Column(name = "temu_final_price", precision = 18, scale = 4)
    private BigDecimal temuFinalPrice;

    @Column(name = "selection_status", nullable = false, length = 32)
    private String selectionStatus;

    @Column(name = "is_primary_sku")
    private Boolean isPrimarySku;

    @Column(name = "stock_risk_level", length = 32)
    private String stockRiskLevel;

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
        if (selectionStatus == null || selectionStatus.isBlank()) {
            selectionStatus = "PENDING_SELECTION";
        }
        if (isPrimarySku == null) {
            isPrimarySku = Boolean.FALSE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
