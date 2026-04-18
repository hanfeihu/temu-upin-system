package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_goods_shop_skc", columnNames = {"shop_id", "product_skc_id"}),
        indexes = {
                @Index(name = "idx_temu_goods_shop", columnList = "shop_id"),
                @Index(name = "idx_temu_goods_product", columnList = "product_id"),
                @Index(name = "idx_temu_goods_name", columnList = "product_name"),
                @Index(name = "idx_temu_goods_leaf_cat", columnList = "leaf_cat_id"),
                @Index(name = "idx_temu_goods_synced", columnList = "synced_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoods {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_skc_id", nullable = false)
    private Long productSkcId;

    @Column(name = "product_name", nullable = false, length = 1000)
    private String productName;

    @Column(name = "ext_code", length = 256)
    private String extCode;

    @Column(name = "main_image_url", columnDefinition = "text")
    private String mainImageUrl;

    @Column(name = "skc_site_status")
    private Integer skcSiteStatus;

    @Builder.Default
    @Column(name = "is_support_personalization")
    private Boolean isSupportPersonalization = false;

    @Builder.Default
    @Column(name = "match_skc_jit_mode")
    private Boolean matchSkcJitMode = false;

    @Builder.Default
    @Column(name = "match_jit_mode")
    private Boolean matchJitMode = false;

    @Builder.Default
    @Column(name = "sign_latest_jit_version")
    private Boolean signLatestJitVersion = false;

    @Column(name = "quick_sell_agt_sign_status")
    private Integer quickSellAgtSignStatus;

    @Column(name = "leaf_cat_id")
    private Integer leafCatId;

    @Column(name = "leaf_cat_name", length = 256)
    private String leafCatName;

    @Column(name = "cat_type")
    private Integer catType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "categories_json", columnDefinition = "jsonb")
    private String categoriesJson;

    @Column(name = "freight_template_id", length = 128)
    private String freightTemplateId;

    @Column(name = "shipment_limit_second")
    private Integer shipmentLimitSecond;

    @Column(name = "select_status")
    private Integer selectStatus;

    @Column(name = "apply_jit_status")
    private Integer applyJitStatus;

    @Builder.Default
    @Column(name = "suggest_close_jit")
    private Boolean suggestCloseJit = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sku_ids_json", columnDefinition = "jsonb")
    private String skuIdsJson;

    @Column(name = "lifecycle_synced_at")
    private LocalDateTime lifecycleSyncedAt;

    @Column(name = "long_transport")
    private Boolean longTransport;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warehouse_region_ids_json", columnDefinition = "jsonb")
    private String warehouseRegionIdsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_origin_json", columnDefinition = "jsonb")
    private String productOriginJson;

    @Column(name = "temu_created_at")
    private Long temuCreatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", columnDefinition = "jsonb")
    private String rawJson;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Transient
    private Integer site100MinSupplierPrice;

    @Transient
    private Integer site100MaxSupplierPrice;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}
