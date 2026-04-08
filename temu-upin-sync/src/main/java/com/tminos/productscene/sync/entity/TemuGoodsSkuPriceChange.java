package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods_sku_price_change",
        indexes = {
                @Index(name = "idx_temu_sku_price_change_shop_sku", columnList = "shop_id, product_sku_id"),
                @Index(name = "idx_temu_sku_price_change_time", columnList = "changed_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoodsSkuPriceChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_skc_id")
    private Long productSkcId;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "sku_price_id")
    private Long skuPriceId;

    @Column(name = "site_id")
    private Integer siteId;

    @Column(name = "old_supplier_price")
    private Integer oldSupplierPrice;

    @Column(name = "new_supplier_price")
    private Integer newSupplierPrice;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Builder.Default
    @Column(name = "change_source", nullable = false, length = 32)
    private String changeSource = "PRICE_SYNC";
}
