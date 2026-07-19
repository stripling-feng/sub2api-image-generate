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

test("text-to-video hides models that require an image", () => {
  assert.match(source, /const selectableModels = computed\(\(\) => models\.value\.filter\(item => creationMode\.value !== "text" \|\| !item\.defaults\?\.requiresImage\)\)/);
  assert.match(source, /v-for="model in selectableModels"/);
  assert.match(source, /!selectableModels\.value\.some\(item => item\.model === form\.model\)/);
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
