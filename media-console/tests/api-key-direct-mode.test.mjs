import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const appSource = await readFile(new URL("../src/App.vue", import.meta.url), "utf8");
const videoSource = await readFile(new URL("../src/VideoWorkbench.vue", import.meta.url), "utf8");
const storeSource = await readFile(new URL("../src/stores/workbench.ts", import.meta.url), "utf8");
const combinedSource = [appSource, videoSource, storeSource].join("\n");

test("front-office workbenches no longer call session bind endpoints", () => {
  assert.doesNotMatch(combinedSource, /\/api\/session\/bind/);
  assert.doesNotMatch(combinedSource, /\bstore\.bind\(/);
});

test("image workbench connects by saving the typed API key and loading business data", () => {
  assert.match(storeSource, /async connect\(apiKey: string\)/);
  assert.match(storeSource, /localStorage\.setItem\("apiKey", normalizedApiKey\)/);
  assert.match(storeSource, /Promise\.all\(\[this\.loadHistory\(\), this\.loadTemplates\(\)\]\)/);
});

test("video workbench gates API-key history without requiring a profile object", () => {
  assert.doesNotMatch(videoSource, /if \(!store\.profile\) return/);
  assert.match(videoSource, /if \(!hasApiKey\(\)\) return/);
});
