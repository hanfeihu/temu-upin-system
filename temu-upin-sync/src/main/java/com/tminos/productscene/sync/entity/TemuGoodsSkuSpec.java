package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods_sku_spec",
        indexes = {
                @Index(name = "idx_temu_goods_sku_spec_sku", columnList = "sku_id"),
                @Index(name = "idx_temu_goods_sku_spec_parent", columnList = "parent_spec_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoodsSkuSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "spec_id", nullable = false)
    private Integer specId;

    @Column(name = "spec_name", nullable = false, length = 256)
    private String specName;

    @Column(name = "parent_spec_id", nullable = false)
    private Integer parentSpecId;

    @Column(name = "parent_spec_name", nullable = false, length = 256)
    private String parentSpecName;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
