package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_activity_blacklist",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_activity_blacklist_product", columnNames = {"shop_id", "product_id"}),
        indexes = {
                @Index(name = "idx_temu_activity_blacklist_shop", columnList = "shop_id"),
                @Index(name = "idx_temu_activity_blacklist_product", columnList = "product_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuActivityBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "goods_id")
    private Long goodsId;

    @Column(name = "product_name", length = 1000)
    private String productName;

    @Column(name = "reason", length = 512)
    private String reason;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
}
