package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_price_review_sku",
        indexes = @Index(name = "idx_temu_price_review_sku_order", columnList = "review_order_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuPriceReviewSku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_order_id", nullable = false)
    private Long reviewOrderId;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "new_price")
    private Integer newPrice;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
