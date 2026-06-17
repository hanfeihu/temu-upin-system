package com.tminos.productscene.dto;

import lombok.*;

import java.time.LocalDateTime;

public class TemuForbiddenWordRuleDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long id;
        private String word;
        private String replacement;
        private String fieldScope;
        private Boolean enabled;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveRequest {
        private String word;
        private String replacement;
        private String fieldScope;
        private Boolean enabled;
        private String remark;
    }
}
