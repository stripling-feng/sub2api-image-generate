# 视频生成接口文档

本文档只整理一个生成接口：`POST /api/videos/generate`。

## 接口

| 方法 | 路径 | Content-Type | 说明 |
|---|---|---|---|
| `POST` | `/api/videos/generate` | `application/json` | 根据模型生成视频 |

请求头：

```http
X-API-Key: <用户填写的 API Key>
```

成功返回：

```json
{
  "requestId": "string",
  "count": 1
}
```

## 上传素材

生成接口不再接收 `multipart/form-data`。图片、视频、音频、首帧、尾帧都需要先上传，拿到公网 URL 后再提交生成任务。

| 方法 | 路径 | Content-Type | 表单字段 | 说明 |
|---|---|---|---|---|
| `POST` | `/api/videos/uploads` | `multipart/form-data` | `file` | 上传图片、视频或音频素材 |

生成请求字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `referenceImageUrls` | `string[]` | 上传后返回的参考图公网 URL |
| `referenceVideoUrls` | `string[]` | 上传后返回的参考视频公网 URL |
| `referenceAudioUrls` | `string[]` | 上传后返回的参考音频公网 URL |
| `firstFrameUrl` | `string` | 上传后返回的首帧图片公网 URL |
| `lastFrameUrl` | `string` | 上传后返回的尾帧图片公网 URL |

## 通用字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `model` | `string` | 是 | 视频模型 |
| `prompt` | `string` | 是 | 提示词，最长 5000 字符 |
| `count` | `number` | 否 | 生成数量，范围 `1-4` |
| `duration` | `number` | 否 | 视频秒数 |
| `aspectRatio` | `string` | 否 | 画幅比例 |
| `resolution` | `string` | 否 | 分辨率 |
| `generateAudio` | `boolean` | 否 | 是否生成音频 |

## 模型字段

| model | 主要素材字段 | 说明 |
|---|---|---|
| `seedance-*` | `referenceImageUrls`、`referenceVideoUrls`、`referenceAudioUrls`、`firstFrameUrl`、`lastFrameUrl` | 支持多模态素材，首尾帧必须符合模型规则 |
| `grok-video` | `referenceImageUrls`、`referenceVideoUrls` | 图片会组装为上游 `image_urls` |
| `grok-video-1.5` | `referenceImageUrls` | 必须且只能传 1 张参考图 |
| `omni-fast*` | `referenceImageUrls`、`firstFrameUrl`、`lastFrameUrl` | 多参考图会组装为上游 `input_reference` |
| `omni-v2v*` | `referenceImageUrls`、`referenceVideoUrls` | 支持参考图和参考视频 |

## 示例

```json
{
  "model": "seedance-2.0",
  "prompt": "未来城市夜景，霓虹灯，电影感运镜",
  "count": 1,
  "duration": 8,
  "aspectRatio": "16:9",
  "resolution": "720p",
  "generateAudio": true,
  "referenceImageUrls": [
    "https://example.com/uploads/video-materials/ref.png"
  ],
  "referenceVideoUrls": [
    "https://example.com/uploads/video-materials/ref.mp4"
  ],
  "referenceAudioUrls": [
    "https://example.com/uploads/video-materials/ref.mp3"
  ]
}
```
