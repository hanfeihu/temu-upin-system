package com.tminos.productscene.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class Alibaba1688AuthSessionDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListItem {
        private Long id;
        private String sessionName;
        private String accountNick;
        private String memberId;
        private String homeUrl;
        private String remark;
        private Boolean enabled;
        private String status;
        private String lastError;
        private Boolean hasStorageState;
        private LocalDateTime storageStateUpdatedAt;
        private LocalDateTime lastVerifiedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionItem {
        private Long id;
        private String sessionName;
        private String accountNick;
        private String memberId;
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveRequest {
        private String sessionName;
        private String remark;
        private Boolean enabled;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerSaveRequest {
        private Long id;
        private String sessionName;
        private String accountNick;
        private String memberId;
        private String homeUrl;
        private String remark;
        private String storageStateJson;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerStatusRequest {
        private String status;
        private String lastError;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StorageStateResponse {
        private Long id;
        private String sessionName;
        private String storageStateJson;
        private String homeUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerSaveResponse {
        private Long id;
        private String sessionName;
        private String status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionResponse {
        private List<OptionItem> items;
    }
}
