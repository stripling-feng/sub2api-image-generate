import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const app = readFileSync(new URL("../src/App.vue", import.meta.url), "utf8");
const styles = readFileSync(new URL("../src/styles.css", import.meta.url), "utf8");

assert.doesNotMatch(app, /切换到视频工作台/);
assert.doesNotMatch(app, /服务商：/);
assert.match(app, /预计扣费：\$\{\{ estimatedChargeUsd \}\}/);
assert.match(styles, /\.prompt\s*\{[^}]*min-height:\s*120px;[^}]*height:\s*clamp\(120px,\s*18vh,\s*180px\);[^}]*max-height:\s*24vh;/s);
