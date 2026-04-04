package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods_sku_price",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_sku_price", columnNames = {"shop_id", "product_sku_id"}),
        indexes = {
                @Index(name = "idx_temu_sku_price_product", columnList = "product_id"),
                @Index(name = "idx_temu_sku_price_skc", columnList = "product_skc_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoodsSkuPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_skc_id", nullable = false)
    private Long productSkcId;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "supplier_price")
    private Integer supplierPrice;

    @Column(name = "currency_type", length = 16)
    private String currencyType;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
