import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../src/VideoWorkbench.vue", import.meta.url), "utf8");
const adminSource = await readFile(new URL("../../frontend/src/views/model/VideoModelView.vue", import.meta.url), "utf8");
const migrationSource = await readFile(new URL("../../backend/src/main/resources/db/migration/V9__omni_video_models.sql", import.meta.url), "utf8");
const v10MigrationSource = await readFile(new URL("../../backend/src/main/resources/db/migration/V10__omni_v2v_optional_references.sql", import.meta.url), "utf8").catch(() => "");

test("video workbench automatically connects without a connect button", () => {
  assert.doesNotMatch(source, />\s*连接\s*<\/button>/);
  assert.match(source, /watch\(apiKey,/);
  assert.match(source, /window\.setTimeout\([\s\S]*?700\)/);
  assert.match(source, /window\.clearTimeout\(autoBindTimer\)/);
});

test("video duration uses a discrete range slider", () => {
  assert.match(source, /field\.key === ['"]duration['"][\s\S]*type="range"/);
  assert.match(source, /setDurationFromSlider/);
  assert.match(source, /numericOptions/);
});

test("video duration defaults to the longest supported option", () => {
  assert.match(source, /form\.duration = longestDurationOption\(\)/);
  assert.match(source, /function longestDurationOption\(\)/);
});

test("grok multi-image duration is capped at ten seconds", () => {
  assert.match(source, /activeModel\.value\?\.model === ['"]grok-video['"][\s\S]*referenceImages\.value\.length > 1[\s\S]*value <= 10/);
  assert.match(source, /function clampDuration/);
  assert.match(source, /removeReferenceImage\(index\)/);
});

test("video toolbar keeps balance next to credentials", () => {
  assert.doesNotMatch(source, /toolbar-brand|brand-mark|返回图片工作台/);
  assert.match(source, /<div class="account-cluster">[\s\S]*class="balance"[\s\S]*<div class="connect-cluster">[\s\S]*API Key/);
  assert.doesNotMatch(source, /\.connect-cluster \{[^}]*margin-left: auto/);
});

test("video history differentiates completed and failed jobs", () => {
  assert.match(source, /:class="job\.status\.toLowerCase\(\)"/);
  assert.match(source, /:title="job\.status === 'FAILED'/);
  assert.match(source, /\.history-video-item\.succeeded/);
  assert.match(source, /\.history-video-item\.failed/);
});

test("video workbench uploads material files into url fields", () => {
  assert.match(source, /api\.postForm<\{ url: string \}>\("\/api\/videos\/uploads"/);
  assert.match(source, /referenceVideos\.value\.map\(uploadMaterialFile\)/);
  assert.match(source, /referenceAudios\.value\.map\(uploadMaterialFile\)/);
  assert.match(source, /referenceVideoUrls: uploadedVideoUrls/);
  assert.match(source, /referenceAudioUrls: uploadedAudioUrls/);
  assert.doesNotMatch(source, /<textarea v-model="form\.referenceVideoUrls"/);
});

test("image-to-video reference videos are listed and removable", () => {
  assert.match(source, /const referenceVideos = ref<File\[\]>\(\[\]\)/);
  assert.match(source, /function removeReferenceVideo\(index: number\)/);
  assert.match(source, /v-for="\(url, index\) in videoPreviewUrls"/);
  assert.match(source, /@click="removeReferenceVideo\(index\)"/);
});

test("image-to-video reference audios match video upload chips", () => {
  assert.match(source, /const referenceAudios = ref<File\[\]>\(\[\]\)/);
  assert.match(source, /function removeReferenceAudio\(index: number\)/);
  assert.match(source, /v-for="\(file, index\) in referenceAudios"/);
  assert.match(source, /@click="removeReferenceAudio\(index\)"/);
  assert.doesNotMatch(source, /<textarea v-model="form\.referenceAudioUrls"/);
});

test("uploaded materials render previews instead of filenames", () => {
  assert.match(source, /class="material-preview image-preview"/);
  assert.match(source, /<img :src="imagePreviewUrls\[index\]"/);
  assert.match(source, /class="material-preview video-preview"/);
  assert.match(source, /<video :src="url" controls/);
  assert.match(source, /class="material-preview audio-preview"/);
  assert.doesNotMatch(source, /class="file-chip">\{\{ file\.name \}\}/);
});

test("video generation shows a fullscreen loading overlay", () => {
  assert.match(source, /v-if="loading" class="fullscreen-loading"/);
  assert.match(source, /\.fullscreen-loading/);
});

test("text-to-video hides only models that require an image", () => {
  assert.match(source, /type CreationMode = "text" \| "image" \| "frames"/);
  assert.match(source, /case "text":[\s\S]*!item\.defaults\?\.requiresImage/);
  assert.match(source, /v-for="model in selectableModels"/);
  assert.match(source, /!selectableModels\.value\.some\(item => item\.model === form\.model\)/);
});

test("image mode includes video-reference models without a separate v2v mode", () => {
  assert.match(source, /case "image": return Number\(item\.defaults\?\.images \?\? item\.maxReferenceImages\) > 0/);
  assert.doesNotMatch(source, /creationMode === 'v2v'|case "v2v":/);
  assert.match(source, /v-if="supportsVideos" class="video-material-field"/);
});

test("material file sizes use model capability metadata", () => {
  assert.match(source, /const maxImageBytes = computed\(\(\) => Number\(capabilities\.value\.maxImageBytes \?\? 30 \* 1024 \* 1024\)\)/);
  assert.match(source, /const maxVideoBytes = computed\(\(\) => Number\(capabilities\.value\.maxVideoBytes \?\? 30 \* 1024 \* 1024\)\)/);
  assert.match(source, /file\.size <= maxImageBytes\.value/);
  assert.match(source, /file\.size <= maxVideoBytes\.value/);
});

test("video parameter controls leave room for omni labels and values", () => {
  assert.match(source, /\.creation-dock \{[^}]*width: min\(1240px, calc\(100% - 42px\)\)/s);
  assert.match(source, /\.dock-field \{ width: 150px;/);
  assert.match(source, /\.dock-field\.model-field \{ width: 220px;/);
  assert.match(source, /\.duration-slider \{ width: 190px;/);
});

test("seedance prompt supports material placeholders", () => {
  assert.match(source, /const placeholderMaterials = computed/);
  assert.match(source, /function handlePromptInput\(event: Event\)/);
  assert.match(source, /function insertPlaceholder\(token: string\)/);
  assert.match(source, /token: `@image\$\{index \+ 1\}`/);
  assert.match(source, /token: `@video\$\{index \+ 1\}`/);
  assert.match(source, /token: `@audio\$\{index \+ 1\}`/);
  assert.match(source, /v-for="item in placeholderMaterials"/);
  assert.match(source, /@click="insertPlaceholder\(item\.token\)"/);
});

test("admin exposes four omni templates on the unified endpoint", () => {
  for (const model of ["omni-fast", "omni-fast-no-water", "omni-v2v", "omni-v2v-no-water"]) {
    assert.match(adminSource, new RegExp(`key: ['"]${model}['"][^\\n]+path: ['"]\\/v1\\/videos['"]`));
  }
  assert.doesNotMatch(adminSource, /key: ['"]grok-video(?:-1\.5)?['"][^\n]+path: ['"]\/v1\/video['"]/);
  for (const model of ["omni-fast", "omni-fast-no-water", "omni-v2v", "omni-v2v-no-water"]) {
    assert.match(migrationSource, new RegExp(`['"]${model}['"]`));
  }
  assert.match(migrationSource, /0, 'PER_REQUEST', 0, seed\.model_sort/);
  assert.match(migrationSource, /ON CONFLICT\(model_key\) DO NOTHING/);
  assert.match(v10MigrationSource, /default_params - 'requiresVideo'/);
});
