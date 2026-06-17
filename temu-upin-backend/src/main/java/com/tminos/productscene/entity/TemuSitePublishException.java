package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_site_publish_exceptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_temu_site_publish_ex_skc_site", columnNames = {"skc", "site_name"})
        },
        indexes = {
                @Index(name = "idx_temu_site_publish_ex_offer", columnList = "offer_id"),
                @Index(name = "idx_temu_site_publish_ex_spu", columnList = "temu_spu_id"),
                @Index(name = "idx_temu_site_publish_ex_goods", columnList = "temu_goods_id"),
                @Index(name = "idx_temu_site_publish_ex_site", columnList = "site_name"),
                @Index(name = "idx_temu_site_publish_ex_reason", columnList = "reason_type"),
                @Index(name = "idx_temu_site_publish_ex_active", columnList = "active"),
                @Index(name = "idx_temu_site_publish_ex_updated", columnList = "updated_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuSitePublishException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skc", nullable = false, length = 256)
    private String skc;

    @Column(name = "offer_id", length = 128)
    private String offerId;

    @Column(name = "product_collection_id")
    private Long productCollectionId;

    @Column(name = "temu_spu_id", length = 128)
    private String temuSpuId;

    @Column(name = "temu_goods_id", length = 128)
    private String temuGoodsId;

    @Column(name = "goods_no", length = 256)
    private String goodsNo;

    @Column(name = "site_name", nullable = false, length = 128)
    private String siteName;

    @Column(name = "status_text", length = 128)
    private String statusText;

    @Column(name = "product_title", columnDefinition = "TEXT")
    private String productTitle;

    @Column(name = "category_text", columnDefinition = "TEXT")
    private String categoryText;

    @Column(name = "declared_price", precision = 18, scale = 2)
    private BigDecimal declaredPrice;

    @Column(name = "reason_text", columnDefinition = "TEXT")
    private String reasonText;

    @Column(name = "reason_type", length = 128)
    private String reasonType;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @Column(name = "created_time_text", length = 128)
    private String createdTimeText;

    @Column(name = "price_confirm_time_text", length = 128)
    private String priceConfirmTimeText;

    @Column(name = "joined_site_time_text", length = 128)
    private String joinedSiteTimeText;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "source_title", length = 256)
    private String sourceTitle;

    @Column(name = "source_collected_at")
    private LocalDateTime sourceCollectedAt;

    @Column(name = "source_file_name", length = 512)
    private String sourceFileName;

    @Column(name = "row_text", columnDefinition = "TEXT")
    private String rowText;

    @Column(name = "detail_text", columnDefinition = "TEXT")
    private String detailText;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (active == null) active = true;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (active == null) active = true;
    }
}
