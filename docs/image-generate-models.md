# 图片生成接口文档

## 认证

所有接口都需要在请求头中传入 API Key。

| Header | 示例 |
| --- | --- |
| `X-API-Key` | `<api-key>` |

## 提交生成任务

| 方法 | 路径 | Content-Type |
| --- | --- | --- |
| `POST` | `/api/images/generate` | `application/json` |

### 请求字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `model` | string | 模型名称。 |
| `prompt` | string | 图片生成描述。 |
| `async` | boolean | 是否异步生成。 |
| `count` | number | 生成数量。 |
| `images` | string[] | 参考图 URL 数组；无参考图时传空数组。 |
| `size` | string | 图片尺寸。 |
| `aspect_ratio` | string | 图片比例。 |
| `quality` | string | 图片质量；未指定时可不传。 |

### 请求示例

`gpt-image-2`

```json
{
  "model": "gpt-image-2",
  "prompt": "一张干净的产品海报，白色背景，主体清晰",
  "async": true,
  "count": 1,
  "size": "1024x1024",
  "images": []
}
```

`gpt-image-2-4k`

```json
{
  "model": "gpt-image-2-4k",
  "prompt": "根据参考图生成一张电商主图，保持主体一致，背景更明亮",
  "async": true,
  "count": 2,
  "aspect_ratio": "1:1",
  "quality": "high",
  "images": [
    "https://example.com/reference.png"
  ]
}
```

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "requestId": "1f2a3b4c5d6e7f80",
    "count": 2
  }
}
```

### 响应字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `requestId` | string | 本次生成请求 ID。 |
| `count` | number | 本次提交的生成数量。 |

## 查询生成结果

| 方法 | 路径 |
| --- | --- |
| `GET` | `/api/images/{requestId}` |

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "request_id": "1f2a3b4c5d6e7f80",
      "request_index": 1,
      "request_total": 2,
      "status": "PENDING",
      "progress": 0,
      "errorMessage": null,
      "url": null
    }
  ]
}
```

### 响应字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `request_id` | string | 本次生成请求 ID。 |
| `request_index` | number | 当前图片序号。 |
| `request_total` | number | 本次请求的图片总数。 |
| `status` | string | 当前状态。 |
| `progress` | number | 当前进度。 |
| `errorMessage` | string/null | 失败原因；无失败时为 `null`。 |
| `url` | string/null | 图片地址；未生成完成或无图片时为 `null`。 |
