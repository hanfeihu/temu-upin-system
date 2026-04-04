package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_activity_enroll_price",
        indexes = {
                @Index(name = "idx_temu_enroll_price_enrollment", columnList = "enrollment_id"),
                @Index(name = "idx_temu_enroll_price_skc", columnList = "skc_id"),
                @Index(name = "idx_temu_enroll_price_sku", columnList = "sku_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuActivityEnrollPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "level", nullable = false, length = 8)
    private String level;

    @Column(name = "skc_id")
    private Long skcId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "site_id")
    private Integer siteId;

    @Column(name = "site_name", length = 128)
    private String siteName;

    @Column(name = "daily_price")
    private Integer dailyPrice;

    @Column(name = "activity_price")
    private Integer activityPrice;

    @Column(name = "activity_discount")
    private Integer activityDiscount;

    @Column(name = "currency", length = 16)
    private String currency;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
