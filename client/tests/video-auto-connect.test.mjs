import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../src/VideoWorkbench.vue", import.meta.url), "utf8");

test("video workbench automatically connects without a connect button", () => {
  assert.doesNotMatch(source, />\s*连接\s*<\/button>/);
  assert.match(source, /watch\(apiKey,/);
  assert.match(source, /window\.setTimeout\([\s\S]*?700\)/);
  assert.match(source, /window\.clearTimeout\(autoBindTimer\)/);
});
