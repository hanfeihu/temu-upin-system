package com.tminos.productscene.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class Alibaba1688CardLinkDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImportRequest {
        private List<ImportItem> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImportItem {
        private String type;
        private String offerId;
        private String detailUrl;
        private String cardHref;
        private String renderKey;
        private String index;
        private String offerIdSource;
        private String cardClass;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportResult {
        private int receivedCount;
        private int validCount;
        private int insertedCount;
        private int updatedCount;
        private int skippedMissingOfferIdCount;
        private int duplicateInPayloadCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListItem {
        private Long id;
        private String type;
        private String offerId;
        private String detailUrl;
        private String cardHref;
        private String renderKey;
        private String cardIndex;
        private String offerIdSource;
        private String cardClass;
        private Integer status;
        private String rawPayload;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateStatusRequest {
        private Integer status;
    }
}
