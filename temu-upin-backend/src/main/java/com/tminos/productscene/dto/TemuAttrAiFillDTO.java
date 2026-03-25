package com.tminos.productscene.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

public class TemuAttrAiFillDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaskRow {
        private Long id;
        private String taskId;
        private Long spuId;
        private String productName;
        private String productMainImage;
        private Integer status;
        private Long leafCatId;
        private String resultSummary;
        private String errorMsg;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;

        public static TaskRowBuilder builder() { return new TaskRowBuilder(); }
        public static class TaskRowBuilder {
            private final TaskRow o = new TaskRow();
            public TaskRowBuilder id(Long v) { o.id = v; return this; }
            public TaskRowBuilder taskId(String v) { o.taskId = v; return this; }
            public TaskRowBuilder spuId(Long v) { o.spuId = v; return this; }
            public TaskRowBuilder productName(String v) { o.productName = v; return this; }
            public TaskRowBuilder productMainImage(String v) { o.productMainImage = v; return this; }
            public TaskRowBuilder status(Integer v) { o.status = v; return this; }
            public TaskRowBuilder leafCatId(Long v) { o.leafCatId = v; return this; }
            public TaskRowBuilder resultSummary(String v) { o.resultSummary = v; return this; }
            public TaskRowBuilder errorMsg(String v) { o.errorMsg = v; return this; }
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
        private String taskId;
        private Long spuId;
        private String productName;
        private String productMainImage;
        private Integer status;
        private Long leafCatId;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private String templateRaw;
        private String promptText;
        private String responseRaw;
        private String parsedJson;
        private String resultJson;
        private String ruleActions;
        private String resultSummary;
        private String errorMsg;

        public static TaskDetailBuilder builder() { return new TaskDetailBuilder(); }
        public static class TaskDetailBuilder {
            private final TaskDetail o = new TaskDetail();
            public TaskDetailBuilder id(Long v) { o.id = v; return this; }
            public TaskDetailBuilder taskId(String v) { o.taskId = v; return this; }
            public TaskDetailBuilder spuId(Long v) { o.spuId = v; return this; }
            public TaskDetailBuilder productName(String v) { o.productName = v; return this; }
            public TaskDetailBuilder productMainImage(String v) { o.productMainImage = v; return this; }
            public TaskDetailBuilder status(Integer v) { o.status = v; return this; }
            public TaskDetailBuilder leafCatId(Long v) { o.leafCatId = v; return this; }
            public TaskDetailBuilder startedAt(LocalDateTime v) { o.startedAt = v; return this; }
            public TaskDetailBuilder finishedAt(LocalDateTime v) { o.finishedAt = v; return this; }
            public TaskDetailBuilder createdAt(LocalDateTime v) { o.createdAt = v; return this; }
            public TaskDetailBuilder updatedAt(LocalDateTime v) { o.updatedAt = v; return this; }
            public TaskDetailBuilder templateRaw(String v) { o.templateRaw = v; return this; }
            public TaskDetailBuilder promptText(String v) { o.promptText = v; return this; }
            public TaskDetailBuilder responseRaw(String v) { o.responseRaw = v; return this; }
            public TaskDetailBuilder parsedJson(String v) { o.parsedJson = v; return this; }
            public TaskDetailBuilder resultJson(String v) { o.resultJson = v; return this; }
            public TaskDetailBuilder ruleActions(String v) { o.ruleActions = v; return this; }
            public TaskDetailBuilder resultSummary(String v) { o.resultSummary = v; return this; }
            public TaskDetailBuilder errorMsg(String v) { o.errorMsg = v; return this; }
            public TaskDetail build() { return o; }
        }
    }

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
}
