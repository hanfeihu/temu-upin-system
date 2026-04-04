package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods_sku_barcode",
        indexes = {
                @Index(name = "idx_temu_goods_sku_barcode_sku", columnList = "sku_id"),
                @Index(name = "idx_temu_goods_sku_barcode_code", columnList = "code")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoodsSkuBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "code", nullable = false, length = 128)
    private String code;

    @Column(name = "code_type", nullable = false)
    private Integer codeType;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
