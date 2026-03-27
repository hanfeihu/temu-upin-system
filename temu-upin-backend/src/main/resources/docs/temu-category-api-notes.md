# TEMU 类目接口整理

本文整理项目内正在使用并已封装为 DTO 的两个 TEMU 类目接口。

## 1. bg.glo.goods.catsmandatory.get

- 接口名：`bg.glo.goods.catsmandatory.get`
- 中文名：类目必填信息接口
- 更新时间：`2025-08-27 16:57:43`
- 说明：类目必填信息查询接口
- 请求地址：`/openapi/router`

### 请求参数

- `leafCatId`：叶子类目 ID，必填
- `configItems`：类目配置项列表，文档标记为必填
- `productPropertyReqs`：货品属性列表，可选
  - `vid`
  - `valueUnit`
  - `pid`
  - `templatePid`
  - `numberInputValue`
  - `propValue`
  - `propName`
  - `refPid`

### 返回结构

顶层统一返回：

- `success`
- `errorCode`
- `errorMsg`
- `requestId`
- `result`

当前文档明确列出的 `result` 字段：

- `needGuideFile`：是否需要说明书文件

### 项目内 DTO

- 请求 DTO：`com.tminos.temu.upin.sdk.v2.category.CategoryMandatoryRequest`
- 结果 DTO：`com.tminos.temu.upin.sdk.v2.category.CategoryMandatoryResult`
- SDK 调用：`CategoryApiClient#getCategoryMandatory(CategoryMandatoryRequest)`

## 2. bg.goods.attrs.get

- 接口名：`bg.goods.attrs.get`
- 中文名：货品模板查询
- 更新时间：`2025-03-13 15:57:46`
- 说明：用于查询发布时的类目属性模板
- 请求地址：`/openapi/router`

### 请求参数

- `catId`：叶子类目 ID，必填
- `productCreateTime`：货品创建时间（毫秒时间戳），可选
- `supportedType`：渠道类型，可选
- `langList`：语言列表，可选

### 返回结构

顶层统一返回：

- `success`
- `errorCode`
- `errorMsg`
- `requestId`
- `result`

当前项目已建模的 `result` 重点字段：

- `inputMaxSpecNum`
- `chooseAllQualifySpec`
- `singleSpecValueNum`
- `properties`
  - `numberInputTitle`
  - `templatePropertyValueParentList`
  - `values`
  - `valueUnit`
  - `referenceType`
  - `pid`
  - `templatePid`
  - `required`
  - `inputMaxNum`
  - `propertyValueType`
  - `minValue`
  - `feature`
  - `valueRule`
  - `propertyChooseTitle`
  - `showType`
  - `parentTemplatePid`
  - `mainSale`
  - `parentSpecId`
  - `maxValue`
  - `lang2Name`
  - `chooseMaxNum`
  - `valuePrecision`
  - `showCondition`
  - `controlType`
  - `name`
  - `isSale`
  - `refPid`

### 项目内 DTO

- 结果 DTO：`com.tminos.temu.upin.sdk.v2.category.CategoryAttributesResult`
- SDK 调用：`CategoryApiClient#getCategoryAttributesResult(Integer)`

## 3. 当前封装策略

- SDK 层保留原始字符串方法，避免破坏需要原始 JSON 落库/透传的旧逻辑。
- 同时新增 DTO 化调用，供发布逻辑和类目逻辑优先使用。
- backend 内部涉及模板字段读取、主销售属性判断、父规格选择的逻辑，优先迁移到 DTO 调用。
