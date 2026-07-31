# 视频工作台接口文档

本文档只说明以下两个接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/videos/generate` | 提交异步视频生成任务 |
| `GET` | `/api/videos/results/{requestId}` | 查询一次生成请求下的全部任务及结果 |

## 认证

两个接口都需要在请求头中传入 API Key：

```http
X-API-Key: <api-key>
```

提交生成任务时还需要指定：

```http
Content-Type: application/json
```

参考图片、视频、音频及首尾帧字段均接收公网 HTTPS URL。

## 提交生成任务

### 通用字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `model` | string | 是 | - | 模型标识，支持的值见各模型分组 |
| `prompt` | string | 是 | - | 视频描述，不能为空，最长 5000 个字符 |
| `count` | integer | 否 | `1` | 本次创建的任务数，范围 `1-4` |
| `duration` | integer | 否 | 依模型而定 | 视频时长，单位为秒 |
| `aspectRatio` | string | 否 | `16:9` | 画面比例 |
| `resolution` | string | 否 | `720p` | 分辨率 |

不同模型支持的素材字段和参数范围不同。不要向模型发送其分组中未列出的素材字段。

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "requestId": "b28f13de69984c1a9e84fd4f8768d6a0",
    "count": 1
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.requestId` | string | 本次生成请求 ID，用于查询结果 |
| `data.count` | integer | 本次创建的任务数 |

## Seedance

### 可用模型

- `seedance-2.0`
- `seedance-2.0-fast`
- `seedance-2.0-mini`

这三个模型的接口字段、默认值和校验规则相同，仅 `model` 值不同。

### 参数规则

| 字段 | 类型 | 必填 | 默认值 | 限制 |
| --- | --- | --- | --- | --- |
| `duration` | integer | 否 | `8` | `4-15` 之间的整数 |
| `aspectRatio` | string | 否 | `16:9` | `16:9`、`9:16`、`1:1`、`21:9`、`3:4`、`4:3` |
| `resolution` | string | 否 | `720p` | `480p`、`720p` |
| `generateAudio` | boolean | 否 | `true` | 是否生成原生音频 |
| `referenceImageUrls` | string[] | 否 | `[]` | 最多 4 张参考图片 |
| `referenceVideoUrls` | string[] | 否 | `[]` | 最多 3 个参考视频 |
| `referenceAudioUrls` | string[] | 否 | `[]` | 最多 1 个参考音频 |
| `firstFrameUrl` | string | 否 | - | 首帧图片 URL，必须和 `lastFrameUrl` 同时传入 |
| `lastFrameUrl` | string | 否 | - | 尾帧图片 URL，必须和 `firstFrameUrl` 同时传入 |

首尾帧模式与其他参考素材互斥：传入 `firstFrameUrl` 和 `lastFrameUrl` 时，不得再传 `referenceImageUrls`、`referenceVideoUrls` 或 `referenceAudioUrls`。

### 多素材请求示例

```json
{
  "model": "seedance-2.0",
  "prompt": "夜晚城市街道，参考图片中的人物向前行走，镜头缓慢跟随",
  "count": 1,
  "duration": 8,
  "aspectRatio": "16:9",
  "resolution": "720p",
  "generateAudio": true,
  "referenceImageUrls": [
    "https://cdn.example.com/reference/person.png"
  ],
  "referenceVideoUrls": [
    "https://cdn.example.com/reference/motion.mp4"
  ],
  "referenceAudioUrls": [
    "https://cdn.example.com/reference/ambient.mp3"
  ]
}
```

### 首尾帧请求示例

```json
{
  "model": "seedance-2.0-fast",
  "prompt": "从日出过渡到正午，保持建筑主体稳定",
  "count": 1,
  "duration": 6,
  "aspectRatio": "16:9",
  "resolution": "720p",
  "generateAudio": false,
  "firstFrameUrl": "https://cdn.example.com/reference/first.png",
  "lastFrameUrl": "https://cdn.example.com/reference/last.png"
}
```

## Grok Video

### 可用模型

- `grok-video`

### 参数规则

| 字段 | 类型 | 必填 | 默认值 | 限制 |
| --- | --- | --- | --- | --- |
| `duration` | integer | 否 | `8` | 仅支持 `4`、`6`、`8`、`10`、`12`、`15` |
| `aspectRatio` | string | 否 | `16:9` | `1:1`、`16:9`、`9:16`、`4:3`、`3:4`、`3:2`、`2:3` |
| `resolution` | string | 否 | `720p` | `480p`、`720p` |
| `referenceImageUrls` | string[] | 否 | `[]` | 最多 7 张参考图片 |
| `referenceVideoUrls` | string[] | 否 | `[]` | 最多 1 个参考视频 |

限制：

- 传入 2 张及以上参考图片时，`duration` 不能超过 10 秒。
- 不支持参考音频、首尾帧或原生音频开关。

### 请求示例

```json
{
  "model": "grok-video",
  "prompt": "参考角色在雪地中奔跑，镜头从侧面平稳跟随",
  "count": 1,
  "duration": 10,
  "aspectRatio": "16:9",
  "resolution": "720p",
  "referenceImageUrls": [
    "https://cdn.example.com/reference/character-front.png",
    "https://cdn.example.com/reference/character-side.png"
  ]
}
```

## Grok Video 1.5

### 可用模型

- `grok-video-1.5`

### 参数规则

| 字段 | 类型 | 必填 | 默认值 | 限制 |
| --- | --- | --- | --- | --- |
| `duration` | integer | 否 | `8` | 仅支持 `4`、`6`、`8`、`10`、`12`、`15` |
| `aspectRatio` | string | 否 | `16:9` | 仅支持 `16:9`、`9:16` |
| `resolution` | string | 否 | `720p` | `480p`、`720p` |
| `referenceImageUrls` | string[] | 是 | - | 必须且只能传入 1 张参考图片 |

不支持参考视频、参考音频、首尾帧或原生音频开关。

### 请求示例

```json
{
  "model": "grok-video-1.5",
  "prompt": "保持参考图人物特征，人物转身看向镜头，背景轻微移动",
  "count": 1,
  "duration": 8,
  "aspectRatio": "9:16",
  "resolution": "720p",
  "referenceImageUrls": [
    "https://cdn.example.com/reference/portrait.png"
  ]
}
```

## Omni Fast

### 可用模型

- `omni-fast`
- `omni-fast-no-water`

两个模型的接口字段、默认值和校验规则相同，仅 `model` 值及对应的水印版本不同。

### 参数规则

| 字段 | 类型 | 必填 | 默认值 | 限制 |
| --- | --- | --- | --- | --- |
| `duration` | integer | 否 | `10` | 固定为 `10` |
| `aspectRatio` | string | 否 | `16:9` | 仅支持 `16:9`、`9:16` |
| `resolution` | string | 否 | `720p` | 固定为 `720p` |
| `referenceImageUrls` | string[] | 否 | `[]` | 最多 5 张参考图片 |
| `firstFrameUrl` | string | 否 | - | 首帧图片 URL |
| `lastFrameUrl` | string | 否 | - | 尾帧图片 URL |

参考图片模式与首尾帧模式互斥：只要传入 `firstFrameUrl` 或 `lastFrameUrl`，就不能再传 `referenceImageUrls`。不支持参考视频、参考音频或原生音频。

### 多图请求示例

```json
{
  "model": "omni-fast",
  "prompt": "融合参考图片中的人物、服装和场景，生成自然的行走镜头",
  "count": 1,
  "duration": 10,
  "aspectRatio": "16:9",
  "resolution": "720p",
  "referenceImageUrls": [
    "https://cdn.example.com/reference/person.png",
    "https://cdn.example.com/reference/outfit.png",
    "https://cdn.example.com/reference/location.png"
  ]
}
```

### 首尾帧请求示例

```json
{
  "model": "omni-fast-no-water",
  "prompt": "镜头从室内平滑过渡到室外花园",
  "count": 1,
  "duration": 10,
  "aspectRatio": "16:9",
  "resolution": "720p",
  "firstFrameUrl": "https://cdn.example.com/reference/indoor.png",
  "lastFrameUrl": "https://cdn.example.com/reference/garden.png"
}
```

## Omni V2V

### 可用模型

- `omni-v2v`
- `omni-v2v-no-water`

两个模型的接口字段、默认值和校验规则相同，仅 `model` 值及对应的水印版本不同。参考图片和参考视频均为可选，可单独使用，也可同时使用。

### 参数规则

| 字段 | 类型 | 必填 | 默认值 | 限制 |
| --- | --- | --- | --- | --- |
| `duration` | integer | 否 | `10` | 固定为 `10` |
| `aspectRatio` | string | 否 | `16:9` | 仅支持 `16:9`、`9:16` |
| `resolution` | string | 否 | `720p` | 固定为 `720p` |
| `referenceImageUrls` | string[] | 否 | `[]` | 最多 2 张参考图片 |
| `referenceVideoUrls` | string[] | 否 | `[]` | 最多 2 个参考视频 |

不支持参考音频、首尾帧或原生音频。

### 请求示例

```json
{
  "model": "omni-v2v-no-water",
  "prompt": "保留参考视频的运镜和节奏，使用参考图中的角色与服装",
  "count": 1,
  "duration": 10,
  "aspectRatio": "16:9",
  "resolution": "720p",
  "referenceImageUrls": [
    "https://cdn.example.com/reference/character.png"
  ],
  "referenceVideoUrls": [
    "https://cdn.example.com/reference/camera-motion.mp4"
  ]
}
```

## 查询生成结果

将提交任务时返回的 `requestId` 放入路径：

```http
GET /api/videos/results/{requestId}
X-API-Key: <api-key>
```

接口返回该请求创建的全部任务。`count` 大于 1 时，`jobs` 中会有多条记录。调用方应持续轮询，直到所有任务的 `status` 都不再是 `PENDING`。

### 进行中响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "jobs": [
      {
        "id": "9512e6d90e184e2c81c056f66f1f3dc4",
        "requestId": "b28f13de69984c1a9e84fd4f8768d6a0",
        "prompt": "夜晚城市街道，人物向前行走",
        "model": "seedance-2.0",
        "duration": 8,
        "aspectRatio": "16:9",
        "resolution": "720p",
        "generateAudio": true,
        "params": {
          "requestIndex": 1,
          "requestTotal": 1,
          "referenceImageCount": 1,
          "referenceVideoUrls": [],
          "referenceAudioUrls": [],
          "hasFrames": false
        },
        "status": "PENDING",
        "progress": 35,
        "upstreamStatus": "processing",
        "billingStatus": "PENDING",
        "billingAmount": null,
        "errorMessage": null,
        "durationMs": null,
        "createdAt": "2026-07-31T02:30:00Z",
        "videos": []
      }
    ]
  }
}
```

### 成功响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "jobs": [
      {
        "id": "9512e6d90e184e2c81c056f66f1f3dc4",
        "requestId": "b28f13de69984c1a9e84fd4f8768d6a0",
        "prompt": "夜晚城市街道，人物向前行走",
        "model": "seedance-2.0",
        "duration": 8,
        "aspectRatio": "16:9",
        "resolution": "720p",
        "generateAudio": true,
        "params": {
          "requestIndex": 1,
          "requestTotal": 1,
          "referenceImageCount": 1,
          "referenceVideoUrls": [],
          "referenceAudioUrls": [],
          "hasFrames": false
        },
        "status": "SUCCEEDED",
        "progress": 100,
        "upstreamStatus": "succeeded",
        "billingStatus": "CHARGED",
        "billingAmount": 0.25,
        "errorMessage": null,
        "durationMs": 48231,
        "createdAt": "2026-07-31T02:30:00Z",
        "videos": [
          {
            "id": "25bed75200964db5ba78b0ed5c51b8d1",
            "jobId": "9512e6d90e184e2c81c056f66f1f3dc4",
            "publicUrl": "https://cdn.example.com/generated/result.mp4",
            "mimeType": "video/mp4",
            "createdAt": "2026-07-31T02:30:48Z"
          }
        ]
      }
    ]
  }
}
```

### 任务字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 单个视频任务 ID |
| `requestId` | string | 本次生成请求 ID |
| `prompt` | string | 提交的提示词 |
| `model` | string | 实际使用的模型标识 |
| `duration` | integer | 视频时长，单位为秒 |
| `aspectRatio` | string | 画面比例 |
| `resolution` | string | 分辨率 |
| `generateAudio` | boolean | 是否生成原生音频 |
| `params` | object | 任务批次和参考素材摘要 |
| `status` | string | `PENDING`、`SUCCEEDED` 或 `FAILED` |
| `progress` | integer/null | 任务进度，通常为 `0-100` |
| `upstreamStatus` | string/null | 上游任务状态 |
| `billingStatus` | string/null | 计费状态 |
| `billingAmount` | number/string/null | 本任务计费金额 |
| `errorMessage` | string/null | 失败原因 |
| `durationMs` | integer/null | 后端记录的任务耗时，单位为毫秒 |
| `createdAt` | string/null | UTC ISO 8601 创建时间 |
| `videos` | object[] | 生成的视频列表，未完成或失败时通常为空数组 |

### `params` 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `requestIndex` | integer | 当前任务在本次请求中的序号 |
| `requestTotal` | integer | 本次请求的任务总数 |
| `referenceImageCount` | integer | 参考图片数量 |
| `referenceVideoUrls` | string[] | 提交的参考视频 URL |
| `referenceAudioUrls` | string[] | 提交的参考音频 URL |
| `hasFrames` | boolean | 是否使用首帧模式 |

### 视频结果字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 视频结果 ID |
| `jobId` | string | 所属任务 ID |
| `publicUrl` | string | 视频公网地址 |
| `mimeType` | string/null | 视频 MIME 类型 |
| `createdAt` | string/null | UTC ISO 8601 创建时间 |

### 状态处理

| 状态 | 说明 | 调用方处理 |
| --- | --- | --- |
| `PENDING` | 排队或生成中 | 继续轮询 |
| `SUCCEEDED` | 生成成功 | 从 `videos[].publicUrl` 获取视频 |
| `FAILED` | 生成失败 | 停止轮询并显示 `errorMessage` |

## 错误响应

业务校验失败通常返回 HTTP `422`：

```json
{
  "code": 422,
  "message": "Invalid video generation parameters.",
  "data": null
}
```

常见状态码：

| HTTP 状态码 | 说明 |
| --- | --- |
| `401` | API Key 无效或未提供 |
| `404` | 资源不存在 |
| `422` | 模型未知、请求字段不合法或参数不符合模型限制 |
| `500` | 服务内部错误 |
