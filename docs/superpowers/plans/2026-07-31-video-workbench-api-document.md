# Video Workbench API Document Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the video API Markdown so it documents only generation and result polling, grouped into the five distinct request contracts implemented by the backend.

**Architecture:** Treat the Java request DTOs, model dispatcher, template defaults, validation rules, controllers, and query mapper as the source of truth. Replace the existing document in place, then validate route scope, model coverage, UTF-8 text, and JSON examples mechanically.

**Tech Stack:** Markdown, Java 17 source inspection, PowerShell validation, Node.js JSON parsing.

---

### Task 1: Rewrite and Verify the Video Workbench API Reference

**Files:**

- Modify: `docs/video-generate-api.md`
- Reference: `media-java/src/main/java/com/feng/system/module/video/dto/VideoGenerationRequest.java`
- Reference: `media-java/src/main/java/com/feng/system/module/video/dto/VideoGenerationRequestDeserializer.java`
- Reference: `media-java/src/main/java/com/feng/system/module/video/dto/*VideoRequest.java`
- Reference: `media-java/src/main/java/com/feng/system/module/video/service/VideoTaskRules.java`
- Reference: `media-java/src/main/java/com/feng/system/module/video/service/VideoQueryService.java`

- [ ] **Step 1: Replace the existing Markdown**

Write these sections in Chinese:

1. Authentication and the two endpoint paths.
2. Common generation request fields and acceptance response.
3. Seedance request contract for `seedance-2.0`, `seedance-2.0-fast`, and `seedance-2.0-mini`.
4. Grok Video request contract for `grok-video`.
5. Grok Video 1.5 request contract for `grok-video-1.5`.
6. Omni Fast request contract for `omni-fast` and `omni-fast-no-water`.
7. Omni V2V request contract for `omni-v2v` and `omni-v2v-no-water`.
8. Common result polling contract with pending, succeeded, and failed semantics.
9. Error response reference.

Each model group must contain an exact constraints table and a complete generation JSON example. Do not document any material upload route. State that material URLs must be public HTTPS URLs.

- [ ] **Step 2: Validate route scope**

Run:

```powershell
rg -o '/api/[^` ]+' docs/video-generate-api.md | Sort-Object -Unique
```

Expected unique routes:

```text
/api/videos/generate
/api/videos/results/{requestId}
```

- [ ] **Step 3: Validate model coverage and grouping**

Run a PowerShell assertion that each of these model keys exists in the Markdown:

```text
seedance-2.0
seedance-2.0-fast
seedance-2.0-mini
grok-video
grok-video-1.5
omni-fast
omni-fast-no-water
omni-v2v
omni-v2v-no-water
```

Also compare the five constraint tables against `VideoTaskRules` and `applyVideoTemplate` for duration, aspect ratio, resolution, material counts, audio, and frame restrictions.

- [ ] **Step 4: Parse every JSON code block**

Use the bundled Node.js executable to extract fenced `json` blocks from `docs/video-generate-api.md`, call `JSON.parse` on each block, and fail with its block number on invalid JSON.

Expected: every JSON block parses successfully.

- [ ] **Step 5: Verify encoding and diff scope**

Run:

```powershell
Get-Content -Raw -Encoding UTF8 docs/video-generate-api.md
git diff --check -- docs/video-generate-api.md
git diff --stat -- docs/video-generate-api.md
```

Expected: readable Chinese, no whitespace errors, and only `docs/video-generate-api.md` changed for the implementation.
