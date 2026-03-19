package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_collection_temu_sku",
        indexes = {
                @Index(name = "idx_pc_temu_sku_spu_id", columnList = "spu_id"),
                @Index(name = "idx_pc_temu_sku_temu_sku_id", columnList = "temu_sku_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCollectionTemuSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spu_id", nullable = false)
    private Long spuId;

    // user-editable TEMU skuId
    @Column(name = "temu_sku_id", length = 128)
    private String temuSkuId;

    // reference to 1688/original sku id
    @Column(name = "origin_sku_id", length = 128)
    private String originSkuId;

    // variant attributes
    @Column(name = "spec_key", columnDefinition = "TEXT")
    private String specKey;

    @Column(name = "spec_json", columnDefinition = "TEXT")
    private String specJson;

    // sku image
    @Column(length = 2000)
    private String image;

    // original sku price (1688) for reference
    private BigDecimal originPrice;

    // TEMU supply price
    private BigDecimal supplyPrice;

    // weight in grams
    private Integer weightG;

    // dimensions in cm
    private BigDecimal lengthCm;
    private BigDecimal widthCm;
    private BigDecimal heightCm;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Explicit getters/setters (keep Lombok too)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }
    public String getTemuSkuId() { return temuSkuId; }
    public void setTemuSkuId(String temuSkuId) { this.temuSkuId = temuSkuId; }
    public String getOriginSkuId() { return originSkuId; }
    public void setOriginSkuId(String originSkuId) { this.originSkuId = originSkuId; }
    public String getSpecKey() { return specKey; }
    public void setSpecKey(String specKey) { this.specKey = specKey; }
    public String getSpecJson() { return specJson; }
    public void setSpecJson(String specJson) { this.specJson = specJson; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public BigDecimal getOriginPrice() { return originPrice; }
    public void setOriginPrice(BigDecimal originPrice) { this.originPrice = originPrice; }
    public BigDecimal getSupplyPrice() { return supplyPrice; }
    public void setSupplyPrice(BigDecimal supplyPrice) { this.supplyPrice = supplyPrice; }
    public Integer getWeightG() { return weightG; }
    public void setWeightG(Integer weightG) { this.weightG = weightG; }
    public BigDecimal getLengthCm() { return lengthCm; }
    public void setLengthCm(BigDecimal lengthCm) { this.lengthCm = lengthCm; }
    public BigDecimal getWidthCm() { return widthCm; }
    public void setWidthCm(BigDecimal widthCm) { this.widthCm = widthCm; }
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
}
