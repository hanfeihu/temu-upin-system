package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods_sku_site_price",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_sku_site_price", columnNames = {"sku_price_id", "site_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoodsSkuSitePrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_price_id", nullable = false)
    private Long skuPriceId;

    @Column(name = "site_id", nullable = false)
    private Integer siteId;

    @Column(name = "supplier_price")
    private Integer supplierPrice;

    @Column(name = "price_review_status")
    private Integer priceReviewStatus;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
