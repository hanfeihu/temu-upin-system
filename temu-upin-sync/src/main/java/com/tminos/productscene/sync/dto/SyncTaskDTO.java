package com.tminos.productscene.sync.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class SyncTaskDTO {

    @Data
    public static class CreateRequest {
        private String shopId;
        private List<String> syncTypes;
        private String goodsSyncMode;
        private String priceAdjustSyncMode;
    }

    @Data
    public static class TaskItem {
        private Long id;
        private String shopId;
        private String syncType;
        private String syncScope;
        private String triggerType;
        private String status;
        private String currentPhase;
        private Integer downloadTotal;
        private Integer downloadCompleted;
        private Integer downloadFailed;
        private Integer persistTotal;
        private Integer persistCompleted;
        private Integer persistFailed;
        private Integer totalBatches;
        private Integer persistedBatches;
        private Integer failedBatchIndex;
        private String lastErrorMsg;
        private Integer retryCount;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
        private List<StepLogItem> logs;
    }

    @Data
    public static class ProgressResponse {
        private Long taskId;
        private String status;
        private String currentPhase;
        private Integer downloadTotal;
        private Integer downloadCompleted;
        private Integer persistTotal;
        private Integer persistCompleted;
        private Integer totalBatches;
        private Integer persistedBatches;
        private List<StepLogItem> latestLogs;
    }

    @Data
    public static class StepLogItem {
        private String phase;
        private String level;
        private String message;
        private LocalDateTime createdAt;
    }

    @Data
    public static class RetryRequest {
        private String mode; // CONTINUE or FULL
    }
}
