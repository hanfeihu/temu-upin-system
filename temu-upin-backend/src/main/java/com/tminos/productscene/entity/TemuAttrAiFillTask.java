package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_attr_ai_fill_tasks",
        indexes = {
                @Index(name = "idx_temu_attr_ai_fill_status", columnList = "status"),
                @Index(name = "idx_temu_attr_ai_fill_spu_id", columnList = "spu_id"),
                @Index(name = "idx_temu_attr_ai_fill_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuAttrAiFillTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "spu_id", nullable = false)
    private Long spuId;

    @Column(name = "product_name", length = 512)
    private String productName;

    @Column(name = "product_main_image", length = 2000)
    private String productMainImage;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "leaf_cat_id")
    private Long leafCatId;

    @Column(name = "template_raw", columnDefinition = "text")
    private String templateRaw;

    @Column(name = "prompt_text", columnDefinition = "text")
    private String promptText;

    @Column(name = "response_raw", columnDefinition = "text")
    private String responseRaw;

    @Column(name = "parsed_json", columnDefinition = "text")
    private String parsedJson;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "rule_actions", columnDefinition = "text")
    private String ruleActions;

    @Column(name = "result_summary", length = 2000)
    private String resultSummary;

    @Column(name = "error_msg", length = 2000)
    private String errorMsg;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTaskId() { return taskId; }
    public Long getSpuId() { return spuId; }
    public String getProductName() { return productName; }
    public String getProductMainImage() { return productMainImage; }
    public Integer getStatus() { return status; }
    public Long getLeafCatId() { return leafCatId; }
    public String getTemplateRaw() { return templateRaw; }
    public String getPromptText() { return promptText; }
    public String getResponseRaw() { return responseRaw; }
    public String getParsedJson() { return parsedJson; }
    public String getResultJson() { return resultJson; }
    public String getRuleActions() { return ruleActions; }
    public String getResultSummary() { return resultSummary; }
    public String getErrorMsg() { return errorMsg; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setTaskId(String taskId) { this.taskId = taskId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setProductMainImage(String productMainImage) { this.productMainImage = productMainImage; }
    public void setStatus(Integer status) { this.status = status; }
    public void setLeafCatId(Long leafCatId) { this.leafCatId = leafCatId; }
    public void setTemplateRaw(String templateRaw) { this.templateRaw = templateRaw; }
    public void setPromptText(String promptText) { this.promptText = promptText; }
    public void setResponseRaw(String responseRaw) { this.responseRaw = responseRaw; }
    public void setParsedJson(String parsedJson) { this.parsedJson = parsedJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public void setRuleActions(String ruleActions) { this.ruleActions = ruleActions; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
