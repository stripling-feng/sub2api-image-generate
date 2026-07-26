# 图片生成接口文档

本文档只整理一个生成接口：`POST /api/images/generate`。

## 接口

| 方法 | 路径 | Content-Type | 说明 |
|---|---|---|---|
| `POST` | `/api/images/generate` | `application/json` | 根据模型生成图片 |

请求头：

```http
X-API-Key: <用户填写的 API Key>
```

成功返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "requestId": "string",
    "count": 1
  }
}
```

## 上传参考图

生成接口不再接收 `multipart/form-data`。需要参考图或 mask 时，先上传文件，拿到公网 URL 后再提交生成任务。

| 方法 | 路径 | Content-Type | 表单字段 | 说明 |
|---|---|---|---|---|
| `POST` | `/api/images/uploads` | `multipart/form-data` | `file` | 上传参考图或 mask |

生成请求字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `referenceImageUrls` | `string[]` | 上传后返回的参考图公网 URL |
| `maskUrl` | `string` | 上传后返回的 PNG mask 公网 URL |

## 通用字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `model` | `string` | 是 | 图片模型 |
| `prompt` | `string` | 是 | 提示词 |
| `negativePrompt` | `string` | 否 | 负向提示词 |
| `count` | `number` | 否 | 生成数量 |
| `parameters` | `object` | 否 | 动态参数 |
| `extraParams` | `object` | 否 | 额外动态参数 |

## 模型处理

| model | 请求类 | 处理类 |
|---|---|---|
| `gpt-image-2` | `ImageGenerateRequest` | `GptImage2AspectRatioRequestFormatter` |
| `gpt-image-2-1k` | `ImageGenerateRequest` | `GptImage2SizedRequestFormatter` |
| `gpt-image-2-2k` | `ImageGenerateRequest` | `GptImage2SizedRequestFormatter` |
| `gpt-image-2-4k` | `ImageGenerateRequest` | `GptImage2SizedRequestFormatter` |

## 示例

```json
{
  "model": "gpt-image-2-4k",
  "prompt": "未来城市夜景，电影感，高细节",
  "aspectRatio": "16:9",
  "size": "3840x2160",
  "quality": "high",
  "count": 1,
  "referenceImageUrls": [
    "https://example.com/uploads/image-references/ref.png"
  ],
  "maskUrl": "https://example.com/uploads/image-references/mask.png"
}
```
