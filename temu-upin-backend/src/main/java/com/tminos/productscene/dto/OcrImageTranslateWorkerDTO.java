package com.tminos.productscene.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class OcrImageTranslateWorkerDTO {

    @Getter
    @Setter
    public static class ConfigView {
        private Long id;
        private String configName;
        private Boolean enabled;
        private Integer maxChineseImageCount;
        private Integer batchSize;
        private Long pollMs;
        private String provider;
        private String model;
        private String quality;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateConfigRequest {
        private String configName;
        private Integer maxChineseImageCount;
        private Integer batchSize;
        private Long pollMs;
        private String provider;
        private String model;
        private String quality;
        private String remark;
    }

    @Getter
    @Setter
    public static class StatusView {
        private Boolean running;
        private Boolean enabled;
        private Integer maxChineseImageCount;
        private Integer batchSize;
        private Long pollMs;
        private String provider;
        private String model;
        private Long pendingProductCount;
        private Long successCount;
        private Long failureCount;
        private Long skippedCount;
        private Long lastSpuId;
        private Long lastOcrTaskId;
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
        private Long spuId;
        private String productId;
        private Long ocrTaskId;
        private Integer imageType;
        private String sourceField;
        private Integer sourceIndex;
        private String status;
        private String model;
        private String originalUrl;
        private String translatedUrl;
        private String temuUrl;
        private String message;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMs;
        private LocalDateTime createdAt;
    }
}
