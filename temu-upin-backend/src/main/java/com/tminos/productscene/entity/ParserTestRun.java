package com.tminos.productscene.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "parser_test_runs",
        indexes = {
                @Index(name = "idx_parser_test_runs_run_id", columnList = "run_id", unique = true),
                @Index(name = "idx_parser_test_runs_parser_type", columnList = "parser_type"),
                @Index(name = "idx_parser_test_runs_created_at", columnList = "created_at")
        }
)
public class ParserTestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 64, unique = true)
    private String runId;

    @Column(name = "parser_type", nullable = false, length = 16)
    private String parserType;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "input_html", columnDefinition = "TEXT", nullable = false)
    private String inputHtml;

    @Column(name = "parse_result_json", columnDefinition = "TEXT")
    private String parseResultJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "STARTED";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getParserType() { return parserType; }
    public void setParserType(String parserType) { this.parserType = parserType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInputHtml() { return inputHtml; }
    public void setInputHtml(String inputHtml) { this.inputHtml = inputHtml; }
    public String getParseResultJson() { return parseResultJson; }
    public void setParseResultJson(String parseResultJson) { this.parseResultJson = parseResultJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
