<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ArrowDownToLine, Brush, ImagePlus, KeyRound, Loader2, Moon, Play, RefreshCw, Sun, Trash2, X } from "lucide-vue-next";
import { useWorkbenchStore } from "./stores/workbench";
import type { GeneratedImage, GenerationJob } from "./types";

type ReferenceImage = {
  name: string;
  mimeType: string;
  data: string;
  previewUrl: string;
  width: number;
  height: number;
};

type PreviewImage = GeneratedImage & { job: GenerationJob };
type ImageSizeTier = "1k" | "2k" | "4k";
type OutputFormat = "png" | "jpeg" | "webp";
type CustomSizeMode = "ratio" | "size" | "reference";

const sizePixels: Record<ImageSizeTier, number> = {
  "1k": 1024,
  "2k": 2048,
  "4k": 4096
};

const maxPixels = 8_294_400;
const sizeMultiple = 16;
const maxReferenceImageBytes = 10 * 1024 * 1024;
const aspectRatios = ["1:1", "3:2", "2:3", "16:9", "9:16", "4:3", "3:4", "21:9"] as const;
const snowflakeEpoch = 1_704_067_200_000;
let snowflakeSequence = 0n;
let snowflakeLastMs = 0n;
const fixedBaseUrl = "https://image.tcboys.de/v1";

const store = useWorkbenchStore();
const theme = ref(localStorage.getItem("theme") ?? "dark");

const connection = reactive({
  baseUrl: fixedBaseUrl,
  apiKey: localStorage.getItem("apiKey") ?? ""
});

const form = reactive({
  prompt: "",
  size: "1k" as ImageSizeTier,
  aspectRatio: "1:1",
  customSizeMode: "ratio" as CustomSizeMode,
  customAspectRatio: "1:1",
  customWidth: 1024,
  customHeight: 1024,
  count: 1,
  quality: "auto",
  outputFormat: "png" as OutputFormat,
  responseFormat: "url" as "url"
});

const autoBindTimer = ref<number | undefined>();
const referenceImages = ref<ReferenceImage[]>([]);
const isReferenceDragging = ref(false);
const previewImageId = ref<string | null>(null);
const previewMode = ref<"latest" | "single" | "reference">("latest");
const singlePreviewImage = ref<PreviewImage | null>(null);
const noticeDialog = reactive({
  open: false,
  title: "",
  message: ""
});
const currentHistoryPage = ref(1);
const historyPageSize = 10;
const pendingImageUrl = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(`
  <svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
    <style>
      @keyframes spin { to { transform: rotate(360deg); } }
      @keyframes shimmer { 0% { transform: translateX(-520px); } 100% { transform: translateX(980px); } }
      @keyframes pulse { 0%, 100% { opacity: .55; } 50% { opacity: 1; } }
      .ring { transform-origin: 512px 462px; animation: spin 1.05s linear infinite; }
      .shine { animation: shimmer 1.65s ease-in-out infinite; }
      .text { animation: pulse 1.4s ease-in-out infinite; }
    </style>
    <rect width="1024" height="1024" fill="#20221c"/>
    <rect x="112" y="112" width="800" height="800" rx="28" fill="#191a16" stroke="#34372d" stroke-width="4"/>
    <g clip-path="url(#clip)">
      <rect class="shine" x="-360" y="112" width="260" height="800" fill="rgba(127,176,105,0.18)" transform="skewX(-18)"/>
    </g>
    <defs>
      <clipPath id="clip"><rect x="112" y="112" width="800" height="800" rx="28"/></clipPath>
    </defs>
    <circle cx="512" cy="462" r="54" fill="none" stroke="#34372d" stroke-width="16"/>
    <circle class="ring" cx="512" cy="462" r="54" fill="none" stroke="#7fb069" stroke-width="16" stroke-linecap="round" stroke-dasharray="96 260"/>
    <text class="text" x="512" y="560" text-anchor="middle" fill="#ecebe4" font-family="Arial, sans-serif" font-size="38" font-weight="700">生成中</text>
  </svg>
`)}`;

const currentResultJobs = computed(() => {
  if (!store.currentRequestId) return [];

  return store.jobs.filter((job) => job.params.request_id === store.currentRequestId);
});
const latestImages = computed(() => currentResultJobs.value.flatMap((job) => {
  const images = job.images ?? [];
  if (images.length) {
    return images.map((image) => ({ ...image, job }));
  }

  if (job.status === "PENDING") {
    return [{
      id: `pending-${job.id}`,
      jobId: job.id,
      publicUrl: pendingImageUrl,
      mimeType: "image/svg+xml",
      sizeBytes: 0,
      createdAt: job.createdAt,
      job
    }];
  }

  return [];
}));
const referencePreviewImages = computed<PreviewImage[]>(() => {
  const now = new Date().toISOString();
  return referenceImages.value.map((image, index) => ({
    id: `reference-${index}`,
    jobId: `reference-${index}`,
    publicUrl: image.previewUrl,
    mimeType: image.mimeType,
    sizeBytes: 0,
    createdAt: now,
    job: {
      id: `reference-job-${index}`,
      prompt: image.name,
      model: "",
      size: "",
      count: 1,
      responseFormat: "url",
      params: {},
      status: "SUCCEEDED",
      createdAt: now,
      images: []
    }
  }));
});
const historyPageCount = computed(() => Math.max(1, Math.ceil(store.jobs.length / historyPageSize)));
const pagedHistoryJobs = computed(() => {
  const page = Math.min(currentHistoryPage.value, historyPageCount.value);
  const start = (page - 1) * historyPageSize;
  return store.jobs.slice(start, start + historyPageSize);
});
const previewImages = computed(() => previewMode.value === "single"
  ? (singlePreviewImage.value ? [singlePreviewImage.value] : [])
  : previewMode.value === "reference"
    ? referencePreviewImages.value
    : latestImages.value);
const previewIndex = computed(() => previewImages.value.findIndex((image: PreviewImage) => image.id === previewImageId.value));
const previewImage = computed<PreviewImage | null>(() => {
  return previewIndex.value >= 0 ? previewImages.value[previewIndex.value] : null;
});
const selectedAspectRatio = computed(() => {
  if (form.aspectRatio !== "custom") return parseAspectRatio(form.aspectRatio);
  if (form.customSizeMode === "ratio") return parseAspectRatio(form.customAspectRatio);
  if (form.customSizeMode === "reference") {
    const image = referenceImages.value[0];
    if (!image || image.width <= 0 || image.height <= 0) return null;
    return { width: image.width, height: image.height, value: image.width / image.height };
  }

  const width = Number(form.customWidth);
  const height = Number(form.customHeight);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return null;
  return { width, height, value: width / height };
});
const aspectRatioError = computed(() => {
  const ratio = selectedAspectRatio.value;
  if (!ratio) {
    if (form.aspectRatio === "custom" && form.customSizeMode === "size") return "请输入有效的宽度和高度";
    if (form.aspectRatio === "custom" && form.customSizeMode === "reference") return "请先上传参考图";
    return "请输入类似 16:9 的比例";
  }
  if (ratio.value < 1 / 3 || ratio.value > 3) return "比例范围不能超过 1:3 或 3:1";
  return "";
});
const computedSize = computed(() => {
  const ratio = selectedAspectRatio.value;
  if (!ratio || aspectRatioError.value) return "";

  if (form.aspectRatio === "custom" && form.customSizeMode === "size") {
    return normalizeSize(Number(form.customWidth), Number(form.customHeight), ratio.value);
  }

  if (form.aspectRatio === "custom" && form.customSizeMode === "reference") {
    return normalizeSize(ratio.width, ratio.height, ratio.value);
  }

  const longSide = sizePixels[form.size];
  let width = ratio.value >= 1 ? longSide : Math.round(longSide * ratio.value);
  let height = ratio.value >= 1 ? Math.round(longSide / ratio.value) : longSide;
  return normalizeSize(width, height, ratio.value);
});

const customSizePreview = computed(() => computedSize.value ? `最终尺寸：${computedSize.value}` : "");

function normalizeSize(inputWidth: number, inputHeight: number, ratioValue: number) {
  let width = Math.round(inputWidth);
  let height = Math.round(inputHeight);
  let pixels = width * height;

  if (pixels > maxPixels) {
    const scale = Math.sqrt(maxPixels / pixels);
    width = Math.floor(width * scale);
    height = Math.floor(height * scale);
  }

  width = Math.max(sizeMultiple, Math.floor(width / sizeMultiple) * sizeMultiple);
  height = Math.max(sizeMultiple, Math.floor(height / sizeMultiple) * sizeMultiple);
  pixels = width * height;

  while (pixels > maxPixels) {
    if (width >= height) {
      width = Math.max(sizeMultiple, width - sizeMultiple);
      height = Math.max(sizeMultiple, Math.floor((width / ratioValue) / sizeMultiple) * sizeMultiple);
    } else {
      height = Math.max(sizeMultiple, height - sizeMultiple);
      width = Math.max(sizeMultiple, Math.floor((height * ratioValue) / sizeMultiple) * sizeMultiple);
    }
    pixels = width * height;
  }

  return `${width}x${height}`;
}

function statusLabel(status: GenerationJob["status"]) {
  return {
    PENDING: "生成中",
    SUCCEEDED: "已完成",
    FAILED: "失败"
  }[status];
}

function durationLabel(durationMs?: number | null, status?: GenerationJob["status"]) {
  if (!durationMs) return status === "FAILED" ? "失败" : "生成中";
  return durationMs >= 1000 ? `${(durationMs / 1000).toFixed(1)}s` : `${durationMs}ms`;
}

function historyThumbnail(job: GenerationJob) {
  return (job.images ?? [])[0]?.publicUrl ?? pendingImageUrl;
}

function createSnowflakeId() {
  let now = BigInt(Date.now());
  if (now === snowflakeLastMs) {
    snowflakeSequence = (snowflakeSequence + 1n) & 4095n;
    if (snowflakeSequence === 0n) {
      while (now <= snowflakeLastMs) {
        now = BigInt(Date.now());
      }
    }
  } else {
    snowflakeSequence = 0n;
  }

  snowflakeLastMs = now;
  return (((now - BigInt(snowflakeEpoch)) << 22n) | snowflakeSequence).toString();
}

function downloadName(image: GeneratedImage) {
  const filename = image.publicUrl.split("/").pop()?.split("?")[0];
  if (filename?.startsWith("tcboys.de_")) return filename;

  const ext = image.mimeType.split("/")[1]?.split(";")[0] || "png";
  return `tcboys.de_${createSnowflakeId()}.${ext}`;
}

function parseAspectRatio(value: string): { width: number; height: number; value: number } | null {
  const match = value.trim().match(/^(\d+(?:\.\d+)?)\s*:\s*(\d+(?:\.\d+)?)$/);
  if (!match) return null;

  const width = Number(match[1]);
  const height = Number(match[2]);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return null;

  return { width, height, value: width / height };
}

function applyTheme() {
  document.documentElement.dataset.theme = theme.value;
  localStorage.setItem("theme", theme.value);
  localStorage.setItem("baseUrl", fixedBaseUrl);
}

async function bind() {
  connection.baseUrl = fixedBaseUrl;
  if (!connection.apiKey) return;
  await store.bind(fixedBaseUrl, connection.apiKey);
}

onMounted(async () => {
  applyTheme();
  if (connection.baseUrl && connection.apiKey) {
    await bind().catch((error) => {
      store.error = error instanceof Error ? error.message : "连接失败";
    });
  }
});

watch(
  () => connection.apiKey,
  (apiKey) => {
    connection.baseUrl = fixedBaseUrl;
    localStorage.setItem("baseUrl", fixedBaseUrl);
    localStorage.setItem("apiKey", apiKey);
    store.resetWorkspace();
    previewImageId.value = null;
    singlePreviewImage.value = null;
    currentHistoryPage.value = 1;

    if (autoBindTimer.value) {
      window.clearTimeout(autoBindTimer.value);
    }

    if (!apiKey || apiKey.length < 8) {
      return;
    }

    autoBindTimer.value = window.setTimeout(() => {
      bind().catch((error) => {
        store.error = error instanceof Error ? error.message : "连接失败";
      });
    }, 700);
  }
);

watch(
  () => store.jobs.length,
  () => {
    currentHistoryPage.value = Math.min(currentHistoryPage.value, historyPageCount.value);
  }
);

function fileToReferenceImage(file: File): Promise<ReferenceImage> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("图片读取失败"));
    reader.onload = () => {
      const result = String(reader.result);
      const data = result.split(",")[1] ?? "";
      const image = new Image();
      image.onerror = () => reject(new Error("图片尺寸读取失败"));
      image.onload = () => {
        resolve({
          name: file.name,
          mimeType: file.type || "image/png",
          data,
          previewUrl: result,
          width: image.naturalWidth,
          height: image.naturalHeight
        });
      };
      image.src = result;
    };
    reader.readAsDataURL(file);
  });
}

async function urlToReferenceImage(url: string, name: string, fallbackMimeType?: string): Promise<ReferenceImage> {
  const response = await fetch(url);
  if (!response.ok) throw new Error("图片读取失败");

  const blob = await response.blob();
  if (!blob.type.startsWith("image/")) throw new Error("只能添加图片格式");
  if (blob.size > maxReferenceImageBytes) throw new Error("参考图不能超过 10MB");

  return await fileToReferenceImage(new File([blob], name, { type: blob.type || fallbackMimeType || "image/png" }));
}

function addReferenceImages(images: ReferenceImage[]) {
  const remaining = Math.max(0, 4 - referenceImages.value.length);
  referenceImages.value = [...referenceImages.value, ...images.slice(0, remaining)];
}

async function handleReferenceUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  const validFiles = files.filter((file) => file.type.startsWith("image/") && file.size <= maxReferenceImageBytes);
  const remaining = Math.max(0, 4 - referenceImages.value.length);
  const selected = validFiles.slice(0, remaining);
  const images = await Promise.all(selected.map(fileToReferenceImage));
  addReferenceImages(images);
  input.value = "";
}

function dragHistoryImage(event: DragEvent, job: GenerationJob) {
  const image = (job.images ?? [])[0];
  if (!image || !event.dataTransfer) return;

  event.dataTransfer.effectAllowed = "copy";
  event.dataTransfer.setData("application/x-tcboys-reference-image", JSON.stringify({
    url: image.publicUrl,
    name: `${job.prompt || "reference"}.${image.mimeType.split("/")[1]?.split(";")[0] || "png"}`,
    mimeType: image.mimeType
  }));
  event.dataTransfer.setData("text/uri-list", image.publicUrl);
}

function handleReferenceDragOver(event: DragEvent) {
  if (!event.dataTransfer) return;
  event.preventDefault();
  event.dataTransfer.dropEffect = "copy";
  isReferenceDragging.value = true;
}

function handleReferenceDragLeave(event: DragEvent) {
  const current = event.currentTarget as HTMLElement | null;
  const next = event.relatedTarget as Node | null;
  if (!current || !next || !current.contains(next)) {
    isReferenceDragging.value = false;
  }
}

async function handleReferenceDrop(event: DragEvent) {
  event.preventDefault();
  isReferenceDragging.value = false;
  if (!event.dataTransfer || referenceImages.value.length >= 4) return;

  const raw = event.dataTransfer.getData("application/x-tcboys-reference-image");
  const uri = event.dataTransfer.getData("text/uri-list").split("\n").find((line) => line && !line.startsWith("#"));
  let payload: { url: string; name?: string; mimeType?: string } | null = null;

  if (raw) {
    try {
      payload = JSON.parse(raw);
    } catch {
      payload = null;
    }
  }

  const url = payload?.url || uri;
  if (!url) return;

  try {
    const image = await urlToReferenceImage(url, payload?.name || "reference.png", payload?.mimeType);
    addReferenceImages([image]);
  } catch (error) {
    store.error = error instanceof Error ? error.message : "参考图添加失败";
  }
}

function removeReferenceImage(index: number) {
  referenceImages.value = referenceImages.value.filter((_, itemIndex) => itemIndex !== index);
}

function showNotice(title: string, message: string) {
  noticeDialog.title = title;
  noticeDialog.message = message;
  noticeDialog.open = true;
}

function closeNotice() {
  noticeDialog.open = false;
}

async function generate() {
  if (!connection.apiKey.trim()) {
    showNotice("缺少 API Key", "请先填写 API Key 后再生成图片。");
    return;
  }

  if (!form.prompt.trim()) {
    store.error = "请先填写描述。";
    return;
  }

  if (!computedSize.value || aspectRatioError.value) return;
  const firstReferenceImage = referenceImages.value[0];

  if (!store.profile) {
    await bind();
  }

  if (!store.profile) {
    store.error = "请先填写中转 URL 和 API Key。";
    return;
  }

  previewMode.value = "latest";
  previewImageId.value = null;
  singlePreviewImage.value = null;

  await store.generate({
    prompt: form.prompt,
    model: "gpt-image-2",
    size: computedSize.value,
    aspectRatio: form.aspectRatio === "custom" && form.customSizeMode === "size"
      ? `${form.customWidth}:${form.customHeight}`
      : form.aspectRatio === "custom" && form.customSizeMode === "reference" && firstReferenceImage
        ? `${firstReferenceImage.width}:${firstReferenceImage.height}`
        : form.aspectRatio,
    customAspectRatio: form.aspectRatio === "custom" && form.customSizeMode === "ratio" ? form.customAspectRatio : undefined,
    count: Number(form.count),
    quality: form.quality === "auto" ? undefined : form.quality,
    outputFormat: form.outputFormat,
    responseFormat: "url",
    extraParams: {
      size_tier: form.size,
      custom_size_mode: form.aspectRatio === "custom" ? form.customSizeMode : undefined,
      custom_width: form.aspectRatio === "custom" && form.customSizeMode === "size" ? Number(form.customWidth) : undefined,
      custom_height: form.aspectRatio === "custom" && form.customSizeMode === "size" ? Number(form.customHeight) : undefined,
      reference_width: form.aspectRatio === "custom" && form.customSizeMode === "reference" ? firstReferenceImage?.width : undefined,
      reference_height: form.aspectRatio === "custom" && form.customSizeMode === "reference" ? firstReferenceImage?.height : undefined,
      computed_size: computedSize.value
    },
    referenceImages: referenceImages.value.map(({ name, mimeType, data }) => ({ name, mimeType, data }))
  });
}

function reuseJob(job: GenerationJob) {
  form.prompt = job.prompt;
  form.size = "1k";
  form.count = job.count;
  form.quality = job.quality ?? "auto";
  form.outputFormat = typeof job.params.output_format === "string"
    ? job.params.output_format as OutputFormat
    : "png";
  const savedAspectRatio = typeof job.params.aspect_ratio === "string" ? job.params.aspect_ratio : "1:1";
  form.customSizeMode = job.params.custom_size_mode === "size"
    ? "size"
    : job.params.custom_size_mode === "reference"
      ? "reference"
      : "ratio";
  form.aspectRatio = form.customSizeMode === "size" || form.customSizeMode === "reference"
    ? "custom"
    : ([...aspectRatios, "custom"].includes(savedAspectRatio) ? savedAspectRatio : "1:1");
  form.customAspectRatio = typeof job.params.custom_aspect_ratio === "string" ? job.params.custom_aspect_ratio : "1:1";
  form.customWidth = typeof job.params.custom_width === "number" ? job.params.custom_width : 1024;
  form.customHeight = typeof job.params.custom_height === "number" ? job.params.custom_height : 1024;
  form.responseFormat = "url";
}

function downloadAll() {
  latestImages.value.forEach((item) => {
    const anchor = document.createElement("a");
    anchor.href = item.publicUrl;
    anchor.download = downloadName(item);
    anchor.click();
  });
}

function downloadJobImages(job: GenerationJob) {
  (job.images ?? []).forEach((image) => {
    const anchor = document.createElement("a");
    anchor.href = image.publicUrl;
    anchor.download = downloadName(image);
    anchor.click();
  });
}

function openPreview(id: string) {
  previewMode.value = "latest";
  singlePreviewImage.value = null;
  previewImageId.value = id;
}

function openHistoryPreview(job: GenerationJob) {
  const image = (job.images ?? [])[0];
  if (!image) return;

  previewMode.value = "single";
  singlePreviewImage.value = { ...image, job };
  previewImageId.value = image.id;
}

function openReferencePreview(index: number) {
  const image = referencePreviewImages.value[index];
  if (!image) return;

  previewMode.value = "reference";
  singlePreviewImage.value = null;
  previewImageId.value = image.id;
}

function closePreview() {
  previewImageId.value = null;
  singlePreviewImage.value = null;
}

function movePreview(step: number) {
  const total = previewImages.value.length;
  if (!total) return;
  const current = previewIndex.value >= 0 ? previewIndex.value : 0;
  previewImageId.value = previewImages.value[(current + step + total) % total].id;
}

function downloadImage(image: GeneratedImage) {
  if (image.id.startsWith("pending-")) return;

  const anchor = document.createElement("a");
  anchor.href = image.publicUrl;
  anchor.download = downloadName(image);
  anchor.click();
}

async function deletePreviewImage() {
  const image = previewImage.value;
  if (!image) return;

  const nextImage = previewImages.value[previewIndex.value + 1] ?? previewImages.value[previewIndex.value - 1] ?? null;
  if (image.id.startsWith("pending-")) {
    await store.deleteJob(image.jobId);
  } else {
    await store.deleteImage(image.id);
  }
  previewImageId.value = nextImage?.id ?? null;
}

async function copyText(text: string) {
  await navigator.clipboard.writeText(text);
  store.status = "已复制";
}

function toggleTheme() {
  theme.value = theme.value === "dark" ? "light" : "dark";
  applyTheme();
}
</script>

<template>
  <main class="shell">
    <div class="utility-actions">
      <button class="icon-btn" type="button" title="切换主题" @click="toggleTheme">
        <Sun v-if="theme === 'dark'" :size="18" />
        <Moon v-else :size="18" />
      </button>
      <button class="icon-btn" type="button" title="刷新历史" :disabled="!store.profile" @click="store.loadHistory">
        <RefreshCw :size="18" />
      </button>
    </div>

    <div class="workspace">
      <aside class="panel controls">
        <section class="panel-section">
          <div class="section-title">
            <KeyRound :size="17" />
            <h2>连接</h2>
          </div>
          <label>
            <span>API Key</span>
            <input v-model="connection.apiKey" type="password" placeholder="sk-..." autocomplete="off" />
          </label>
        </section>

        <section class="panel-section">
          <div class="section-title">
            <Brush :size="17" />
            <h2>参数</h2>
          </div>
          <div class="split">
            <label>
              <span>尺寸</span>
              <select v-model="form.size" disabled>
                <option value="4k">4k原生</option>
                <option value="2k">2k原生</option>
                <option value="1k">1k原生</option>
              </select>
            </label>
            <label>
              <span>宽高比</span>
              <select v-model="form.aspectRatio">
                <option v-for="ratio in aspectRatios" :key="ratio" :value="ratio">{{ ratio }}</option>
                <option value="custom">自定义</option>
              </select>
            </label>
          </div>
          <div v-if="form.aspectRatio === 'custom'" class="custom-size-panel">
            <div class="radio-row">
              <label class="radio-option">
                <input v-model="form.customSizeMode" type="radio" value="ratio" />
                <span>自定义比例</span>
              </label>
              <label class="radio-option">
                <input v-model="form.customSizeMode" type="radio" value="size" />
                <span>自定义尺寸</span>
              </label>
              <label class="radio-option">
                <input v-model="form.customSizeMode" type="radio" value="reference" />
                <span>根据参考图的尺寸</span>
              </label>
            </div>
            <label v-if="form.customSizeMode === 'ratio'">
              <span>自定义比例</span>
              <input v-model.trim="form.customAspectRatio" placeholder="例如 5:2，范围 1:3 到 3:1" />
            </label>
            <div v-else-if="form.customSizeMode === 'size'" class="split">
              <label>
                <span>宽度</span>
                <input v-model.number="form.customWidth" type="number" min="16" step="16" />
              </label>
              <label>
                <span>高度</span>
                <input v-model.number="form.customHeight" type="number" min="16" step="16" />
              </label>
            </div>
            <small v-else-if="referenceImages.length" class="field-hint">
              使用第一张参考图：{{ referenceImages[0].width }}x{{ referenceImages[0].height }}
            </small>
            <small v-if="aspectRatioError" class="field-error">{{ aspectRatioError }}</small>
            <small v-else-if="customSizePreview" class="field-hint">{{ customSizePreview }}</small>
          </div>
          <div class="split">
            <label>
              <span>质量</span>
              <select v-model="form.quality">
                <option value="auto">自动</option>
                <option value="low">低质量</option>
                <option value="medium">标准质量</option>
                <option value="high">高质量</option>
              </select>
            </label>
            <label>
              <span>输出格式</span>
              <select v-model="form.outputFormat">
                <option value="png">PNG</option>
                <option value="jpeg">JPEG</option>
                <option value="webp">WEBP</option>
              </select>
            </label>
          </div>
          <label>
            <span>数量</span>
            <input v-model.number="form.count" type="number" min="1" max="10" />
          </label>
        </section>
      </aside>

      <section class="panel composer">
        <div class="section-title results-title">
          <div class="section-title-label">
            <Play :size="17" />
            <h2>生成结果</h2>
          </div>
          <button class="title-action" type="button" :disabled="!latestImages.length" @click="downloadAll">
            <ArrowDownToLine :size="14" />
            下载全部
          </button>
        </div>

        <div class="results">
          <figure v-for="item in latestImages" :key="item.id" class="image-tile">
            <button class="image-preview-trigger" type="button" title="预览图片" @click="openPreview(item.id)">
              <img :src="item.publicUrl" :alt="item.job.prompt" />
            </button>
            <figcaption>
              <p>{{ item.job.prompt }}</p>
              <div class="image-meta">
                <span>耗时：{{ durationLabel(item.job.durationMs, item.job.status) }}</span>
              </div>
              <div class="image-actions">
                <button type="button" title="下载图片" @click="downloadImage(item)">
                  <ArrowDownToLine :size="15" />
                  下载
                </button>
                <button class="danger-text" type="button" title="删除图片" @click="item.id.startsWith('pending-') ? store.deleteJob(item.jobId) : store.deleteImage(item.id)">
                  <Trash2 :size="15" />
                  删除
                </button>
              </div>
            </figcaption>
          </figure>
          <div v-if="!latestImages.length" class="empty">生成结果会显示在这里。</div>
        </div>

        <div
          :class="['prompt-panel', { 'is-reference-dragging': isReferenceDragging }]"
          @dragover="handleReferenceDragOver"
          @dragleave="handleReferenceDragLeave"
          @drop="handleReferenceDrop"
        >
          <div class="reference-bar">
            <label class="upload-button">
              <ImagePlus :size="17" />
              上传参考图
              <input type="file" accept="image/*" multiple @change="handleReferenceUpload" />
            </label>
            <span class="muted">最多 4 张，每张不超过 10MB。</span>
          </div>

          <div v-if="referenceImages.length" class="reference-grid">
            <figure v-for="image, index in referenceImages" :key="image.previewUrl" class="reference-tile">
              <button class="reference-preview-button" type="button" title="预览参考图" @click="openReferencePreview(index)">
                <img :src="image.previewUrl" :alt="image.name" />
              </button>
              <button class="reference-remove" type="button" title="移除参考图" @click="removeReferenceImage(index)">
                <X :size="13" />
              </button>
            </figure>
          </div>

          <textarea v-model="form.prompt" class="prompt" placeholder="描述你要生成的画面、主体、镜头、材质和光线。"></textarea>

          <div class="button-row composer-actions">
            <button class="primary big" type="button" :disabled="store.loading" @click="generate">
              <Loader2 v-if="store.loading" class="spin" :size="18" />
              <Play v-else :size="18" />
              生成图片
            </button>
          </div>
          <p v-if="store.status || store.error" :class="['composer-message', { error: store.error }]">
            {{ store.error || store.status }}
          </p>
        </div>
      </section>

      <aside class="panel side">
        <div class="side-title">
          <h2>历史</h2>
        </div>

        <section class="list">
          <article v-for="job in pagedHistoryJobs" :key="job.id" class="list-item">
            <button class="history-thumb-button" type="button" title="预览图片" :disabled="!job.images?.length" @click="openHistoryPreview(job)">
              <img
                class="history-thumb"
                :src="historyThumbnail(job)"
                :alt="job.prompt"
                :draggable="!!job.images?.length"
                @dragstart="dragHistoryImage($event, job)"
              />
            </button>
            <div class="history-meta">
              <strong class="history-prompt" :title="job.prompt">{{ job.prompt }}</strong>
              <p>耗时：{{ durationLabel(job.durationMs, job.status) }}</p>
              <span :class="['history-status', job.status.toLowerCase()]">{{ statusLabel(job.status) }}</span>
            </div>
            <div class="item-actions">
              <button class="icon-btn small" title="下载图片" type="button" :disabled="!job.images?.length" @click="downloadJobImages(job)">
                <ArrowDownToLine :size="15" />
              </button>
              <button class="icon-btn small" title="复用参数" type="button" @click="reuseJob(job)">
                <RefreshCw :size="15" />
              </button>
              <button class="icon-btn small danger-text" title="删除任务" type="button" @click="store.deleteJob(job.id)">
                <Trash2 :size="15" />
              </button>
            </div>
          </article>
          <div v-if="!store.jobs.length" class="empty compact">暂无历史。</div>
        </section>
        <div v-if="store.jobs.length > historyPageSize" class="pagination">
          <button type="button" :disabled="currentHistoryPage <= 1" @click="currentHistoryPage -= 1">上一页</button>
          <span>{{ currentHistoryPage }} / {{ historyPageCount }}</span>
          <button type="button" :disabled="currentHistoryPage >= historyPageCount" @click="currentHistoryPage += 1">下一页</button>
        </div>
      </aside>
    </div>

    <div v-if="previewImage" class="preview-modal" role="dialog" aria-modal="true" @click.self="closePreview">
      <div class="preview-content">
        <div class="preview-toolbar">
          <span>{{ previewIndex + 1 }} / {{ previewImages.length }}</span>
          <button class="preview-close" type="button" title="关闭预览" @click="closePreview">关闭</button>
        </div>
        <button v-if="previewImages.length > 1" class="preview-nav previous" type="button" title="上一张" @click="movePreview(-1)">‹</button>
        <img :src="previewImage.publicUrl" :alt="previewImage.job.prompt" />
        <button v-if="previewImages.length > 1" class="preview-nav next" type="button" title="下一张" @click="movePreview(1)">›</button>
        <p>{{ previewImage.job.prompt }}</p>
        <div v-if="previewMode !== 'reference'" class="preview-actions">
          <button type="button" title="下载图片" @click="downloadImage(previewImage)">
            <ArrowDownToLine :size="15" />
            下载
          </button>
          <button class="danger-text" type="button" title="删除图片" @click="deletePreviewImage">
            <Trash2 :size="15" />
            删除
          </button>
        </div>
      </div>
    </div>

    <div v-if="noticeDialog.open" class="notice-modal" role="alertdialog" aria-modal="true" @click.self="closeNotice">
      <div class="notice-card">
        <div class="notice-icon">
          <KeyRound :size="20" />
        </div>
        <div class="notice-copy">
          <h3>{{ noticeDialog.title }}</h3>
          <p>{{ noticeDialog.message }}</p>
        </div>
        <button class="primary notice-action" type="button" @click="closeNotice">知道了</button>
      </div>
    </div>
  </main>
</template>
