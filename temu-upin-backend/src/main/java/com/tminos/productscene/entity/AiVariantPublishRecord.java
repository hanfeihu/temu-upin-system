package com.tminos.productscene.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ai_variant_publish_records",
        indexes = {
                @Index(name = "idx_ai_variant_publish_records_created_at", columnList = "createdAt"),
                @Index(name = "idx_ai_variant_publish_records_shop_record_id", columnList = "shopRecordId"),
                @Index(name = "idx_ai_variant_publish_records_status", columnList = "status")
        }
)
public class AiVariantPublishRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_record_id", nullable = false)
    private Long shopRecordId;

    @Column(name = "shop_id", length = 128)
    private String shopId;

    @Column(name = "shop_name", length = 255)
    private String shopName;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "source_biz_type", length = 64)
    private String sourceBizType;

    @Column(name = "source_biz_id")
    private Long sourceBizId;

    @Column(name = "source_biz_name", length = 255)
    private String sourceBizName;

    @Column(name = "source_note", columnDefinition = "TEXT")
    private String sourceNote;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "goods_id", length = 128)
    private String goodsId;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
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
        if (status == null) {
            status = "STARTED";
        }
        if (sourceType == null || sourceType.isBlank()) {
            sourceType = "RAW_PUBLISH";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getShopRecordId() {
        return shopRecordId;
    }

    public void setShopRecordId(Long shopRecordId) {
        this.shopRecordId = shopRecordId;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceNote() {
        return sourceNote;
    }

    public String getSourceBizType() {
        return sourceBizType;
    }

    public void setSourceBizType(String sourceBizType) {
        this.sourceBizType = sourceBizType;
    }

    public Long getSourceBizId() {
        return sourceBizId;
    }

    public void setSourceBizId(Long sourceBizId) {
        this.sourceBizId = sourceBizId;
    }

    public String getSourceBizName() {
        return sourceBizName;
    }

    public void setSourceBizName(String sourceBizName) {
        this.sourceBizName = sourceBizName;
    }

    public void setSourceNote(String sourceNote) {
        this.sourceNote = sourceNote;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
