package com.tminos.productscene.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_auto_publish_logs",
        indexes = {
                @Index(name = "idx_temu_auto_publish_logs_run", columnList = "run_id"),
                @Index(name = "idx_temu_auto_publish_logs_stage", columnList = "stage")
        }
)
public class TemuAutoPublishLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(nullable = false, length = 64)
    private String stage;

    @Column(nullable = false, length = 16)
    private String level; // INFO/WARN/ERROR

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String dataJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (level == null) level = "INFO";
        if (stage == null) stage = "";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
