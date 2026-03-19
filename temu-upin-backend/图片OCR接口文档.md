# 图片OCR 任务接口文档

本文档描述给 OCR 消费者(执行 OCR 的外部服务/脚本)调用的 2 个接口。

基础路径: `/api/ocr`

状态定义

- `execStatus`
  - `0` 待接受
  - `1` 执行中
  - `2` 执行完成
  - `3` 执行失败

图片类型

- `imageType`
  - `1` 轮播图
  - `2` 详情图
  - `3` SKU 图

## 1) 获取任务(Claim)

接口: `POST /api/ocr/tasks/claim`

说明:

- 每次只返回 1 条任务；当没有可用任务时返回 `data=null`。
- 成功领取后，该任务会从 `execStatus=0` 变更为 `execStatus=1`，并写入 `executorPublicIp`(如果传了)与 `taskStartedAt`。
- 同时会把对应 `ProductCollection.ocrStatus` 尝试更新为 `1`(OCR中)。

请求体(可选):

```json
{
  "publicIp": "1.2.3.4"
}
```

响应示例(成功领取到任务):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": 123,
    "spuId": 456,
    "productId": "1688xxxx",
    "imageType": 1,
    "imageUrl": "https://...",
    "execStatus": 1,
    "execResult": null,
    "failReason": null,
    "executorPublicIp": "1.2.3.4",
    "taskStartedAt": "2026-03-16T12:00:00",
    "taskFinishedAt": null,
    "filtered": false,
    "createdAt": "2026-03-16T11:59:00",
    "updatedAt": "2026-03-16T12:00:00"
  }
}
```

响应示例(没有任务):

```json
{
  "success": true,
  "message": "Success",
  "data": null
}
```

## 2) 任务完成(Complete)

接口: `POST /api/ocr/tasks/complete`

说明:

- 成功完成: 传 `taskId + ocrText`，任务会变更为 `execStatus=2` 并保存 `execResult=ocrText`。
- 失败完成: 传 `taskId + failReason`，任务会变更为 `execStatus=3` 并保存 `failReason`。
- 当成功完成时，会读取后台配置的“产品图过滤词”(过滤词管理)，若 `ocrText` 包含任意过滤词，则自动设置该任务 `filtered=true`。
- 每次完成会检查该 `spuId` 下是否所有 OCR 任务都已完成:
  - 全部成功 -> `ProductCollection.ocrStatus=2`
  - 存在失败且无 pending/running -> `ProductCollection.ocrStatus=3`
  - 仍有 pending/running -> `ProductCollection.ocrStatus=1`

请求体(成功示例):

```json
{
  "taskId": 123,
  "ocrText": "...OCR内容..."
}
```

请求体(失败示例):

```json
{
  "taskId": 123,
  "failReason": "download failed: timeout"
}
```

响应: 返回更新后的任务对象(同 claim 接口的 data 结构)。
