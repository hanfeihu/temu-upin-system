package com.tminos.productscene.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class Alibaba1688DetailTaskDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListItem {
        private Long id;
        private Long credentialId;
        private String credentialName;
        private String claimToken;
        private String offerId;
        private String detailUrl;
        private String sourceType;
        private String status;
        private Integer attemptCount;
        private String workerName;
        private String lastError;
        private Long detailRecordId;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateDialogRequest {
        private Long credentialId;
        private String rawInput;
        private Boolean forceRefresh;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateDialogResponse {
        private Integer parsedUrlCount;
        private Integer createdCount;
        private Integer skippedExistingRecordCount;
        private Integer skippedActiveTaskCount;
        private Integer invalidCount;
        private List<ListItem> createdItems;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportFromCardLinksRequest {
        private Long credentialId;
        private List<Long> cardLinkIds;
        private Boolean allMatching;
        private String keyword;
        private String type;
        private Integer status;
        private Boolean forceRefresh;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportFromCardLinksResponse {
        private Integer selectedCount;
        private Integer availableCount;
        private Integer createdCount;
        private Integer skippedExistingRecordCount;
        private Integer skippedActiveTaskCount;
        private Integer skippedUnavailableCount;
        private Integer processedCount;
        private List<ListItem> createdItems;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerClaimRequest {
        private Long credentialId;
        private String workerName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerClaimResponse {
        private Boolean claimed;
        private ListItem task;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerSuccessRequest {
        private String claimToken;
        private String finalUrl;
        private String pageTitle;
        private String html;
        private String extractedJson;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerFailureRequest {
        private String claimToken;
        private String errorMessage;
        private String currentUrl;
        private Boolean authExpired;
    }
}
