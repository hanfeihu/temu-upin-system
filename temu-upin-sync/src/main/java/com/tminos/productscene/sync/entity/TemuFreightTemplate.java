package com.tminos.productscene.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "temu_freight_template",
        uniqueConstraints = @UniqueConstraint(name = "uk_temu_freight_tpl", columnNames = {"shop_id", "freight_template_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemuFreightTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "freight_template_id", nullable = false, length = 128)
    private String freightTemplateId;

    @Column(name = "template_name", length = 256)
    private String templateName;

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
