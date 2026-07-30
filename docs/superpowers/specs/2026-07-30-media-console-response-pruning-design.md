# Media Console Response Pruning Design

## Goal

Reduce the successful response payloads of backend endpoints used by `media-console` so they contain only fields the console reads. This includes pruning nested task `params` objects while preserving the existing error behavior and API envelope.

## Scope

The change covers only endpoints called from `media-console`:

- `GET /api/images/models`
- `POST /api/images/generate`
- `POST /api/images/uploads`
- `GET /api/images/history`
- `GET /api/images/results/{requestId}`
- `GET /api/images/{id}/download`
- `DELETE /api/jobs/{id}`
- `DELETE /api/jobs`
- `GET /api/videos/models`
- `POST /api/videos/generate`
- `POST /api/videos/uploads`
- `GET /api/videos/history`
- `GET /api/videos/results/{requestId}`
- `DELETE /api/video-jobs/{id}`
- `DELETE /api/video-jobs`
- `GET /api/docs/image`
- `GET /api/docs/video`

The binary image download response is already minimal and remains unchanged. `media-admin`, endpoints not called by `media-console`, error response structures, and request payloads are out of scope.

## Approach

Use explicit response records and whitelist mapping at the controller/service boundary. Dynamic containers such as model `parameters`, model `defaults`, and task `params` remain maps where their keys are data-driven, but their contents are filtered according to console usage.

Exact-key serialization tests will assert both required fields and the absence of extra fields. This is preferred over deleting individual `Map.put` calls without a typed contract, and over route-specific Jackson filters whose behavior would only be visible at runtime.

## Response Contracts

All endpoints currently using `ApiResponse` retain the `code`, `message`, and `data` envelope. Error responses retain their existing fields and status behavior.

### Models

Image model items contain:

- `id`
- `model`
- `name`
- `unitPriceUsd`
- `maxCount`
- `maxReferenceImages`
- `supportsMask`
- `parameters`
- `defaults`

Video model items contain:

- `id`
- `model`
- `name`
- `unitPriceUsd`
- `billingMode`
- `maxCount`
- `maxReferenceImages`
- `parameters`
- `defaults`

Image parameter definitions retain `key`, `label`, `type`, `default`, `placeholder`, and `options`. Video parameter definitions retain `key`, `label`, `type`, `default`, and `options`. Option objects retain `label` and `value`. A model's `defaults` retains schema parameter defaults and console capability keys because the console reads them dynamically.

### Image Operations

The image generation acceptance payload contains `requestId` and `count`. The upload payload contains only `url`.

Image history contains `jobs`, `page`, `pageSize`, `total`, and `totalPages`. Image results contain `jobs`.

Each image job contains:

- `id`
- `prompt`
- `model`
- `count`
- `params`
- `status`
- `progress`
- `errorMessage`
- `durationMs`
- `createdAt`
- `images`

Image job `params` contains `request_id`, `request_index`, `request_total`, plus generation parameters declared by the active image model schema and reusable by the console. Internal metadata such as reference counts is excluded.

Each generated image contains `id`, `publicUrl`, and `mimeType`.

Delete endpoints return a successful `ApiResponse` with no business response fields. The existing envelope remains intact.

### Video Operations

The video generation acceptance payload contains only `requestId`. The upload payload contains only `url`.

Video history contains `jobs`, `page`, `total`, and `totalPages`. Video results contain `jobs`.

Each video job contains:

- `id`
- `requestId`
- `prompt`
- `model`
- `duration`
- `aspectRatio`
- `resolution`
- `generateAudio`
- `params`
- `status`
- `progress`
- `errorMessage`
- `createdAt`
- `videos`

Video job `params` contains only `duration`, `aspectRatio`, `resolution`, and `generateAudio`.

Each generated video contains `id` and `publicUrl`.

Delete endpoints return a successful `ApiResponse` with no business response fields. The existing envelope remains intact.

### Documentation

Successful public document responses contain only `content`. Existing not-found and configuration error responses remain unchanged.

## Data Flow

Controllers keep their current routes and authentication behavior. Query and model services load the same database data as before, then map it through endpoint-specific response records. Nested parameter filtering occurs during mapping, before serialization. Controllers wrap the resulting records in the existing response envelope where applicable.

No database migration or frontend behavior change is required.

## Testing

Backend contract tests serialize representative successful responses and compare exact key sets for:

- image and video model items;
- image and video history and result jobs;
- nested image and video `params`;
- generated image and video items;
- upload and generation acceptance payloads;
- delete payloads;
- public documentation responses.

Existing image, video, controller, and service tests must remain green. The `media-console` tests and production build must also pass to verify the retained fields still satisfy all current consumers.

## Compatibility

This is an intentional breaking change for consumers that relied on extra successful-response fields from the scoped endpoints. Compatibility is guaranteed only for the current `media-console` usage. Authentication, request formats, HTTP statuses, error responses, and the common success envelope remain compatible.
