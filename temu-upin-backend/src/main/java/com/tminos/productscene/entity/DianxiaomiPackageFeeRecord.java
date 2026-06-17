package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "dianxiaomi_package_fee_record",
        indexes = {
                @Index(name = "idx_dxm_package_fee_record_updated_at", columnList = "updated_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dxm_package_fee_record_package_number", columnNames = {"dianxiaomi_package_number"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DianxiaomiPackageFeeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dianxiaomi_package_number", nullable = false, length = 128)
    private String dianxiaomiPackageNumber;

    @Column(name = "total_fee", precision = 19, scale = 4)
    private BigDecimal totalFee;

    @Column(name = "fee_detail_json", columnDefinition = "TEXT")
    private String feeDetailJson;

    @Column(name = "raw_response_json", columnDefinition = "TEXT")
    private String rawResponseJson;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "last_queried_at")
    private LocalDateTime lastQueriedAt;

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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
