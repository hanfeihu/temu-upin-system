package com.tminos.productscene.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "post_import_runs",
        indexes = {
                @Index(name = "idx_post_import_runs_spu", columnList = "spu_id"),
                @Index(name = "idx_post_import_runs_status", columnList = "status")
        }
)
public class PostImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spu_id", nullable = false)
    private Long spuId;

    @Column(nullable = false, length = 32)
    private String status; // STARTED/SUCCEEDED/FAILED

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(columnDefinition = "TEXT")
    private String sampleJson;

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
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getSampleJson() { return sampleJson; }
    public void setSampleJson(String sampleJson) { this.sampleJson = sampleJson; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
