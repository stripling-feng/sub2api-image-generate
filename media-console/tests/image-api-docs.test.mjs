import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../src/DocsDialog.vue", import.meta.url), "utf8");

test("index docs contain a complete third-party image API integration path", () => {
  assert.match(source, /api\.tcboys\.de\/v1\/images\/generations/);
  assert.match(source, /api\.tcboys\.de\/v1\/images\/tasks\/\{task_id\}/);
  assert.doesNotMatch(source, /image\.tcboys\.de\/v1/);
  assert.doesNotMatch(source, /ai\.cangyuansuanli\.cn/);
  assert.doesNotMatch(source, /cy-img1-gpt-image-2/);
  assert.match(source, /"model": "gpt-image-2"/);
  assert.match(source, /Authorization: Bearer/);
  assert.match(source, /curl[\s\S]*images\/generations/);
  assert.match(source, /curl[\s\S]*images\/edits/);
  assert.doesNotMatch(source, /images\/generations\/\{task_id\}/);
  assert.doesNotMatch(source, /images\/edits\/\{task_id\}/);
  assert.match(source, /queued[\s\S]*completed[\s\S]*failed/);
  assert.doesNotMatch(source, /images\/\{task_id\}\/content/);
  assert.match(source, /\"error\"/);
});
