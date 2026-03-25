package com.tminos.productscene.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_publish_success_cases",
        indexes = {
                @Index(name = "idx_temu_publish_success_cases_spu", columnList = "spu_id"),
                @Index(name = "idx_temu_publish_success_cases_cat", columnList = "temu_catid")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_temu_publish_success_cases_run", columnNames = "publish_run_id")
        }
)
public class TemuPublishSuccessCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publish_run_id", nullable = false)
    private Long publishRunId;

    @Column(name = "spu_id", nullable = false)
    private Long spuId;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "product_name", nullable = false, length = 1000)
    private String productName;

    @Column(name = "temu_catid", columnDefinition = "TEXT")
    private String temuCatid;

    @Column(name = "temu_catname", columnDefinition = "TEXT")
    private String temuCatname;

    @Column(name = "goods_id", length = 128)
    private String goodsId;

    @Column(name = "request_json", columnDefinition = "TEXT", nullable = false)
    private String requestJson;

    @Column(name = "response_raw", columnDefinition = "TEXT", nullable = false)
    private String responseRaw;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPublishRunId() {
        return publishRunId;
    }

    public void setPublishRunId(Long publishRunId) {
        this.publishRunId = publishRunId;
    }

    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getTemuCatid() {
        return temuCatid;
    }

    public void setTemuCatid(String temuCatid) {
        this.temuCatid = temuCatid;
    }

    public String getTemuCatname() {
        return temuCatname;
    }

    public void setTemuCatname(String temuCatname) {
        this.temuCatname = temuCatname;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }

    public String getResponseRaw() {
        return responseRaw;
    }

    public void setResponseRaw(String responseRaw) {
        this.responseRaw = responseRaw;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}