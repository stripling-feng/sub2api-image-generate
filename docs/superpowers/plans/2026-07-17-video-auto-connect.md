# Video Workbench Auto Connect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the video workbench connect button and automatically bind a valid API Key after a 700ms input pause.

**Architecture:** Keep the existing `connect()` request path and add the same debounced API Key watcher already used by the image workbench. The watcher owns local storage synchronization, stale profile clearing, and timer cleanup; no new dependency or shared abstraction is introduced.

**Tech Stack:** Vue 3 Composition API, TypeScript, Node.js built-in test runner, Vite

---

### Task 1: Lock the expected source behavior

**Files:**
- Create: `client/tests/video-auto-connect.test.mjs`
- Test: `client/tests/video-auto-connect.test.mjs`

- [ ] **Step 1: Write the failing test**

```js
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../src/VideoWorkbench.vue", import.meta.url), "utf8");

test("video workbench automatically connects without a connect button", () => {
  assert.doesNotMatch(source, />\s*连接\s*<\/button>/);
  assert.match(source, /watch\(apiKey,/);
  assert.match(source, /window\.setTimeout\([\s\S]*?700\)/);
  assert.match(source, /window\.clearTimeout\(autoBindTimer\)/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test client/tests/video-auto-connect.test.mjs`

Expected: FAIL because the template still contains the connect button and no API Key watcher exists.

### Task 2: Implement automatic binding

**Files:**
- Modify: `client/src/VideoWorkbench.vue:20-25`
- Modify: `client/src/VideoWorkbench.vue:117-124`
- Modify: `client/src/VideoWorkbench.vue:217-223`
- Modify: `client/src/VideoWorkbench.vue:233-237`
- Modify: `client/src/VideoWorkbench.vue:365-367`

- [ ] **Step 1: Add timer state and normalize the existing connect path**

Add `let autoBindTimer: number | undefined;`. In `connect()`, trim the current API Key, return for keys shorter than eight characters, store the normalized value, call `/api/session/bind`, then load history.

- [ ] **Step 2: Add the debounced API Key watcher**

```ts
watch(apiKey, (value) => {
  const normalized = value.trim();
  localStorage.setItem("baseUrl", fixedBaseUrl);
  localStorage.setItem("apiKey", normalized);
  store.profile = null;
  if (autoBindTimer) window.clearTimeout(autoBindTimer);
  if (normalized.length < 8) return;
  autoBindTimer = window.setTimeout(() => {
    connect().catch(cause => { error.value = cause instanceof Error ? cause.message : "连接失败"; });
  }, 700);
});
```

- [ ] **Step 3: Remove the button and obsolete styles**

Delete the toolbar button with `@click="connect"`, remove `.connect-action`, and let the API Key input fill the existing connection cluster.

- [ ] **Step 4: Clean up the timer on unmount**

Replace `onUnmounted(stopPolling)` with a callback that calls `stopPolling()` and clears `autoBindTimer` when defined.

- [ ] **Step 5: Run the focused test**

Run: `node --test client/tests/video-auto-connect.test.mjs`

Expected: PASS, 1 test and 0 failures.

### Task 3: Verify and commit

**Files:**
- Test: `client/tests/video-auto-connect.test.mjs`
- Modify: `client/src/VideoWorkbench.vue`

- [ ] **Step 1: Run static verification**

Run: `npm --prefix client run typecheck`

Expected: exit code 0.

- [ ] **Step 2: Run production build**

Run: `npm --prefix client run build`

Expected: exit code 0; the existing Vite bundle-size warning may remain.

- [ ] **Step 3: Run browser verification**

Open `http://localhost:62530/video`, verify the Connect button is absent, enter a valid API Key, wait at least 700ms, and confirm profile balance/history loads without a manual click. Confirm console logs contain no relevant warnings or errors and the toolbar has no overflow at 1440x900 and 390x844.

- [ ] **Step 4: Commit only scoped files**

```powershell
git add -- client/src/VideoWorkbench.vue client/tests/video-auto-connect.test.mjs
git commit -m "feat: auto connect video workbench" --only -- client/src/VideoWorkbench.vue client/tests/video-auto-connect.test.mjs
```
