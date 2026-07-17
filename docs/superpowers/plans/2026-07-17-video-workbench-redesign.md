# Video Workbench Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing three-column video dashboard with the approved canvas-first creation workspace while preserving all current video behavior.

**Architecture:** Keep API calls, form state, polling, billing estimates, history, and deletion inside the existing `VideoWorkbench.vue`. Add only local presentation state for creation mode, material controls, and the collapsible history drawer, then replace the template and scoped CSS around the existing handlers.

**Tech Stack:** Vue 3 Composition API, TypeScript, Vue Router, Lucide Vue, scoped CSS, Vite.

---

### Task 1: Add local workspace presentation state

**Files:**
- Modify: `client/src/VideoWorkbench.vue`

- [ ] **Step 1: Run the current type check as the baseline**

Run: `npm --prefix client run typecheck`

Expected: PASS before the layout change.

- [ ] **Step 2: Add mode and drawer state using existing Vue primitives**

Add local state and a computed pending count next to the existing refs:

```ts
type CreationMode = "text" | "image" | "frames";

const creationMode = ref<CreationMode>("text");
const historyOpen = ref(true);
const materialsOpen = ref(false);
const pendingCount = computed(() => history.value.filter(job => job.status === "PENDING").length);
```

Add a mode setter that clears incompatible inputs through the current refs and form:

```ts
function setCreationMode(mode: CreationMode) {
  creationMode.value = mode;
  materialsOpen.value = mode !== "text";
  if (mode === "text") {
    referenceImages.value = [];
    firstFrame.value = null;
    lastFrame.value = null;
    form.referenceVideoUrls = "";
    form.referenceAudioUrls = "";
  } else if (mode === "image") {
    firstFrame.value = null;
    lastFrame.value = null;
  } else {
    referenceImages.value = [];
    form.referenceVideoUrls = "";
    form.referenceAudioUrls = "";
  }
}
```

- [ ] **Step 3: Run the type check**

Run: `npm --prefix client run typecheck`

Expected: PASS with no Vue or TypeScript errors.

### Task 2: Replace the dashboard structure with the canvas-first workspace

**Files:**
- Modify: `client/src/VideoWorkbench.vue`

- [ ] **Step 1: Replace the template while reusing current handlers**

Build these five regions without changing API methods:

```vue
<main class="video-studio" :class="{ 'history-collapsed': !historyOpen }">
  <header class="studio-toolbar"><!-- back, title, API key, balance --></header>
  <aside class="mode-rail"><!-- text/image/frames buttons --></aside>
  <section class="stage"><!-- batch results and empty state --></section>
  <section class="creation-dock"><!-- prompt, materials, model parameters, count, price, generate --></section>
  <aside class="task-drawer"><!-- existing history list, pagination, delete actions --></aside>
</main>
```

Use native buttons and existing Lucide icons. Keep the existing `generate`, `loadResults`, `openHistory`, `deleteJob`, `deleteAll`, `selectImages`, and `selectFrame` handlers unchanged.

- [ ] **Step 2: Replace the scoped CSS with stable desktop and mobile layouts**

Desktop grid:

```css
.video-studio {
  height: 100dvh;
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr) 320px;
  grid-template-rows: 58px minmax(0, 1fr);
  overflow: hidden;
}
.stage { position: relative; min-width: 0; min-height: 0; overflow: auto; }
.creation-dock { position: absolute; left: 50%; bottom: 20px; transform: translateX(-50%); width: min(860px, calc(100% - 40px)); }
.history-collapsed { grid-template-columns: 68px minmax(0, 1fr) 52px; }
```

At `max-width: 900px`, hide the drawer body, change the mode rail to a horizontal segmented control, keep the stage unframed, and make the creation dock flow below results so no content overlaps or scrolls horizontally.

- [ ] **Step 3: Run static verification**

Run: `npm --prefix client run typecheck`

Expected: PASS.

Run: `npm --prefix client run build`

Expected: PASS and Vite emits `client/dist`.

- [ ] **Step 4: Verify the rendered workspace in a browser**

Run: `npm --prefix client run dev -- --port 62530`

Check `/video` at desktop `1440x900` and mobile `390x844`:

- mode buttons show only compatible material controls;
- results remain the largest visual region;
- the dock does not cover video controls or overflow the viewport;
- the history drawer collapses and restores;
- model parameters and price update from the loaded model configuration;
- generate, polling, history restore, pagination, and deletion still call their existing handlers.

- [ ] **Step 5: Commit the implementation only**

```bash
git commit --only client/src/VideoWorkbench.vue -m "feat: redesign video generation workspace"
```
