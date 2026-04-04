package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_price_review_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_price_review", columnNames = {"shop_id", "order_id"}),
        indexes = {
                @Index(name = "idx_temu_price_review_status", columnList = "order_status"),
                @Index(name = "idx_temu_price_review_action", columnList = "review_action")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuPriceReviewOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_status")
    private Integer orderStatus;

    @Column(name = "supply_price")
    private Integer supplyPrice;

    @Column(name = "price_currency", length = 16)
    private String priceCurrency;

    @Column(name = "suggest_supply_price")
    private Integer suggestSupplyPrice;

    @Column(name = "suggest_price_currency", length = 16)
    private String suggestPriceCurrency;

    @Builder.Default
    @Column(name = "can_bargain")
    private Boolean canBargain = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "site_ids_json", columnDefinition = "jsonb")
    private String siteIdsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "site_names_json", columnDefinition = "jsonb")
    private String siteNamesJson;

    @Column(name = "review_action", length = 16)
    private String reviewAction;

    @Column(name = "review_at")
    private LocalDateTime reviewAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reject_reasons_json", columnDefinition = "jsonb")
    private String rejectReasonsJson;

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
