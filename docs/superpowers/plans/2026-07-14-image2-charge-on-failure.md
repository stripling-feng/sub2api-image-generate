# GPT-Image-2 Temporary Charge-on-Failure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a default-off temporary switch that settles every successfully reserved GPT-Image-2 charge, including failed creation, failed polling, and timeout outcomes.

**Architecture:** Keep the existing transactional reservation and settlement SQL unchanged. Add a pure billing-policy function, then route every failed-job billing path through the policy so enabled tests settle while normal production behavior still releases.

**Tech Stack:** TypeScript, Node.js test runner, Express, Prisma, PostgreSQL

---

### Task 1: Add And Test The Failure Billing Policy

**Files:**
- Create: `server/src/imageBillingPolicy.ts`
- Create: `server/src/imageBillingPolicy.test.ts`
- Modify: `server/src/config.ts`
- Modify: `server/.env.example`

- [ ] **Step 1: Write the failing policy tests**

```ts
import assert from "node:assert/strict";
import test from "node:test";
import { failedImageBillingAction } from "./imageBillingPolicy.js";

test("failed image jobs settle when temporary charging is enabled", () => {
  assert.equal(failedImageBillingAction(true), "settle");
});

test("failed image jobs release when temporary charging is disabled", () => {
  assert.equal(failedImageBillingAction(false), "release");
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `npm test -w server -- --test-name-pattern="failed image jobs"`

Expected: FAIL because `imageBillingPolicy.js` does not exist.

- [ ] **Step 3: Implement the minimal policy and configuration**

```ts
// server/src/imageBillingPolicy.ts
export function failedImageBillingAction(chargeOnFailure: boolean): "settle" | "release" {
  return chargeOnFailure ? "settle" : "release";
}
```

Add to `server/src/config.ts`:

```ts
image2ChargeOnFailure: process.env.IMAGE2_CHARGE_ON_FAILURE?.trim().toLowerCase() === "true",
```

Add to `server/.env.example`:

```env
# Temporary test switch. Failed GPT-Image-2 jobs are charged after reservation when true.
IMAGE2_CHARGE_ON_FAILURE="false"
```

- [ ] **Step 4: Run focused and full server tests and verify GREEN**

Run: `npm test -w server -- --test-name-pattern="failed image jobs"`

Expected: 2 passing tests.

Run: `npm test -w server`

Expected: all server tests pass.

### Task 2: Apply The Policy To Every Failed Billing Path

**Files:**
- Modify: `server/src/index.ts`
- Modify: `server/src/imageTaskPoller.ts`

- [ ] **Step 1: Add a shared settlement/release result helper in the poller module's billing boundary**

Use `failedImageBillingAction(config.image2ChargeOnFailure)` at every failure site. For the settle branch call:

```ts
const settled = await settleImageCharge({
  jobId,
  apiKeyId,
  userId,
  accountId,
  amountUsd,
  count,
  size,
  operation,
  durationMs
});
```

Persist `billingStatus: "CHARGED"`, `billingUsageLogId: settled.usageLogId`, and `billingSettledAt`. For the release branch retain the current `releaseImageCharge` call and `RELEASED` status.

- [ ] **Step 2: Change the request-creation failure path**

Compute `operation` before the upstream call so the catch block can use it. If reservation exists and the switch is enabled, settle even for HTTP errors or a missing task ID. Preserve `status: "FAILED"`, `upstreamStatus: "create_failed"`, and the original error message.

- [ ] **Step 3: Change asynchronous failure and timeout handling**

In `failJob`, settle reserved charges when the switch is enabled. Persist the usage log ID and settled timestamp while leaving the generation job failed.

- [ ] **Step 4: Change abandoned-reservation recovery**

Include `CHARGE_FAILED` in the recovery query. When the switch is enabled, settle recoverable reservations even if `upstreamTaskId` is null; when disabled, retain release behavior. Failed billing attempts remain recoverable and record `billingError`.

- [ ] **Step 5: Run server tests and typecheck**

Run: `npm test -w server`

Expected: all tests pass.

Run: `npm run typecheck -w server`

Expected: exit code 0.

### Task 3: Enable The Local Test Switch And Verify Runtime

**Files:**
- Modify: `server/.env` (ignored local configuration)

- [ ] **Step 1: Enable the local-only switch**

```env
IMAGE2_CHARGE_ON_FAILURE="true"
```

- [ ] **Step 2: Build the backend**

Run: `npm run build -w server`

Expected: exit code 0 and updated `server/dist` output.

- [ ] **Step 3: Restart the backend and verify health**

Restart the process listening on port 5001, then run:

```powershell
Invoke-WebRequest -Uri 'http://localhost:5001/api/health' -UseBasicParsing
```

Expected: HTTP 200 with `{"ok":true}`.

- [ ] **Step 4: Do not trigger a paid generation automatically**

The user submits the test request. Afterward inspect the matching local job and sub2api records to confirm `billingStatus = CHARGED`, a `usage_logs` row exists for `image-workbench:<jobId>`, and the balance decreased exactly once.

