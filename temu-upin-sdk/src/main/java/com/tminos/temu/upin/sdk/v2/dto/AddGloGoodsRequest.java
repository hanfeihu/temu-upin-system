package com.tminos.temu.upin.sdk.v2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO: bg.local.goods.add 请求体（置于顶层 request 字段内）
 * 与提供的文档字段完全对齐；复杂结构尽量封装，无法确定的扩展保留为 Map。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddGloGoodsRequest {

    // ==== 顶层基础字段 ====
    private ProductSemiManagedReq productSemiManagedReq;      // 否

    private List<ProductCarouseVideoReq> productCarouseVideoReqList; // 否
    private List<GoodsLayerDecorationReq> goodsLayerDecorationReqs;  // 否
    private List<ProductPropertyReq> productPropertyReqs;            // 是
    private List<String> carouselImageUrls;                          // 否（服饰类目不必填）
    private List<ProductOuterPackageImageReq> productOuterPackageImageReqs; // 否

    private Long copyFromProductId;                          // 否
    private Integer source;                                  // 否
    private List<ProductGuideFileReq> productGuideFileReqs;  // 否

    private String productName;                              // 是
    private List<String> materialMultiLanguages;             // 否
    private List<ProductI18nReq> productI18nReqs;            // 否

    private ProductWarehouseRouteReq productWarehouseRouteReq; // 否
    private Integer sellOutProductIdSrc;                     // 否
    private List<GoodsModelReq> goodsModelReqs;              // 否
    private Integer sizeTemplateId;                          // 否

    private ProductOuterPackageReq productOuterPackageReq;   // 否
    private ProductShipmentReq productShipmentReq;           // 否（其中字段必填）
    private List<SensitiveTransNormalFileReq> sensitiveTransNormalFileReqs; // 否

    // 类目层级
    private Integer cat1Id;  // 是
    private Integer cat2Id;  // 是（无则 0）
    private Integer cat3Id;  // 是（无则 0）
    private Integer cat4Id;  // 是（无则 0）
    private Integer cat5Id;  // 是（无则 0）
    private Integer cat6Id;  // 是（无则 0）
    private Integer cat7Id;  // 是（无则 0）
    private Integer cat8Id;  // 是（无则 0）
    private Integer cat9Id;  // 是（无则 0）
    private Integer cat10Id; // 是（无则 0）

    private String sellOutProductId;                         // 否
    private List<Integer> showSizeTemplateIds;               // 否
    private List<CarouselImageI18nReq> carouselImageI18nReqs;// 否
    private List<Integer> sizeTemplateIds;                   // 否

    private List<ProductSpecPropertyReq> productSpecPropertyReqs;    // 是
    private ProductWhExtAttrReq productWhExtAttrReq;                 // 否



    private List<ProductSkcReq> productSkcReqs;             // 是

    private ProductSaleExtAttrReq productSaleExtAttrReq;    // 否
    private Integer inventoryRegion;                        // 否
    private ProductSecondHandReq productSecondHandReq;      // 否
    private CustomizedTechnologyReq customizedTechnologyReq;// 否
    private ProductNoChargerReq productNoChargerReq;        // 否（空列表清空）
    private Integer personalizationSwitch;                  // 否（0/1）
    private ProductCustomReq productCustomReq;              // 否
    private String goodsLabelName;                          // 否
    private Boolean isRecommendedTag;                       // 是

    private List<VehicleLibraryRelationReq> vehicleLibraryRelationReqList; // 否
    private List<ProductPropValueDependencyReq> productPropValueDependencyReqList; // 否

    private String materialImgUrl;                          // 是（材质图）
    private ProductComplianceStatementReq productComplianceStatementReq; // 否（其中字段必填）



    public Integer getSizeTemplateId() { return sizeTemplateId; }
    public void setSizeTemplateId(Integer sizeTemplateId) { this.sizeTemplateId = sizeTemplateId; }
    public List<Integer> getSizeTemplateIds() { return sizeTemplateIds; }
    public void setSizeTemplateIds(List<Integer> sizeTemplateIds) { this.sizeTemplateIds = sizeTemplateIds; }
    public List<Integer> getShowSizeTemplateIds() { return showSizeTemplateIds; }
    public void setShowSizeTemplateIds(List<Integer> showSizeTemplateIds) { this.showSizeTemplateIds = showSizeTemplateIds; }
    public List<ProductPropertyReq> getProductPropertyReqs() { return productPropertyReqs; }
    public void setProductPropertyReqs(List<ProductPropertyReq> productPropertyReqs) { this.productPropertyReqs = productPropertyReqs; }
    public List<ProductSpecPropertyReq> getProductSpecPropertyReqs() { return productSpecPropertyReqs; }
    public void setProductSpecPropertyReqs(List<ProductSpecPropertyReq> productSpecPropertyReqs) { this.productSpecPropertyReqs = productSpecPropertyReqs; }

    // ==== 子结构定义 ====

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSemiManagedReq {
        // Semi-managed - Material Language Strategy
        private Integer semiLanguageStrategy; // 否

        // Bound Site List
        private List<Integer> bindSiteIds;   // 是

        // Semi-managed Site Sales Mode
        private Integer semiManagedSiteMode; // 否

        // 文档未覆盖/灰度字段预留
        private Map<String, Object> extra;   // 否

        public void putExtra(String key, Object value) {
            if (this.extra == null) this.extra = new java.util.HashMap<>();
            this.extra.put(key, value);
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductCarouseVideoReq {
        private String vid;        // 是
        private String coverUrl;   // 是
        private String videoUrl;   // 是
        private Integer width;     // 是
        private Integer height;    // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsLayerDecorationReq {
        private Integer floorId;   // 否，null 新建
        private Integer goodsId;   // 否
        private String lang;       // 是
        private String type;       // 是（image/text）
        private Integer priority;  // 是
        private List<GoodsLayerContent> contentList; // 是

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class GoodsLayerContent {
            private String imgUrl;                      // 否（通用）
            private TextModuleDetails textModuleDetails;// 否
            private String backgroundColor;             // 是
            private Integer fontFamily;                 // 否
            private Integer fontSize;                   // 是
            private String align;                       // 是（left/right/center/justify）
            private String fontColor;                   // 是
            private Integer width;                      // 否
            private String text;                        // 否
            private Integer height;                     // 否
            private String key;                         // 是（默认 'DecImage'）
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class TextModuleDetails {
            private String backgroundColor; // 是
            private Integer fontFamily;     // 否
            private Integer fontSize;       // 是
            private String align;           // 是
            private String fontColor;       // 是
            private Integer width;          // 否
            private String text;            // 否
            private Integer height;         // 否
            private String key;             // 是
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductPropertyReq {
        private Integer vid;              // 是（无则 0）
        private String valueUnit;         // 是（无则空串）
        private Integer pid;              // 是
        private Integer templatePid;      // 是
        private String numberInputValue;  // 否
        private String propValue;         // 是
        private String propName;          // 是
        private Integer refPid;           // 是

        public String getPropName() { return propName; }
        public void setPropName(String propName) { this.propName = propName; }
        public String getPropValue() { return propValue; }
        public void setPropValue(String propValue) { this.propValue = propValue; }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductOuterPackageImageReq {
        private String imageUrl; // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductGuideFileReq {
        private String fileName;          // 是
        private Integer pdfMaterialId;    // 是
        private List<String> languages;   // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductI18nReq {
        private String language;          // 是
        private String productName;       // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductWarehouseRouteReq {
        private List<RouteItem> targetRouteList;  // 是
        private List<RouteItem> currentRouteList; // 否

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class RouteItem {
            private List<Integer> siteIdList; // 是
            private String warehouseId;       // 是
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsModelReq {
        private String modelProfileUrl;   // 是
        private String sizeSpecName;      // 是
        private Integer modelId;          // 是（新虚拟模特不传）
        private Integer sizeSpecId;       // 是
        private String modelWaist;        // 否
        private Integer modelType;        // 否（1服装 2鞋）
        private String modelName;         // 是
        private String modelHeight;       // 否
        private Integer modelFeature;     // 否（1真人 2虚拟）
        private String modelFootWidth;    // 否
        private String modelBust;         // 否
        private String modelFootLength;   // 否
        private Integer tryOnResult;      // 否（1/2/3）
        private String modelHip;          // 否
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductOuterPackageReq {
        private Integer packageShape;     // 否
        private Integer packageType;      // 否
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductShipmentReq {
        private String freightTemplateId; // 是
        private Integer shipmentLimitSecond; // 是（86400/172800/259200）
        private Integer sourceInvitationId;  // 否
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SensitiveTransNormalFileReq {
        private String fileName; // 是
        private String fileUrl;  // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CarouselImageI18nReq {
        private List<String> imgUrlList; // 否（空列表删除，null不变）
        private String language;         // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSpecPropertyReq {
        private Integer vid;              // 是（无则 0）
        private Integer specId;           // 是
        private Integer valueGroupId;     // 是（无则 0）
        private Integer parentSpecId;     // 是
        private String valueGroupName;    // 是（无则空串）
        private String valueUnit;         // 是（无则空串）
        private Integer pid;              // 是
        private Integer templatePid;      // 是
        private String numberInputValue;  // 否
        private String propValue;         // 是
        private String propName;          // 是
        private Integer refPid;           // 是

        public String getPropName() { return propName; }
        public void setPropName(String propName) { this.propName = propName; }
        public String getPropValue() { return propValue; }
        public void setPropValue(String propValue) { this.propValue = propValue; }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductWhExtAttrReq {
        private List<ProductOriginCertFile> productOriginCertFiles;      // 否
        // 仓配扩展属性，文档未详述，预留 Map

        private  String outerGoodsUrl;
        private ProductOrigin productOrigin;                    // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductOriginCertFile {
        private String fileName; // 是
        private String fileUrl;  // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductOrigin {
        private Long region2Id;          // 否
        private String region1ShortName;    // 是（两字符缩写）
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSkcReq {
        // SKC preview images
        private List<String> previewImgUrls; // 是（非服饰不必填）

        // SKC Carousel Multi-Language Information Request
        private List<ProductSkcCarouselImageI18nReq> productSkcCarouselImageI18nReqs; // 否

        // SKC Color Block Diagram
        private String colorImageUrl; // 否

        // Main Sales Specification List
        private List<MainProductSkuSpecReq> mainProductSkuSpecReqs; // 是

        // Whether Baseplate
        private Integer isBasePlate; // 否

        // Product SKU List (up to 10 for Apparel Category)
        private List<ProductSkuReq> productSkuReqs; // 是

        // SKC ExtCode
        private String extCode; // 是（空串表示无）

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class MainProductSkuSpecReq {
            private Integer parentSpecId;   // 是
            private String parentSpecName;  // 是
            private Integer specId;         // 是
            private String specName;        // 是
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSkuReq {
        private String currencyType; // 是（CNY/USD，默认CNY）

        private ProductSkuMultiPackReq productSkuMultiPackReq; // 否
        private Integer mixedType;                               // 否（1/2）




        private ProductSkuSuggestedPriceReq productSkuSuggestedPriceReq;     // 否
        private List<SiteSupplierPrice> siteSupplierPrices;                  // 否（半托管）Site Supply Price List, for semi_managed merchant scenario only
        private ProductSkuUsSuggestedPriceReq productSkuUsSuggestedPriceReq; // 否

        private ProductSkuStockQuantityReq productSkuStockQuantityReq;       // 否
        private List<ProductSkuThumbUrlI18nReq> productSkuThumbUrlI18nReqs;  // 否
        private ProductSkuAccessoriesReq productSkuAccessoriesReq;            // 否
        private ProductSkuWhExtAttrReq productSkuWhExtAttrReq;                // 是
        private BigDecimal supplierPrice;//Supply Price, deprecated in semi_managed merchant scenario


        private List<ProductSkuSpecReq> productSkuSpecReqs;                   // 是

        private String extCode; // 是（与 skc 外码一致，空串表示无）
        private String thumbUrl; // 是（与 skc 外码一致，空串表示无）

        // ==== 嵌套对象 ====

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuMultiPackReq {
            private Integer numberOfPieces;   // 否，默认1
            private Integer individuallyPacked; // 否（-1 清空）
            private ProductSkuNetContentReq productSkuNetContentReq; // 否（空对象表示清空）
            private Integer mixedType;//Mixed set type, 1: different products, 2: same product with different specifications

            private TotalNetContent totalNetContent;                 // 否
            private Integer pieceNewUnitCode;                        // 否（1件）
            private Integer skuClassification;                       // 否（1/2/3）

            private Integer numberOfPiecesNew;                       // 否
            private Integer pieceUnitCode;                           // 否（1件/2双/3包）

        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuNetContentReq {
            private Integer netContentUnitCode; // 否（1:Fl Oz,2:mL,3:Gallon,4:Liter,5:Gram,6:Kg,7:Troy Oz,8:Pound）
            private Integer netContentNumber;   // 否（传值需*1000）
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class TotalNetContent {
            private Integer netContentUnitCode; // 否
            private Integer netContentNumber;   // 否（传值需*1000）
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuSuggestedPriceReq {
            private String suggestedPriceCurrencyType; // 否
            private Integer suggestedPrice;            // 否
            private String specialSuggestedPrice;      // 否
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class SiteSupplierPrice {
            private Integer siteId;          // 是
            private Integer supplierPrice;   // 是（单位：分/美分）
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuUsSuggestedPriceReq {
            private String suggestedPriceCurrencyType; // 否
            private Integer suggestedPrice;            // 否
            private String specialSuggestedPrice;      // 否
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuStockQuantityReq {
            private List<WarehouseStockQuantityReq> warehouseStockQuantityReqs; // 是

            @Data @NoArgsConstructor @AllArgsConstructor
            public static class WarehouseStockQuantityReq {
                private Integer targetStockAvailable; // 是
                private String warehouseId;           // 是
                private Integer currentStockAvailable;// 否
            }
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuThumbUrlI18nReq {
            private List<String> imgUrlList; // 否（空列表删除，null 不变）
            private String language;         // 是
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuAccessoriesReq {
            private List<ProductSkuAccessory> productSkuAccessories; // 是

            @Data @NoArgsConstructor @AllArgsConstructor
            public static class ProductSkuAccessory {
                private Integer vid;       // 是
                private Integer num;       // 是
                private Integer unitCode;  // 是
                private String thumbUrl;   // 是
            }
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuWhExtAttrReq {
            private ProductSkuWeightReq productSkuWeightReq;                      // 是
            private ProductSkuSameReferPriceReq productSkuSameReferPriceReq;      // 否
            private ProductSkuSensitiveLimitReq productSkuSensitiveLimitReq;      // 是
            private ProductSkuVolumeReq productSkuVolumeReq;                      // 是
            private List<ProductSkuBarCodeReq> productSkuBarCodeReqs;             // 否
            private ProductSkuSensitiveAttrReq productSkuSensitiveAttrReq;        // 是
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuWeightReq {
            private String inputUnit;   // 否
            private String inputValue;  // 否
            private Integer value;      // 是（单位 mg）
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuSameReferPriceReq {
            private String url; // 否
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuSensitiveLimitReq {
            private Integer maxBatteryCapacityHp; // 否（mWh）
            private Integer maxBatteryCapacity;   // 否（Wh，优先Hp）
            private Integer maxLiquidCapacity;    // 否（mL，优先Hp）
            private Integer maxLiquidCapacityHp;  // 否（μL）
            private Integer maxKnifeLength;       // 否（mm，优先Hp）
            private Integer maxKnifeLengthHp;     // 否（μm）
            private KnifeTipAngle knifeTipAngle;  // 否

            @Data @NoArgsConstructor @AllArgsConstructor
            public static class KnifeTipAngle {
                private Integer degrees; // 是
            }
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuVolumeReq {
            private String inputUnit;   // 否
            private Integer len;        // 是（最短边 mm）
            private String inputLen;    // 否（最长输入边）
            private String inputHeight; // 否（最短输入边）
            private Integer width;      // 是（次长 mm）
            private String inputWidth;  // 否
            private Integer height;     // 是（最短边 mm）
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuBarCodeReq {
            private String code;     // 否
            private Integer codeType; // 否（1:EAN,2:UPC,3:ISBN）
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuSensitiveAttrReq {
            private List<Integer> sensitiveTypes; // 否（1..7）
            private Integer isSensitive;          // 否（0/1）
            private List<Integer> sensitiveList;  // 否（110001..170001）
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuSpecReq {
            private Integer specId;          // 是
            private String parentSpecName;   // 是
            private Integer parentSpecId;    // 是
            private String specName;         // 是
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSkcCarouselImageI18nReq {
        private List<String> imgUrlList; // 否（空列表删除，null 不变）
        private String language;         // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSaleExtAttrReq {
        private Map<String, Object> data; // 预留
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSecondHandReq {
        private Boolean isSecondHand;   // 否
        private Integer secondHandLevel;// 否
        private Boolean discreetShipping;// 否（成人品类）
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CustomizedTechnologyReq {
        private List<Integer> twiceType; // 否
        private Integer firstType;       // 是
        private Integer technologyType;  // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductNoChargerReq {
        private List<Integer> noChargerProductIds; // 是（空列表清除）
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductCustomReq {
        private Map<String, Object> data; // 预留
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class VehicleLibraryRelationReq {
        private Integer vehicleLibraryId; // 是
        private Integer propertyValueDependencyId1; // 否
        private Integer propertyValueDependencyId2; // 否
        private Integer propertyValueDependencyId3; // 否
        private Integer propertyValueDependencyId4; // 否
        private Integer propertyValueDependencyId5; // 否
        private Integer propertyValueDependencyId6; // 否
        private Integer propertyValueDependencyId7; // 否
        private Integer propertyValueDependencyId8; // 否
        private Integer propertyValueDependencyId9; // 否
        private Integer propertyValueDependencyId10;// 否
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductPropValueDependencyReq {
        private Integer propertyValueDependencyId1; // 否
        private Integer propertyValueDependencyId2; // 否
        private Integer propertyValueDependencyId3; // 否
        private Integer propertyValueDependencyId4; // 否
        private Integer propertyValueDependencyId5; // 否
        private Integer propertyValueDependencyId6; // 否
        private Integer propertyValueDependencyId7; // 否
        private Integer propertyValueDependencyId8; // 否
        private Integer propertyValueDependencyId9; // 否
        private Integer propertyValueDependencyId10;// 否
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductComplianceStatementReq {
        private String protocolVersion; // 是
        private String protocolUrl;     // 是
    }

    // ===== 兼容旧版结构（用于现有工具类/界面编译通过），后续可逐步迁移 =====
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsBasic {
        private String goodsName;
        private Long catId;
        private Long cat1Id;
        private Long cat2Id;
        private Long cat3Id;
        private Long cat4Id;
        private Long cat5Id;
        private Long cat6Id;
        private Long cat7Id;
        private Long cat8Id;
        private Long cat9Id;
        private Long cat10Id;
        private GoodsGallery goodsGallery;
        private String importDesignation;
        private String outGoodsSn;
        private Integer productType;

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class GoodsGallery {
            private DetailVideo detailVideo;
            private List<String> detailImage;
            private CarouselVideo carouselVideo;

            @Data @NoArgsConstructor @AllArgsConstructor
            public static class DetailVideo {
                private String vid;
                private String videoUrl;
            }
            @Data @NoArgsConstructor @AllArgsConstructor
            public static class CarouselVideo {
                private String vid;
                private String videoUrl;
            }
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsServicePromise {
        private Integer shipmentLimitDay;
        private Integer fulfillmentType;
        private String costTemplateId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsProperty {
        private List<GoodsPropertyItem> goodsProperties;

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class GoodsPropertyItem {
            private Long vid;
            private String value;
            private String valueUnit;
            private Long valueUnitId;
            private Long templatePid;
            private Long parentSpecId;
            private Long specId;
            private String note;
            private String imgUrl;
            private Integer groupId;
            private Long refPid;
            private String numberInputValue;
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsOriginInfo {
        private String originRegion1;
        private String originRegion2;
        private Boolean agreeDefaultOriginRegion;
        private List<String> proofImageUrls;
        private List<String> labelManufacturerProofImageUrls;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CertificationInfo {
        private CertificateInfo certificateInfo;
        private Object extraTemplate; // 兼容占位
        private Object actualPhoto;   // 兼容占位
        private Object repInfo;       // 兼容占位

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class CertificateInfo {
            private List<CertificateDetail> certificateDetailList;

            @Data @NoArgsConstructor @AllArgsConstructor
            public static class CertificateDetail {
                private Integer certType;
                private Boolean skip;
                private String authCode;
                private List<AuthCode> authCodes;
                private List<FileItem> certFiles;
                private List<FileItem> inspectReportFiles;

                @Data @NoArgsConstructor @AllArgsConstructor
                public static class AuthCode { private String authCode; }
                @Data @NoArgsConstructor @AllArgsConstructor
                public static class FileItem { private String fileName; private String fileUrl; private String language; }
            }
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GuideFileInfo { private Map<String, String> lang2GuideFileUrl; }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsSizeChartList {
        private List<SizeChart> goodsSizeChartList;
        @Data @NoArgsConstructor @AllArgsConstructor
        public static class SizeChart {
            private Integer classId; private Meta meta; private List<Record> records; private BodyMeta bodyMeta; private List<Record> bodyRecords;
            @Data @NoArgsConstructor @AllArgsConstructor public static class Meta { private List<Simple> groups; private List<Simple> elements; }
            @Data @NoArgsConstructor @AllArgsConstructor public static class BodyMeta { private List<Simple> groups; private List<Simple> elements; }
            @Data @NoArgsConstructor @AllArgsConstructor public static class Record { private List<Value> values; }
            @Data @NoArgsConstructor @AllArgsConstructor public static class Simple { private String name; private Integer id; }
            @Data @NoArgsConstructor @AllArgsConstructor public static class Value { private Integer id; private String value; }
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SkuItem {
        private Price price; private Long quantity; private List<Long> specIdList; private String outSkuSn; private String weight; private String weightUnit;
        private String length; private String width; private String height; private String volumeUnit; private List<String> images; private Integer externalProductType;
        private String externalProductId; private String referenceLink; private MultiplePackage multiplePackage;
        @Data @NoArgsConstructor @AllArgsConstructor public static class Price { private Money basePrice; private Money listPrice; private Integer listPriceType; @Data @NoArgsConstructor @AllArgsConstructor public static class Money { private String amount; private String currency; } }
        @Data @NoArgsConstructor @AllArgsConstructor public static class MultiplePackage { private Integer skuClassification; private Integer mixedSetType; private Integer numberOfPieces; private Integer pieceUnitCode; private String originNetContentNumber; private String originTotalNetContentNumber; private Integer netContentUnitCode; private Integer individuallyPacked; }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsTrademark { private Long brandId; private Long trademarkId; private Boolean noTrademark; }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TaxCodeInfo { private String itemTaxCode; }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsVehiclePropertyRelation { private Long relationId; private Integer relationType; private List<Long> leafPropertyValueDependencyIdList; }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SecondHand { private Boolean secondHandGoods; private Integer level; private Integer businessScope; private String insName; private String grade; }
}
