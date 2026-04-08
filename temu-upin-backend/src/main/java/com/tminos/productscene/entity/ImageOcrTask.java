package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "image_ocr_task",
        indexes = {
                @Index(name = "idx_ocr_task_spu_id", columnList = "spu_id"),
                @Index(name = "idx_ocr_task_product_id", columnList = "product_id"),
                @Index(name = "idx_ocr_task_status", columnList = "exec_status"),
                @Index(name = "idx_ocr_task_type", columnList = "image_type"),
                @Index(name = "idx_ocr_task_filtered", columnList = "filtered"),
                @Index(name = "idx_ocr_task_contains_chinese", columnList = "contains_chinese")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageOcrTask {

    /**
     * Image type:
     * 1 carousel, 2 detail, 3 sku
     */
    public static final int IMAGE_TYPE_CAROUSEL = 1;
    public static final int IMAGE_TYPE_DETAIL = 2;
    public static final int IMAGE_TYPE_SKU = 3;

    /**
     * Exec status:
     * 0 pending, 1 running, 2 success, 3 failed
     */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_FAILED = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ProductCollection id
    @Column(name = "spu_id", nullable = false)
    private Long spuId;

    // External product id (e.g. 1688 productId)
    @Column(name = "product_id", length = 128)
    private String productId;

    @Column(name = "image_type", nullable = false)
    private Integer imageType;

    @Column(name = "image_url", length = 2000, nullable = false)
    private String imageUrl;

    @Builder.Default
    @Column(name = "exec_status", nullable = false)
    private Integer execStatus = STATUS_PENDING;

    // OCR content
    @Column(name = "exec_result", columnDefinition = "TEXT")
    private String execResult;

    @Column(name = "fail_reason", columnDefinition = "TEXT")
    private String failReason;

    @Column(name = "executor_ip", length = 64)
    private String executorPublicIp;

    private LocalDateTime taskStartedAt;
    private LocalDateTime taskFinishedAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean filtered = false;

    @Column(name = "contains_chinese")
    private Boolean containsChinese;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (execStatus == null) execStatus = STATUS_PENDING;
        if (filtered == null) filtered = false;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Explicit getters/setters (keep Lombok too)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public Integer getImageType() { return imageType; }
    public void setImageType(Integer imageType) { this.imageType = imageType; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Integer getExecStatus() { return execStatus; }
    public void setExecStatus(Integer execStatus) { this.execStatus = execStatus; }
    public String getExecResult() { return execResult; }
    public void setExecResult(String execResult) { this.execResult = execResult; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getExecutorPublicIp() { return executorPublicIp; }
    public void setExecutorPublicIp(String executorPublicIp) { this.executorPublicIp = executorPublicIp; }
    public LocalDateTime getTaskStartedAt() { return taskStartedAt; }
    public void setTaskStartedAt(LocalDateTime taskStartedAt) { this.taskStartedAt = taskStartedAt; }
    public LocalDateTime getTaskFinishedAt() { return taskFinishedAt; }
    public void setTaskFinishedAt(LocalDateTime taskFinishedAt) { this.taskFinishedAt = taskFinishedAt; }
    public Boolean getFiltered() { return filtered; }
    public void setFiltered(Boolean filtered) { this.filtered = filtered; }
    public Boolean getContainsChinese() { return containsChinese; }
    public void setContainsChinese(Boolean containsChinese) { this.containsChinese = containsChinese; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
