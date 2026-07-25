import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const apiSource = await readFile(new URL("../src/api/system.js", import.meta.url), "utf8");
const routerSource = await readFile(new URL("../src/router/index.js", import.meta.url), "utf8");
const viewSource = await readFile(new URL("../src/views/system/PublicDocsConfigView.vue", import.meta.url), "utf8").catch(() => "");

test("admin exposes public docs config management", () => {
  assert.match(apiSource, /publicDocsConfigApi/);
  assert.match(apiSource, /\/api\/system\/docs-config/);
  assert.match(routerSource, /PublicDocsConfigView\.vue/);
  assert.match(viewSource, /docs\.image\.file-id/);
  assert.match(viewSource, /docs\.video\.file-id/);
  assert.match(viewSource, /accept="\.md,text\/markdown"/);
  assert.match(viewSource, /uploadFileApi\.upload/);
  assert.match(viewSource, /publicDocsConfigApi\.update/);
  assert.match(viewSource, /图片文档/);
  assert.match(viewSource, /视频文档/);
});
