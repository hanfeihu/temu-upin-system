package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "supplier_product_submissions",
        indexes = {
                @Index(name = "idx_supplier_product_submission_status", columnList = "status"),
                @Index(name = "idx_supplier_product_submission_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierProductSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_name", nullable = false, length = 255)
    private String supplierName;

    @Column(name = "supplier_phone", length = 64)
    private String supplierPhone;

    @Column(name = "supplier_address", columnDefinition = "TEXT")
    private String supplierAddress;

    @Column(name = "product_name", nullable = false, columnDefinition = "TEXT")
    private String productName;

    @Column(name = "supply_price", precision = 18, scale = 4)
    private BigDecimal supplyPrice;

    @Column(name = "weight_g", precision = 18, scale = 4)
    private BigDecimal weightG;

    @Column(name = "length_cm", precision = 18, scale = 4)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 18, scale = 4)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 18, scale = 4)
    private BigDecimal heightCm;

    @Column(name = "image_urls_json", columnDefinition = "TEXT")
    private String imageUrlsJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
