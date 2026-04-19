package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_order_logistics",
        indexes = {
                @Index(name = "idx_temu_order_logistics_shop_record_id", columnList = "shop_record_id"),
                @Index(name = "idx_temu_order_logistics_parent_order_sn", columnList = "parent_order_sn"),
                @Index(name = "idx_temu_order_logistics_provider_code", columnList = "provider_code")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_temu_order_logistics_shop_parent_provider", columnNames = {"shop_record_id", "parent_order_sn", "provider_code"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuOrderLogistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_record_id", nullable = false)
    private Long shopRecordId;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "shop_name", length = 128)
    private String shopName;

    @Column(name = "parent_order_sn", nullable = false, length = 128)
    private String parentOrderSn;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "provider_name", length = 128)
    private String providerName;

    @Column(name = "reference_no", length = 255)
    private String referenceNo;

    @Column(name = "shipping_method_no", length = 255)
    private String shippingMethodNo;

    @Column(name = "tracking_number", length = 255)
    private String trackingNumber;

    @Column(name = "destination_country", length = 64)
    private String destinationCountry;

    @Column(name = "track_status", length = 64)
    private String trackStatus;

    @Column(name = "track_status_name", length = 128)
    private String trackStatusName;

    @Column(name = "track_details_json", columnDefinition = "TEXT")
    private String trackDetailsJson;

    @Column(name = "order_fee_detail_json", columnDefinition = "TEXT")
    private String orderFeeDetailJson;

    @Column(name = "order_weight_info_json", columnDefinition = "TEXT")
    private String orderWeightInfoJson;

    @Column(name = "gross_weight", precision = 19, scale = 4)
    private BigDecimal grossWeight;

    @Column(name = "volume_weight", precision = 19, scale = 4)
    private BigDecimal volumeWeight;

    @Column(name = "charge_weight", precision = 19, scale = 4)
    private BigDecimal chargeWeight;

    @Column(name = "first_leg_logistics_fee", precision = 19, scale = 4)
    private BigDecimal firstLegLogisticsFee;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
