package com.tminos.productscene.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TemuSitePublishExceptionDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private Long id;
        private String skc;
        private String offerId;
        private Long productCollectionId;
        private String temuSpuId;
        private String temuGoodsId;
        private String goodsNo;
        private String siteName;
        private String statusText;
        private String productTitle;
        private String categoryText;
        private BigDecimal declaredPrice;
        private String reasonText;
        private String reasonType;
        private String operatorName;
        private String createdTimeText;
        private String priceConfirmTimeText;
        private String joinedSiteTimeText;
        private String sourceUrl;
        private String sourceTitle;
        private LocalDateTime sourceCollectedAt;
        private String sourceFileName;
        private String rowText;
        private String detailText;
        private Boolean active;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportResponse {
        private int totalRecords;
        private int imported;
        private int created;
        private int updated;
        private int skipped;
        private List<String> skippedReasons;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportPathRequest {
        private String filePath;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private Boolean active;
        private String reasonType;
        private String remark;
    }
}
