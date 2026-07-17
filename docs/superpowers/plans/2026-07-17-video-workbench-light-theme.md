# Video Workbench Light Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `/video` an isolated white workspace theme without changing other routes or video behavior.

**Architecture:** Override the existing global color tokens at the `.video-studio` root so every descendant uses the light palette through normal CSS inheritance. Keep the current template, state, and global stylesheet unchanged.

**Tech Stack:** Vue 3 scoped CSS, existing CSS custom properties, Vite.

---

### Task 1: Apply and verify the isolated light palette

**Files:**
- Modify: `client/src/VideoWorkbench.vue:330`

- [ ] **Step 1: Add local theme tokens to `.video-studio`**

Add these declarations before its layout properties:

```css
.video-studio {
  --bg: #f5f7fa;
  --bg-soft: #f8fafc;
  --panel: #ffffff;
  --panel-strong: #f3f5f7;
  --panel-soft: #f7f8fa;
  --text: #171a21;
  --muted: #697386;
  --line: rgba(23, 26, 33, 0.12);
  --line-strong: rgba(23, 26, 33, 0.22);
  --accent: #0891b2;
  --accent-2: #059669;
  --accent-strong: #0e7490;
  --danger: #dc2626;
  --input: #ffffff;
  --shadow: rgba(23, 26, 33, 0.12);
}
```

- [ ] **Step 2: Run static checks**

Run: `npm --prefix client run typecheck`

Expected: PASS with exit code 0.

Run: `npm --prefix client run build`

Expected: PASS with exit code 0; the existing bundle-size warning is allowed.

- [ ] **Step 3: Verify rendered isolation**

Open `http://localhost:62530/video` at `1440x900` and `390x844` and verify white surfaces, readable contrast, cyan/green primary action, no horizontal overflow, and a black video playback surface.

Open `/` and verify that its existing theme is unchanged.

- [ ] **Step 4: Commit only the component**

```bash
git commit --only client/src/VideoWorkbench.vue -m "style: use light video workspace theme"
```
