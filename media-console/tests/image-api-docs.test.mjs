import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../src/DocsPage.vue", import.meta.url), "utf8");
const indexSource = await readFile(new URL("../src/IndexPage.vue", import.meta.url), "utf8");
const routerSource = await readFile(new URL("../src/main.ts", import.meta.url), "utf8");

test("index docs contain a complete third-party image API integration path", () => {
  assert.match(routerSource, /path: "\/docs"/);
  assert.match(indexSource, /navigateParent\('\/docs'\)/);
  assert.doesNotMatch(indexSource, /DocsDialog/);
  assert.match(source, /\/api\/docs\/image/);
  assert.match(source, /\/api\/docs\/video/);
  assert.match(source, /marked\.parse/);
  assert.match(source, /DOMPurify\.sanitize/);
  assert.match(source, /extractHeadings/);
  assert.match(source, /scrollIntoView/);
  assert.match(source, /doc-loading/);
  assert.match(source, /doc-error/);
});
