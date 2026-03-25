package com.tminos.productscene.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ProductCollectionDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductCollectionResponse {
        private Long id;
        private String productId;
        private String productName;
        private String sourcePlatform;
        private Integer collectionStatus;
        private Integer execStatus;
        private String execResult;
        private Long lastPublishRunId;
        private Integer collectCount;
        private String companyName;
        private String productMainImage;
        private String temuCatid;
        private String temuCatname;
        private Boolean temuPublished;
        private String temuGoodsId;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;

        // OCR status
        private Integer ocrStatus;

        // Counts (derived for list view)
        private Integer carouselImageCount;
        private Integer detailImageCount;
        private Integer skuCount;

        // MOQ
        private Integer moq;
        private String moqText;

        // Weight (kg)
        private BigDecimal netWeight;
        private BigDecimal packagingWeight;
        private Boolean deleted;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductCollectionDetailResponse {
        private Long id;
        private LocalDateTime createdAt;
        private Long createdBy;
        private Boolean deleted;
        private LocalDateTime updatedAt;
        private Long updatedBy;
        private Integer version;

        private String annualSales;
        private String attributesData;
        private String carouselImages;
        private Integer collectCount;
        private Integer collectionStatus;
        private Integer execStatus;
        private String execResult;
        private Long lastPublishRunId;
        private LocalDateTime collectionTime;

        private String companyLocation;
        private String companyName;
        private String detailImages;
        private Boolean hasSevereInventory;

        private BigDecimal maxPrice;
        private BigDecimal minPrice;
        private String monthlyConsignment;
        private String monthlySales;
        private Integer moq;
        private BigDecimal netWeight;

        private String originalCategory;
        private String originalContent;
        private String packagingDimensions;
        private BigDecimal packagingHeight;
        private BigDecimal packagingLength;
        private BigDecimal packagingWeight;
        private BigDecimal packagingWidth;

        private String productCategory;
        private String productId;
        private String productMainImage;
        private String productName;
        private String productUrl;

        private BigDecimal ratingScore;
        private BigDecimal repeatCustomerRate;
        private Integer reviewCount;
        private BigDecimal serviceScore;

        private String shippingLocation;
        private String skuData;
        private String skuModel;
        private String sourcePlatform;
        private String temuCatid;
        private String temuCatname;

        private String carouselThumbImages;
        private String carouselVideo;
        private BigDecimal baseFreight;
        private String customMadeSpecs;
        private String shippingServicesInfo;
        private String moqText;
        private String priceSteps;
        private String alibabaProductId;
        private String originalHtml;
        private String temuAttributes;
        private java.util.List<com.tminos.productscene.dto.TemuSkuDTO.TemuSkuRow> temuSkus;
        private List<ProductCollectionSkuPropResponse> skuPropsExt;
        private List<ProductCollectionSkuResponse> skuRows;

        public static ProductCollectionDetailResponseBuilder builder() {
            return new ProductCollectionDetailResponseBuilder();
        }

        public static class ProductCollectionDetailResponseBuilder {
            private final ProductCollectionDetailResponse o = new ProductCollectionDetailResponse();

            public ProductCollectionDetailResponseBuilder id(Long v) { o.id = v; return this; }
            public ProductCollectionDetailResponseBuilder createdAt(LocalDateTime v) { o.createdAt = v; return this; }
            public ProductCollectionDetailResponseBuilder createdBy(Long v) { o.createdBy = v; return this; }
            public ProductCollectionDetailResponseBuilder deleted(Boolean v) { o.deleted = v; return this; }
            public ProductCollectionDetailResponseBuilder updatedAt(LocalDateTime v) { o.updatedAt = v; return this; }
            public ProductCollectionDetailResponseBuilder updatedBy(Long v) { o.updatedBy = v; return this; }
            public ProductCollectionDetailResponseBuilder version(Integer v) { o.version = v; return this; }
            public ProductCollectionDetailResponseBuilder annualSales(String v) { o.annualSales = v; return this; }
            public ProductCollectionDetailResponseBuilder attributesData(String v) { o.attributesData = v; return this; }
            public ProductCollectionDetailResponseBuilder carouselImages(String v) { o.carouselImages = v; return this; }
            public ProductCollectionDetailResponseBuilder collectCount(Integer v) { o.collectCount = v; return this; }
            public ProductCollectionDetailResponseBuilder collectionStatus(Integer v) { o.collectionStatus = v; return this; }
            public ProductCollectionDetailResponseBuilder collectionTime(LocalDateTime v) { o.collectionTime = v; return this; }
            public ProductCollectionDetailResponseBuilder companyLocation(String v) { o.companyLocation = v; return this; }
            public ProductCollectionDetailResponseBuilder companyName(String v) { o.companyName = v; return this; }
            public ProductCollectionDetailResponseBuilder detailImages(String v) { o.detailImages = v; return this; }
            public ProductCollectionDetailResponseBuilder hasSevereInventory(Boolean v) { o.hasSevereInventory = v; return this; }
            public ProductCollectionDetailResponseBuilder maxPrice(BigDecimal v) { o.maxPrice = v; return this; }
            public ProductCollectionDetailResponseBuilder minPrice(BigDecimal v) { o.minPrice = v; return this; }
            public ProductCollectionDetailResponseBuilder monthlyConsignment(String v) { o.monthlyConsignment = v; return this; }
            public ProductCollectionDetailResponseBuilder monthlySales(String v) { o.monthlySales = v; return this; }
            public ProductCollectionDetailResponseBuilder moq(Integer v) { o.moq = v; return this; }
            public ProductCollectionDetailResponseBuilder netWeight(BigDecimal v) { o.netWeight = v; return this; }
            public ProductCollectionDetailResponseBuilder originalCategory(String v) { o.originalCategory = v; return this; }
            public ProductCollectionDetailResponseBuilder originalContent(String v) { o.originalContent = v; return this; }
            public ProductCollectionDetailResponseBuilder packagingDimensions(String v) { o.packagingDimensions = v; return this; }
            public ProductCollectionDetailResponseBuilder packagingHeight(BigDecimal v) { o.packagingHeight = v; return this; }
            public ProductCollectionDetailResponseBuilder packagingLength(BigDecimal v) { o.packagingLength = v; return this; }
            public ProductCollectionDetailResponseBuilder packagingWeight(BigDecimal v) { o.packagingWeight = v; return this; }
            public ProductCollectionDetailResponseBuilder packagingWidth(BigDecimal v) { o.packagingWidth = v; return this; }
            public ProductCollectionDetailResponseBuilder productCategory(String v) { o.productCategory = v; return this; }
            public ProductCollectionDetailResponseBuilder productId(String v) { o.productId = v; return this; }
            public ProductCollectionDetailResponseBuilder productMainImage(String v) { o.productMainImage = v; return this; }
            public ProductCollectionDetailResponseBuilder productName(String v) { o.productName = v; return this; }
            public ProductCollectionDetailResponseBuilder productUrl(String v) { o.productUrl = v; return this; }
            public ProductCollectionDetailResponseBuilder ratingScore(BigDecimal v) { o.ratingScore = v; return this; }
            public ProductCollectionDetailResponseBuilder repeatCustomerRate(BigDecimal v) { o.repeatCustomerRate = v; return this; }
            public ProductCollectionDetailResponseBuilder reviewCount(Integer v) { o.reviewCount = v; return this; }
            public ProductCollectionDetailResponseBuilder serviceScore(BigDecimal v) { o.serviceScore = v; return this; }
            public ProductCollectionDetailResponseBuilder shippingLocation(String v) { o.shippingLocation = v; return this; }
            public ProductCollectionDetailResponseBuilder skuData(String v) { o.skuData = v; return this; }
            public ProductCollectionDetailResponseBuilder skuModel(String v) { o.skuModel = v; return this; }
            public ProductCollectionDetailResponseBuilder sourcePlatform(String v) { o.sourcePlatform = v; return this; }
            public ProductCollectionDetailResponseBuilder temuCatid(String v) { o.temuCatid = v; return this; }
            public ProductCollectionDetailResponseBuilder temuCatname(String v) { o.temuCatname = v; return this; }
            public ProductCollectionDetailResponseBuilder carouselThumbImages(String v) { o.carouselThumbImages = v; return this; }
            public ProductCollectionDetailResponseBuilder carouselVideo(String v) { o.carouselVideo = v; return this; }
            public ProductCollectionDetailResponseBuilder baseFreight(BigDecimal v) { o.baseFreight = v; return this; }
            public ProductCollectionDetailResponseBuilder customMadeSpecs(String v) { o.customMadeSpecs = v; return this; }
            public ProductCollectionDetailResponseBuilder shippingServicesInfo(String v) { o.shippingServicesInfo = v; return this; }
            public ProductCollectionDetailResponseBuilder moqText(String v) { o.moqText = v; return this; }
            public ProductCollectionDetailResponseBuilder priceSteps(String v) { o.priceSteps = v; return this; }
            public ProductCollectionDetailResponseBuilder alibabaProductId(String v) { o.alibabaProductId = v; return this; }
            public ProductCollectionDetailResponseBuilder originalHtml(String v) { o.originalHtml = v; return this; }
            public ProductCollectionDetailResponseBuilder temuAttributes(String v) { o.temuAttributes = v; return this; }
            public ProductCollectionDetailResponseBuilder temuSkus(java.util.List<com.tminos.productscene.dto.TemuSkuDTO.TemuSkuRow> v) { o.temuSkus = v; return this; }
            public ProductCollectionDetailResponseBuilder skuPropsExt(List<ProductCollectionSkuPropResponse> v) { o.skuPropsExt = v; return this; }
            public ProductCollectionDetailResponseBuilder skuRows(List<ProductCollectionSkuResponse> v) { o.skuRows = v; return this; }

            public ProductCollectionDetailResponse build() { return o; }
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductCollectionSkuPropResponse {
        private Long id;
        private Integer fid;
        private String name;
        private Integer sort;
        private List<ProductCollectionSkuPropValueResponse> values;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductCollectionSkuPropValueResponse {
        private Long id;
        private String value;
        private String image;
        private Integer sort;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductCollectionSkuResponse {
        private Long id;
        private String skuId;
        private String specKey;
        private String specJson;
        private Integer stock;
        private BigDecimal price;
        private String image;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateProductCollectionRequest {
        // Title
        private String productName;

        private Integer collectionStatus;
        private Integer collectCount;
        private Boolean deleted;

        private String companyName;
        private String companyLocation;
        private String shippingLocation;

        private String productCategory;
        private String originalCategory;
        private String temuCatid;
        private String temuCatname;

        private String productMainImage;
        private String productUrl;
        private String carouselVideo;

        private String annualSales;
        private String monthlySales;
        private String monthlyConsignment;
        private Integer moq;
        private String moqText;

        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private BigDecimal baseFreight;

        private BigDecimal netWeight;
        private BigDecimal packagingWeight;
        private BigDecimal packagingLength;
        private BigDecimal packagingWidth;
        private BigDecimal packagingHeight;

        private Boolean hasSevereInventory;

        private String attributesData;
        private String skuData;
        private String skuModel;
        private String carouselImages;
        private String carouselThumbImages;
        private String detailImages;
        private String customMadeSpecs;
        private String shippingServicesInfo;
        private String priceSteps;
        private String originalContent;
        private String originalHtml;

        private String temuAttributes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SplitProductRequest {
        private List<SplitGroupRequest> groups;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SplitGroupRequest {
        private String name;
        private List<Long> skuRowIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SplitProductResponse {
        private Long sourceSpuId;
        private String sourceProductName;
        private Integer splitCount;
        private List<SplitCreatedProductResponse> products;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SplitCreatedProductResponse {
        private Long id;
        private String productId;
        private String productName;
        private Integer skuCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TemuPublishPayloadResponse {
        private Long spuId;
        private String temuCatid;
        private String sourceProductName;
        private String translatedProductName;
        private List<String> carouselImages;
        private List<String> detailImages;
        private Object temuAttributes;
        private List<com.tminos.productscene.dto.TemuSkuDTO.TemuSkuRow> temuSkus;
    }
}
