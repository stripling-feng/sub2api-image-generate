import assert from "node:assert/strict";
import test from "node:test";
import { failedImageBillingAction, finalizeFailedImageBilling, settleReservedImageBilling } from "./imageBillingPolicy.js";

test("failed image jobs settle when temporary charging is enabled", () => {
  assert.equal(failedImageBillingAction(true), "settle");
});

test("failed image jobs release when temporary charging is disabled", () => {
  assert.equal(failedImageBillingAction(false), "release");
});

const context = {
  jobId: "job-1",
  apiKeyId: "key-1",
  userId: "user-1",
  accountId: "account-1",
  amountUsd: "0.5000000000",
  count: 1,
  size: "1:1",
  operation: "generations" as const,
  durationMs: 123
};

test("failure billing settles the reservation when temporary charging is enabled", async () => {
  let settleCalls = 0;
  let releaseCalls = 0;
  const result = await finalizeFailedImageBilling(context, {
    chargeOnFailure: true,
    settle: async () => {
      settleCalls += 1;
      return { usageLogId: "usage-1" };
    },
    release: async () => {
      releaseCalls += 1;
      return "released";
    }
  });

  assert.equal(settleCalls, 1);
  assert.equal(releaseCalls, 0);
  assert.equal(result.billingStatus, "CHARGED");
  assert.equal(result.billingUsageLogId, "usage-1");
  assert.ok(result.billingSettledAt instanceof Date);
});

test("failure billing releases the reservation when temporary charging is disabled", async () => {
  let settleCalls = 0;
  let releaseCalls = 0;
  const result = await finalizeFailedImageBilling(context, {
    chargeOnFailure: false,
    settle: async () => {
      settleCalls += 1;
      return { usageLogId: "usage-1" };
    },
    release: async () => {
      releaseCalls += 1;
      return "released";
    }
  });

  assert.equal(settleCalls, 0);
  assert.equal(releaseCalls, 1);
  assert.equal(result.billingStatus, "RELEASED");
  assert.equal(result.billingUsageLogId, undefined);
  assert.equal(result.billingSettledAt, undefined);
});

test("reserved image billing settles immediately when temporary charging is enabled", async () => {
  let settleCalls = 0;
  const result = await settleReservedImageBilling(context, {
    chargeImmediately: true,
    settle: async () => {
      settleCalls += 1;
      return { usageLogId: "usage-click" };
    }
  });

  assert.equal(settleCalls, 1);
  assert.equal(result.billingStatus, "CHARGED");
  assert.equal(result.billingUsageLogId, "usage-click");
  assert.ok(result.billingSettledAt instanceof Date);
});

test("reserved image billing stays reserved when temporary charging is disabled", async () => {
  let settleCalls = 0;
  const result = await settleReservedImageBilling(context, {
    chargeImmediately: false,
    settle: async () => {
      settleCalls += 1;
      return { usageLogId: "usage-click" };
    }
  });

  assert.equal(settleCalls, 0);
  assert.equal(result.billingStatus, "RESERVED");
  assert.equal(result.billingUsageLogId, undefined);
  assert.equal(result.billingSettledAt, undefined);
});
