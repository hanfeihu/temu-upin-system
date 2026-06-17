package com.tminos.productscene.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

public class Alibaba1688SelectionAutoPushDTO {

    @Getter
    @Setter
    public static class ConfigView {
        private Long id;
        private String configName;
        private Boolean enabled;
        private List<String> targetShopIds;
        private List<String> targetShopNames;
        private Integer batchSize;
        private Long pollMs;
        private Boolean forceCreate;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateConfigRequest {
        private String configName;
        private List<String> targetShopIds;
        private Integer batchSize;
        private Long pollMs;
        private Boolean forceCreate;
        private String remark;
    }

    @Getter
    @Setter
    public static class StatusView {
        private Boolean running;
        private Boolean enabled;
        private Integer batchSize;
        private Long pollMs;
        private Integer pendingCount;
        private Long successCount;
        private Long failureCount;
        private Long skippedCount;
        private Long lastPoolId;
        private String lastOfferId;
        private LocalDateTime startedAt;
        private LocalDateTime stoppedAt;
        private LocalDateTime lastScanAt;
        private LocalDateTime lastWorkAt;
        private LocalDateTime lastErrorAt;
        private String lastError;
    }

    @Getter
    @Setter
    public static class LogView {
        private Long id;
        private Long poolId;
        private String offerId;
        private Long productCollectionId;
        private String status;
        private List<String> targetShopIds;
        private List<String> targetShopNames;
        private String message;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMs;
        private LocalDateTime createdAt;
    }
}
