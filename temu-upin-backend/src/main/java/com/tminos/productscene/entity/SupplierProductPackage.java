package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "supplier_product_packages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_supplier_product_package_submission", columnNames = "submission_id")
        },
        indexes = {
                @Index(name = "idx_supplier_product_package_status", columnList = "status"),
                @Index(name = "idx_supplier_product_package_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierProductPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "ai_title", columnDefinition = "TEXT")
    private String aiTitle;

    @Column(name = "ai_title_zh", columnDefinition = "TEXT")
    private String aiTitleZh;

    @Column(name = "generated_image_urls_json", columnDefinition = "TEXT")
    private String generatedImageUrlsJson;

    @Column(name = "prompt_json", columnDefinition = "TEXT")
    private String promptJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "product_collection_id")
    private Long productCollectionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null || status.isBlank()) status = "PENDING";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
