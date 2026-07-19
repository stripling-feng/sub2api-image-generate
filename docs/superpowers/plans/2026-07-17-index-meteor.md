# Index Meteor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `/index` hero sphere with a responsive cinematic procedural meteor entering from the upper-right.

**Architecture:** Keep the existing Vue component and lifecycle boundary. Replace the sphere scene with deterministic Three.js geometry deformation, generated canvas maps, a small set of particle systems, and the installed Three.js postprocessing modules; update only the stage CSS needed for composition.

**Tech Stack:** Vue 3, TypeScript, Three.js 0.185, Vite

---

### Task 1: Replace the sphere renderer with the meteor scene

**Files:**
- Modify: `client/src/IndexPage.vue`

- [ ] **Step 1: Establish the failing visual baseline**

Run the current client and inspect `/index`. The assertion is intentionally visual: the canvas currently contains a smooth blue sphere, has no diagonal atmospheric trail, and uses no `EffectComposer`.

Run: `npm run dev -w client -- --port 6656`

Expected: `/index` renders the existing smooth sphere, confirming the requested state is absent.

- [ ] **Step 2: Add the existing Three.js postprocessing imports**

```ts
import { EffectComposer } from "three/examples/jsm/postprocessing/EffectComposer.js";
import { RenderPass } from "three/examples/jsm/postprocessing/RenderPass.js";
import { UnrealBloomPass } from "three/examples/jsm/postprocessing/UnrealBloomPass.js";
```

- [ ] **Step 3: Replace generated planet textures with deterministic rock maps**

Create one `createMeteorTextures()` function returning `map`, `bumpMap`, and `emissiveMap`. Use a seeded scalar noise function and Canvas 2D drawing so the same surface is produced on every load. The color map must stay in charcoal volcanic-rock values; the bump map combines coarse noise and dark crater circles; the emissive map draws sparse branching orange-white fissures on black.

```ts
function seededNoise(x: number, y: number) {
  const value = Math.sin(x * 127.1 + y * 311.7) * 43758.5453;
  return value - Math.floor(value);
}
```

- [ ] **Step 4: Create a genuinely irregular meteor mesh**

Start with `new THREE.IcosahedronGeometry(1.72, 5)`. For every vertex, normalize its direction and set its radius from three frequency bands plus localized crater depressions. Stretch the body slightly on X/Z, recompute normals, and use `MeshStandardMaterial` with the generated maps, high roughness, low metalness, and subdued emissive fissures.

```ts
const radius = 1.55 + coarse * 0.28 + medium * 0.12 + fine * 0.045 - craterDepth;
position.set(direction.x * radius * 1.12, direction.y * radius * 0.92, direction.z * radius);
geometry.computeVertexNormals();
```

- [ ] **Step 5: Add atmosphere-entry layers**

Add these scene children without introducing new component abstractions:

- An emissive fissure shell sharing the deformed geometry at scale `1.006`.
- Two translucent tapered cone meshes aligned toward the upper-right for hot core and faint outer flame.
- Smoke/dust `THREE.Points` using a generated radial sprite, with desktop/mobile counts of `110/55`.
- Eight/five small displaced `IcosahedronGeometry` fragment meshes distributed around the trail.
- `THREE.FogExp2(0x070a12, 0.055)`, cool hemisphere fill, warm key/rim lights, and a shadow-receiving invisible plane for soft grounding.

- [ ] **Step 6: Add restrained bloom and responsive quality**

Create an `EffectComposer(renderer)`, append `RenderPass` and `UnrealBloomPass`, and render through the composer. On widths below `768px`, cap pixel ratio at `1.25`, reduce bloom strength, particle counts, fragments, and shadow map size; otherwise cap pixel ratio at `1.8`.

```ts
const mobile = window.matchMedia("(max-width: 767px)").matches;
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const bloom = new UnrealBloomPass(new THREE.Vector2(1, 1), mobile ? 0.42 : 0.68, 0.72, 0.56);
```

- [ ] **Step 7: Animate and dispose the scene**

Rotate the meteor slowly around two axes, pulse fissure intensity slightly, recycle smoke positions along the entry vector, drift fragments, and render with the composer. Multiply speeds by `0.25` under reduced motion. Cleanup must dispose every unique geometry, material, generated texture, composer target, renderer, listener, and canvas.

- [ ] **Step 8: Run static checks**

Run: `npm run typecheck -w client`

Expected: exit code 0.

Run: `npm run build -w client`

Expected: exit code 0 and Vite emits `client/dist`.

### Task 2: Recompose the right-side stage

**Files:**
- Modify: `client/src/IndexPage.vue`

- [ ] **Step 1: Remove obsolete orb-only CSS**

Delete the unused ring, glass, scan, satellite, beam, code-rain, and spherical core rules and their keyframes. Keep only stage sizing, canvas layout, pointer tilt, entry animation, and responsive media queries.

- [ ] **Step 2: Position the meteor in the approved A composition**

Use a rectangular stage rather than a circular one, shift it right, and allow overflow so roughly one quarter of the meteor body sits outside the viewport. The canvas remains pointer-transparent and does not cover the headline or cards.

```css
.hero-orb {
  width: min(50vw, 820px);
  min-width: 560px;
  aspect-ratio: 1.18;
  margin-right: clamp(-190px, -9vw, -80px);
}

.orbital-system :deep(canvas) {
  filter: drop-shadow(0 28px 52px rgba(0, 0, 0, 0.48));
}
```

- [ ] **Step 3: Add mobile composition rules**

At the existing mobile breakpoint, reduce the minimum stage size, keep the body right-aligned and partially clipped, and preserve readable hero text. Do not add or change visible copy.

- [ ] **Step 4: Re-run static checks**

Run: `npm run typecheck -w client && npm run build -w client`

Expected: both commands exit 0.

### Task 3: Browser verification

**Files:**
- Verify: `client/src/IndexPage.vue`

- [ ] **Step 1: Verify desktop rendering**

Open `http://localhost:6656/index` at `1440x900`. Confirm the canvas is nonblank, the silhouette is visibly irregular, cracks glow orange-red, the trail points upper-right, smoke and fragments move, about 25% of the body is beyond the right edge, and no text overlaps.

- [ ] **Step 2: Verify mobile degradation**

Open the same URL at `390x844`. Confirm the canvas stays nonblank, the meteor remains recognizable, text and controls fit, the stage does not create horizontal page overflow, and reduced particle density is visible.

- [ ] **Step 3: Check browser health**

Inspect console errors and sample nontransparent canvas pixels in both viewports. Expected: no runtime errors and the canvas contains a meaningful number of nontransparent pixels.

- [ ] **Step 4: Capture screenshots and compare**

Save desktop and mobile screenshots under `.playwright-mcp/`. Inspect both images directly for material integration, fog, bloom restraint, edge clipping, and the approved A composition.

- [ ] **Step 5: Final repository check**

Run: `git diff --check -- client/src/IndexPage.vue`

Expected: no whitespace errors.
