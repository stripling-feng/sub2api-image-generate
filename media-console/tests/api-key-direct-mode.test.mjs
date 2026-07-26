import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const appSource = await readFile(new URL("../src/App.vue", import.meta.url), "utf8");
const videoSource = await readFile(new URL("../src/VideoWorkbench.vue", import.meta.url), "utf8");
const storeSource = await readFile(new URL("../src/stores/workbench.ts", import.meta.url), "utf8");
const javaImageGatewaySource = await readFile(new URL("../../media-java/src/main/java/com/feng/system/module/image/service/ImageGateway.java", import.meta.url), "utf8");
const javaImageGenerationSource = await readFile(new URL("../../media-java/src/main/java/com/feng/system/module/image/service/ImageGenerationService.java", import.meta.url), "utf8");
const javaImageRequestSource = await readFile(new URL("../../media-java/src/main/java/com/feng/system/module/image/dto/ImageGenerateRequest.java", import.meta.url), "utf8");
const javaGptImage2Source = await readFile(new URL("../../media-java/src/main/java/com/feng/system/module/image/formatter/GptImage2AspectRatioRequestFormatter.java", import.meta.url), "utf8");
const javaSizedSource = await readFile(new URL("../../media-java/src/main/java/com/feng/system/module/image/formatter/GptImage2SizedRequestFormatter.java", import.meta.url), "utf8");
const javaGenericFormatterSource = await readFile(new URL("../../media-java/src/main/java/com/feng/system/module/image/formatter/GenericImageRequestFormatter.java", import.meta.url), "utf8");
const combinedSource = [appSource, videoSource, storeSource].join("\n");
const imageGenerateCall = appSource.match(/await store\.generate\(\{[\s\S]*?\n  \}\);/)?.[0] ?? "";

test("front-office workbenches no longer call session bind endpoints", () => {
  assert.doesNotMatch(combinedSource, /\/api\/session\/bind/);
  assert.doesNotMatch(combinedSource, /\bstore\.bind\(/);
});

test("image workbench connects by saving the typed API key and loading business data", () => {
  assert.match(storeSource, /async connect\(apiKey: string\)/);
  assert.match(storeSource, /localStorage\.setItem\("apiKey", normalizedApiKey\)/);
  assert.match(storeSource, /await this\.loadHistory\(\)/);
  assert.doesNotMatch(storeSource, /\/api\/templates/);
});

test("image workbench uploads reference files before submitting generation JSON", () => {
  assert.doesNotMatch(storeSource, /postForm\("\/api\/images\/generate"/);
  assert.match(storeSource, /api\.postForm<\{ url: string; publicUrl\?: string \}>\("\/api\/images\/uploads", form\)/);
  assert.match(storeSource, /images = await Promise\.all\(referenceImages\.map\(uploadReferenceImage\)\)/);
  assert.match(storeSource, /api\.post\("\/api\/images\/generate", \{/);
  assert.match(storeSource, /\bimages\b/);
  assert.doesNotMatch(storeSource, /referenceImageUrls = await Promise/);
  assert.doesNotMatch(storeSource, /maskUrl/);
});

test("image generation request omits frontend-only defaults", () => {
  assert.match(imageGenerateCall, /prompt: form\.prompt/);
  assert.match(imageGenerateCall, /model: form\.model/);
  assert.match(imageGenerateCall, /async: true/);
  assert.match(imageGenerateCall, /\.\.\.payloadFields/);
  assert.doesNotMatch(imageGenerateCall, /\bextraParams:/);
  assert.doesNotMatch(imageGenerateCall, /\bparameters:/);
  assert.doesNotMatch(imageGenerateCall, /\bresponseFormat:/);
  assert.doesNotMatch(appSource, /function generationParameters\(\)/);
  assert.match(storeSource, /const body: Record<string, unknown> = \{[\s\S]*model: payload\.model,[\s\S]*prompt: payload\.prompt,[\s\S]*async: payload\.async,[\s\S]*count: payload\.count,[\s\S]*images[\s\S]*\}/);
  assert.match(storeSource, /if \(payload\.size\) body\.size = payload\.size/);
  assert.match(storeSource, /if \(payload\.aspect_ratio\) body\.aspect_ratio = payload\.aspect_ratio/);
  assert.match(storeSource, /if \(payload\.quality\) body\.quality = payload\.quality/);
});

test("image reference fields stay fixed as images arrays", () => {
  assert.match(storeSource, /images = await Promise\.all\(referenceImages\.map\(uploadReferenceImage\)\)/);
  assert.match(javaImageGenerationSource, /upstream\.setImages\(imageUrls\)/);
  assert.doesNotMatch(storeSource, /referenceImageUrls = await Promise/);
  assert.doesNotMatch(javaImageGenerationSource, /downloadReferenceImages\(imageUrls\)/);
  assert.doesNotMatch(javaImageGenerationSource, /gateway\.download\(/);
  assert.doesNotMatch(javaImageGenerationSource, /multipartImageModel|imageField/);
  assert.doesNotMatch(javaImageGatewaySource, /image\[\]|imageField|resource\(image\)|MULTIPART_FORM_DATA|LinkedMultiValueMap|MultiValueMap/);
  assert.doesNotMatch(javaImageGenerationSource, /"image\\[\\]"/);
  assert.doesNotMatch(javaImageGenerationSource, /put\("image",/);
  assert.doesNotMatch(javaImageGatewaySource, /form\.add\("images", resource\(image\)\)/);
});

test("image upstream generation submits json only", () => {
  assert.match(javaImageGatewaySource, /headers\.setContentType\(MediaType\.APPLICATION_JSON\)/);
  assert.match(javaImageGatewaySource, /request = new HttpEntity<>\(body, headers\)/);
  assert.doesNotMatch(javaImageGatewaySource, /multipart|Multipart|MULTIPART_FORM_DATA|addFormField|multipartFormModel/);
  assert.doesNotMatch(javaImageGatewaySource, /ByteArrayResource|LinkedMultiValueMap|MultiValueMap/);
  assert.doesNotMatch(javaImageGenerationSource, /downloadReferenceImages|validImage|isPng|extension\(/);
});

test("image upstream always uses generations endpoint", () => {
  assert.match(javaImageGatewaySource, /configuredEndpoint\(baseUrl, generationPath\)/);
  assert.doesNotMatch(javaImageGatewaySource, /editPath|edits|editsEndpoint|hasImageReferences/);
  assert.doesNotMatch(javaImageGenerationSource, /getEditPath\(|\"edits\"|hasReferenceInputs\(\) \? "edits"/);
});

test("image generation chain removes legacy request fields and generic passthrough", () => {
  for (const legacy of [
    "negativePrompt",
    "customAspectRatio",
    "style",
    "responseFormat",
    "outputFormat",
    "parameters",
    "extraParams",
    "referenceImages",
    "referenceImageUrls",
    "maskUrl"
  ]) {
    assert.doesNotMatch(javaImageRequestSource, new RegExp(`private .* ${legacy};`));
  }
  assert.doesNotMatch(javaImageRequestSource, /suppliedParameters\(/);
  assert.doesNotMatch(javaImageGenerationSource, /getReferenceImageUrls\(/);
  assert.doesNotMatch(javaImageGenerationSource, /getReferenceImages\(/);
  assert.doesNotMatch(javaImageGenerationSource, /getExtraParams\(/);
  assert.doesNotMatch(javaImageGenerationSource, /getParameters\(/);
  assert.doesNotMatch(javaGptImage2Source, /suppliedParameters\(/);
  assert.doesNotMatch(javaSizedSource, /suppliedParameters\(/);
  assert.doesNotMatch(javaGenericFormatterSource, /put\(value, "size"|put\(value, "quality"|put\(value, "style"|put\(value, "aspect_ratio"/);
  assert.doesNotMatch(storeSource, /\bparameters\?: Record/);
  assert.doesNotMatch(storeSource, /\bmaskUrl/);
  assert.doesNotMatch(storeSource, /\boutputFormat\?:/);
  assert.doesNotMatch(storeSource, /\bnegativePrompt\?:/);
});

test("image formatters whitelist exact upstream fields", () => {
  assert.match(javaGptImage2Source, /put\(value, "model", request\.getModel\(\)\)/);
  assert.match(javaGptImage2Source, /put\(value, "prompt", request\.getPrompt\(\)\)/);
  assert.match(javaGptImage2Source, /put\(value, "async", request\.getAsync\(\)\)/);
  assert.match(javaGptImage2Source, /put\(value, "size", request\.getSize\(\)\)/);
  assert.match(javaGptImage2Source, /put\(value, "images", request\.getImages\(\)\)/);
  assert.doesNotMatch(javaGptImage2Source, /aspect_ratio|quality|count/);

  assert.match(javaSizedSource, /put\(value, "model", request\.getModel\(\)\)/);
  assert.match(javaSizedSource, /put\(value, "prompt", request\.getPrompt\(\)\)/);
  assert.match(javaSizedSource, /put\(value, "async", request\.getAsync\(\)\)/);
  assert.match(javaSizedSource, /put\(value, "images", request\.getImages\(\)\)/);
  assert.match(javaSizedSource, /put\(value, "size", request\.getSize\(\)\)/);
  assert.match(javaSizedSource, /put\(value, "aspect_ratio", request\.getAspectRatio\(\)\)/);
  assert.match(javaSizedSource, /put\(value, "quality", request\.getQuality\(\)\)/);
  assert.doesNotMatch(javaSizedSource, /count|response_format|output_format/);
});

test("image results and preview no longer expose single-image deletion", () => {
  assert.doesNotMatch(appSource, /deletePreviewImage/);
  assert.doesNotMatch(appSource, /store\.deleteImage/);
  assert.doesNotMatch(storeSource, /async deleteImage/);
  assert.doesNotMatch(storeSource, /api\.delete<\{ ok: boolean \}>\(`\/api\/images\/\$\{id\}`\)/);
  assert.doesNotMatch(appSource, /title="删除图片"/);
});

test("image downloads fetch remote images as blobs before saving", () => {
  assert.match(appSource, /async function saveImageFile\(image: GeneratedImage\)/);
  assert.match(appSource, /`\/api\/images\/\$\{encodeURIComponent\(image\.id\)\}\/download`/);
  assert.match(appSource, /headers: apiKeyHeaders\(\)/);
  assert.match(appSource, /URL\.createObjectURL\(blob\)/);
  assert.match(appSource, /anchor\.download = filename/);
  assert.doesNotMatch(appSource, /anchor\.href = image\.publicUrl;\s*anchor\.download = downloadName\(image\);/);
});

test("history images remain draggable into reference inputs", () => {
  assert.match(appSource, /id: image\.id/);
  assert.match(appSource, /urlToReferenceImage\(url, payload\?\.name \|\| "reference\.png", payload\?\.mimeType, payload\?\.id\)/);
  assert.match(appSource, /role="button"/);
  assert.doesNotMatch(appSource, /<button\s+class="history-thumb-button"/);
});

test("reference drag import shows a loading overlay", () => {
  assert.match(appSource, /const isReferenceImporting = ref\(false\)/);
  assert.match(appSource, /isReferenceImporting\.value = true/);
  assert.match(appSource, /finally\s*\{\s*isReferenceImporting\.value = false;\s*\}/);
  assert.match(appSource, /class="reference-import-overlay"/);
});

test("multi-image generation creates one local pending history item per image", () => {
  assert.match(storeSource, /return Array\.from\(\{ length: total \}/);
  assert.match(storeSource, /id: `\$\{localJobPrefix\}\$\{requestId\}-\$\{index \+ 1\}`/);
  assert.match(storeSource, /request_index: index \+ 1/);
  assert.match(storeSource, /request_total: total/);
  assert.match(storeSource, /count: 1/);
});

test("video workbench gates API-key history without requiring a profile object", () => {
  assert.doesNotMatch(videoSource, /if \(!store\.profile\) return/);
  assert.match(videoSource, /if \(!hasApiKey\(\)\) return/);
});

test("video workbench uploads all materials before submitting generation JSON", () => {
  assert.doesNotMatch(videoSource, /postForm\("\/api\/videos\/generate"/);
  assert.match(videoSource, /referenceImageUrls: uploadedImageUrls/);
  assert.match(videoSource, /firstFrameUrl,/);
  assert.match(videoSource, /lastFrameUrl/);
  assert.match(videoSource, /api\.post<\{ requestId: string; count: number \}>\("\/api\/videos\/generate", payload\)/);
  assert.match(videoSource, /api\.postForm<\{ url: string; publicUrl\?: string \}>\("\/api\/videos\/uploads", body\)/);
});
