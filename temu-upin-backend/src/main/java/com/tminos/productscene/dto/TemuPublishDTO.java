package com.tminos.productscene.dto;

import java.util.List;
import java.util.Map;

public class TemuPublishDTO {

    public static class PublishResponse {
        private Boolean success;
        private String message;
        private Long runId;
        private String goodsId;
        private String raw;
        private Map<String, Object> normalized;
        private List<String> warnings;
        private Long mainSaleSpecTaskId;
        private Integer mainSaleSpecTaskStatus;
        private Boolean blockedByMainSaleSpec;

        public PublishResponse() {}

        public PublishResponse(Boolean success, String message, Long runId, String goodsId, String raw, Map<String, Object> normalized, List<String> warnings) {
            this.success = success;
            this.message = message;
            this.runId = runId;
            this.goodsId = goodsId;
            this.raw = raw;
            this.normalized = normalized;
            this.warnings = warnings;
        }

        public PublishResponse(Boolean success,
                               String message,
                               Long runId,
                               String goodsId,
                               String raw,
                               Map<String, Object> normalized,
                               List<String> warnings,
                               Long mainSaleSpecTaskId,
                               Integer mainSaleSpecTaskStatus,
                               Boolean blockedByMainSaleSpec) {
            this(success, message, runId, goodsId, raw, normalized, warnings);
            this.mainSaleSpecTaskId = mainSaleSpecTaskId;
            this.mainSaleSpecTaskStatus = mainSaleSpecTaskStatus;
            this.blockedByMainSaleSpec = blockedByMainSaleSpec;
        }

        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Long getRunId() { return runId; }
        public void setRunId(Long runId) { this.runId = runId; }
        public String getGoodsId() { return goodsId; }
        public void setGoodsId(String goodsId) { this.goodsId = goodsId; }
        public String getRaw() { return raw; }
        public void setRaw(String raw) { this.raw = raw; }
        public Map<String, Object> getNormalized() { return normalized; }
        public void setNormalized(Map<String, Object> normalized) { this.normalized = normalized; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        public Long getMainSaleSpecTaskId() { return mainSaleSpecTaskId; }
        public void setMainSaleSpecTaskId(Long mainSaleSpecTaskId) { this.mainSaleSpecTaskId = mainSaleSpecTaskId; }
        public Integer getMainSaleSpecTaskStatus() { return mainSaleSpecTaskStatus; }
        public void setMainSaleSpecTaskStatus(Integer mainSaleSpecTaskStatus) { this.mainSaleSpecTaskStatus = mainSaleSpecTaskStatus; }
        public Boolean getBlockedByMainSaleSpec() { return blockedByMainSaleSpec; }
        public void setBlockedByMainSaleSpec(Boolean blockedByMainSaleSpec) { this.blockedByMainSaleSpec = blockedByMainSaleSpec; }
    }
}
