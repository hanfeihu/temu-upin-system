package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_shops",
        indexes = {
                @Index(name = "idx_temu_shops_enabled", columnList = "enabled"),
                @Index(name = "idx_temu_shops_app_id", columnList = "app_id"),
                @Index(name = "idx_temu_shops_shop_id", columnList = "shop_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_temu_shops_shop_id", columnNames = {"shop_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuShop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_name", nullable = false, length = 128)
    private String shopName;

    /** TEMU Shop ID, e.g. 634418212966313. Store as string to avoid JS precision issues. */
    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    /** TEMU API token for this shop */
    @Column(name = "token", nullable = false, length = 2000)
    private String token;

    @Column(name = "site_id", nullable = false)
    private Integer siteId;

    @Column(name = "warehouse_id", nullable = false, length = 128)
    private String warehouseId;

    @Column(name = "sku_default_stock", nullable = false)
    private Integer skuDefaultStock;

    @Column(name = "sku_max_stock", nullable = false)
    private Integer skuMaxStock;

    @Column(name = "origin_region1_short_name", nullable = false, length = 32)
    private String originRegion1ShortName;

    @Column(name = "origin_region2_id", nullable = false)
    private Long originRegion2Id;

    @Column(name = "freight_template_id", nullable = false, length = 128)
    private String freightTemplateId;

    @Column(name = "shipment_limit_second", nullable = false)
    private Integer shipmentLimitSecond;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false)
    private TemuSelfApp app;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getShopName() { return shopName; }
    public String getShopId() { return shopId; }
    public String getToken() { return token; }
    public Integer getSiteId() { return siteId; }
    public String getWarehouseId() { return warehouseId; }
    public Integer getSkuDefaultStock() { return skuDefaultStock; }
    public Integer getSkuMaxStock() { return skuMaxStock; }
    public String getOriginRegion1ShortName() { return originRegion1ShortName; }
    public Long getOriginRegion2Id() { return originRegion2Id; }
    public String getFreightTemplateId() { return freightTemplateId; }
    public Integer getShipmentLimitSecond() { return shipmentLimitSecond; }
    public TemuSelfApp getApp() { return app; }
    public Boolean getEnabled() { return enabled; }
}
