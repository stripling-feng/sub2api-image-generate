import assert from "node:assert/strict";
import test from "node:test";
import { decideImageTaskState } from "./imageTaskState.js";

test("completed upstream tasks win over local elapsed timeout", () => {
  assert.equal(decideImageTaskState("completed", 24 * 60 * 60 * 1000, 30 * 60 * 1000), "completed");
  assert.equal(decideImageTaskState("succeeded", 24 * 60 * 60 * 1000, 30 * 60 * 1000), "completed");
});

test("only unfinished upstream tasks time out", () => {
  assert.equal(decideImageTaskState("processing", 31 * 60 * 1000, 30 * 60 * 1000), "timeout");
  assert.equal(decideImageTaskState("processing", 29 * 60 * 1000, 30 * 60 * 1000), "pending");
  assert.equal(decideImageTaskState("failed", 60_000, 30 * 60 * 1000), "failed");
});
