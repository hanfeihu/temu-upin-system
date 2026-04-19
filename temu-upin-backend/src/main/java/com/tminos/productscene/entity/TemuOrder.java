package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_orders",
        indexes = {
                @Index(name = "idx_temu_orders_shop_record_id", columnList = "shop_record_id"),
                @Index(name = "idx_temu_orders_shop_id", columnList = "shop_id"),
                @Index(name = "idx_temu_orders_parent_order_sn", columnList = "parent_order_sn"),
                @Index(name = "idx_temu_orders_dianxiaomi_package_number", columnList = "dianxiaomi_package_number"),
                @Index(name = "idx_temu_orders_order_status", columnList = "order_status"),
                @Index(name = "idx_temu_orders_matched_spu_id", columnList = "matched_spu_id"),
                @Index(name = "idx_temu_orders_updated_at", columnList = "updated_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_temu_orders_shop_order_sn", columnNames = {"shop_record_id", "order_sn"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_record_id", nullable = false)
    private Long shopRecordId;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "shop_name", length = 128)
    private String shopName;

    @Column(name = "order_sn", nullable = false, length = 128)
    private String orderSn;

    @Column(name = "parent_order_sn", length = 128)
    private String parentOrderSn;

    @Column(name = "dianxiaomi_package_number", length = 128)
    private String dianxiaomiPackageNumber;

    @Column(name = "goods_id", length = 128)
    private String goodsId;

    @Column(name = "goods_name", length = 1000)
    private String goodsName;

    @Column(name = "spec", length = 500)
    private String spec;

    @Column(name = "thumb_url", length = 2000)
    private String thumbUrl;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "order_status")
    private Integer orderStatus;

    @Column(name = "parent_order_status")
    private Integer parentOrderStatus;

    @Column(name = "order_payment_type", length = 128)
    private String orderPaymentType;

    @Column(name = "inventory_deduction_warehouse_id", length = 128)
    private String inventoryDeductionWarehouseId;

    @Column(name = "inventory_deduction_warehouse_name", length = 255)
    private String inventoryDeductionWarehouseName;

    @Column(name = "order_time_ms")
    private Long orderTimeMs;

    @Column(name = "update_time_ms")
    private Long updateTimeMs;

    @Column(name = "earliest_time_get_shipping_document_ms")
    private Long earliestTimeGetShippingDocumentMs;

    @Column(name = "expect_ship_latest_time_ms")
    private Long expectShipLatestTimeMs;

    @Column(name = "region_id")
    private Integer regionId;

    @Column(name = "site_id")
    private Integer siteId;

    @Column(name = "product_skus_json", columnDefinition = "TEXT")
    private String productSkusJson;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "matched_spu_id")
    private Long matchedSpuId;

    @Column(name = "matched_temu_sku_id", length = 128)
    private String matchedTemuSkuId;

    @Column(name = "matched_origin_sku_id", length = 128)
    private String matchedOriginSkuId;

    @Column(name = "matched_product_name", length = 500)
    private String matchedProductName;

    @Column(name = "match_status", length = 32)
    private String matchStatus;

    @Column(name = "match_message", length = 500)
    private String matchMessage;

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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
