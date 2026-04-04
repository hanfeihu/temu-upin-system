package com.tminos.productscene.sync.dto;

import lombok.Data;

import java.time.LocalDateTime;

public class SyncLogDTO {

    @Data
    public static class LogItem {
        private Long id;
        private String shopId;
        private String syncType;
        private String status;
        private Integer totalCount;
        private Integer successCount;
        private Integer failCount;
        private String errorMsg;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
    }

    @Data
    public static class TriggerRequest {
        private String shopId;
        private String syncType;
    }
}
