package com.tminos.productscene.dto;

import lombok.*;

import java.time.LocalDateTime;

public class TemuMainSaleSpecInferenceDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateTaskRequest {
        private Long spuId;

        public Long getSpuId() { return spuId; }
        public void setSpuId(Long spuId) { this.spuId = spuId; }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaskRow {
        private Long id;
        private String inferenceId;
        private Long spuId;
        private String productName;
        private String productMainImage;
        private Integer status;
        private String resultSummary;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;

        public static TaskRowBuilder builder() { return new TaskRowBuilder(); }
        public static class TaskRowBuilder {
            private final TaskRow o = new TaskRow();
            public TaskRowBuilder id(Long v) { o.id = v; return this; }
            public TaskRowBuilder inferenceId(String v) { o.inferenceId = v; return this; }
            public TaskRowBuilder spuId(Long v) { o.spuId = v; return this; }
            public TaskRowBuilder productName(String v) { o.productName = v; return this; }
            public TaskRowBuilder productMainImage(String v) { o.productMainImage = v; return this; }
            public TaskRowBuilder status(Integer v) { o.status = v; return this; }
            public TaskRowBuilder resultSummary(String v) { o.resultSummary = v; return this; }
            public TaskRowBuilder startedAt(LocalDateTime v) { o.startedAt = v; return this; }
            public TaskRowBuilder finishedAt(LocalDateTime v) { o.finishedAt = v; return this; }
            public TaskRowBuilder createdAt(LocalDateTime v) { o.createdAt = v; return this; }
            public TaskRow build() { return o; }
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaskDetail {
        private Long id;
        private String inferenceId;
        private Long spuId;
        private String productName;
        private String productMainImage;
        private Integer status;
        private Long leafCatId;
        private String resultSummary;

        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private String promptText;
        private String responseRaw;
        private String responseContent;
        private String parsedJson;
        private String resultJson;
        private String mainProductSkuSpecReqs;
        private String productSpecPropertyReqs;
        private String productSkuReqs;
        private String errorMsg;

        public static TaskDetailBuilder builder() { return new TaskDetailBuilder(); }
        public static class TaskDetailBuilder {
            private final TaskDetail o = new TaskDetail();
            public TaskDetailBuilder id(Long v) { o.id = v; return this; }
            public TaskDetailBuilder inferenceId(String v) { o.inferenceId = v; return this; }
            public TaskDetailBuilder spuId(Long v) { o.spuId = v; return this; }
            public TaskDetailBuilder productName(String v) { o.productName = v; return this; }
            public TaskDetailBuilder productMainImage(String v) { o.productMainImage = v; return this; }
            public TaskDetailBuilder status(Integer v) { o.status = v; return this; }
            public TaskDetailBuilder leafCatId(Long v) { o.leafCatId = v; return this; }
            public TaskDetailBuilder resultSummary(String v) { o.resultSummary = v; return this; }
            public TaskDetailBuilder startedAt(LocalDateTime v) { o.startedAt = v; return this; }
            public TaskDetailBuilder finishedAt(LocalDateTime v) { o.finishedAt = v; return this; }
            public TaskDetailBuilder createdAt(LocalDateTime v) { o.createdAt = v; return this; }
            public TaskDetailBuilder updatedAt(LocalDateTime v) { o.updatedAt = v; return this; }
            public TaskDetailBuilder promptText(String v) { o.promptText = v; return this; }
            public TaskDetailBuilder responseRaw(String v) { o.responseRaw = v; return this; }
            public TaskDetailBuilder responseContent(String v) { o.responseContent = v; return this; }
            public TaskDetailBuilder parsedJson(String v) { o.parsedJson = v; return this; }
            public TaskDetailBuilder resultJson(String v) { o.resultJson = v; return this; }
            public TaskDetailBuilder mainProductSkuSpecReqs(String v) { o.mainProductSkuSpecReqs = v; return this; }
            public TaskDetailBuilder productSpecPropertyReqs(String v) { o.productSpecPropertyReqs = v; return this; }
            public TaskDetailBuilder productSkuReqs(String v) { o.productSkuReqs = v; return this; }
            public TaskDetailBuilder errorMsg(String v) { o.errorMsg = v; return this; }
            public TaskDetail build() { return o; }
        }
    }
}
