package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_draft",
        indexes = {
                @Index(name = "idx_product_draft_source_platform", columnList = "source_platform"),
                @Index(name = "idx_product_draft_product_id", columnList = "product_id"),
                @Index(name = "idx_product_draft_deleted", columnList = "deleted")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(length = 64)
    private String sourcePlatform;

    @Column(nullable = false, length = 2000)
    private String productName;

    @Column(nullable = false, length = 256)
    private String productId;

    @Column(length = 2000)
    private String productMainImage;

    @Column(length = 2000)
    private String productUrl;

    @Column(length = 2000)
    private String sourceUrl;

    @Column(length = 2000)
    private String productCategory;

    @Column(columnDefinition = "TEXT")
    private String originalCategory;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String originalHtml;

    @Column(columnDefinition = "TEXT")
    private String extractedJson;

    @Column(columnDefinition = "TEXT")
    private String parserSnapshotJson;

    private String companyName;

    private String monthlySales;

    private Integer reviewCount;

    @Column(columnDefinition = "TEXT")
    private String targetShopIds;

    @Column(columnDefinition = "TEXT")
    private String targetShopNames;

    private Long pushedCollectionId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean pushedToCollection = false;

    private LocalDateTime pushedAt;

    @Column(length = 512)
    private String pushMessage;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (deleted == null) deleted = false;
        if (version == null) version = 0;
        if (pushedToCollection == null) pushedToCollection = false;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
