package com.tminos.productscene.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_collection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Long createdBy;

    @Column(nullable = false)
    private Boolean deleted;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Long updatedBy;

    @Column(nullable = false)
    private Integer version;

    private String annualSales;

    @Column(columnDefinition = "TEXT")
    private String attributesData;

    @Column(columnDefinition = "TEXT")
    private String carouselImages;

    private Integer collectCount;
    private Integer collectionStatus;
    private LocalDateTime collectionTime;

    private String companyLocation;
    private String companyName;

    @Column(columnDefinition = "TEXT")
    private String targetShopIds;

    @Column(columnDefinition = "TEXT")
    private String targetShopNames;

    @Column(columnDefinition = "TEXT")
    private String detailImages;

    private Boolean hasSevereInventory;

    private BigDecimal maxPrice;
    private BigDecimal minPrice;

    private String monthlyConsignment;
    private String monthlySales;

    private Integer moq;
    private BigDecimal netWeight;

    private String originalCategory;

    @Column(columnDefinition = "TEXT")
    private String originalContent;

    private String packagingDimensions;
    private BigDecimal packagingHeight;
    private BigDecimal packagingLength;
    private BigDecimal packagingWeight;
    private BigDecimal packagingWidth;

    private String productCategory;

    @Column(nullable = false)
    private String productId;

    @Column(length = 2000)
    private String productMainImage;

    @Column(nullable = false)
    private String productName;

    @Column(length = 2000)
    private String productUrl;

    private BigDecimal ratingScore;
    private BigDecimal repeatCustomerRate;
    private Integer reviewCount;
    private BigDecimal serviceScore;

    private String shippingLocation;

    @Column(columnDefinition = "TEXT")
    private String skuData;

    @Column(columnDefinition = "TEXT")
    private String skuModel;

    public String getSkuModel() {
        return skuModel;
    }

    public void setSkuModel(String skuModel) {
        this.skuModel = skuModel;
    }

    private String sourcePlatform;

    @Column(columnDefinition = "TEXT")
    private String temuCatid;

    @Column(columnDefinition = "TEXT")
    private String temuCatname;

    @Column(columnDefinition = "TEXT")
    private String temuOptimizedTitleEn;

    @Column(columnDefinition = "TEXT")
    private String temuOptimizedTitleZh;

    @Column(columnDefinition = "TEXT")
    private String temuCategoryKeywords;

    @Column(columnDefinition = "TEXT")
    private String carouselThumbImages;

    @Column(length = 2000)
    private String carouselVideo;

    private BigDecimal baseFreight;

    @Column(columnDefinition = "TEXT")
    private String customMadeSpecs;

    @Column(columnDefinition = "TEXT")
    private String shippingServicesInfo;

    private String moqText;

    @Column(columnDefinition = "TEXT")
    private String priceSteps;

    private String alibabaProductId;

    @Column(columnDefinition = "TEXT")
    private String originalHtml;

    @Column(columnDefinition = "TEXT")
    private String temuAttributes;

    // Publish status
    private Boolean temuPublished;

    @Column(length = 128)
    private String temuGoodsId;

    private LocalDateTime temuPublishedAt;

    @Column(columnDefinition = "TEXT")
    private String temuPublishRaw;

    // Post-import automation status (AI attributes + sku convert + kwcdn link replace)
    // 0 pending, 1 running, 2 success, 3 failed
    private Integer execStatus;

    // OCR status for product images
    // 0 pending, 1 running, 2 success, 3 failed
    @Column(name = "ocr_status")
    private Integer ocrStatus;

    @Column(columnDefinition = "TEXT")
    private String execResult;

    // Publish run id for tooltip/debugging
    private Long lastPublishRunId;


    public Long getId() {
        return id;
    }

    public String getTemuAttributes() {
        return temuAttributes;
    }

    public void setTemuAttributes(String temuAttributes) {
        this.temuAttributes = temuAttributes;
    }

    public Boolean getTemuPublished() {
        return temuPublished;
    }

    public void setTemuPublished(Boolean temuPublished) {
        this.temuPublished = temuPublished;
    }

    public String getTemuGoodsId() {
        return temuGoodsId;
    }

    public void setTemuGoodsId(String temuGoodsId) {
        this.temuGoodsId = temuGoodsId;
    }

    public LocalDateTime getTemuPublishedAt() {
        return temuPublishedAt;
    }

    public void setTemuPublishedAt(LocalDateTime temuPublishedAt) {
        this.temuPublishedAt = temuPublishedAt;
    }

    public String getTemuPublishRaw() {
        return temuPublishRaw;
    }

    public void setTemuPublishRaw(String temuPublishRaw) {
        this.temuPublishRaw = temuPublishRaw;
    }

    public Integer getExecStatus() {
        return execStatus;
    }

    public void setExecStatus(Integer execStatus) {
        this.execStatus = execStatus;
    }

    public Integer getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(Integer ocrStatus) {
        this.ocrStatus = ocrStatus;
    }

    public String getExecResult() {
        return execResult;
    }

    public void setExecResult(String execResult) {
        this.execResult = execResult;
    }

    public Long getLastPublishRunId() {
        return lastPublishRunId;
    }

    public void setLastPublishRunId(Long lastPublishRunId) {
        this.lastPublishRunId = lastPublishRunId;
    }

    public String getProductId() {
        return productId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }


    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Integer getVersion() {
        return version;
    }

    public String getAnnualSales() {
        return annualSales;
    }

    public String getAttributesData() {
        return attributesData;
    }

    public String getCarouselImages() {
        return carouselImages;
    }

    public void setCarouselImages(String carouselImages) {
        this.carouselImages = carouselImages;
    }

    public Integer getCollectCount() {
        return collectCount;
    }

    public Integer getCollectionStatus() {
        return collectionStatus;
    }

    public void setCollectionStatus(Integer collectionStatus) {
        this.collectionStatus = collectionStatus;
    }

    public LocalDateTime getCollectionTime() {
        return collectionTime;
    }

    public String getCompanyLocation() {
        return companyLocation;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getDetailImages() {
        return detailImages;
    }

    public void setDetailImages(String detailImages) {
        this.detailImages = detailImages;
    }

    public Boolean getHasSevereInventory() {
        return hasSevereInventory;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public String getMonthlyConsignment() {
        return monthlyConsignment;
    }

    public String getMonthlySales() {
        return monthlySales;
    }

    public Integer getMoq() {
        return moq;
    }

    public BigDecimal getNetWeight() {
        return netWeight;
    }

    public String getOriginalCategory() {
        return originalCategory;
    }

    public String getOriginalContent() {
        return originalContent;
    }

    public String getPackagingDimensions() {
        return packagingDimensions;
    }

    public BigDecimal getPackagingHeight() {
        return packagingHeight;
    }

    public BigDecimal getPackagingLength() {
        return packagingLength;
    }

    public BigDecimal getPackagingWeight() {
        return packagingWeight;
    }

    public BigDecimal getPackagingWidth() {
        return packagingWidth;
    }

    public String getProductCategory() {
        return productCategory;
    }

    public String getProductMainImage() {
        return productMainImage;
    }

    public void setProductMainImage(String productMainImage) {
        this.productMainImage = productMainImage;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public BigDecimal getRatingScore() {
        return ratingScore;
    }

    public BigDecimal getRepeatCustomerRate() {
        return repeatCustomerRate;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public BigDecimal getServiceScore() {
        return serviceScore;
    }

    public String getShippingLocation() {
        return shippingLocation;
    }

    public String getSkuData() {
        return skuData;
    }

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public String getTemuCatid() {
        return temuCatid;
    }

    public String getTemuCatname() {
        return temuCatname;
    }

    public String getCarouselThumbImages() {
        return carouselThumbImages;
    }

    public String getCarouselVideo() {
        return carouselVideo;
    }

    public BigDecimal getBaseFreight() {
        return baseFreight;
    }

    public String getCustomMadeSpecs() {
        return customMadeSpecs;
    }

    public String getShippingServicesInfo() {
        return shippingServicesInfo;
    }

    public String getMoqText() {
        return moqText;
    }

    public String getPriceSteps() {
        return priceSteps;
    }

    public String getAlibabaProductId() {
        return alibabaProductId;
    }

    public String getOriginalHtml() {
        return originalHtml;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (deleted == null) deleted = false;
        if (version == null) version = 1;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
