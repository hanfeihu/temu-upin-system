package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods_decoration",
        indexes = @Index(name = "idx_temu_goods_decoration_goods", columnList = "goods_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoodsDecoration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "floor_id")
    private Integer floorId;

    @Column(name = "floor_key", length = 128)
    private String floorKey;

    @Column(name = "floor_type", length = 64)
    private String floorType;

    @Column(name = "lang", length = 16)
    private String lang;

    @Builder.Default
    @Column(name = "priority")
    private Integer priority = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", columnDefinition = "jsonb")
    private String contentJson;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
