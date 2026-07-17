# Index Meteor Design

## Goal

Replace the polished sphere on `/index` with a cinematic meteor that reads as an object entering the atmosphere, while preserving the current right-side hero composition and interaction.

## Composition

- The meteor enters diagonally from the upper-right toward the lower-left.
- Roughly 25% of the body remains outside the right viewport edge.
- The flame, smoke, and dust trail extends toward the upper-right.
- The meteor must not cover the left headline or feature cards.

## Rendering

- Reuse the existing Three.js scene in `client/src/IndexPage.vue`; add no dependency beyond the installed `three` package.
- Replace all sphere layers with a subdivided `IcosahedronGeometry` whose vertices are displaced by deterministic multi-frequency noise and several localized crater depressions.
- Use a dark volcanic-rock `MeshStandardMaterial` with procedurally generated color, displacement, and normal textures. Add scorched edges through roughness, lighting, and dark surface variation.
- Render orange-red energy fissures as a slightly expanded emissive mesh with a generated crack mask.
- Add a tapered flame trail, smoke and dust particles, and a small field of irregular rock fragments behind and around the body.
- Use `EffectComposer`, `RenderPass`, and `UnrealBloomPass` for restrained bloom. Add scene fog, warm rim lighting, cool ambient fill, and soft shadowing so the object shares the page lighting.

## Motion And Interaction

- Keep the current pointer-driven stage tilt, but slow the meteor tumble so it retains weight.
- Drift trail particles and fragments along the entry vector and recycle them in place.
- Under `prefers-reduced-motion`, reduce rotation and particle speed.

## Responsive Performance

- Desktop uses the full renderer pixel ratio cap, particle counts, shadows, and bloom strength.
- Mobile lowers pixel ratio, particle counts, shadow cost, and bloom strength while preserving the same composition and materials.
- Resize the renderer and composer from the actual mount bounds.

## Cleanup And Verification

- Dispose geometries, materials, generated textures, renderer, composer targets, and event listeners on unmount.
- Run the client typecheck and production build.
- Verify `/index` in the in-app browser at desktop and mobile viewports, including canvas pixel checks, screenshots, layout overlap, responsive overflow, and console errors.

## Out Of Scope

- No downloaded meteor model or static meteor image.
- No new controls, copy, or page sections.
- No physics simulation or collision behavior.
