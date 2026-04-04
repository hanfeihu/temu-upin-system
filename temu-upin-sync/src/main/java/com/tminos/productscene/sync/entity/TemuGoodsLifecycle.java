package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods_lifecycle",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_lifecycle_shop_skc", columnNames = {"shop_id", "skc_id"}),
        indexes = {
                @Index(name = "idx_temu_lifecycle_product", columnList = "product_id"),
                @Index(name = "idx_temu_lifecycle_select", columnList = "select_status")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoodsLifecycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "skc_id", nullable = false)
    private Long skcId;

    @Column(name = "select_status")
    private Integer selectStatus;

    @Column(name = "apply_jit_status")
    private Integer applyJitStatus;

    @Builder.Default
    @Column(name = "suggest_close_jit")
    private Boolean suggestCloseJit = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sku_ids_json", columnDefinition = "jsonb")
    private String skuIdsJson;

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
