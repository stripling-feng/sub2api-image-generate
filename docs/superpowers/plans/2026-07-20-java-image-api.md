# Java Image API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let third-party callers create and query Java-managed image tasks at `image.tcboys.de` with their existing Sub2API API key.

**Architecture:** Keep the existing Java generation controllers, `generation_jobs`, poller, storage, and billing services. Extend `ImageSessionService` so a valid `X-API-Key` creates its Java profile on first use, then document only the Java create/result endpoints in the index dialog.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, JUnit 5/Mockito, Vue 3, TypeScript, Node test runner.

---

## File Map

- Modify `backend/src/main/java/com/feng/system/module/image/ImageSessionService.java`: resolve or create a profile for `X-API-Key` requests.
- Modify `backend/src/test/java/com/feng/system/module/image/ImageSessionPlainApiKeyTest.java`: cover first-use creation, reuse, and unique-key race recovery.
- Modify `backend/src/test/java/com/feng/system/module/image/ImageApiContractTest.java`: exercise JSON, multipart, and result endpoints with `X-API-Key`.
- Modify `client/src/DocsDialog.vue`: replace Sub2API image examples with the Java task API contract.
- Modify `client/tests/image-api-docs.test.mjs`: lock the documented host, headers, payload names, result endpoint, and statuses.

### Task 1: Automatically Create Java Profiles

**Files:**
- Modify: `backend/src/test/java/com/feng/system/module/image/ImageSessionPlainApiKeyTest.java`
- Modify: `backend/src/test/java/com/feng/system/module/image/ImageApiContractTest.java`
- Modify: `backend/src/main/java/com/feng/system/module/image/ImageSessionService.java`

- [ ] **Step 1: Add failing first-use and reuse tests**

Add tests that set `upstreamBaseUrl`, send `X-API-Key`, and assert profile creation or reuse:

```java
@Test
void createsProfileOnFirstApiKeyRequest() {
    ApiProfileMapper profiles = mock(ApiProfileMapper.class);
    ApiSessionMapper sessions = mock(ApiSessionMapper.class);
    Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
    when(profiles.selectOne(any())).thenReturn(null);
    when(billing.validateApiKey("sk-first-use")).thenReturn(
            new Sub2apiBillingService.BillingAccount("150", "1", "10", "10"));

    ImageSessionService service = new ImageSessionService(profiles, sessions, billing);
    ReflectionTestUtils.setField(service, "upstreamBaseUrl", "https://image-upstream.example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-API-Key", "sk-first-use");

    ApiProfile resolved = service.resolveProfile(request, new MockHttpServletResponse());

    ArgumentCaptor<ApiProfile> saved = ArgumentCaptor.forClass(ApiProfile.class);
    verify(profiles).insert(saved.capture());
    assertEquals(saved.getValue().getId(), resolved.getId());
    assertEquals("https://image-upstream.example.com", resolved.getBaseUrl());
    assertEquals("sk-first-use", resolved.getEncryptedKey());
}

@Test
void reusesProfileForKnownApiKey() {
    ApiProfileMapper profiles = mock(ApiProfileMapper.class);
    ApiSessionMapper sessions = mock(ApiSessionMapper.class);
    Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
    ApiProfile existing = new ApiProfile();
    existing.setId("existing");
    when(profiles.selectOne(any())).thenReturn(existing);

    ImageSessionService service = new ImageSessionService(profiles, sessions, billing);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-API-Key", "sk-known-key");

    assertEquals(existing, service.resolveProfile(request, new MockHttpServletResponse()));
    verifyNoInteractions(billing);
    verify(profiles, never()).insert(any());
}
```

Import `MockHttpServletRequest`, `MockHttpServletResponse`, `ReflectionTestUtils`, and the additional Mockito assertions used above.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn -f backend/pom.xml -Dtest=ImageSessionPlainApiKeyTest test
```

Expected: FAIL because `resolveProfile` returns `null` for a previously unbound `X-API-Key`.

- [ ] **Step 3: Implement first-use profile creation**

Add the configured upstream URL and route header authentication through a new helper:

```java
import org.springframework.dao.DuplicateKeyException;

@Value("${image.upstream-base-url:}") private String upstreamBaseUrl;

public ApiProfile resolveProfile(HttpServletRequest request, HttpServletResponse response) {
    String apiKey = request.getHeader("X-API-Key");
    if (apiKey != null && !apiKey.isBlank()) return resolveApiKeyProfile(apiKey);
    String token = cookie(request);
    if (token == null) return null;
    ApiSession session = sessionMapper.selectOne(
            new LambdaQueryWrapper<ApiSession>().eq(ApiSession::getTokenHash, hash(token)));
    if (session == null || session.getExpiresAt().isBefore(ImageTime.now())) {
        if (session != null) sessionMapper.deleteById(session.getId());
        clearCookie(response);
        return null;
    }
    return profileMapper.selectById(session.getProfileId());
}

private ApiProfile resolveApiKeyProfile(String apiKey) {
    String keyHash = hash(apiKey);
    ApiProfile existing = profileMapper.selectOne(
            new LambdaQueryWrapper<ApiProfile>().eq(ApiProfile::getKeyHash, keyHash));
    if (existing != null) return existing;
    if (upstreamBaseUrl == null || upstreamBaseUrl.isBlank()) {
        throw new ImageApiException(503, "Image upstream is not configured.", "IMAGE_UPSTREAM_NOT_CONFIGURED", null);
    }
    validateBind(upstreamBaseUrl, apiKey);
    billing.validateApiKey(apiKey);
    LocalDateTime now = ImageTime.now();
    ApiProfile profile = new ApiProfile();
    profile.setId(id());
    profile.setBaseUrl(upstreamBaseUrl);
    profile.setKeyHash(keyHash);
    profile.setEncryptedKey(apiKey);
    profile.setCreatedAt(now);
    profile.setUpdatedAt(now);
    try {
        profileMapper.insert(profile);
        return profile;
    } catch (DuplicateKeyException duplicate) {
        ApiProfile concurrent = profileMapper.selectOne(
                new LambdaQueryWrapper<ApiProfile>().eq(ApiProfile::getKeyHash, keyHash));
        if (concurrent != null) return concurrent;
        throw duplicate;
    }
}
```

- [ ] **Step 4: Add invalid-key and race-recovery tests**

```java
@Test
void rejectsInvalidFirstUseApiKeyWithoutCreatingProfile() {
    ApiProfileMapper profiles = mock(ApiProfileMapper.class);
    ApiSessionMapper sessions = mock(ApiSessionMapper.class);
    Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
    when(profiles.selectOne(any())).thenReturn(null);
    when(billing.validateApiKey("sk-invalid-key")).thenThrow(
            new ImageApiException(401, "Invalid, expired, or disabled sub2api API Key.", "INVALID_API_KEY", null));

    ImageSessionService service = new ImageSessionService(profiles, sessions, billing);
    ReflectionTestUtils.setField(service, "upstreamBaseUrl", "https://image-upstream.example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-API-Key", "sk-invalid-key");

    ImageApiException error = assertThrows(ImageApiException.class,
            () -> service.resolveProfile(request, new MockHttpServletResponse()));
    assertEquals(401, error.getStatus());
    verify(profiles, never()).insert(any());
}
```

```java
@Test
void reusesConcurrentlyCreatedProfile() {
    ApiProfileMapper profiles = mock(ApiProfileMapper.class);
    ApiSessionMapper sessions = mock(ApiSessionMapper.class);
    Sub2apiBillingService billing = mock(Sub2apiBillingService.class);
    ApiProfile concurrent = new ApiProfile();
    concurrent.setId("concurrent");
    when(profiles.selectOne(any())).thenReturn(null, concurrent);
    when(profiles.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
    when(billing.validateApiKey("sk-race-key")).thenReturn(
            new Sub2apiBillingService.BillingAccount("150", "1", "10", "10"));

    ImageSessionService service = new ImageSessionService(profiles, sessions, billing);
    ReflectionTestUtils.setField(service, "upstreamBaseUrl", "https://image-upstream.example.com");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-API-Key", "sk-race-key");

    assertEquals(concurrent, service.resolveProfile(request, new MockHttpServletResponse()));
}
```

- [ ] **Step 5: Add `X-API-Key` to the public API contract test**

Import `MockMultipartFile`, then replace the generation/result requests and add the multipart request:

```java
MockMultipartFile payload = new MockMultipartFile("payload", "", "text/plain",
        "{\"prompt\":\"edit\",\"model\":\"gpt-image-2\"}".getBytes());
MockMultipartFile image = new MockMultipartFile("image", "input.png", "image/png", new byte[]{1, 2, 3});

mvc.perform(post("/api/images/generate").header("X-API-Key", "sk-contract-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"x\",\"model\":\"gpt-image-2\"}"))
        .andExpect(status().isAccepted()).andExpect(jsonPath("$.requestId").value("request"));
mvc.perform(multipart("/api/images/generate").file(payload).file(image)
                .header("X-API-Key", "sk-contract-key"))
        .andExpect(status().isAccepted()).andExpect(jsonPath("$.requestId").value("request"));
mvc.perform(get("/api/images/results/request").header("X-API-Key", "sk-contract-key"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.jobs").isArray());
```

- [ ] **Step 6: Run backend tests and verify GREEN**

Run:

```powershell
mvn -f backend/pom.xml -Dtest=ImageSessionPlainApiKeyTest,ImageApiContractTest test
```

Expected: all selected tests pass with zero failures.

- [ ] **Step 7: Commit backend authentication**

```powershell
git add backend/src/main/java/com/feng/system/module/image/ImageSessionService.java backend/src/test/java/com/feng/system/module/image/ImageSessionPlainApiKeyTest.java backend/src/test/java/com/feng/system/module/image/ImageApiContractTest.java
git commit -m "feat: auto-bind Java image API keys"
```

### Task 2: Document the Java Image Task API

**Files:**
- Modify: `client/tests/image-api-docs.test.mjs`
- Modify: `client/src/DocsDialog.vue`

- [ ] **Step 1: Replace the documentation regression assertions**

Use one source-level contract test:

```js
test("index docs describe the Java image task API", () => {
  assert.match(source, /image\.tcboys\.de\/api\/images\/generate/);
  assert.match(source, /image\.tcboys\.de\/api\/images\/results\/\{requestId\}/);
  assert.match(source, /X-API-Key: &lt;SUB2API_KEY&gt;/);
  assert.match(source, /"model": "gpt-image-2"/);
  assert.match(source, /"count": 1/);
  assert.match(source, /payload=\{/);
  assert.match(source, /image\[\]=@/);
  assert.match(source, /PENDING[\s\S]*SUCCEEDED[\s\S]*FAILED/);
  assert.match(source, /images\[\]\.publicUrl/);
  assert.doesNotMatch(source, /api\.tcboys\.de\/v1/);
  assert.doesNotMatch(source, /\/v1\/images\/tasks/);
  assert.doesNotMatch(source, /Authorization: Bearer/);
});
```

- [ ] **Step 2: Run the client test and verify RED**

Run:

```powershell
node --test client/tests/image-api-docs.test.mjs
```

Expected: FAIL because the dialog still documents Sub2API image endpoints.

- [ ] **Step 3: Replace the quick-start and task-query sections**

Document the Java host and request lifecycle:

```html
<li><strong>服务地址：</strong><code>https://image.tcboys.de</code></li>
<li><strong>鉴权：</strong>所有请求携带 <code>X-API-Key: &lt;SUB2API_KEY&gt;</code>。</li>
<li><strong>创建任务：</strong><code>POST /api/images/generate</code> 返回 <code>requestId</code>。</li>
<li><strong>任务状态：</strong><code>PENDING</code> 表示继续查询，<code>SUCCEEDED</code> 表示成功，<code>FAILED</code> 表示失败。</li>

<pre><code>curl "https://image.tcboys.de/api/images/results/{requestId}" \
  -H "X-API-Key: &lt;SUB2API_KEY&gt;"</code></pre>
<p>成功后从 <code>jobs[].images[].publicUrl</code> 读取图片地址。</p>
```

- [ ] **Step 4: Replace generation and edit curl examples**

Use the Java JSON field names and multipart `payload` contract:

```html
<pre><code>curl -X POST "https://image.tcboys.de/api/images/generate" \
  -H "X-API-Key: &lt;SUB2API_KEY&gt;" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-image-2",
    "prompt": "一只橘猫坐在窗台上，午后阳光",
    "count": 1,
    "size": "1:1"
  }'</code></pre>

<pre><code>curl -X POST "https://image.tcboys.de/api/images/generate" \
  -H "X-API-Key: &lt;SUB2API_KEY&gt;" \
  -F 'payload={"model":"gpt-image-2","prompt":"把背景改成雪山","count":1,"size":"1:1"}' \
  -F "image[]=@./reference-1.png" \
  -F "image[]=@./reference-2.png"

# 可选蒙版：-F "mask=@./mask.png"</code></pre>
```

Add the exact create and result response examples:

```html
<h3>创建返回</h3>
<pre><code>{
  "requestId": "0123456789abcdef",
  "count": 1
}</code></pre>

<h3>查询返回</h3>
<pre><code>{
  "jobs": [
    {
      "id": "job_01",
      "status": "SUCCEEDED",
      "progress": 100,
      "images": [
        {
          "publicUrl": "https://image.tcboys.de/uploads/image.png"
        }
      ]
    }
  ]
}</code></pre>
```

Use the same Java endpoint and field names for the 4K tab:

```html
<pre><code>curl -X POST "https://image.tcboys.de/api/images/generate" \
  -H "X-API-Key: &lt;SUB2API_KEY&gt;" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-image-2-4k",
    "prompt": "电影感城市夜景",
    "count": 1,
    "size": "3840x2160"
  }'</code></pre>
```

Document the actual Java authentication error shape:

```html
<h3>错误返回</h3>
<pre><code>{
  "error": "Invalid, expired, or disabled sub2api API Key.",
  "code": "INVALID_API_KEY"
}</code></pre>
```

- [ ] **Step 5: Run client verification and verify GREEN**

Run:

```powershell
node --test client/tests/image-api-docs.test.mjs
node --test client/tests/*.test.mjs
npm run typecheck -w client
npm run build -w client
```

Expected: all tests and type checking pass; Vite build exits 0.

- [ ] **Step 6: Commit the Java API documentation**

```powershell
git add client/src/DocsDialog.vue client/tests/image-api-docs.test.mjs
git commit -m "docs: publish Java image task API"
```

### Task 3: Full Verification

**Files:**
- Verify only; no production files should change.

- [ ] **Step 1: Run the complete backend suite**

```powershell
mvn -f backend/pom.xml test
```

Expected: all backend tests pass with zero failures and errors.

- [ ] **Step 2: Run whitespace and repository checks**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only intentional files are present.

- [ ] **Step 3: Verify the rendered index documentation**

Open `http://localhost:6655/index`, click `文档`, and verify:

- Page title and index content render without a framework error overlay.
- The dialog shows `https://image.tcboys.de/api/images/generate`.
- The dialog shows `/api/images/results/{requestId}` and `X-API-Key`.
- The dialog does not show `api.tcboys.de/v1` or `/v1/images/tasks`.
- The `gpt-image-2-4k` tab switches correctly.
- Browser console has no relevant errors or warnings.

- [ ] **Step 4: Record the final evidence**

Report the focused/backend/client command results and one rendered screenshot. Do not deploy or alter Sub2API as part of this plan.
