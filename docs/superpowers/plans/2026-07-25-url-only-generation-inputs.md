# URL-Only Generation Inputs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reject Base64 generation inputs and outputs while recording multipart metadata and the exact URL-based upstream request.

**Architecture:** Extend the existing `GenerationAuditJson` boundary helper with strict Base64 detection and a two-sided request snapshot. Image and video services validate raw JSON before parsing, then update `raw_request.upstream` immediately before gateway invocation.

**Tech Stack:** Java 17, Spring Boot, Jackson, MyBatis-Plus, JUnit 5, Mockito

---

### Task 1: Reject Base64 Inputs and Outputs

**Files:**
- Modify: `backend/src/main/java/com/feng/system/module/image/GenerationAuditJson.java`
- Modify: `backend/src/main/java/com/feng/system/module/image/ImageGenerationService.java`
- Modify: `backend/src/main/java/com/feng/system/module/video/VideoGenerationService.java`
- Test: `backend/src/test/java/com/feng/system/module/image/GenerationAuditJsonTest.java`
- Test: `backend/src/test/java/com/feng/system/module/image/ImageGenerationFanoutTest.java`

- [ ] Add failing tests proving recursive Data URL/Base64 rejection and `b64_json` rejection.
- [ ] Run `mvn -f backend/pom.xml "-Dtest=GenerationAuditJsonTest,ImageGenerationFanoutTest" test`; expect assertion failures because requests are currently accepted.
- [ ] Add `GenerationAuditJson.rejectBase64(Object)`, call it at both service boundaries, remove JSON Base64 image decoding, and reject merged `response_format=b64_json` with HTTP 422.
- [ ] Re-run the focused tests; expect PASS.

### Task 2: Persist Client and Upstream Request Views

**Files:**
- Modify: `backend/src/main/java/com/feng/system/module/image/GenerationAuditJson.java`
- Modify: `backend/src/main/java/com/feng/system/module/image/ImageGenerationService.java`
- Modify: `backend/src/main/java/com/feng/system/module/video/VideoGenerationService.java`
- Modify: `backend/src/main/java/com/feng/system/module/image/ImageGenerationWorker.java`
- Test: `backend/src/test/java/com/feng/system/module/image/ImageGenerationFanoutTest.java`
- Test: `backend/src/test/java/com/feng/system/module/video/VideoGenerationServiceTest.java`

- [ ] Add failing assertions for `raw_request.client` file metadata and `raw_request.upstream.image_url`.
- [ ] Run the two service tests; expect failures against the current flat audit JSON.
- [ ] Add request snapshot/update helpers. Store multipart files as `{name,mimeType,sizeBytes}` and store actual outbound bodies plus repeated `input_reference` URLs.
- [ ] Pass the prepared image upstream snapshot into the background worker so its update cannot erase request audit data.
- [ ] Re-run focused tests; expect PASS.

### Task 3: Remove Grok Base64 Upstream Path and Verify

**Files:**
- Modify: `backend/src/main/java/com/feng/system/module/video/VideoGenerationService.java`
- Modify: `backend/src/test/java/com/feng/system/module/video/VideoGenerationServiceTest.java`
- Modify: `client/src/types.ts`
- Modify: `client/src/stores/workbench.ts`

- [ ] Change the existing Grok test to expect an uploaded HTTPS URL and add a video Data URL rejection test.
- [ ] Run `mvn -f backend/pom.xml "-Dtest=VideoGenerationServiceTest" test`; expect failure because Grok currently emits a Data URL.
- [ ] Upload Grok reference images through `VideoMaterialUploadService`, remove the `dataUrl` encoder, and remove `b64_json` from the client response-format type/options.
- [ ] Run `mvn -f backend/pom.xml test`, `node --test client/tests/*.test.mjs`, and `npm run typecheck -w client`; expect all commands to pass.
- [ ] Run `git diff --check`; expect no whitespace errors.
