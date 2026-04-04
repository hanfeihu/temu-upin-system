package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_goods_site",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_goods_site", columnNames = {"goods_id", "site_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuGoodsSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goods_id", nullable = false)
    private Long goodsId;

    @Column(name = "site_id", nullable = false)
    private Integer siteId;

    @Column(name = "site_name", length = 128)
    private String siteName;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
