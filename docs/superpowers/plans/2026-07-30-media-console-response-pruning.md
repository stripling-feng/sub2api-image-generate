# Media Console Response Pruning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restrict successful backend responses used by `media-console` to the exact top-level and nested fields read by that frontend.

**Architecture:** Add one shared `MediaConsoleResponse` contract containing typed records for model, task, media, history, upload, generation, and document payloads. Image/video services map persistence objects into those records and filter dynamic maps with explicit allowlists before controllers serialize them through the existing `ApiResponse` envelope.

**Tech Stack:** Java 17, Spring Boot 3.2, Jackson, MyBatis-Plus, JUnit 5, Mockito, Spring MockMvc, Vue 3, TypeScript, Vite.

---

## File Structure

- Create `media-java/src/main/java/com/feng/system/module/media/dto/MediaConsoleResponse.java`: shared successful-response records for endpoints consumed by `media-console`.
- Create `media-java/src/test/java/com/feng/system/module/media/MediaConsoleResponseTest.java`: exact serialized key-set tests for every response record.
- Modify `media-java/src/main/java/com/feng/system/module/image/service/ImageModelConfigService.java`: split image/video public model mapping and filter parameter/default maps.
- Modify `media-java/src/test/java/com/feng/system/module/image/ImageModelConfigServiceTest.java`: assert exact image/video model fields and nested maps.
- Modify `media-java/src/main/java/com/feng/system/module/image/service/ImageQueryService.java`: return typed image history/jobs and filter task params/results.
- Modify `media-java/src/test/java/com/feng/system/module/image/ImageQueryServiceTest.java`: cover exact image job, params, and generated-image fields.
- Modify `media-java/src/main/java/com/feng/system/module/video/service/VideoQueryService.java`: return typed video history/jobs and filter task params/results.
- Modify `media-java/src/test/java/com/feng/system/module/video/VideoQueryServiceTest.java`: cover exact video job, params, and generated-video fields.
- Modify image/video controllers under `media-java/src/main/java/com/feng/system/module/{image,video}/controller`: use typed contracts, minimal uploads/acceptance payloads, and empty delete data.
- Modify `media-java/src/main/java/com/feng/system/module/system/controller/PublicDocsController.java`: expose only document content on successful responses.
- Modify `ImageApiContractTest.java`, `VideoApiContractTest.java`, and `PublicDocsControllerTest.java`: assert exact controller JSON and retained errors/routes.
- Read and verify `media-console` without modifying it: its current runtime accesses define the backend allowlists, while tests, typecheck, and production build prove retained compatibility.

### Task 1: Define Exact Response Records

- [ ] **Step 1: Write the failing serialization contract test**

Create `MediaConsoleResponseTest.java`. Build representative `ImageModel`, `VideoModel`, `ImageJob`, `VideoJob`, `Image`, `Video`, `Upload`, `VideoAccepted`, `ImageHistory`, `VideoHistory`, `Jobs`, and `Document` records. Serialize each with `ObjectMapper.valueToTree` and assert exact field-name sets, for example:

```java
private static void assertKeys(JsonNode node, String... expected) {
    assertEquals(Set.of(expected), StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(node.fieldNames(), Spliterator.ORDERED), false)
            .collect(Collectors.toSet()));
}

assertKeys(mapper.valueToTree(imageJob), "id", "prompt", "model", "count", "params",
        "status", "progress", "errorMessage", "durationMs", "createdAt", "images");
assertKeys(mapper.valueToTree(videoJob), "id", "requestId", "prompt", "model", "duration",
        "aspectRatio", "resolution", "generateAudio", "params", "status", "progress",
        "errorMessage", "createdAt", "videos");
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
cd media-java
mvn -Dtest=MediaConsoleResponseTest test
```

Expected: compilation fails because `MediaConsoleResponse` does not exist.

- [ ] **Step 3: Add the minimal shared response contract**

Create `MediaConsoleResponse.java` as a non-instantiable holder with these public nested records:

```java
public record Models<T>(List<T> models) {}
public record Jobs<T>(List<T> jobs) {}
public record Upload(String url) {}
public record VideoAccepted(String requestId) {}
public record Document(String content) {}
public record ImageHistory(List<ImageJob> jobs, int page, int pageSize, long total, long totalPages) {}
public record VideoHistory(List<VideoJob> jobs, int page, long total, long totalPages) {}
public record ImageModel(Long id, String model, String name, BigDecimal unitPriceUsd, Integer maxCount,
        Integer maxReferenceImages, boolean supportsMask, List<Map<String, Object>> parameters,
        Map<String, Object> defaults) {}
public record VideoModel(Long id, String model, String name, BigDecimal unitPriceUsd, String billingMode,
        Integer maxCount, Integer maxReferenceImages, List<Map<String, Object>> parameters,
        Map<String, Object> defaults) {}
public record ImageJob(String id, String prompt, String model, int count, Map<String, Object> params,
        String status, Integer progress, String errorMessage, Integer durationMs, String createdAt,
        List<Image> images) {}
public record Image(String id, String publicUrl, String mimeType) {}
public record VideoJob(String id, String requestId, String prompt, String model, int duration,
        String aspectRatio, String resolution, boolean generateAudio, Map<String, Object> params,
        String status, Integer progress, String errorMessage, String createdAt, List<Video> videos) {}
public record Video(String id, String publicUrl) {}
```

- [ ] **Step 4: Run the test and verify GREEN**

Run `mvn -Dtest=MediaConsoleResponseTest test` and expect all tests in the class to pass.

- [ ] **Step 5: Commit the isolated contract**

```powershell
git add media-java/src/main/java/com/feng/system/module/media/dto/MediaConsoleResponse.java media-java/src/test/java/com/feng/system/module/media/MediaConsoleResponseTest.java
git commit -m "feat: define minimal media console responses"
```

### Task 2: Prune Public Model Responses

- [ ] **Step 1: Add failing model mapping assertions**

Update `ImageModelConfigServiceTest` so `publicImages()` and `publicVideos()` are asserted as typed records. Serialize the first item and assert the exact model field sets. Include schemas/defaults containing extra keys and assert:

```java
assertEquals(Set.of("key", "label", "type", "default", "placeholder", "options"), imageParameter.keySet());
assertEquals(Set.of("size"), imageModel.defaults().keySet());
assertEquals(Set.of("images", "videos", "audios", "frameInputs", "requiresImage",
        "maxImageBytes", "maxVideoBytes"), videoModel.defaults().keySet());
```

- [ ] **Step 2: Run the test and verify RED**

Run `mvn -Dtest=ImageModelConfigServiceTest test`. Expected: type/key assertions fail because public models are unfiltered maps containing `provider`, `billingMode` on image models, or `supportsMask` on video models.

- [ ] **Step 3: Implement model-specific whitelist mapping**

Change `publicImages()` to return `List<MediaConsoleResponse.ImageModel>` and `publicVideos()` to return `List<MediaConsoleResponse.VideoModel>`. Replace the shared `publicModel` mapper with image/video mappers. Filter parameter definition maps to:

```java
private static final Set<String> IMAGE_PARAMETER_FIELDS =
        Set.of("key", "label", "type", "default", "placeholder", "options");
private static final Set<String> VIDEO_PARAMETER_FIELDS =
        Set.of("key", "label", "type", "default", "options");
private static final Set<String> VIDEO_DEFAULT_FIELDS = Set.of(
        "images", "videos", "audios", "frameInputs", "requiresImage", "maxImageBytes", "maxVideoBytes");
```

For image defaults, retain only keys named by the filtered image parameter definitions. For video defaults, retain only `VIDEO_DEFAULT_FIELDS`. Filter option objects to `label` and `value`; scalar options pass through unchanged.

- [ ] **Step 4: Update the model controller**

Return `ApiResponse<MediaConsoleResponse.Models<MediaConsoleResponse.ImageModel>>` and the equivalent video type. Do not change routes or the common envelope.

- [ ] **Step 5: Run model and controller tests**

Run `mvn -Dtest=ImageModelConfigServiceTest,ImageApiContractTest,VideoApiContractTest test` and expect PASS.

- [ ] **Step 6: Commit model pruning**

```powershell
git add media-java/src/main/java/com/feng/system/module/image/service/ImageModelConfigService.java media-java/src/main/java/com/feng/system/module/image/controller/PublicModelController.java media-java/src/test/java/com/feng/system/module/image/ImageModelConfigServiceTest.java
git commit -m "refactor: prune console model responses"
```

### Task 3: Prune Image Task and Controller Responses

- [ ] **Step 1: Add a failing image task mapping test**

Extend `ImageQueryServiceTest` with a task containing all current task data and a result containing width, height, size, and creation metadata. Assert the mapped job and result exact serialized keys. Assert image params equal:

```java
Map.of(
    "request_id", "request-1",
    "request_index", 1,
    "request_total", 2,
    "size", "1024x1024",
    "aspect_ratio", "1:1",
    "quality", "high"
)
```

and do not contain `reference_image_count` or arbitrary audit keys.

- [ ] **Step 2: Run the image query test and verify RED**

Run `mvn -Dtest=ImageQueryServiceTest test`. Expected: the service returns maps with extra task/result/params fields.

- [ ] **Step 3: Implement typed image mapping**

Change `history()` to return `MediaConsoleResponse.ImageHistory`, `results()` to return `List<MediaConsoleResponse.ImageJob>`, and mapping helpers to build typed records. Filter params with:

```java
private static final Set<String> CONSOLE_PARAM_FIELDS = Set.of(
        "request_id", "request_index", "request_total", "size", "aspect_ratio", "quality");
```

Map generated images to only `id`, `publicUrl`, and `mimeType`. Adapt `compactResults()` to read typed job accessors so the unscoped compact route preserves its existing response.

- [ ] **Step 4: Add failing image controller assertions**

Update `ImageApiContractTest` to return typed mocks and assert:

```java
jsonPath("$.data", aMapWithSize(1)); // upload: url only
jsonPath("$.data", nullValue());     // delete endpoints
```

Also assert generation still contains exactly `requestId` and `count`, and history/results retain their required outer fields.

- [ ] **Step 5: Implement minimal image controller payloads**

Use `MediaConsoleResponse.Upload` for uploads, typed history/jobs records for queries, and `ApiResponse<Void>.success()` after delete services complete. Keep `GenerationAcceptedResponse` for image generation.

- [ ] **Step 6: Run image tests and verify GREEN**

Run `mvn -Dtest=ImageQueryServiceTest,ImageApiContractTest test` and expect PASS.

- [ ] **Step 7: Commit image pruning**

```powershell
git add media-java/src/main/java/com/feng/system/module/image/service/ImageQueryService.java media-java/src/main/java/com/feng/system/module/image/controller/ImageQueryController.java media-java/src/main/java/com/feng/system/module/image/controller/ImageGenerationController.java media-java/src/test/java/com/feng/system/module/image/ImageQueryServiceTest.java media-java/src/test/java/com/feng/system/module/image/ImageApiContractTest.java
git commit -m "refactor: prune console image responses"
```

### Task 4: Prune Video Task and Controller Responses

- [ ] **Step 1: Add a failing video task mapping test**

Extend `VideoQueryServiceTest` with a fully populated task and result. Assert the exact video job/result key sets and assert params contain only `duration`, `aspectRatio`, `resolution`, and `generateAudio`.

- [ ] **Step 2: Run the video query test and verify RED**

Run `mvn -Dtest=VideoQueryServiceTest test`. Expected: current maps include upstream, billing, duration, result MIME, and result creation fields not used by the console.

- [ ] **Step 3: Implement typed video mapping**

Change `history()` to return `MediaConsoleResponse.VideoHistory`, `results()` to return `List<MediaConsoleResponse.VideoJob>`, and mapping helpers to typed records. Filter params with:

```java
private static final Set<String> CONSOLE_PARAM_FIELDS =
        Set.of("duration", "aspectRatio", "resolution", "generateAudio");
```

Map generated videos to only `id` and `publicUrl`.

- [ ] **Step 4: Add failing video controller assertions**

Update `VideoApiContractTest` and add query controller coverage. Assert generation data has only `requestId`, upload data has only `url`, video history omits `pageSize`, and delete data is null.

- [ ] **Step 5: Implement minimal video controller payloads**

Use `MediaConsoleResponse.VideoAccepted`, `Upload`, `VideoHistory`, `Jobs<VideoJob>`, and `ApiResponse<Void>` as appropriate. Keep authentication, audit recording, routes, and status codes unchanged.

- [ ] **Step 6: Run video tests and verify GREEN**

Run `mvn -Dtest=VideoQueryServiceTest,VideoApiContractTest test` and expect PASS.

- [ ] **Step 7: Commit video pruning**

```powershell
git add media-java/src/main/java/com/feng/system/module/video/service/VideoQueryService.java media-java/src/main/java/com/feng/system/module/video/controller/VideoQueryController.java media-java/src/main/java/com/feng/system/module/video/controller/VideoGenerationController.java media-java/src/test/java/com/feng/system/module/video/VideoQueryServiceTest.java media-java/src/test/java/com/feng/system/module/video/VideoApiContractTest.java
git commit -m "refactor: prune console video responses"
```

### Task 5: Prune Documentation and Verify Frontend Compatibility

- [ ] **Step 1: Change the public document test to require content only**

Update `PublicDocsControllerTest` to assert the successful JSON object has one field, `content`, while the existing unknown-key and configuration error tests remain unchanged.

- [ ] **Step 2: Run the document test and verify RED**

Run `mvn -Dtest=PublicDocsControllerTest test`. Expected: successful response still contains `key`, `title`, and `updatedAt`.

- [ ] **Step 3: Map documents to the minimal response**

In `PublicDocsController`, wrap successful service output as `new MediaConsoleResponse.Document(document.content())`. Do not modify the service record or error maps.

- [ ] **Step 4: Run focused backend and frontend checks**

```powershell
cd media-java
mvn -Dtest=PublicDocsControllerTest test
cd ..\media-console
npm test --if-present
npm run typecheck
npm run build
```

Expected: all commands pass; typecheck/build report no missing response properties.

- [ ] **Step 5: Commit document pruning**

```powershell
git add media-java/src/main/java/com/feng/system/module/system/controller/PublicDocsController.java media-java/src/test/java/com/feng/system/module/system/PublicDocsControllerTest.java
git commit -m "refactor: prune console document responses"
```

### Task 6: Full Verification

- [ ] **Step 1: Run the complete backend suite**

```powershell
cd media-java
mvn test
```

Expected: BUILD SUCCESS with no test failures.

- [ ] **Step 2: Run all console tests and production build**

```powershell
cd media-console
node --test tests/*.test.mjs
npm run typecheck
npm run build
```

Expected: all Node tests pass and Vite produces a successful production build.

- [ ] **Step 3: Inspect the final diff for scope**

```powershell
git diff --check
git status --short
git diff --stat
```

Expected: no whitespace errors; only scoped response contracts, mappings, controllers, and their tests changed. Existing unrelated worktree changes remain preserved.
