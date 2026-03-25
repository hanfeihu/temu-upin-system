# TEMU 发布成功案例沉淀与排障经验

## 为什么要单独沉淀成功案例

TEMU 发布的行为不是只看字段是否齐全，很多类目还有隐含约束：

- 主销售属性选择会受类目习惯影响
- 同一类目下 `mainProductSkuSpecReqs` 的结构不一定能按直觉填写
- 价格字段、规格字段、分组字段之间存在组合约束

因此，成功案例不能只放在通用运行日志里，必须单独沉淀为可检索的数据资产。当前系统已经新增成功案例归档表，用来单独保存：

- spuId
- 产品名 / 产品ID
- TEMU 类目链路 ID / 类目名
- 发布运行 ID
- goodsId
- 完整 `AddGloGoodsRequest`
- 完整响应原文

查询接口：

- `GET /api/platform/temu-publish/success-cases`
- `GET /api/platform/temu-publish/success-cases/{id}`

可选查询参数：

- `spuId`
- `temuCatid`

## 这次最有价值的成功样本

成功样本来自同类目基线：

- 类目链路：`27011,28946,29473,29513,29520`
- 同类目成功发布样本：spu 177，对应成功运行 run 123

这个样本给出的关键信号：

- 主销售属性使用的是 `型号`，不是 `颜色`
- `productSpecPropertyReqs` 和 `productSkuSpecReqs` 中的规格值要保留原始业务语义，不能被 AI 擅自缩写
- `mainProductSkuSpecReqs` 不是填真实型号值，而是空占位：

```json
[{"parentSpecId":0,"parentSpecName":"","specId":0,"specName":""}]
```

- 价格只传 `siteSupplierPrices`，`supplierPrice` 必须为空

## 这次排障过程中确认的失败模式

### 1. createSpec failed for 紫水滴

根因不是 TEMU 真不支持该值，而是本地代码错误解析了 `createSpec` 返回，拿整段 JSON 去按整数解析，导致规格创建失败。

结论：

- 先验证 SDK/解析链路是否正确，再怀疑规格值本身

### 2. 主销售属性不合法

这类报错不能只理解成“值不合法”，更常见的是结构不符合该类目既有范式。已确认的触发原因包括：

- 主销售属性维度选错，错误用了 `颜色`
- `mainProductSkuSpecReqs` 与 `productSkuSpecReqs` 的职责混淆
- SKC 分组与 SKU 规格组合关系不匹配
- 对“型号/规格类”类目，错误把真实值直接塞进 `mainProductSkuSpecReqs`
- 对叶子类目 `29271`（女士贝雷帽），当只有 `颜色` 变化、其余规格都是常量时，不应该按颜色拆多个 SKC，而应该生成 `1 个空主规格 SKC + 多个 SKU`

结论：

- 新类目第一次发布时，优先找同类目成功案例比对结构，而不是只看字段名语义
- 对 `29271` 这类帽子类目，`颜色` 只能留在 `productSkuSpecReqs`，不能继续承担 `mainProductSkuSpecReqs` / SKC 分组职责

### 3. 当前商品仅允许填写分站点申报价格

根因是同一个 SKU 同时提交了：

- `supplierPrice`
- `siteSupplierPrices`

结论：

- 当前这类商品只保留 `siteSupplierPrices`
- 生成任务草案和最终发布请求时，都要确保 `supplierPrice` 被清理掉

### 4. SKU Sales Specification Attribute Value List Duplicated

这类错误对应本次 `run 441 / spu 184`：

- TEMU 返回：`errorCode=1000003`
- 错误文案：`参数错误：参数错误：SKU Sales Specification Attribute Value List Duplicated`

这次不是 `mainProductSkuSpecReqs` 超过 1 条，也不是价格字段错误，而是 **同一个 SKC 下面挂了多条 SKU，但这些 SKU 的 `productSkuSpecReqs` 本质上是同一组销售属性值**。

`run 441` 的请求里，整组商品只有一个销售属性：

- `parentSpecName=尺码`
- `specName=均码`

但 `productSkcReqs[0].productSkuReqs` 下面却提交了多条 SKU，而且它们的销售属性列表几乎完全相同：

- 大部分 SKU 都是 `尺码=均码`
- 其中一条写成了 `尺码=均 码`

这个“`均码` / `均 码`”看起来像不同值，但对 TEMU 来说通常会按规范化后的销售属性值比较，空格变体不能作为真正的 SKU 区分维度。所以结果仍然是：**一个 SKC 内出现多条 SKU，销售属性组合重复**。

结论：

- 一个 `productSkcReq` 下的每条 `productSkuReq`，其 `productSkuSpecReqs` 必须是唯一组合，不能重复
- 不能把图片不同、库存不同、编码不同，当成创建多个 SKU 的理由；如果销售属性相同，它们在 TEMU 看来仍然是同一个 SKU
- `均码`、`均 码`、大小写差异、前后空格这类“文本变体”不能拿来充当新的销售属性值
- 如果整组商品真实只有 `尺码=均码` 这一种销售属性，就只能保留一个 SKU；如果确实有多条 SKU，必须补出真正能区分它们的销售属性维度

补充：`run 443` 证明“重新执行主销售属性推理任务”并不一定会自动修好这类问题。如果任务生成代码仍然把 AI 已经识别出的多维结果压回成单维，那么重跑后落库的草案依旧会是：

- `dimensions=尺码`
- `skuPlans=8`
- `skcPlans=1`

也就是 8 条 SKU 继续共用同一组 `尺码=均码` 销售属性，发布时仍然会报重复。真正要修的是 **任务生成阶段的维度归一化逻辑**，而不是只重复点击“重新执行”。

## 已经固化到代码里的规则

### 成功归档

发布成功后，系统会把完整成功样本单独写入成功案例表，而不是只写通用运行日志。

### 主销售属性草案优先级

发布时优先读取已持久化的主销售属性草案：

- `mainProductSkuSpecReqs`
- `productSpecPropertyReqs`
- `productSkuReqs`

这样可以避免每次重新推断，减少回归风险。

### 价格字段清洗

发布路径已经针对存量草案补充清洗逻辑，避免同时带出 `supplierPrice` 和 `siteSupplierPrices`。

### 这次 spu 184 最终成功链路

这次 `spu 184 / leafCatId=29271 / 女士贝雷帽` 最终成功，不是靠一次“大兜底”修出来的，而是按失败阶段逐层收敛：

1. 先修任务层维度归类
	- 不能把所有 SKU 维度都抬成 `mainProductSkuSpecReqs`
	- 只有真实变化的维度才有资格进入主销售属性

2. 再修类目特例结构
	- 对 `29271`，当只有 `颜色` 变化、其他维度恒定时，最终不能按颜色拆多个 SKC
	- 正确结构是：`1 个 SKC + 空 mainProductSkuSpecReqs 占位 + 多个 SKU`

3. 再修发布层物化草案
	- 空占位 `parentSpecId=0/specId=0` 不能再按真实规格去映射
	- 如果草案已经明确是空主规格单 SKC，发布层不能在 regroup 时再把 `productSkuSpecReqs` 回抬成多个主销售属性

4. 最后修库存上限
	- 主销售属性结构正确后，TEMU 才继续暴露下一层校验：库存上限
	- 源库存不能直接透传，最终通过 `sku.maxStock` 做统一钳制后发布成功

这个顺序很重要：

- 如果主销售属性结构还错，TEMU 根本不会走到库存校验
- 如果库存钳制先做了，但主销售属性仍错，发布一样不会成功

### 主销售属性任务去重与维度约束

当前主销售属性任务生成链路已经补上两层约束：

- 先按同类目成功案例提取允许的维度结构与主销售属性数量
- 再对 `productSkuSpecReqs` / `mainProductSkuSpecReqs` 按 `parentSpecName + specName` 去重

这样可以避免再生成：

- 同一个 SKC 里重复的主销售属性
- 同维度同值被重复落到草案里
- 仅靠文本空格差异制造“伪不同”销售属性值

### 空主规格规则的生效边界

这次新增的空主规格逻辑，不是全局对所有 `颜色` 类目生效，而是刻意收窄到了可验证条件：

- 叶子类目是 `29271`
- 主维度候选只有 1 个
- 该主维度属于外观型维度（如 `颜色/花色/图案`）
- 其他维度在 SKU 之间不发生真实变化

只有同时满足这几个条件，才会把草案改写成：

- `mainProductSkuSpecReqs = [{"parentSpecId":0,"parentSpecName":"","specId":0,"specName":""}]`
- `skcPlans = 1`
- 所有颜色差异只保留在 `productSkuSpecReqs`

这条边界是为了避免把 `29271` 的类目范式误推广到其他类目。

结论：

- `空主规格` 是“类目特例规则”，不是“颜色类目通用规则”
- 其他类目仍应优先遵循：成功案例结构 + 真实变化维度 + 发布请求 diff

### 库存上限规则

这次还确认了一个独立于主销售属性的约束：

- TEMU 对单 SKU 的可提交库存存在上限
- `run 456` 中 `均码-红底黑叶--黑辫子` 因为提交了 `45019`，被拒绝并提示最大上限 `10842`

因此当前代码已经改成：

- 优先读取平台配置 `sku.maxStock`
- 默认上限使用 `10842`
- 发布时 `targetStockAvailable` 取 `min(源库存, sku.maxStock)`

结论：

- 高库存是发布约束，不是源数据真值
- 发布请求中的库存应视为“可提交库存”，而不是“原始库存直传”

### 类目模板驱动的属性序列化

这次 `spuId=149 / runId=412` 的成功，核心不是给 `pid=1776` 写了硬编码，而是把属性序列化改成了按类目模板结构判断：

- 如果模板属性 `values = null`，且本质上是自由输入型属性，就按 `freeText/propValue` 发送
- 只有同时满足“存在可选值”并且模板显示还需要数值录入（例如 `numberInputTitle` 有值，或 `controlType = 16`）时，才发送 `numberInputValue`

这条规则解决了两个之前互相打架的错误：

- `run 409`：`平方克重（g/㎡）` 被发成空值，TEMU 返回 `Base Property Value Cannot Be Empty`
- `run 411`：`平方克重（g/㎡）` 被同时带了 `propValue=1` 和 `numberInputValue=1`，TEMU 返回 `数值录入信息必须为空`

最终成功的原因是：

- `平方克重（g/㎡）` 按纯输入属性发送，只保留 `propValue=1`
- 不再把它错误地当成“选项 + 数值”的复合属性

对应的 live template 证据（leafCatId=`18954`）：

- `pid=1776`
- `name=平方克重（g/㎡）`
- `values=null`
- `controlType=0`
- `propertyValueType=1`
- `valueRule=2`

这说明它是“必填的纯输入属性”，不是“必填的复合属性”。

需要注意：当前代码里仍然保留了少量兼容性兜底，例如 `成分` 在缺少数值比例时会补默认值；这属于特定失败模式的保护逻辑，不等于本次 `pid=1776` 的修复方式。`pid=1776` 这次成功，依赖的是模板驱动判断，不是写死类目 ID 或属性 ID 的值映射。

### 发布前自动补建并执行 TEMU 类目属性 AI 填写任务

当前发布链路已经补上了一个自动兜底：

- 如果商品在 `TEMU 类目属性AI填写任务` 里还没有当前类目的可用任务结果，发布时会先自动创建一条任务
- 如果任务还没跑出可用 `resultJson`，发布时会自动执行一次
- 如果商品当前 `temu_attributes` 为空，且任务执行成功，发布会自动把任务结果回填到商品属性里，再继续发布

这样做的目的有两个：

- 保证发布依赖的 AI 属性结果链路是闭环的
- 避免“任务页没建任务 / 没执行任务，结果发布时直接因为属性为空失败”

为了避免覆盖人工修正，自动回填只在商品当前 `temu_attributes` 为空时发生；如果商品属性已经手工保存过，发布不会用任务结果强行覆盖。

## 后续类目发布建议

发布一个新类目前，先按下面顺序检查：

1. 先查同 `temuCatid` 的成功案例
2. 对比主销售属性维度是否一致
3. 对比 `mainProductSkuSpecReqs` 是否是真值还是空占位
4. 对比 `productSpecPropertyReqs` 的规格值是否保留原始语义
5. 确认价格字段只保留当前类目允许的那一种表达方式
6. 对比属性模板中该字段到底属于：
	- 纯输入属性
	- 单选/多选属性
	- 选项 + 数值的复合属性
7. 检查同一个 `productSkcReq` 下，所有 `productSkuReqs[*].productSkuSpecReqs` 是否真的唯一，不要接受 `均码` / `均 码` 这种伪差异

如果以后同类目再次失败，优先把失败请求和成功案例做逐字段 diff，不要只盯着错误文案。

## 给以后改代码时的提醒

如果后面其他类目再次发布失败，优先遵守下面这条判断顺序，不要直接复用 `29271` 的处理方式：

1. 先看是否有同类目成功案例
2. 再看失败请求里的 `mainProductSkuSpecReqs`、`productSkuSpecReqs`、`productSkcReqs` 结构差异
3. 先判断这是“通用结构问题”还是“类目特例问题”
4. 只有拿到证据后，才新增叶子类目级规则

这次能成功，是因为我们把规则拆成了两层：

- 通用层：真实变化维度、去重、空占位映射保护、库存上限钳制
- 特例层：仅对 `29271` 生效的空主规格单 SKC 规则

后续如果新增别的类目特例，尽量继续保持这个模式，不要把某个类目的成功范式直接写成全局默认。