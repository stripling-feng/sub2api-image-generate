# Single-Image Request Fanout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Always send `n=1` upstream while allowing every image model to generate 1–10 images per user request.

**Architecture:** Reuse `generation_jobs` as child tasks grouped by `requestId`. The async generation path loops over the requested count, creating, reserving, and submitting one independently billed task per image; existing polling and result grouping remain unchanged.

**Tech Stack:** Spring Boot 3, MyBatis Plus, PostgreSQL/Flyway CLI, JUnit 5, Vue 3.

---

### Task 1: Prove async fanout behavior

**Files:**
- Create: `backend/src/test/java/com/feng/system/module/image/ImageGenerationFanoutTest.java`

- [ ] Write a test that requests four images from an async runtime model.
- [ ] Capture four `GenerationJob` inserts and four gateway request bodies.
- [ ] Assert every job has `count=1`, shared `request_id`, indexes 1–4, and total 4.
- [ ] Assert billing receives four reservations with count 1 and every body contains `n=1`.
- [ ] Run `mvn -f backend/pom.xml -Dtest=ImageGenerationFanoutTest test` and confirm it fails against the current single-request implementation.

### Task 2: Implement child-task fanout and model limits

**Files:**
- Modify: `backend/src/main/java/com/feng/system/module/image/ImageGenerationService.java`
- Create: `backend/src/main/resources/db/migration/V5__single_image_request_fanout.sql`
- Modify: `backend/src/main/java/com/feng/system/config/SchemaGuard.java`
- Modify: `backend/src/test/java/com/feng/system/config/PostgresSchemaTest.java`
- Modify: `backend/src/test/java/com/feng/system/config/SchemaGuardTest.java`

- [ ] Replace the async single-task call with a loop from 1 through `input.count`.
- [ ] Each loop iteration calls the existing async creation logic with index, total, job count 1, billing count 1, settlement count 1, and request body `n=1`.
- [ ] Catch an individual child creation failure so later children still run; the failed job already records its error and billing release.
- [ ] Add V5 SQL: `UPDATE ai_models SET max_count = 10 WHERE model_type = 'IMAGE' AND deleted = 0;`.
- [ ] Raise `SchemaGuard.REQUIRED_VERSION` to 5 and update its test.
- [ ] Run focused backend tests and apply `npm run db:migrate`.

### Task 3: Verify model configuration and builds

**Files:**
- Existing workbench uses `maxCount`; no frontend code change is required.

- [ ] Query `/api/model/images` and confirm all four models return `maxCount=10`.
- [ ] Run `mvn -f backend/pom.xml test`.
- [ ] Run `npm --prefix client run build`.
- [ ] Run `git diff --check`.
