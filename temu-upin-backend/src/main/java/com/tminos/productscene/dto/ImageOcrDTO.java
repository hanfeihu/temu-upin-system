package com.tminos.productscene.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

public class ImageOcrDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OcrTaskResponse {
        private Long id;
        private Long spuId;
        private String productId;
        private Integer imageType;
        private String imageUrl;
        private Integer execStatus;
        private String execResult;
        private String failReason;
        private String executorPublicIp;
        private LocalDateTime taskStartedAt;
        private LocalDateTime taskFinishedAt;
        private Boolean filtered;
        private Boolean containsChinese;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpsertTaskRequest {
        @NotNull
        private Long spuId;
        private String productId;
        @NotNull
        private Integer imageType;
        @NotBlank
        private String imageUrl;
        private Integer execStatus;
        private String execResult;
        private String failReason;
        private String executorPublicIp;
        private Boolean filtered;
        private Boolean containsChinese;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimTaskRequest {
        // optional
        private String publicIp;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteTaskRequest {
        @NotNull
        private Long taskId;
        // success content (when success)
        private String ocrText;
        // failure reason (when failed)
        private String failReason;
    }
}
