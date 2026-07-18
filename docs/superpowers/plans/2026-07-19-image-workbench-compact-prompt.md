# Image Workbench Compact Prompt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove two nonessential image-workbench labels and reduce the default prompt textarea height.

**Architecture:** Keep the change in the existing Vue template and shared stylesheet. Add one dependency-free Node source check because this is static markup and CSS behavior.

**Tech Stack:** Vue 3, CSS, Node.js `assert`

---

### Task 1: Add the UI regression check

**Files:**
- Create: `client/tests/image-workbench-compact-ui.test.mjs`

- [ ] **Step 1: Write the failing test**

```js
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const app = readFileSync(new URL("../src/App.vue", import.meta.url), "utf8");
const styles = readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");

assert.doesNotMatch(app, /切换到视频工作台/);
assert.doesNotMatch(app, /服务商：/);
assert.match(app, /预计扣费：\$\{\{ estimatedChargeUsd \}\}/);
assert.match(styles, /\.prompt\s*\{[^}]*min-height:\s*120px;[^}]*height:\s*clamp\(120px,\s*18vh,\s*180px\);[^}]*max-height:\s*24vh;/s);
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node client/tests/image-workbench-compact-ui.test.mjs`
Expected: FAIL because the link, provider label, and old prompt heights still exist.

### Task 2: Apply the minimal UI changes

**Files:**
- Modify: `client/src/App.vue`
- Modify: `client/src/styles.css`

- [ ] **Step 1: Remove the video-workbench entry and provider label**

Delete the `control-nav` block from `App.vue` and replace the pricing line with:

```vue
<p class="muted">预计扣费：${{ estimatedChargeUsd }}</p>
```

- [ ] **Step 2: Reduce prompt height**

Replace the prompt sizing declarations with:

```css
min-height: 120px;
height: clamp(120px, 18vh, 180px);
max-height: 24vh;
```

- [ ] **Step 3: Run the regression check**

Run: `node client/tests/image-workbench-compact-ui.test.mjs`
Expected: PASS with exit code 0.

### Task 3: Verify the client

**Files:**
- Verify: `client/src/App.vue`
- Verify: `client/src/styles.css`

- [ ] **Step 1: Run type checking and build**

Run: `npm run typecheck && npm run build -w client`
Expected: both commands exit 0.

- [ ] **Step 2: Verify the rendered image workbench**

Open the local image workbench at desktop and mobile widths. Confirm the removed labels are absent, the prompt is visibly shorter, the pricing text and generate button remain visible, and there are no relevant console errors.

- [ ] **Step 3: Commit only task files**

```bash
git add client/tests/image-workbench-compact-ui.test.mjs client/src/App.vue client/src/styles.css
git commit -m "style: simplify image workbench controls"
```
