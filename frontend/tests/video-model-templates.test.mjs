import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../src/views/model/VideoModelView.vue", import.meta.url), "utf8");

test("admin exposes Omni video templates on the unified endpoint", () => {
  for (const key of ["omni-fast", "omni-fast-no-water", "omni-v2v", "omni-v2v-no-water"]) {
    assert.match(source, new RegExp(`key: '${key}'[^\\n]*path: '/v1/videos'`), key);
  }
  assert.match(source, /key: 'grok-video'[^\n]*path: '\/v1\/videos'/);
  assert.match(source, /key: 'grok-video-1\.5'[^\n]*path: '\/v1\/videos'/);
});

test("admin labels Omni fast and video-to-video capabilities accurately", () => {
  assert.match(source, /key\.startsWith\('omni-fast'\)[\s\S]*'文生 \/ 图生'[\s\S]*'首尾帧'[\s\S]*'最多 5 图'[\s\S]*'10 秒 \/ 720p'[\s\S]*'16:9 \/ 9:16'/);
  assert.match(source, /key\.startsWith\('omni-v2v'\)[\s\S]*'视频必填'[\s\S]*'1–2 视频'[\s\S]*'可选 2 图'[\s\S]*'10 秒 \/ 720p'[\s\S]*'16:9 \/ 9:16'/);
});
