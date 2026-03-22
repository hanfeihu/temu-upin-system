package com.tminos.temu.upin.sdk.v2.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
/**
 * DTO: bg.local.goods.add 请求体（置于顶层 request 字段内）
 * 与提供的文档字段完全对齐；复杂结构尽量封装，无法确定的扩展保留为 Map。
 */
public class AddGloGoodsRequest {
    // ==== 顶层基础字段 ====
    public ProductSemiManagedReq productSemiManagedReq;      // 否
    public List<ProductCarouseVideoReq> productCarouseVideoReqList; // 否
    public List<GoodsLayerDecorationReq> goodsLayerDecorationReqs;  // 否
    public List<ProductPropertyReq> productPropertyReqs;            // 是
    public List<String> carouselImageUrls;                          // 否（服饰类目不必填）
    public List<ProductOuterPackageImageReq> productOuterPackageImageReqs; // 否
    public Long copyFromProductId;                          // 否
    public Integer source;                                  // 否
    public List<ProductGuideFileReq> productGuideFileReqs;  // 否
    public String productName;                              // 是
    public List<String> materialMultiLanguages;             // 否
    public List<ProductI18nReq> productI18nReqs;            // 否
    public ProductWarehouseRouteReq productWarehouseRouteReq; // 否
    public Integer sellOutProductIdSrc;                     // 否
    public List<GoodsModelReq> goodsModelReqs;              // 否
    public Integer sizeTemplateId;                          // 否
    public ProductOuterPackageReq productOuterPackageReq;   // 否
    public ProductShipmentReq productShipmentReq;           // 否（其中字段必填）
    public List<SensitiveTransNormalFileReq> sensitiveTransNormalFileReqs; // 否
    // 类目层级
    public Integer cat1Id;  // 是
    public Integer cat2Id;  // 是（无则 0）
    public Integer cat3Id;  // 是（无则 0）
    public Integer cat4Id;  // 是（无则 0）
    public Integer cat5Id;  // 是（无则 0）
    public Integer cat6Id;  // 是（无则 0）
    public Integer cat7Id;  // 是（无则 0）
    public Integer cat8Id;  // 是（无则 0）
    public Integer cat9Id;  // 是（无则 0）
    public Integer cat10Id; // 是（无则 0）
    public String sellOutProductId;                         // 否
    public List<Integer> showSizeTemplateIds;               // 否
    public List<CarouselImageI18nReq> carouselImageI18nReqs;// 否
    public List<Integer> sizeTemplateIds;                   // 否
    public List<ProductSpecPropertyReq> productSpecPropertyReqs;    // 是
    public ProductWhExtAttrReq productWhExtAttrReq;                 // 否
    public List<ProductSkcReq> productSkcReqs;             // 是
    public ProductSaleExtAttrReq productSaleExtAttrReq;    // 否
    public Integer inventoryRegion;                        // 否
    public ProductSecondHandReq productSecondHandReq;      // 否
    public CustomizedTechnologyReq customizedTechnologyReq;// 否
    public ProductNoChargerReq productNoChargerReq;        // 否（空列表清空）
    public Integer personalizationSwitch;                  // 否（0/1）
    public ProductCustomReq productCustomReq;              // 否
    public String goodsLabelName;                          // 否
    public Boolean isRecommendedTag;                       // 是
    public List<VehicleLibraryRelationReq> vehicleLibraryRelationReqList; // 否
    public List<ProductPropValueDependencyReq> productPropValueDependencyReqList; // 否
    public String materialImgUrl;                          // 是（材质图）
    public ProductComplianceStatementReq productComplianceStatementReq; // 否（其中字段必填）
    // 兼容旧版 setter，避免现有代码编译错误（数据写入到 extra 中）
    // ==== 子结构定义 ====
    
    public static class ProductSemiManagedReq {
        // Semi-managed - Material Language Strategy
        public Integer semiLanguageStrategy; // 否
        // Bound Site List
        public List<Integer> bindSiteIds;   // 是
        // Semi-managed Site Sales Mode
        public Integer semiManagedSiteMode; // 否
        // 文档未覆盖/灰度字段预留
        public Map<String, Object> extra;   // 否
        public void putExtra(String key, Object value) {
            if (this.extra == null) this.extra = new java.util.HashMap<>();
            this.extra.put(key, value);
        }
    }
    
    public static class ProductCarouseVideoReq {
        public String vid;        // 是
        public String coverUrl;   // 是
        public String videoUrl;   // 是
        public Integer width;     // 是
        public Integer height;    // 是
    }
    
    public static class GoodsLayerDecorationReq {
        public Integer floorId;   // 否，null 新建
        public Integer goodsId;   // 否
        public String lang;       // 是
        public String type;       // 是（image/text）
        public Integer priority;  // 是
        public List<GoodsLayerContent> contentList; // 是
        
        public static class GoodsLayerContent {
            public String imgUrl;                      // 否（通用）
            public TextModuleDetails textModuleDetails;// 否
            public String backgroundColor;             // 是
            public Integer fontFamily;                 // 否
            public Integer fontSize;                   // 是
            public String align;                       // 是（left/right/center/justify）
            public String fontColor;                   // 是
            public Integer width;                      // 否
            public String text;                        // 否
            public Integer height;                     // 否
            public String key;                         // 是（默认 'DecImage'）
        }
        
        public static class TextModuleDetails {
            public String backgroundColor; // 是
            public Integer fontFamily;     // 否
            public Integer fontSize;       // 是
            public String align;           // 是
            public String fontColor;       // 是
            public Integer width;          // 否
            public String text;            // 否
            public Integer height;         // 否
            public String key;             // 是
        }
    }
    
    public static class ProductPropertyReq {
        public Integer vid;              // 是（无则 0）
        public String valueUnit;         // 是（无则空串）
        public Integer pid;              // 是
        public Integer templatePid;      // 是
        public String numberInputValue;  // 否
        public String propValue;         // 是
        public String propName;          // 是
        public Integer refPid;           // 是
    }
    
    public static class ProductOuterPackageImageReq {
        public String imageUrl; // 是
    }
    
    public static class ProductGuideFileReq {
        public String fileName;          // 是
        public Integer pdfMaterialId;    // 是
        public List<String> languages;   // 是
    }
    
    public static class ProductI18nReq {
        public String language;          // 是
        public String productName;       // 是
    }
    
    public static class ProductWarehouseRouteReq {
        public List<RouteItem> targetRouteList;  // 是
        public List<RouteItem> currentRouteList; // 否
        
        public static class RouteItem {
            public List<Integer> siteIdList; // 是
            public String warehouseId;       // 是
        }
    }
    
    public static class GoodsModelReq {
        public String modelProfileUrl;   // 是
        public String sizeSpecName;      // 是
        public Integer modelId;          // 是（新虚拟模特不传）
        public Integer sizeSpecId;       // 是
        public String modelWaist;        // 否
        public Integer modelType;        // 否（1服装 2鞋）
        public String modelName;         // 是
        public String modelHeight;       // 否
        public Integer modelFeature;     // 否（1真人 2虚拟）
        public String modelFootWidth;    // 否
        public String modelBust;         // 否
        public String modelFootLength;   // 否
        public Integer tryOnResult;      // 否（1/2/3）
        public String modelHip;          // 否
    }
    
    public static class ProductOuterPackageReq {
        public Integer packageShape;     // 否
        public Integer packageType;      // 否
    }
    
    public static class ProductShipmentReq {
        public String freightTemplateId; // 是
        public Integer shipmentLimitSecond; // 是（86400/172800/259200）
        public Integer sourceInvitationId;  // 否
    }
    
    public static class SensitiveTransNormalFileReq {
        public String fileName; // 是
        public String fileUrl;  // 是
    }
    
    public static class CarouselImageI18nReq {
        public List<String> imgUrlList; // 否（空列表删除，null不变）
        public String language;         // 是
    }
    
    public static class ProductSpecPropertyReq {
        public Integer vid;              // 是（无则 0）
        public Integer specId;           // 是
        public Integer valueGroupId;     // 是（无则 0）
        public Integer parentSpecId;     // 是
        public String valueGroupName;    // 是（无则空串）
        public String valueUnit;         // 是（无则空串）
        public Integer pid;              // 是
        public Integer templatePid;      // 是
        public String numberInputValue;  // 否
        public String propValue;         // 是
        public String propName;          // 是
        public Integer refPid;           // 是
    }
    
    public static class ProductWhExtAttrReq {
        public List<ProductOriginCertFile> productOriginCertFiles;      // 否
        // 仓配扩展属性，文档未详述，预留 Map
        public String outerGoodsUrl;
        public ProductOrigin productOrigin;                    // 是
    }
    
    public static class ProductOriginCertFile {
        public String fileName; // 是
        public String fileUrl;  // 是
    }
    
    public static class ProductOrigin {
        public Long region2Id;          // 否
        public String region1ShortName;    // 是（两字符缩写）
    }
    
    public static class ProductSkcReq {
        // SKC preview images
        public List<String> previewImgUrls; // 是（非服饰不必填）
        // SKC Carousel Multi-Language Information Request
        public List<ProductSkcCarouselImageI18nReq> productSkcCarouselImageI18nReqs; // 否
        // SKC Color Block Diagram
        public String colorImageUrl; // 否
        // Main Sales Specification List
        public List<MainProductSkuSpecReq> mainProductSkuSpecReqs; // 是
        // Whether Baseplate
        public Integer isBasePlate; // 否
        // Product SKU List (up to 10 for Apparel Category)
        public List<ProductSkuReq> productSkuReqs; // 是
        // SKC ExtCode
        public String extCode; // 是（空串表示无）
        
        public static class MainProductSkuSpecReq {
            public Integer parentSpecId;   // 是
            public String parentSpecName;  // 是
            public Integer specId;         // 是
            public String specName;        // 是
        }
    }
    
    public static class ProductSkuReq {
        public String currencyType; // 是（CNY/USD，默认CNY）
        public ProductSkuMultiPackReq productSkuMultiPackReq; // 否
        public Integer mixedType;                               // 否（1/2）
        public ProductSkuSuggestedPriceReq productSkuSuggestedPriceReq;     // 否
        public List<SiteSupplierPrice> siteSupplierPrices;                  // 否（半托管）Site Supply Price List, for semi_managed merchant scenario only
        public ProductSkuUsSuggestedPriceReq productSkuUsSuggestedPriceReq; // 否
        public ProductSkuStockQuantityReq productSkuStockQuantityReq;       // 否
        public List<ProductSkuThumbUrlI18nReq> productSkuThumbUrlI18nReqs;  // 否
        public ProductSkuAccessoriesReq productSkuAccessoriesReq;            // 否
        public ProductSkuWhExtAttrReq productSkuWhExtAttrReq;                // 是
        public BigDecimal supplierPrice;//Supply Price, deprecated in semi_managed merchant scenario
        public List<ProductSkuSpecReq> productSkuSpecReqs;                   // 是
        public String extCode; // 是（与 skc 外码一致，空串表示无）
        public String thumbUrl; // 是（与 skc 外码一致，空串表示无）
        // ==== 嵌套对象 ====
        
        public static class ProductSkuMultiPackReq {
            public Integer numberOfPieces;   // 否，默认1
            public Integer individuallyPacked; // 否（-1 清空）
            public ProductSkuNetContentReq productSkuNetContentReq; // 否（空对象表示清空）
            public Integer mixedType;//Mixed set type, 1: different products, 2: same product with different specifications
            public TotalNetContent totalNetContent;                 // 否
            public Integer pieceNewUnitCode;                        // 否（1件）
            public Integer skuClassification;                       // 否（1/2/3）
            public Integer numberOfPiecesNew;                       // 否
            public Integer pieceUnitCode;                           // 否（1件/2双/3包）
        }
        
        public static class ProductSkuNetContentReq {
            public Integer netContentUnitCode; // 否（1:Fl Oz,2:mL,3:Gallon,4:Liter,5:Gram,6:Kg,7:Troy Oz,8:Pound）
            public Integer netContentNumber;   // 否（传值需*1000）
        }
        
        public static class TotalNetContent {
            public Integer netContentUnitCode; // 否
            public Integer netContentNumber;   // 否（传值需*1000）
        }
        
        public static class ProductSkuSuggestedPriceReq {
            public String suggestedPriceCurrencyType; // 否
            public Integer suggestedPrice;            // 否
            public String specialSuggestedPrice;      // 否
        }
        
        public static class SiteSupplierPrice {
            public Integer siteId;          // 是
            public Integer supplierPrice;   // 是（单位：分/美分）
        }
        
        public static class ProductSkuUsSuggestedPriceReq {
            public String suggestedPriceCurrencyType; // 否
            public Integer suggestedPrice;            // 否
            public String specialSuggestedPrice;      // 否
        }
        
        public static class ProductSkuStockQuantityReq {
            public List<WarehouseStockQuantityReq> warehouseStockQuantityReqs; // 是
            
            public static class WarehouseStockQuantityReq {
                public Integer targetStockAvailable; // 是
                public String warehouseId;           // 是
                public Integer currentStockAvailable;// 否
            }
        }
        
        public static class ProductSkuThumbUrlI18nReq {
            public List<String> imgUrlList; // 否（空列表删除，null 不变）
            public String language;         // 是
        }
        
        public static class ProductSkuAccessoriesReq {
            public List<ProductSkuAccessory> productSkuAccessories; // 是
            
            public static class ProductSkuAccessory {
                public Integer vid;       // 是
                public Integer num;       // 是
                public Integer unitCode;  // 是
                public String thumbUrl;   // 是
            }
        }
        
        public static class ProductSkuWhExtAttrReq {
            public ProductSkuWeightReq productSkuWeightReq;                      // 是
            public ProductSkuSameReferPriceReq productSkuSameReferPriceReq;      // 否
            public ProductSkuSensitiveLimitReq productSkuSensitiveLimitReq;      // 是
            public ProductSkuVolumeReq productSkuVolumeReq;                      // 是
            public List<ProductSkuBarCodeReq> productSkuBarCodeReqs;             // 否
            public ProductSkuSensitiveAttrReq productSkuSensitiveAttrReq;        // 是
        }
        
        public static class ProductSkuWeightReq {
            public String inputUnit;   // 否
            public String inputValue;  // 否
            public Integer value;      // 是（单位 mg）
        }
        
        public static class ProductSkuSameReferPriceReq {
            public String url; // 否
        }
        
        public static class ProductSkuSensitiveLimitReq {
            public Integer maxBatteryCapacityHp; // 否（mWh）
            public Integer maxBatteryCapacity;   // 否（Wh，优先Hp）
            public Integer maxLiquidCapacity;    // 否（mL，优先Hp）
            public Integer maxLiquidCapacityHp;  // 否（μL）
            public Integer maxKnifeLength;       // 否（mm，优先Hp）
            public Integer maxKnifeLengthHp;     // 否（μm）
            public KnifeTipAngle knifeTipAngle;  // 否
            
            public static class KnifeTipAngle {
                public Integer degrees; // 是
            }
        }
        
        public static class ProductSkuVolumeReq {
            public String inputUnit;   // 否
            public Integer len;        // 是（最短边 mm）
            public String inputLen;    // 否（最长输入边）
            public String inputHeight; // 否（最短输入边）
            public Integer width;      // 是（次长 mm）
            public String inputWidth;  // 否
            public Integer height;     // 是（最短边 mm）
        }
        
        public static class ProductSkuBarCodeReq {
            public String code;     // 否
            public Integer codeType; // 否（1:EAN,2:UPC,3:ISBN）
        }
        
        public static class ProductSkuSensitiveAttrReq {
            public List<Integer> sensitiveTypes; // 否（1..7）
            public Integer isSensitive;          // 否（0/1）
            public List<Integer> sensitiveList;  // 否（110001..170001）
        }
        
        public static class ProductSkuSpecReq {
            public Integer specId;          // 是
            public String parentSpecName;   // 是
            public Integer parentSpecId;    // 是
            public String specName;         // 是
        }
    }
    
    public static class ProductSkcCarouselImageI18nReq {
        public List<String> imgUrlList; // 否（空列表删除，null 不变）
        public String language;         // 是
    }
    
    public static class ProductSaleExtAttrReq {
        public Map<String, Object> data; // 预留
    }
    
    public static class ProductSecondHandReq {
        public Boolean isSecondHand;   // 否
        public Integer secondHandLevel;// 否
        public Boolean discreetShipping;// 否（成人品类）
    }
    
    public static class CustomizedTechnologyReq {
        public List<Integer> twiceType; // 否
        public Integer firstType;       // 是
        public Integer technologyType;  // 是
    }
    
    public static class ProductNoChargerReq {
        public List<Integer> noChargerProductIds; // 是（空列表清除）
    }
    
    public static class ProductCustomReq {
        public Map<String, Object> data; // 预留
    }
    
    public static class VehicleLibraryRelationReq {
        public Integer vehicleLibraryId; // 是
        public Integer propertyValueDependencyId1; // 否
        public Integer propertyValueDependencyId2; // 否
        public Integer propertyValueDependencyId3; // 否
        public Integer propertyValueDependencyId4; // 否
        public Integer propertyValueDependencyId5; // 否
        public Integer propertyValueDependencyId6; // 否
        public Integer propertyValueDependencyId7; // 否
        public Integer propertyValueDependencyId8; // 否
        public Integer propertyValueDependencyId9; // 否
        public Integer propertyValueDependencyId10;// 否
    }
    
    public static class ProductPropValueDependencyReq {
        public Integer propertyValueDependencyId1; // 否
        public Integer propertyValueDependencyId2; // 否
        public Integer propertyValueDependencyId3; // 否
        public Integer propertyValueDependencyId4; // 否
        public Integer propertyValueDependencyId5; // 否
        public Integer propertyValueDependencyId6; // 否
        public Integer propertyValueDependencyId7; // 否
        public Integer propertyValueDependencyId8; // 否
        public Integer propertyValueDependencyId9; // 否
        public Integer propertyValueDependencyId10;// 否
    }
    
    public static class ProductComplianceStatementReq {
        public String protocolVersion; // 是
        public String protocolUrl;     // 是
    }
    // ===== 兼容旧版结构（用于现有工具类/界面编译通过），后续可逐步迁移 =====
    
    public static class GoodsBasic {
        public String goodsName;
        public Long catId;
        public Long cat1Id;
        public Long cat2Id;
        public Long cat3Id;
        public Long cat4Id;
        public Long cat5Id;
        public Long cat6Id;
        public Long cat7Id;
        public Long cat8Id;
        public Long cat9Id;
        public Long cat10Id;
        public GoodsGallery goodsGallery;
        public String importDesignation;
        public String outGoodsSn;
        public Integer productType;
        
        public static class GoodsGallery {
            public DetailVideo detailVideo;
            public List<String> detailImage;
            public CarouselVideo carouselVideo;
            
            public static class DetailVideo {
                public String vid;
                public String videoUrl;
            }
            
            public static class CarouselVideo {
                public String vid;
                public String videoUrl;
            }
        }
    }
    
    public static class GoodsServicePromise {
        public Integer shipmentLimitDay;
        public Integer fulfillmentType;
        public String costTemplateId;
    }
    
    public static class GoodsProperty {
        public List<GoodsPropertyItem> goodsProperties;
        
        public static class GoodsPropertyItem {
            public Long vid;
            public String value;
            public String valueUnit;
            public Long valueUnitId;
            public Long templatePid;
            public Long parentSpecId;
            public Long specId;
            public String note;
            public String imgUrl;
            public Integer groupId;
            public Long refPid;
            public String numberInputValue;
        }
    }
    
    public static class GoodsOriginInfo {
        public String originRegion1;
        public String originRegion2;
        public Boolean agreeDefaultOriginRegion;
        public List<String> proofImageUrls;
        public List<String> labelManufacturerProofImageUrls;
    }
    
    public static class CertificationInfo {
        public CertificateInfo certificateInfo;
        public Object extraTemplate; // 兼容占位
        public Object actualPhoto;   // 兼容占位
        public Object repInfo;       // 兼容占位
        
        public static class CertificateInfo {
            public List<CertificateDetail> certificateDetailList;
            
            public static class CertificateDetail {
                public Integer certType;
                public Boolean skip;
                public String authCode;
                public List<AuthCode> authCodes;
                public List<FileItem> certFiles;
                public List<FileItem> inspectReportFiles;
                
                public static class AuthCode { public String authCode; }
                
                public static class FileItem { public String fileName; public String fileUrl; public String language; }
            }
        }
    }
    
    public static class GuideFileInfo { public Map<String, String> lang2GuideFileUrl; }
    
    public static class GoodsSizeChartList {
        public List<SizeChart> goodsSizeChartList;
        
        public static class SizeChart {
            public Integer classId; public Meta meta; public List<Record> records; public BodyMeta bodyMeta; public List<Record> bodyRecords;
             public static class Meta { public List<Simple> groups; public List<Simple> elements; }
             public static class BodyMeta { public List<Simple> groups; public List<Simple> elements; }
             public static class Record { public List<Value> values; }
             public static class Simple { public String name; public Integer id; }
             public static class Value { public Integer id; public String value; }
        }
    }
    
    public static class SkuItem {
        public Price price; public Long quantity; public List<Long> specIdList; public String outSkuSn; public String weight; public String weightUnit;
        public String length; public String width; public String height; public String volumeUnit; public List<String> images; public Integer externalProductType;
        public String externalProductId; public String referenceLink; public MultiplePackage multiplePackage;
         public static class Price { public Money basePrice; public Money listPrice; public Integer listPriceType;  public static class Money { public String amount; public String currency; } }
         public static class MultiplePackage { public Integer skuClassification; public Integer mixedSetType; public Integer numberOfPieces; public Integer pieceUnitCode; public String originNetContentNumber; public String originTotalNetContentNumber; public Integer netContentUnitCode; public Integer individuallyPacked; }
    }
    
    public static class GoodsTrademark { public Long brandId; public Long trademarkId; public Boolean noTrademark; }
    
    public static class TaxCodeInfo { public String itemTaxCode; }
    
    public static class GoodsVehiclePropertyRelation { public Long relationId; public Integer relationType; public List<Long> leafPropertyValueDependencyIdList; }
    
    public static class SecondHand { public Boolean secondHandGoods; public Integer level; public Integer businessScope; public String insName; public String grade; }
}
