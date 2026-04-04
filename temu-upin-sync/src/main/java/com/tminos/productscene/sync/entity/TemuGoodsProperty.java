package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods_property",
        indexes = {
                @Index(name = "idx_temu_goods_property_goods", columnList = "goods_id"),
                @Index(name = "idx_temu_goods_property_pid", columnList = "pid")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoodsProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "pid", nullable = false)
    private Integer pid;

    @Column(name = "template_pid")
    private Integer templatePid;

    @Column(name = "ref_pid")
    private Integer refPid;

    @Column(name = "prop_name", length = 256)
    private String propName;

    @Column(name = "vid")
    private Integer vid;

    @Column(name = "prop_value", length = 1000)
    private String propValue;

    @Column(name = "value_unit", length = 64)
    private String valueUnit;

    @Column(name = "value_extend_info", length = 1000)
    private String valueExtendInfo;

    @Column(name = "number_input_value", length = 256)
    private String numberInputValue;

    @Column(name = "language", length = 16)
    private String language;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
