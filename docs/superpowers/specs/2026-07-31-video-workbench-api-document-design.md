# Video Workbench API Document Design

## Goal

Rewrite `docs/video-generate-api.md` as a consumer-facing Markdown reference for exactly two video workbench endpoints:

- `POST /api/videos/generate`
- `GET /api/videos/results/{requestId}`

The document must reflect the current Java request DTOs, model dispatch, validation rules, and result mapping.

## Scope

The document includes authentication, request fields, model-specific validation rules, successful responses, task statuses, result fields, and representative error responses.

The document does not include `/api/videos/uploads`, history, deletion, model listing, admin APIs, billing internals, or upstream-provider request formats. Material fields are documented only as public HTTPS URLs supplied by the caller.

## Model Grouping

Models are grouped only when their accepted client fields, defaults, and validation rules are identical apart from `model`:

1. Seedance: `seedance-2.0`, `seedance-2.0-fast`, `seedance-2.0-mini`
2. Grok Video: `grok-video`
3. Grok Video 1.5: `grok-video-1.5`
4. Omni Fast: `omni-fast`, `omni-fast-no-water`
5. Omni V2V: `omni-v2v`, `omni-v2v-no-water`

`grok-video` and `grok-video-1.5` remain separate because they have different image requirements, video support, aspect ratios, and multi-image duration constraints.

## Document Structure

The document contains:

1. Authentication and endpoint overview.
2. Common generation fields and generation acceptance response.
3. Five model-group sections. Each section lists allowed `model` values, defaults, exact field constraints, prohibited combinations, and a complete JSON request example.
4. One common results section because `GET /api/videos/results/{requestId}` returns the same structure for every model. The returned job's `model` field identifies the model used.
5. Status and error-response reference.

## Request Rules

All requests use `application/json` and `X-API-Key`. `prompt` is required and limited to 5000 characters. `count` defaults to 1 and accepts 1 through 4. Material URL fields accept only public HTTPS URLs.

The five model sections reproduce the current rules from `VideoTaskRules`, `VideoGenerationRequest` subclasses, and `ImageModelConfigService.applyVideoTemplate`, including duration sets, aspect ratios, resolutions, material limits, frame pairing, mutual exclusions, and audio support.

## Results Contract

The results section documents the current `ApiResponse` envelope and `data.jobs` response. It covers job identity, request identity, prompt/model parameters, status/progress, error and timing fields, nested params, creation time, and generated video records.

Examples show both a pending task and a succeeded task so callers can implement polling without relying on undocumented behavior.

## Verification

Before completion:

- Search the final Markdown and assert it contains only the two scoped route strings.
- Confirm all nine supported model keys appear exactly in their correct groups.
- Compare duration, ratio, resolution, material, frame, and audio rules against the Java sources.
- Check every JSON code block parses successfully.
- Render/read the Markdown as UTF-8 and confirm Chinese text is not corrupted.
