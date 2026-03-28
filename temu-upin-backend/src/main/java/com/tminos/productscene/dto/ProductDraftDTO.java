package com.tminos.productscene.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

public class ProductDraftDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportRequest {
        @NotBlank(message = "HTML content is required")
        private String html;
        private String extractedJson;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String productName;
        private String productCategory;
        private String originalCategory;
        private String productMainImage;
        private String productUrl;
        private String monthlySales;
        private Integer reviewCount;
        private String companyName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListItem {
        private Long id;
        private String sourcePlatform;
        private String productId;
        private String productName;
        private String productCategory;
        private String originalCategory;
        private String productMainImage;
        private String productUrl;
        private String monthlySales;
        private Integer reviewCount;
        private String companyName;
        private Boolean pushedToCollection;
        private Long pushedCollectionId;
        private LocalDateTime pushedAt;
        private String pushMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Detail {
        private Long id;
        private String sourcePlatform;
        private String productId;
        private String productName;
        private String productCategory;
        private String originalCategory;
        private String productMainImage;
        private String productUrl;
        private String monthlySales;
        private Integer reviewCount;
        private String companyName;
        private String originalHtml;
        private String extractedJson;
        private String parserSnapshotJson;
        private Boolean pushedToCollection;
        private Long pushedCollectionId;
        private LocalDateTime pushedAt;
        private String pushMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PushResponse {
        private Long draftId;
        private Long collectionId;
        private String productId;
        private String message;
    }
}
