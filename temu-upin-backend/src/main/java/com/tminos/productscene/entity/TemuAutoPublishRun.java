package com.tminos.productscene.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_auto_publish_runs",
        indexes = {
                @Index(name = "idx_temu_auto_publish_runs_spu", columnList = "spu_id"),
                @Index(name = "idx_temu_auto_publish_runs_status", columnList = "status"),
                @Index(name = "idx_temu_auto_publish_runs_action", columnList = "action")
        }
)
public class TemuAutoPublishRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spu_id", nullable = false)
    private Long spuId;

    /**
     * Status:
     * - STARTED: run started
     * - SKIPPED: not eligible
     * - SUCCEEDED: publish succeeded
     * - FAILED: publish failed / unexpected error
     */
    @Column(nullable = false, length = 32)
    private String status;

    /**
     * Action:
     * - SKIP
     * - PUBLISH
     */
    @Column(nullable = false, length = 16)
    private String action;

    // Linked TEMU publish run id (temu_publish_runs.id)
    private Long publishRunId;

    @Column(columnDefinition = "TEXT")
    private String eligibilityJson;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @PrePersist
    public void prePersist() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (status == null) status = "STARTED";
        if (action == null) action = "SKIP";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSpuId() { return spuId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getPublishRunId() { return publishRunId; }
    public void setPublishRunId(Long publishRunId) { this.publishRunId = publishRunId; }
    public String getEligibilityJson() { return eligibilityJson; }
    public void setEligibilityJson(String eligibilityJson) { this.eligibilityJson = eligibilityJson; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
