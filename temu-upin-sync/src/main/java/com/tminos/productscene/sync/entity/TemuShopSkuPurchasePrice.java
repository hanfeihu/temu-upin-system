package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_shop_sku_purchase_price",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_shop_sku_purchase_price", columnNames = {"shop_id", "product_sku_id"}),
        indexes = {
                @Index(name = "idx_temu_shop_sku_purchase_price_skc", columnList = "shop_id, product_skc_id"),
                @Index(name = "idx_temu_shop_sku_purchase_price_ext_code", columnList = "shop_id, sku_ext_code")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuShopSkuPurchasePrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "product_skc_id")
    private Long productSkcId;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "sku_ext_code", length = 256)
    private String skuExtCode;

    @Column(name = "purchase_price")
    private Integer purchasePrice;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

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
