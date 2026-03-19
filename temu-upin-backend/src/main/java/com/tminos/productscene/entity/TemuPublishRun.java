package com.tminos.productscene.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_publish_runs",
        indexes = {
                @Index(name = "idx_temu_publish_runs_spu", columnList = "spu_id"),
                @Index(name = "idx_temu_publish_runs_status", columnList = "status")
        }
)
public class TemuPublishRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spu_id", nullable = false)
    private Long spuId;

    @Column(nullable = false, length = 32)
    private String status; // STARTED/SUCCEEDED/FAILED

    @Column(length = 128)
    private String goodsId;

    @Column(columnDefinition = "TEXT")
    private String requestJson;

    @Column(columnDefinition = "TEXT")
    private String responseRaw;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @PrePersist
    public void prePersist() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (status == null) status = "STARTED";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getGoodsId() { return goodsId; }
    public void setGoodsId(String goodsId) { this.goodsId = goodsId; }
    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String requestJson) { this.requestJson = requestJson; }
    public String getResponseRaw() { return responseRaw; }
    public void setResponseRaw(String responseRaw) { this.responseRaw = responseRaw; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
