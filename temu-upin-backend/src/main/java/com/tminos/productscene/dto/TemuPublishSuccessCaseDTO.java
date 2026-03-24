package com.tminos.productscene.dto;

import com.tminos.productscene.entity.TemuPublishSuccessCase;

import java.time.LocalDateTime;

public class TemuPublishSuccessCaseDTO {

    public record Row(
            Long id,
            Long publishRunId,
            Long spuId,
            String productId,
            String productName,
            String temuCatid,
            String temuCatname,
            String goodsId,
            LocalDateTime publishedAt,
            LocalDateTime createdAt
    ) {
        public static Row from(TemuPublishSuccessCase entity) {
            return new Row(
                    entity.getId(),
                    entity.getPublishRunId(),
                    entity.getSpuId(),
                    entity.getProductId(),
                    entity.getProductName(),
                    entity.getTemuCatid(),
                    entity.getTemuCatname(),
                    entity.getGoodsId(),
                    entity.getPublishedAt(),
                    entity.getCreatedAt()
            );
        }
    }

    public record Detail(
            Long id,
            Long publishRunId,
            Long spuId,
            String productId,
            String productName,
            String temuCatid,
            String temuCatname,
            String goodsId,
            String requestJson,
            String responseRaw,
            LocalDateTime publishedAt,
            LocalDateTime createdAt
    ) {
        public static Detail from(TemuPublishSuccessCase entity) {
            return new Detail(
                    entity.getId(),
                    entity.getPublishRunId(),
                    entity.getSpuId(),
                    entity.getProductId(),
                    entity.getProductName(),
                    entity.getTemuCatid(),
                    entity.getTemuCatname(),
                    entity.getGoodsId(),
                    entity.getRequestJson(),
                    entity.getResponseRaw(),
                    entity.getPublishedAt(),
                    entity.getCreatedAt()
            );
        }
    }
}