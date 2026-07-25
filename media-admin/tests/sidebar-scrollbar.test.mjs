import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const sidebarLayouts = [
  "../src/components/layout/ClassicSidebarLayout.vue",
  "../src/components/layout/SplitSidebarLayout.vue",
  "../src/components/layout/TopSidebarLayout.vue",
];

test("sidebar scrollbar view does not force a phantom overflow height", async () => {
  for (const layoutPath of sidebarLayouts) {
    const source = await readFile(new URL(layoutPath, import.meta.url), "utf8");
    assert.doesNotMatch(
      source,
      /\.sidebar\s+:deep\(\.el-scrollbar__view\)\s*\{\s*min-height:\s*100%;\s*\}/,
      layoutPath,
    );
  }
});
