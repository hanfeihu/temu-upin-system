package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "temu_main_sale_spec_inference_tasks",
        indexes = {
                @Index(name = "idx_temu_ms_infer_status", columnList = "status"),
                @Index(name = "idx_temu_ms_infer_spu_id", columnList = "spu_id"),
                @Index(name = "idx_temu_ms_infer_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemuMainSaleSpecInferenceTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inference_id", nullable = false, length = 64)
    private String inferenceId;

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

    @Column(name = "prompt_text", columnDefinition = "text")
    private String promptText;

    @Column(name = "prompt_parts_json", columnDefinition = "text")
    private String promptPartsJson;

    @Column(name = "parent_spec_list_raw", columnDefinition = "text")
    private String parentSpecListRaw;

    @Column(name = "response_raw", columnDefinition = "text")
    private String responseRaw;

    @Column(name = "response_content", columnDefinition = "text")
    private String responseContent;

    @Column(name = "parsed_json", columnDefinition = "text")
    private String parsedJson;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "main_product_sku_spec_reqs", columnDefinition = "text")
    private String mainProductSkuSpecReqs;

    @Column(name = "product_spec_property_reqs", columnDefinition = "text")
    private String productSpecPropertyReqs;

    @Column(name = "product_sku_reqs", columnDefinition = "text")
    private String productSkuReqs;

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
    public String getInferenceId() { return inferenceId; }
    public Long getSpuId() { return spuId; }
    public String getProductName() { return productName; }
    public String getProductMainImage() { return productMainImage; }
    public Integer getStatus() { return status; }
    public Long getLeafCatId() { return leafCatId; }
    public String getPromptText() { return promptText; }
    public String getPromptPartsJson() { return promptPartsJson; }
    public String getParentSpecListRaw() { return parentSpecListRaw; }
    public String getResponseRaw() { return responseRaw; }
    public String getResponseContent() { return responseContent; }
    public String getParsedJson() { return parsedJson; }
    public String getResultJson() { return resultJson; }
    public String getMainProductSkuSpecReqs() { return mainProductSkuSpecReqs; }
    public String getProductSpecPropertyReqs() { return productSpecPropertyReqs; }
    public String getProductSkuReqs() { return productSkuReqs; }
    public String getResultSummary() { return resultSummary; }
    public String getErrorMsg() { return errorMsg; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setInferenceId(String inferenceId) { this.inferenceId = inferenceId; }
    public void setSpuId(Long spuId) { this.spuId = spuId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setProductMainImage(String productMainImage) { this.productMainImage = productMainImage; }
    public void setStatus(Integer status) { this.status = status; }
    public void setLeafCatId(Long leafCatId) { this.leafCatId = leafCatId; }
    public void setPromptText(String promptText) { this.promptText = promptText; }
    public void setPromptPartsJson(String promptPartsJson) { this.promptPartsJson = promptPartsJson; }
    public void setParentSpecListRaw(String parentSpecListRaw) { this.parentSpecListRaw = parentSpecListRaw; }
    public void setResponseRaw(String responseRaw) { this.responseRaw = responseRaw; }
    public void setResponseContent(String responseContent) { this.responseContent = responseContent; }
    public void setParsedJson(String parsedJson) { this.parsedJson = parsedJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public void setMainProductSkuSpecReqs(String mainProductSkuSpecReqs) { this.mainProductSkuSpecReqs = mainProductSkuSpecReqs; }
    public void setProductSpecPropertyReqs(String productSpecPropertyReqs) { this.productSpecPropertyReqs = productSpecPropertyReqs; }
    public void setProductSkuReqs(String productSkuReqs) { this.productSkuReqs = productSkuReqs; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
