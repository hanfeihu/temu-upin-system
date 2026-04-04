package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_price_adjust_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_price_adjust", columnNames = {"shop_id", "price_order_sn"}),
        indexes = {
                @Index(name = "idx_temu_price_adjust_status", columnList = "status"),
                @Index(name = "idx_temu_price_adjust_skc", columnList = "skc_id"),
                @Index(name = "idx_temu_price_adjust_review", columnList = "review_action")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuPriceAdjustOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "price_order_sn", nullable = false, length = 128)
    private String priceOrderSn;

    @Column(name = "skc_id")
    private Long skcId;

    @Column(name = "product_name", length = 1000)
    private String productName;

    @Column(name = "price_type")
    private Integer priceType;

    @Column(name = "source", length = 32)
    private String source;

    @Column(name = "adjust_reason", length = 2000)
    private String adjustReason;

    @Column(name = "new_supply_price", length = 64)
    private String newSupplyPrice;

    @Column(name = "price_currency", length = 16)
    private String priceCurrency;

    @Column(name = "reject_reason", length = 2000)
    private String rejectReason;

    @Builder.Default
    @Column(name = "traffic_low_expose")
    private Boolean trafficLowExpose = false;

    @Column(name = "status")
    private Integer status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "site_names_json", columnDefinition = "jsonb")
    private String siteNamesJson;

    @Column(name = "review_action", length = 16)
    private String reviewAction;

    @Column(name = "review_at")
    private LocalDateTime reviewAt;

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
