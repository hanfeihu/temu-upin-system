package com.tminos.temu.upin.sdk.v2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO: 货品发布请求体（置于顶层 request 字段内）。
 * 中文说明按当前货品发布接口文档补齐；无法稳定建模的扩展字段继续保留为 Map。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddGloGoodsRequest {

    // ==== 顶层基础字段 ====
    private ProductSemiManagedReq productSemiManagedReq;      // 半托管商家信息

    private List<ProductCarouseVideoReq> productCarouseVideoReqList; // 商品轮播视频
    private List<GoodsLayerDecorationReq> goodsLayerDecorationReqs;  // 商详装修楼层
    private List<ProductPropertyReq> productPropertyReqs;            // 商品属性
    private List<String> carouselImageUrls;                          // 商品轮播图，服饰类目通常不传
    private List<ProductOuterPackageImageReq> productOuterPackageImageReqs; // 外包装图片

    private Long copyFromProductId;                          // 复制来源商品 ID
    private Integer source;                                  // 商品来源
    private List<ProductGuideFileReq> productGuideFileReqs;  // 说明书多语言文件

    private String productName;                              // 商品名称
    private List<String> materialMultiLanguages;             // 材质图多语言列表
    private List<ProductI18nReq> productI18nReqs;            // 商品多语言信息

    private ProductWarehouseRouteReq productWarehouseRouteReq; // 仓库路由信息
    private Integer sellOutProductIdSrc;                     // 售罄商品来源 ID
    private List<GoodsModelReq> goodsModelReqs;              // 模特信息列表
    private Long sizeTemplateId;                             // 尺码表模板 ID

    private ProductOuterPackageReq productOuterPackageReq;   // 外包装信息
    private ProductShipmentReq productShipmentReq;           // 发货信息
    private List<SensitiveTransNormalFileReq> sensitiveTransNormalFileReqs; // 敏感品转普货证明文件

    // 类目层级 ID，无对应层级时按文档传 0
    private Integer cat1Id;  // 一级类目 ID
    private Integer cat2Id;  // 二级类目 ID
    private Integer cat3Id;  // 三级类目 ID
    private Integer cat4Id;  // 四级类目 ID
    private Integer cat5Id;  // 五级类目 ID
    private Integer cat6Id;  // 六级类目 ID
    private Integer cat7Id;  // 七级类目 ID
    private Integer cat8Id;  // 八级类目 ID
    private Integer cat9Id;  // 九级类目 ID
    private Integer cat10Id; // 十级类目 ID

    private String sellOutProductId;                         // 售罄商品 ID
    private List<Long> showSizeTemplateIds;                  // 重点展示尺码表模板 ID 列表
    private List<CarouselImageI18nReq> carouselImageI18nReqs;// 商品轮播图多语言信息
    private List<Long> sizeTemplateIds;                      // 尺码表模板 ID 列表

    private List<ProductSpecPropertyReq> productSpecPropertyReqs;    // 商品规格属性
    private ProductWhExtAttrReq productWhExtAttrReq;                 // 仓配及供应链扩展属性
    private List<ProductSkcReq> productSkcReqs;                      // 商品 SKC 列表

    private ProductSaleExtAttrReq productSaleExtAttrReq;    // 销售侧扩展属性
    private Integer personalizationSwitch;                  // 是否支持定制模板，0 不支持，1 支持
    private ProductCustomReq productCustomReq;              // 商品海关信息
    private List<VehicleLibraryRelationReq> vehicleLibraryRelationReqList; // 车型库关联配置

    private String materialImgUrl;                          // 材质图
    private ProductComplianceStatementReq productComplianceStatementReq; // 合规声明签署信息

    // ==== 子结构定义 ====

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSemiManagedReq {
        private Integer semiLanguageStrategy; // 半托管材质图多语言策略
        private List<Integer> bindSiteIds;   // 绑定站点 ID 列表
        private Integer semiManagedSiteMode; // 半托管站点售卖模式
        private Map<String, Object> extra;   // 文档未稳定开放的灰度字段

        public void putExtra(String key, Object value) {
            if (this.extra == null) this.extra = new java.util.HashMap<>();
            this.extra.put(key, value);
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductCarouseVideoReq {
        private String vid;        // 视频素材 ID
        private String coverUrl;   // 封面图 URL
        private String videoUrl;   // 视频 URL
        private Integer width;     // 视频宽度
        private Integer height;    // 视频高度
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsLayerDecorationReq {
        private Integer floorId;   // 楼层 ID，null 表示新建
        private Integer goodsId;   // 商品 ID
        private String lang;       // 语言
        private String type;       // 楼层类型，image 或 text
        private Integer priority;  // 楼层优先级
        private List<GoodsLayerContent> contentList; // 楼层内容

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class GoodsLayerContent {
            private String imgUrl;                      // 图片 URL
            private TextModuleDetails textModuleDetails;// 文字模块详情
            private String backgroundColor;             // 背景色
            private Integer fontFamily;                 // 字体族
            private Integer fontSize;                   // 字号
            private String align;                       // 对齐方式：left/right/center/justify
            private String fontColor;                   // 字体颜色
            private Integer width;                      // 宽度
            private String text;                        // 文本内容
            private Integer height;                     // 高度
            private String key;                         // 内容标识，默认 DecImage
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class TextModuleDetails {
            private String backgroundColor; // 背景色
            private Integer fontFamily;     // 字体族
            private Integer fontSize;       // 字号
            private String align;           // 对齐方式
            private String fontColor;       // 字体颜色
            private Integer width;          // 宽度
            private String text;            // 文本内容
            private Integer height;         // 高度
            private String key;             // 模块标识
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductPropertyReq {
        private Integer vid;              // 属性值 ID，无则传 0
        private String valueUnit;         // 属性值单位，无则空串
        private Integer pid;              // 属性 ID
        private Integer templatePid;      // 模板属性 ID
        private String numberInputValue;  // 数值型输入值
        private String propValue;         // 属性值名称
        private String propName;          // 属性名称
        private Integer refPid;           // 引用属性 ID
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductOuterPackageImageReq {
        private String imageUrl; // 是
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductGuideFileReq {
        private String fileName;          // 文件名
        private Integer pdfMaterialId;    // PDF 素材 ID
        private List<String> languages;   // 适用语言列表
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductI18nReq {
        private String language;          // 语言
        private String productName;       // 该语言下的商品名称
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductWarehouseRouteReq {
        private List<RouteItem> targetRouteList;  // 目标路由列表
        private List<RouteItem> currentRouteList; // 当前路由列表

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class RouteItem {
            private List<Integer> siteIdList; // 站点 ID 列表
            private String warehouseId;       // 仓库 ID
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GoodsModelReq {
        private String modelProfileUrl;   // 模特资料图 URL
        private String sizeSpecName;      // 试穿尺码名称
        private Integer modelId;          // 模特 ID，新虚拟模特可不传
        private Integer sizeSpecId;       // 试穿尺码规格 ID
        private String modelWaist;        // 腰围
        private Integer modelType;        // 模特类型，1 服装，2 鞋
        private String modelName;         // 模特名称
        private String modelHeight;       // 身高
        private Integer modelFeature;     // 模特特征，1 真人，2 虚拟
        private String modelFootWidth;    // 脚宽
        private String modelBust;         // 胸围
        private String modelFootLength;   // 脚长
        private Integer tryOnResult;      // 试穿结果
        private String modelHip;          // 臀围
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
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductWhExtAttrReq {
        private List<ProductOriginCertFile> productOriginCertFiles; // 产地证明文件列表
        private String outerGoodsUrl;                              // 外部商品链接
        private ProductOrigin productOrigin;                       // 商品原产地信息
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductOriginCertFile {
        private String fileName; // 文件名
        private String fileUrl;  // 文件 URL
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductOrigin {
        private Long region2Id;             // 二级区域 ID
        private String region1ShortName;   // 一级区域简称，2 位国家/地区缩写
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSkcReq {
        private List<String> previewImgUrls; // SKU 预览图列表，非服饰通常可不传
        private List<ProductSkcCarouselImageI18nReq> productSkcCarouselImageI18nReqs; // SKC 轮播图多语言信息
        private String colorImageUrl; // 色块图 URL
        private List<MainProductSkuSpecReq> mainProductSkuSpecReqs; // 主销售规格列表
        private Integer isBasePlate; // 是否底板
        private List<ProductSkuReq> productSkuReqs; // SKU 列表
        private String extCode; // SKC 外部编码，空串表示无

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class MainProductSkuSpecReq {
            private Integer parentSpecId;   // 父规格 ID
            private String parentSpecName;  // 父规格名称
            private Integer specId;         // 规格 ID
            private String specName;        // 规格名称
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSkuReq {
        private String currencyType; // 币种，常见为 CNY 或 USD
        private ProductSkuMultiPackReq productSkuMultiPackReq; // 多件装信息
        private Integer mixedType; // 混装类型，1 不同商品，2 同商品不同规格
        private ProductSkuSuggestedPriceReq productSkuSuggestedPriceReq;     // 建议售价
        private List<SiteSupplierPrice> siteSupplierPrices;                  // 半托管站点供货价列表
        private ProductSkuUsSuggestedPriceReq productSkuUsSuggestedPriceReq; // 美国站建议售价
        private ProductSkuStockQuantityReq productSkuStockQuantityReq;       // 库存信息
        private List<ProductSkuThumbUrlI18nReq> productSkuThumbUrlI18nReqs;  // SKU 预览图多语言信息
        private ProductSkuAccessoriesReq productSkuAccessoriesReq;            // 配件信息
        private ProductSkuWhExtAttrReq productSkuWhExtAttrReq;               // SKU 仓配扩展属性
        private BigDecimal supplierPrice; // 供货价，半托管场景已逐步废弃
        private List<ProductSkuSpecReq> productSkuSpecReqs;                  // SKU 规格列表
        private String extCode; // SKU 外部编码，空串表示无
        private String thumbUrl; // SKU 预览图 URL

        // ==== 嵌套对象 ====

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuMultiPackReq {
            private Integer numberOfPieces;   // 件数，默认 1
            private Integer individuallyPacked; // 是否独立包装，-1 表示清空
            private ProductSkuNetContentReq productSkuNetContentReq; // 单件净含量，空对象表示清空
            private Integer mixedType; // 混装类型，1 不同商品，2 同商品不同规格
            private TotalNetContent totalNetContent; // 总净含量
            private Integer pieceNewUnitCode; // 新件数单位编码
            private Integer skuClassification; // SKU 分类
            private Integer numberOfPiecesNew; // 新件数
            private Integer pieceUnitCode; // 件数单位编码，如件/双/包

        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuNetContentReq {
            private Integer netContentUnitCode; // 净含量单位编码
            private Integer netContentNumber;   // 净含量数值，按文档要求放大 1000 倍
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class TotalNetContent {
            private Integer netContentUnitCode; // 总净含量单位编码
            private Integer netContentNumber;   // 总净含量数值，按文档要求放大 1000 倍
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuSuggestedPriceReq {
            private String suggestedPriceCurrencyType; // 建议售价币种
            private Integer suggestedPrice;            // 建议售价
            private String specialSuggestedPrice;      // 特殊建议售价文案
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class SiteSupplierPrice {
            private Integer siteId;          // 站点 ID
            private Integer supplierPrice;   // 站点供货价，单位分/美分
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuUsSuggestedPriceReq {
            private String suggestedPriceCurrencyType; // 美国站建议售价币种
            private Integer suggestedPrice;            // 美国站建议售价
            private String specialSuggestedPrice;      // 美国站特殊建议售价文案
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuStockQuantityReq {
            private List<WarehouseStockQuantityReq> warehouseStockQuantityReqs; // 仓库库存列表

            @Data @NoArgsConstructor @AllArgsConstructor
            public static class WarehouseStockQuantityReq {
                private Integer targetStockAvailable; // 目标可售库存
                private String warehouseId;           // 仓库 ID
                private Integer currentStockAvailable;// 当前可售库存
            }
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuThumbUrlI18nReq {
            private List<String> imgUrlList; // 图片列表，空列表表示删除，null 表示不变
            private String language;         // 语言
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuAccessoriesReq {
            private List<ProductSkuAccessory> productSkuAccessories; // 配件列表

            @Data @NoArgsConstructor @AllArgsConstructor
            public static class ProductSkuAccessory {
                private Integer vid;       // 配件属性值 ID
                private Integer num;       // 数量
                private Integer unitCode;  // 单位编码
                private String thumbUrl;   // 配件图片 URL
            }
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuWhExtAttrReq {
            private ProductSkuWeightReq productSkuWeightReq;                 // 重量信息
            private ProductSkuSameReferPriceReq productSkuSameReferPriceReq; // 同款参考价链接
            private ProductSkuSensitiveLimitReq productSkuSensitiveLimitReq; // 敏感品限制信息
            private ProductSkuVolumeReq productSkuVolumeReq;                 // 体积信息
            private List<ProductSkuBarCodeReq> productSkuBarCodeReqs;        // 条码列表
            private ProductSkuSensitiveAttrReq productSkuSensitiveAttrReq;   // 敏感属性信息
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuWeightReq {
            private String inputUnit;   // 输入重量单位
            private String inputValue;  // 输入重量值
            private Integer value;      // 标准重量值，单位 mg
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuSameReferPriceReq {
            private String url; // 同款参考价链接
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuSensitiveLimitReq {
            private Integer maxBatteryCapacityHp; // 最大电池容量，mWh
            private Integer maxBatteryCapacity;   // 最大电池容量，Wh
            private Integer maxLiquidCapacity;    // 最大液体容量，mL
            private Integer maxLiquidCapacityHp;  // 最大液体容量，μL
            private Integer maxKnifeLength;       // 最大刀刃长度，mm
            private Integer maxKnifeLengthHp;     // 最大刀刃长度，μm
            private KnifeTipAngle knifeTipAngle;  // 刀尖角度

            @Data @NoArgsConstructor @AllArgsConstructor
            public static class KnifeTipAngle {
                private Integer degrees; // 角度值
            }
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuVolumeReq {
            private String inputUnit;   // 输入体积单位
            private Integer len;        // 长边，标准单位 mm
            private String inputLen;    // 输入长边值
            private String inputHeight; // 输入高值
            private Integer width;      // 宽边，标准单位 mm
            private String inputWidth;  // 输入宽值
            private Integer height;     // 高边，标准单位 mm
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuBarCodeReq {
            private String code;      // 条码
            private Integer codeType; // 条码类型，1 EAN，2 UPC，3 ISBN
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuSensitiveAttrReq {
            private List<Integer> sensitiveTypes; // 敏感类型列表
            private Integer isSensitive;          // 是否敏感品，0 否，1 是
            private List<Integer> sensitiveList;  // 敏感属性编码列表
        }

        @Data @NoArgsConstructor @AllArgsConstructor
        public static class ProductSkuSpecReq {
            private Integer specId;          // 规格 ID
            private String parentSpecName;   // 父规格名称
            private Integer parentSpecId;    // 父规格 ID
            private String specName;         // 规格名称
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSkcCarouselImageI18nReq {
        private List<String> imgUrlList; // 图片列表，空列表表示删除，null 表示不变
        private String language;         // 语言
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSaleExtAttrReq {
        private Integer inventoryRegion;                    // 备货区域
        private ProductSecondHandReq productSecondHandReq;  // 二手商品信息
        private Boolean discreetShipping;                   // 是否隐私发货
        private CustomizedTechnologyReq customizedTechnologyReq; // 定制工艺信息
        private ProductNoChargerReq productNoChargerReq;   // 无充电器版本信息
        private List<String> ipCodes;                       // IP 编码列表
        private Map<String, Object> extra;                  // 文档新增灰度字段预留
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductSecondHandReq {
        private Boolean isSecondHand;    // 是否二手商品
        private Integer secondHandLevel; // 二手成色等级
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CustomizedTechnologyReq {
        private List<Integer> twiceType; // 二级工艺列表
        private Integer firstType;       // 一级工艺
        private Integer technologyType;  // 工艺类型
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductNoChargerReq {
        private List<Integer> noChargerProductIds; // 无充电器版本商品 ID 列表，空列表表示清空
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductCustomReq {
        private String goodsLabelName;    // 商品标签
        private Boolean isRecommendedTag; // 是否选择推荐标签
        private Map<String, Object> extra; // 海关扩展字段预留
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class VehicleLibraryRelationReq {
        private Integer vehicleLibraryId; // 车型库 ID
        private List<ProductPropValueDependencyReq> productPropValueDependencyReqList; // 属性值依赖配置列表
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductPropValueDependencyReq {
        private Integer propertyValueDependencyId1; // 属性值依赖 ID1
        private Integer propertyValueDependencyId2; // 属性值依赖 ID2
        private Integer propertyValueDependencyId3; // 属性值依赖 ID3
        private Integer propertyValueDependencyId4; // 属性值依赖 ID4
        private Integer propertyValueDependencyId5; // 属性值依赖 ID5
        private Integer propertyValueDependencyId6; // 属性值依赖 ID6
        private Integer propertyValueDependencyId7; // 属性值依赖 ID7
        private Integer propertyValueDependencyId8; // 属性值依赖 ID8
        private Integer propertyValueDependencyId9; // 属性值依赖 ID9
        private Integer propertyValueDependencyId10;// 属性值依赖 ID10
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ProductComplianceStatementReq {
        private String protocolVersion; // 协议版本号
        private String protocolUrl;     // 协议链接
    }
}
