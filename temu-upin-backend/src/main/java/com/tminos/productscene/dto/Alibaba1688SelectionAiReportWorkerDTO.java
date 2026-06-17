package com.tminos.productscene.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

public class Alibaba1688SelectionAiReportWorkerDTO {

    @Getter
    @Setter
    public static class ConfigView {
        private Long id;
        private String configName;
        private Integer threadCount;
        private Boolean enabled;
        private Long pollMs;
        private String remark;
        private String createdAt;
        private String updatedAt;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateConfigRequest {
        private String configName;
        @JsonAlias({"configuredThreads"})
        private Integer threadCount;
        private String remark;
    }

    @Getter
    @Setter
    public static class ThreadSnapshot {
        private Integer workerIndex;
        private String threadName;
        private Boolean alive;
        private Boolean working;
        private Boolean stuck;
        private Long currentPoolId;
        private String currentOfferId;
        private LocalDateTime lastHeartbeatAt;
        private LocalDateTime currentTaskStartedAt;
        private LocalDateTime lastFinishedAt;
        private Long successCount;
        private Long failureCount;
        private String lastError;
    }

    @Getter
    @Setter
    public static class TaskEvent {
        private Long sequence;
        private Integer workerIndex;
        private String threadName;
        private Long poolId;
        private String offerId;
        private String status;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMs;
        private String error;
    }

    @Getter
    @Setter
    public static class StatusView {
        private Boolean running;
        private Integer configuredThreadCount;
        private Long pollMs;
        private Integer pendingCount;
        private Integer runningCount;
        private Integer readyCount;
        private Integer failedCount;
        private Integer activeThreadCount;
        private Integer aliveThreadCount;
        private Boolean hasStuckThreads;
        private LocalDateTime startedAt;
        private LocalDateTime stoppedAt;
        private LocalDateTime lastScanAt;
        private LocalDateTime lastWorkAt;
        private LocalDateTime lastErrorAt;
        private String lastError;
        private List<ThreadSnapshot> threads;
        private List<TaskEvent> recentTaskEvents;
    }
}
