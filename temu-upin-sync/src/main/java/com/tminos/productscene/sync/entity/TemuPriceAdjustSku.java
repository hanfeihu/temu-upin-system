package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_price_adjust_sku",
        indexes = {
                @Index(name = "idx_temu_price_adjust_sku_order", columnList = "adjust_order_id"),
                @Index(name = "idx_temu_price_adjust_sku_sku", columnList = "product_sku_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuPriceAdjustSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adjust_order_id", nullable = false)
    private Long adjustOrderId;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "price")
    private Integer price;

    @Column(name = "spec", length = 2000)
    private String spec;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
