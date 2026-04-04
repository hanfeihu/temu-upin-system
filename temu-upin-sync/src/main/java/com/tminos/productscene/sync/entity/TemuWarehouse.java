package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_warehouse",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_warehouse", columnNames = {"shop_id", "site_id", "warehouse_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuWarehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "site_id", nullable = false)
    private Integer siteId;

    @Column(name = "site_name", length = 128)
    private String siteName;

    @Column(name = "warehouse_id", nullable = false, length = 128)
    private String warehouseId;

    @Column(name = "warehouse_name", length = 256)
    private String warehouseName;

    @Column(name = "management_type", length = 16)
    private String managementType;

    @Builder.Default
    @Column(name = "warehouse_disable")
    private Boolean warehouseDisable = false;

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
