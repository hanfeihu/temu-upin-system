package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_order_aftersales",
        indexes = {
                @Index(name = "idx_temu_order_aftersales_shop_record_id", columnList = "shop_record_id"),
                @Index(name = "idx_temu_order_aftersales_parent_order_sn", columnList = "parent_order_sn"),
                @Index(name = "idx_temu_order_aftersales_status_group", columnList = "after_sales_status_group"),
                @Index(name = "idx_temu_order_aftersales_updated_at", columnList = "updated_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_temu_order_aftersales_shop_parent_after_sales_sn", columnNames = {"shop_record_id", "parent_after_sales_sn"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuOrderAftersale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_record_id", nullable = false)
    private Long shopRecordId;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "shop_name", length = 128)
    private String shopName;

    @Column(name = "parent_after_sales_sn", nullable = false, length = 128)
    private String parentAfterSalesSn;

    @Column(name = "parent_order_sn", length = 128)
    private String parentOrderSn;

    @Column(name = "after_sales_status_group")
    private Integer afterSalesStatusGroup;

    @Column(name = "after_sales_status_group_name", length = 200)
    private String afterSalesStatusGroupName;

    @Column(name = "parent_after_sales_status")
    private Integer parentAfterSalesStatus;

    @Column(name = "parent_after_sales_status_name", length = 200)
    private String parentAfterSalesStatusName;

    @Column(name = "after_sales_type")
    private Integer afterSalesType;

    @Column(name = "after_sales_type_name", length = 200)
    private String afterSalesTypeName;

    @Column(name = "available_operate_list_json", columnDefinition = "TEXT")
    private String availableOperateListJson;

    @Column(name = "available_operate_names_json", columnDefinition = "TEXT")
    private String availableOperateNamesJson;

    @Column(name = "return_delivery_type")
    private Integer returnDeliveryType;

    @Column(name = "return_delivery_type_name", length = 200)
    private String returnDeliveryTypeName;

    @Column(name = "operate_expire_time_ms")
    private Long operateExpireTimeMs;

    @Column(name = "create_at_ms")
    private Long createAtMs;

    @Column(name = "update_at_ms")
    private Long updateAtMs;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

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
