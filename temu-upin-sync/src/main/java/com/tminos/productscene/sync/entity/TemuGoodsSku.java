package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods_sku",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_goods_sku", columnNames = {"shop_id", "product_sku_id"}),
        indexes = {
                @Index(name = "idx_temu_goods_sku_goods", columnList = "goods_id"),
                @Index(name = "idx_temu_goods_sku_ext_code", columnList = "ext_code")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoodsSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "ext_code", length = 256)
    private String extCode;

    @Column(name = "virtual_stock")
    private Integer virtualStock;

    @Column(name = "weight_mg")
    private Integer weightMg;

    @Column(name = "wms_weight_mg")
    private Integer wmsWeightMg;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @Column(name = "wms_length_mm")
    private Integer wmsLengthMm;

    @Column(name = "wms_width_mm")
    private Integer wmsWidthMm;

    @Column(name = "wms_height_mm")
    private Integer wmsHeightMm;

    @Column(name = "wms_collection_source_type")
    private Integer wmsCollectionSourceType;

    @Builder.Default
    @Column(name = "is_sensitive")
    private Integer isSensitive = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sensitive_types_json", columnDefinition = "jsonb")
    private String sensitiveTypesJson;

    @Builder.Default
    @Column(name = "is_fragile")
    private Boolean isFragile = false;

    @Builder.Default
    @Column(name = "is_side_over_length")
    private Boolean isSideOverLength = false;

    @Builder.Default
    @Column(name = "is_volume_over_size")
    private Boolean isVolumeOverSize = false;

    @Builder.Default
    @Column(name = "is_force_to_normal")
    private Boolean isForceToNormal = false;

    @Column(name = "shipping_mode")
    private Integer shippingMode;

    @Column(name = "individually_packed")
    private Integer individuallyPacked;

    @Column(name = "sub_sell_mode")
    private Integer subSellMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sensitive_limit_json", columnDefinition = "jsonb")
    private String sensitiveLimitJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", columnDefinition = "jsonb")
    private String rawJson;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
