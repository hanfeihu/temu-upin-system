package com.tminos.productscene.dto;

import lombok.*;

import java.time.LocalDateTime;

public class Alibaba1688SelectionPoolFilterCategoryDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long id;
        private String categoryName;
        private Boolean enabled;
        private String source;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveRequest {
        private String categoryName;
        private Boolean enabled;
        private String source;
        private String remark;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ToggleEnabledRequest {
        private Boolean enabled;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRemarkRequest {
        private String remark;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuickAddRequest {
        private String categoryName;
        private Boolean enabled;
        private String source;
        private String remark;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuickAddResponse {
        private Boolean created;
        private Boolean reEnabled;
        private Item item;
    }
}
