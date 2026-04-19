# bg.glo.goods.add 上传供应商货品接口文档

## 1. 接口概览

- 接口标识：`bg.glo.goods.add`
- 接口名称：上传供应商货品
- 更新时间：`2026-03-24 18:14:33`
- 接口介绍：用于发布货品
- 请求地址：`/openapi/router`
- 数据存储地区：`CN`
- 权限包：`货品API组`
- 可获得/可申请此权限包的应用类型：`He uses type`、`Self use type`

## 2. 公共请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | STRING | 是 | API 接口名，固定传 `bg.glo.goods.add` |
| `app_key` | STRING | 是 | 已创建成功的应用标志 |
| `timestamp` | STRING | 是 | UNIX 时间戳（秒，10 位），要求当前时间前后 300 秒内 |
| `sign` | STRING | 是 | API 入参签名 |
| `data_type` | STRING | 否 | 返回数据格式，可选值固定为 `JSON` |
| `access_token` | STRING | 是 | 用户授权令牌 |
| `version` | STRING | 是 | API 版本，默认 `V1` |

## 3. 请求参数主层级

下面按“主要层次”整理业务参数，便于快速看懂这支接口的整体结构。

### 3.1 商品基础信息

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productName` | STRING | 是 | 商品名称 |
| `productI18nReqs` | LIST | 否 | 商品多语言信息 |
| `materialImgUrl` | STRING | 是 | 素材图 |
| `materialMultiLanguages` | LIST | 否 | 素材图多语言列表 |
| `carouselImageUrls` | LIST | 否 | 商品轮播图 |
| `carouselImageI18nReqs` | LIST | 否 | 商品轮播图多语言信息 |
| `productCarouseVideoReqList` | LIST | 否 | 轮播视频 |
| `goodsLayerDecorationReqs` | LIST | 否 | 商品详情装修楼层 |
| `copyFromProductId` | INTEGER | 否 | 复制来源商品 ID |
| `source` | INTEGER | 否 | 商品来源 |
| `sourceInvitationId` | INTEGER | 否 | 来源邀约 ID |
| `sellOutProductId` | STRING | 否 | 售罄商品 ID |
| `sellOutProductIdSrc` | INTEGER | 否 | 售罄商品源 ID |

### 3.2 类目与分类路径

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `cat1Id` | INTEGER | 是 | 一级类目 ID |
| `cat2Id` | INTEGER | 是 | 二级类目 ID，没有则传 `0` |
| `cat3Id` | INTEGER | 是 | 三级类目 ID，没有则传 `0` |
| `cat4Id` | INTEGER | 是 | 四级类目 ID，没有则传 `0` |
| `cat5Id` | INTEGER | 是 | 五级类目 ID，没有则传 `0` |
| `cat6Id` | INTEGER | 是 | 六级类目 ID，没有则传 `0` |
| `cat7Id` | INTEGER | 是 | 七级类目 ID，没有则传 `0` |
| `cat8Id` | INTEGER | 是 | 八级类目 ID，没有则传 `0` |
| `cat9Id` | INTEGER | 是 | 九级类目 ID，没有则传 `0` |
| `cat10Id` | INTEGER | 是 | 十级类目 ID，没有则传 `0` |

### 3.3 属性与规格

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productPropertyReqs` | LIST | 是 | 商品属性列表 |
| `productSpecPropertyReqs` | LIST | 是 | 商品规格属性列表 |
| `sizeTemplateId` | INTEGER | 否 | 尺码表模板 ID |
| `sizeTemplateIds` | LIST | 否 | 尺码表模板 ID 列表 |
| `showSizeTemplateIds` | LIST | 否 | 重点展示尺码表模板 ID 列表 |
| `goodsModelReqs` | LIST | 否 | 模特信息列表 |

### 3.4 SKU / SKC 信息

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productSkcReqs` | LIST | 是 | 商品 SKC 列表 |
| `personalizationSwitch` | INTEGER | 否 | 是否支持个性化模板，`0` 不支持，`1` 支持 |

### 3.5 仓配、库存与物流

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productWarehouseRouteReq` | OBJECT | 否 | 商品仓库路由信息 |
| `productShipmentReq` | OBJECT | 否 | 商品发货信息 |
| `productWhExtAttrReq` | OBJECT | 否 | 商品仓储/供应链扩展属性 |
| `productSaleExtAttrReq` | OBJECT | 否 | 商品销售侧扩展属性 |

### 3.6 包装、说明书、合规与附件

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productOuterPackageReq` | OBJECT | 否 | 商品外包装信息 |
| `productOuterPackageImageReqs` | LIST | 否 | 外包装图片 |
| `productGuideFileReqs` | LIST | 否 | 商品说明书文件（多语言） |
| `sensitiveTransNormalFileReqs` | LIST | 否 | 敏感商品转普货证明文件 |
| `productComplianceStatementReq` | OBJECT | 否 | 合规声明签署信息 |

### 3.7 特殊业务场景

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productSemiManagedReq` | OBJECT | 否 | 半托管商家信息 |
| `productCustomReq` | OBJECT | 否 | 商品定制/标签信息 |
| `vehicleLibraryRelationReqList` | LIST | 否 | 车型库关联配置 |

## 4. 关键嵌套对象

### 4.1 `productSemiManagedReq`

半托管相关配置。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `semiLanguageStrategy` | INTEGER | 否 | 半托管素材语言策略 |
| `bindSiteIds` | LIST\<INTEGER\> | 是 | 绑定站点列表 |
| `semiManagedSiteMode` | INTEGER | 否 | 半托管站点售卖模式 |

### 4.2 `productCarouseVideoReqList[]`

商品轮播视频。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `vid` | STRING | 是 | 视频 VID |
| `coverUrl` | STRING | 是 | 视频封面图 |
| `videoUrl` | STRING | 是 | 视频地址 |
| `width` | INTEGER | 是 | 视频宽度 |
| `height` | INTEGER | 是 | 视频高度 |

### 4.3 `goodsLayerDecorationReqs[]`

商品详情装修楼层。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `floorId` | INTEGER | 否 | 楼层 ID，空表示新增，否则表示更新 |
| `goodsId` | INTEGER | 否 | 商品 ID |
| `lang` | STRING | 是 | 语言 |
| `type` | STRING | 是 | 组件类型，`image` 或 `text` |
| `priority` | INTEGER | 是 | 楼层排序 |
| `contentList` | LIST | 是 | 楼层内容 |
| `key` | STRING | 是 | 楼层类型 Key，当前默认 `DecImage` |

`contentList[]` 主要字段：

- `imgUrl`：图片地址
- `text`：文本内容
- `width` / `height`：图片尺寸
- `textModuleDetails`：文本模块样式
- `textModuleDetails.backgroundColor`：背景色
- `textModuleDetails.fontFamily`：字体类型
- `textModuleDetails.fontSize`：字号
- `textModuleDetails.align`：对齐方式
- `textModuleDetails.fontColor`：字体颜色

### 4.4 `productPropertyReqs[]`

商品属性。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `vid` | INTEGER | 是 | 属性值 ID，没有则传 `0` |
| `valueUnit` | STRING | 是 | 属性值单位，没有则传空字符串 |
| `pid` | INTEGER | 是 | 属性 ID |
| `templatePid` | INTEGER | 是 | 模板属性 ID |
| `numberInputValue` | STRING | 否 | 数值输入 |
| `propValue` | STRING | 是 | 属性值 |
| `propName` | STRING | 是 | 属性名称 |
| `refPid` | INTEGER | 是 | 引用属性 ID |

### 4.5 `productSpecPropertyReqs[]`

商品规格属性。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `vid` | INTEGER | 是 | 属性值 ID，没有则传 `0` |
| `specId` | INTEGER | 是 | 规格 ID |
| `valueGroupId` | INTEGER | 是 | 属性值组 ID，没有则传 `0` |
| `parentSpecId` | INTEGER | 是 | 父规格 ID |
| `valueGroupName` | STRING | 是 | 属性组名称，没有则传空字符串 |
| `valueUnit` | STRING | 是 | 属性值单位，没有则传空字符串 |
| `pid` | INTEGER | 是 | 属性 ID |
| `templatePid` | INTEGER | 是 | 模板属性 ID |
| `numberInputValue` | STRING | 否 | 数值输入 |
| `propValue` | STRING | 是 | 属性值 |
| `propName` | STRING | 是 | 属性名称 |
| `refPid` | INTEGER | 是 | 引用属性 ID |

### 4.6 `productGuideFileReqs[]`

说明书文件。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileName` | STRING | 是 | 文件名 |
| `pdfMaterialId` | INTEGER | 是 | PDF 文件 ID |
| `languages` | LIST\<STRING\> | 是 | 语言列表 |

### 4.7 `productI18nReqs[]`

商品多语言标题。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `language` | STRING | 是 | 语言编码 |
| `productName` | STRING | 是 | 对应语言商品名称 |

### 4.8 `productWarehouseRouteReq`

商品仓库路由。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `targetRouteList` | LIST | 是 | 目标站点与仓库关系 |
| `currentRouteList` | LIST | 否 | 当前站点与仓库关系 |

`targetRouteList[]` / `currentRouteList[]` 结构：

- `siteIdList`：站点 ID 列表
- `warehouseId`：仓库 ID

### 4.9 `goodsModelReqs[]`

模特信息。

主要字段：

- `modelProfileUrl`：模特图
- `sizeSpecName`：试穿尺码规格名称
- `modelId`：模特 ID，新虚拟模特场景可不传
- `sizeSpecId`：试穿尺码规格 ID
- `modelType`：模特类型，`1` 服装模特，`2` 鞋类模特
- `modelFeature`：模特属性，`1` 真人模特，`2` 虚拟模特
- `modelName`：模特名称
- `modelHeight` / `modelWaist` / `modelBust` / `modelHip`：身体数据文案
- `modelFootLength` / `modelFootWidth`：脚部数据文案
- `tryOnResult`：试穿体验

### 4.10 `productShipmentReq`

发货信息。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `freightTemplateId` | STRING | 是 | 运费模板 ID |
| `shipmentLimitSecond` | INTEGER | 是 | 承诺发货时效，单位秒 |

### 4.11 `productWhExtAttrReq`

仓储/供应链扩展属性。

主要字段：

- `productOriginCertFiles[]`：产地证明文件
- `outerGoodsUrl`：站外商品链接，兜底可传空字符串
- `productOrigin`：商品产地信息
- `productOrigin.region1ShortName`：一级产地简称
- `productOrigin.region2Id`：二级产地 ID

### 4.12 `productSkcReqs[]`

商品 SKC 主结构。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `extCode` | STRING | 是 | SKC 外部编码，没有则传空字符串 |
| `productSkuReqs` | LIST | 是 | SKU 列表 |
| `mainProductSkuSpecReqs` | LIST | 是 | 主销规格列表 |
| `previewImgUrls` | LIST | 是 | 预览图列表，非服饰类目可按规则处理 |
| `productSkcCarouselImageI18nReqs` | LIST | 否 | SKC 轮播图多语言信息 |
| `isBasePlate` | INTEGER | 否 | 是否底板 |
| `colorImageUrl` | STRING | 否 | 色块图 |

`mainProductSkuSpecReqs[]` 字段：

- `specId`
- `parentSpecName`
- `parentSpecId`
- `specName`

### 4.13 `productSkcReqs[].productSkuReqs[]`

SKU 是整支接口里最核心的一层。

主要字段：

- `currencyType`：币种，常见 `CNY`、`USD`
- `thumbUrl`：SKU 预览图
- `extCode`：SKU 外部编码
- `supplierPrice`：供货价
- `siteSupplierPrices[]`：分站点申报价
- `productSkuSuggestedPriceReq`：建议售价
- `productSkuUsSuggestedPriceReq`：美国站建议售价
- `productSkuStockQuantityReq`：库存信息
- `productSkuThumbUrlI18nReqs[]`：SKU 预览图多语言信息
- `productSkuSpecReqs[]`：SKU 规格信息
- `productSkuAccessoriesReq`：配件信息
- `productSkuMultiPackReq`：多件装信息
- `productSkuWhExtAttrReq`：SKU 仓储扩展属性

### 4.14 `productSkuStockQuantityReq`

库存信息。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `warehouseStockQuantityReqs` | LIST | 是 | 出库仓库存列表 |

`warehouseStockQuantityReqs[]` 字段：

- `warehouseId`：仓库 ID
- `targetStockAvailable`：目标库存
- `currentStockAvailable`：当前库存

### 4.15 `productSkuSpecReqs[]`

SKU 规格列表。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `specId` | INTEGER | 是 | 规格 ID |
| `parentSpecName` | STRING | 是 | 父规格名称 |
| `parentSpecId` | INTEGER | 是 | 父规格 ID |
| `specName` | STRING | 是 | 规格名称 |

### 4.16 `productSkuWhExtAttrReq`

SKU 扩展属性主结构。

包含以下几个主要对象：

- `productSkuWeightReq`：重量信息
- `productSkuVolumeReq`：体积信息
- `productSkuBarCodeReqs[]`：条码信息
- `productSkuSensitiveAttrReq`：敏感属性声明
- `productSkuSensitiveLimitReq`：敏感属性限制
- `productSkuSameReferPriceReq`：同款参考链接

#### 4.16.1 `productSkuWeightReq`

- `value`：重量值，单位 `mg`
- `inputUnit`：输入单位
- `inputValue`：输入重量值

#### 4.16.2 `productSkuVolumeReq`

- `len`：最长边，单位 `mm`
- `width`：次长边，单位 `mm`
- `height`：最短边，单位 `mm`
- `inputLen` / `inputWidth` / `inputHeight`：输入值
- `inputUnit`：输入单位

#### 4.16.3 `productSkuBarCodeReqs[]`

- `code`：条码
- `codeType`：条码类型，`1` EAN，`2` UPC，`3` ISBN

#### 4.16.4 `productSkuSensitiveAttrReq`

- `isSensitive`：是否敏感，`0` 非敏感，`1` 敏感
- `sensitiveTypes`：敏感类型列表
- `sensitiveList`：敏感类型编码列表

#### 4.16.5 `productSkuSensitiveLimitReq`

常用字段：

- `maxBatteryCapacityHp` / `maxBatteryCapacity`
- `maxLiquidCapacityHp` / `maxLiquidCapacity`
- `maxKnifeLengthHp` / `maxKnifeLength`
- `knifeTipAngle.degrees`

### 4.17 `productSaleExtAttrReq`

销售侧扩展属性。

主要字段：

- `inventoryRegion`：备货区域
- `productSecondHandReq`：二手商品信息
- `discreetShipping`：隐私发货，成人类目重点关注
- `customizedTechnologyReq`：定制工艺
- `productNoChargerReq`：无充电器版本信息
- `ipCodes`：IP 编码列表

### 4.18 `productCustomReq`

商品标签与推荐标识。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `goodsLabelName` | STRING | 否 | 商品标签 |
| `isRecommendedTag` | BOOLEAN | 是 | 是否选择推荐标签 |

### 4.19 `vehicleLibraryRelationReqList[]`

车型库关联配置。

主要字段：

- `vehicleLibraryId`：车型库 ID
- `productPropValueDependencyReqList[]`：属性值依赖配置

### 4.20 `productComplianceStatementReq`

合规声明信息。

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `protocolVersion` | STRING | 是 | 协议版本号 |
| `protocolUrl` | STRING | 是 | 协议链接 |

## 5. 请求结构示例（主层级示意）

```json
{
  "type": "bg.glo.goods.add",
  "app_key": "your_app_key",
  "timestamp": "1710000000",
  "sign": "your_sign",
  "data_type": "JSON",
  "access_token": "your_access_token",
  "version": "V1",

  "productName": "商品名称",
  "materialImgUrl": "https://example.com/material.jpg",

  "cat1Id": 1,
  "cat2Id": 2,
  "cat3Id": 3,
  "cat4Id": 0,
  "cat5Id": 0,
  "cat6Id": 0,
  "cat7Id": 0,
  "cat8Id": 0,
  "cat9Id": 0,
  "cat10Id": 0,

  "productPropertyReqs": [
    {
      "pid": 1001,
      "templatePid": 1001,
      "vid": 0,
      "propName": "材质",
      "propValue": "棉",
      "valueUnit": "",
      "refPid": 0
    }
  ],

  "productSpecPropertyReqs": [
    {
      "specId": 2001,
      "parentSpecId": 0,
      "pid": 3001,
      "templatePid": 3001,
      "vid": 0,
      "propName": "颜色",
      "propValue": "黑色",
      "valueGroupId": 0,
      "valueGroupName": "",
      "valueUnit": "",
      "refPid": 0
    }
  ],

  "productShipmentReq": {
    "freightTemplateId": "123456",
    "shipmentLimitSecond": 86400
  },

  "productSkcReqs": [
    {
      "extCode": "SKC-001",
      "previewImgUrls": [
        "https://example.com/skc-preview-1.jpg"
      ],
      "mainProductSkuSpecReqs": [
        {
          "specId": 2001,
          "parentSpecId": 0,
          "parentSpecName": "颜色",
          "specName": "黑色"
        }
      ],
      "productSkuReqs": [
        {
          "extCode": "SKU-001",
          "currencyType": "CNY",
          "thumbUrl": "https://example.com/sku-thumb.jpg",
          "supplierPrice": 1999,
          "productSkuSpecReqs": [
            {
              "specId": 2101,
              "parentSpecId": 2002,
              "parentSpecName": "尺码",
              "specName": "M"
            }
          ],
          "productSkuStockQuantityReq": {
            "warehouseStockQuantityReqs": [
              {
                "warehouseId": "WH001",
                "targetStockAvailable": 100
              }
            ]
          },
          "productSkuWhExtAttrReq": {
            "productSkuWeightReq": {
              "value": 500000
            },
            "productSkuVolumeReq": {
              "len": 300,
              "width": 200,
              "height": 100
            },
            "productSkuSensitiveAttrReq": {
              "isSensitive": 0
            }
          }
        }
      ]
    }
  ]
}
```

## 6. 返回参数

### 6.1 返回主结构

| 参数名 | 类型 | 说明 |
| --- | --- | --- |
| `success` | BOOLEAN | 是否成功 |
| `errorCode` | INTEGER | 错误码 |
| `errorMsg` | STRING | 错误信息 |
| `result` | OBJECT | 业务结果 |

### 6.2 `result` 结构

| 参数名 | 类型 | 说明 |
| --- | --- | --- |
| `productId` | INTEGER | 商品 ID |
| `productSkcList` | LIST | SKC 列表 |
| `productSkuList` | LIST | SKU 列表 |

### 6.3 `result.productSkcList[]`

| 参数名 | 类型 | 说明 |
| --- | --- | --- |
| `productSkcId` | INTEGER | SKC ID |

### 6.4 `result.productSkuList[]`

| 参数名 | 类型 | 说明 |
| --- | --- | --- |
| `productSkuId` | INTEGER | SKU ID |
| `extCode` | STRING | SKU 外部编码 |
| `productSkcId` | INTEGER | 所属 SKC ID |
| `skuSpecList` | LIST | SKU 规格列表 |

`skuSpecList[]` 字段：

- `specId`
- `parentSpecName`
- `parentSpecId`
- `specName`

### 6.5 返回示例

```json
{
  "success": true,
  "errorCode": 0,
  "errorMsg": "",
  "result": {
    "productId": 123456789,
    "productSkcList": [
      {
        "productSkcId": 987654321
      }
    ],
    "productSkuList": [
      {
        "productSkuId": 111111,
        "extCode": "SKU-001",
        "productSkcId": 987654321,
        "skuSpecList": [
          {
            "specId": 2101,
            "parentSpecName": "尺码",
            "parentSpecId": 2002,
            "specName": "M"
          }
        ]
      }
    ]
  }
}
```

## 7. 常见错误码

以下为文档中较常用、排查价值较高的错误码整理。

| 错误码 | 错误描述 | 解决办法 |
| --- | --- | --- |
| `1000001` | 服务器开小差 | 一般为系统抖动，可重试或联系管理员 |
| `1000003` | 参数错误 | 结合具体参数报错原因排查 |
| `1000005` | 系统异常 | 尝试重试，仍失败请联系管理员 |
| `2000004` | 不合法的规格属性 | 检查 `specId` 与 `specName` 是否匹配 |
| `2000009` | 不合法的类目 | 检查类目 ID 是否正确 |
| `2000010` | 属性模板查询失败 | 可重试，或检查当前类目是否已配置属性模板 |
| `2000014` | 服饰类目 SKC 轮播图校验失败 | 图片需满足 `3:4`、`>=1340x1785`、`<=2M` |
| `2000017` | 素材图校验失败 | 图片需满足 `1:1`、`>=800x800`、`<=2M` |
| `2000018` | 非服饰类目商品轮播图校验失败 | 图片需满足 `1:1`、`>=800x800`、`<=2M` |
| `2000020` | 非服饰类目 SKU 预览图校验失败 | 图片需满足 `1:1`、`>=800x800`、`<=2M` |
| `2000021` | 图片格式校验失败 | 仅支持 `JPG`、`JPEG`、`PNG` |
| `2000031` | 服装类目 SKC 下价格需保持一致 | 调整同一 SKC 下的价格一致性 |
| `2000060` | 请选择正确的币种 | 检查币种入参是否合法 |
| `2000125` | 运费模板不存在 | 检查 `freightTemplateId` 是否正确 |
| `2000127` | 货品运费模板校验失败 | 检查模板、区域与运费配置 |
| `2000146` | 产地必填 | 补充商品产地信息 |
| `2000148` | 说明书未上传 | 按要求上传说明书 |
| `2000158` | URL 域名校验不通过或包含非法字符串 | 检查 URL 字段 |
| `2000184` | 合规声明未签署 | 补齐合规声明信息 |
| `2000204` | 分站点申报价格校验失败 | 检查站点信息与申报价配置 |
| `2000301` | 商详装修楼层 ID 不合法 | 检查装修楼层 ID |
| `2000302` | 商详装修楼层优先级不合法 | 楼层优先级不能重复 |
| `2000320` | 视频未转码 | 先完成视频转码 |
| `6000002` | 货品属性校验失败 | 根据具体原因检查属性入参 |
| `6000009` | 发布商品失败 | 根据具体错误原因排查 |
| `6000011` | 所选类目不合法 | 检查类目路径入参 |
| `6000012` | 尺码表校验失败 | 根据具体原因检查尺码表 |
| `6000081` | 库存信息校验失败 | 根据具体报错检查库存入参 |

## 8. 录入注意事项

### 8.1 图片与视频

- 服饰类目 SKC 图通常要求 `3:4`、宽高不低于 `1340x1785`、文件不超过 `2M`
- 非服饰主图、素材图、SKU 图通常要求 `1:1`、宽高不低于 `800x800`、文件不超过 `2M`
- 图片格式仅支持 `JPG`、`JPEG`、`PNG`
- 视频比例仅允许 `1:1`、`4:3`、`16:9`

### 8.2 体积与重量

- 商品体积必须满足：`最长边 >= 次长边 >= 最短边`
- SKU 重量字段 `value` 单位为 `mg`
- SKU 体积字段 `len`、`width`、`height` 单位为 `mm`

### 8.3 价格与库存

- 同一 SKC 下，服装类目价格通常要保持一致
- 币种必须与店铺支持币种匹配
- 分站点申报价场景下，要确保站点和经营站点一致
- 库存一般需要至少传出库仓 `warehouseId` 与目标库存

### 8.4 合规与特殊类目

- 某些类目要求必填产地、说明书、净含量、合规声明
- 成人类目需要关注 `discreetShipping`
- 敏感商品需要补充敏感属性和相关证明文件
- 半托管场景需要重点关注 `productSemiManagedReq`、分站点申报价、站点绑定信息

