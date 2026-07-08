<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { ArrowDownToLine, BookOpenText, Brush, Clock3, Eye, EyeOff, ImagePlus, KeyRound, Loader2, Play, RefreshCw, Trash2, X } from "lucide-vue-next";
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
type ImageModel = "gpt-image-2-4k" | "gpt-image-2";
type ImageSizeTier = "auto" | "1k" | "2k" | "4k";
type AspectRatioPreset = "auto" | "1:1" | "2:3" | "3:2" | "9:16" | "16:9";
type OutputFormat = "png" | "jpeg" | "webp";
type CustomSizeMode = "ratio" | "size" | "reference";

const maxPixels = 8_294_400;
const presetSizes: Record<Exclude<ImageSizeTier, "auto">, Record<Exclude<AspectRatioPreset, "auto">, { width: number; height: number }>> = {
  "1k": {
    "1:1": { width: 1024, height: 1024 },
    "2:3": { width: 1024, height: 1536 },
    "3:2": { width: 1536, height: 1024 },
    "9:16": { width: 1008, height: 1792 },
    "16:9": { width: 1792, height: 1008 }
  },
  "2k": {
    "1:1": { width: 2048, height: 2048 },
    "2:3": { width: 1360, height: 2048 },
    "3:2": { width: 2048, height: 1360 },
    "9:16": { width: 1152, height: 2048 },
    "16:9": { width: 2048, height: 1152 }
  },
  "4k": {
    "1:1": { width: 2880, height: 2880 },
    "2:3": { width: 2352, height: 3520 },
    "3:2": { width: 3520, height: 2352 },
    "9:16": { width: 2160, height: 3840 },
    "16:9": { width: 3840, height: 2160 }
  }
};

const sizeMultiple = 16;
const maxReferenceImageBytes = 10 * 1024 * 1024;
const aspectRatios = ["auto", "1:1", "2:3", "3:2", "9:16", "16:9"] as const;
const modelSizeTier: Record<ImageModel, Exclude<ImageSizeTier, "auto">> = {
  "gpt-image-2": "1k",
  "gpt-image-2-4k": "4k"
};
const snowflakeEpoch = 1_704_067_200_000;
let snowflakeSequence = 0n;
let snowflakeLastMs = 0n;
const fixedBaseUrl = "https://image.tcboys.de";

const store = useWorkbenchStore();
const theme = "light";

const connection = reactive({
  baseUrl: fixedBaseUrl,
  apiKey: localStorage.getItem("apiKey") ?? ""
});

const form = reactive({
  prompt: "",
  model: "gpt-image-2" as ImageModel,
  aspectRatio: "auto" as AspectRatioPreset,
  customSizeMode: "ratio" as CustomSizeMode,
  customAspectRatio: "1:1",
  customWidth: 1024,
  customHeight: 1024,
  count: 1,
  quality: "auto",
  outputFormat: "png" as OutputFormat,
  responseFormat: "b64_json" as "b64_json" | "url"
});

const autoBindTimer = ref<number | undefined>();
const referenceImages = ref<ReferenceImage[]>([]);
const isReferenceDragging = ref(false);
const showApiKey = ref(false);
const docDialogOpen = ref(false);
const activeDocTab = ref<"gpt-image-2" | "gpt-image-2-4k">("gpt-image-2");
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
const failedImageUrl = `${import.meta.env.BASE_URL}assets/generation-failed-mark.svg`;
const unavailableImageUrl = `${import.meta.env.BASE_URL}assets/image-unavailable-mark.svg`;
const currentTimeMs = ref(Date.now());
let historyClockTimer: number | undefined;

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
      publicUrl: "",
      mimeType: "application/x-pending",
      sizeBytes: 0,
      createdAt: job.createdAt,
      job
    }];
  }

  if (job.status === "FAILED") {
    return [{
      id: `failed-${job.id}`,
      jobId: job.id,
      publicUrl: failedImageUrl,
      mimeType: "application/x-failed",
      sizeBytes: 0,
      createdAt: job.createdAt,
      job
    }];
  }

  return [];
}));
const resultsLayoutClass = computed(() => {
  const count = latestImages.value.length;
  if (count <= 1) return "single";
  if (count === 2) return "double";
  if (count === 3) return "triple";
  return "grid";
});
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
const historyPageCount = computed(() => store.historyTotalPages);
const pagedHistoryJobs = computed(() => store.jobs);
const previewImages = computed(() => previewMode.value === "single"
  ? (singlePreviewImage.value ? [singlePreviewImage.value] : [])
  : previewMode.value === "reference"
    ? referencePreviewImages.value
    : latestImages.value);
const previewIndex = computed(() => previewImages.value.findIndex((image: PreviewImage) => image.id === previewImageId.value));
const previewImage = computed<PreviewImage | null>(() => {
  return previewIndex.value >= 0 ? previewImages.value[previewIndex.value] : null;
});
const computedSize = computed(() => {
  if (form.aspectRatio === "auto") return "auto";

  const preset = presetSizes[modelSizeTier[form.model]][form.aspectRatio as Exclude<AspectRatioPreset, "auto">];
  return preset ? `${preset.width}x${preset.height}` : "auto";
});

const upstreamSize = computed(() => {
  if (form.model === "gpt-image-2-4k") return computedSize.value;
  return form.aspectRatio;
});

const upstreamResponseFormat = computed<"b64_json" | "url">(() => form.model === "gpt-image-2-4k" ? "url" : "b64_json");

function normalizeSize(inputWidth: number, inputHeight: number, ratioValue: number, pixelLimit = maxPixels) {
  let width = Math.round(inputWidth);
  let height = Math.round(inputHeight);
  let pixels = width * height;

  const effectivePixelLimit = Math.min(maxPixels, pixelLimit);

  if (pixels > effectivePixelLimit) {
    const scale = Math.sqrt(effectivePixelLimit / pixels);
    width = Math.floor(width * scale);
    height = Math.floor(height * scale);
  }

  width = Math.max(sizeMultiple, Math.floor(width / sizeMultiple) * sizeMultiple);
  height = Math.max(sizeMultiple, Math.floor(height / sizeMultiple) * sizeMultiple);
  pixels = width * height;

  while (pixels > effectivePixelLimit) {
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

function formatDuration(durationMs: number) {
  if (durationMs >= 1000) return `${(durationMs / 1000).toFixed(1)}s`;
  return `${Math.max(0, Math.round(durationMs))}ms`;
}

function durationLabel(job: GenerationJob) {
  if (job.durationMs != null) return formatDuration(job.durationMs);
  if (job.status !== "PENDING") return job.status === "FAILED" ? "失败" : "-";

  const createdAtMs = Date.parse(job.createdAt);
  if (!Number.isFinite(createdAtMs)) return "生成中";

  return formatDuration(currentTimeMs.value - createdAtMs);
}

function failedReason(job: GenerationJob) {
  return job.errorMessage?.trim() || "上游生成失败";
}

function failedReasonTitle(job: GenerationJob) {
  return `失败原因：${failedReason(job)}`;
}

function historyThumbnail(job: GenerationJob) {
  const image = (job.images ?? [])[0];
  if (image) return image.publicUrl;
  if (job.status === "FAILED") return failedImageUrl;
  return "";
}

function requestIdForJob(job: GenerationJob) {
  return typeof job.params.request_id === "string" ? job.params.request_id : "";
}

function isPendingPreview(item: PreviewImage) {
  return item.job.status === "PENDING" || item.id.startsWith("pending-");
}

function isFailedPreview(item: PreviewImage) {
  return item.job.status === "FAILED" || item.id.startsWith("failed-");
}

function useUnavailableImage(event: Event) {
  const image = event.target instanceof HTMLImageElement ? event.target : null;
  if (!image || image.src.endsWith("/assets/image-unavailable-mark.svg")) return;
  image.src = unavailableImageUrl;
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
  document.documentElement.dataset.theme = theme;
  localStorage.setItem("theme", theme);
  localStorage.setItem("baseUrl", fixedBaseUrl);
}

async function bind() {
  connection.baseUrl = fixedBaseUrl;
  const apiKey = connection.apiKey.trim();
  if (apiKey.length < 8) return;
  connection.apiKey = apiKey;
  await store.bind(fixedBaseUrl, apiKey);
}

onMounted(async () => {
  applyTheme();
  historyClockTimer = window.setInterval(() => {
    currentTimeMs.value = Date.now();
  }, 1000);

  if (connection.baseUrl && connection.apiKey) {
    await bind().catch((error) => {
      store.error = error instanceof Error ? error.message : "连接失败";
    });
  }
});

onUnmounted(() => {
  if (historyClockTimer) {
    window.clearInterval(historyClockTimer);
    historyClockTimer = undefined;
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

watch(
  () => store.historyPage,
  (page) => {
    if (currentHistoryPage.value !== page) currentHistoryPage.value = page;
  }
);

watch(
  currentHistoryPage,
  (page) => {
    if (store.profile && page !== store.historyPage) {
      store.loadHistory(page).catch((error) => {
        store.error = error instanceof Error ? error.message : "历史加载失败";
      });
    }
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
  const hasReferenceImage = Array.from(event.dataTransfer.types).includes("application/x-tcboys-reference-image")
    || Array.from(event.dataTransfer.types).includes("Files")
    || Array.from(event.dataTransfer.items ?? []).some((item) => item.kind === "file" && item.type.startsWith("image/"));
  if (!hasReferenceImage) return;

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

  const droppedFiles = Array.from(event.dataTransfer.files ?? [])
    .filter((file) => file.type.startsWith("image/") && file.size <= maxReferenceImageBytes);
  if (droppedFiles.length) {
    const remaining = Math.max(0, 4 - referenceImages.value.length);
    const selected = droppedFiles.slice(0, remaining);
    const images = await Promise.all(selected.map(fileToReferenceImage));
    addReferenceImages(images);
    return;
  }

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
  form.count = Math.max(1, Math.min(10, Number(form.count) || 1));

  await store.generate({
    prompt: form.prompt,
    model: form.model,
    size: upstreamSize.value || "auto",
    aspectRatio: form.aspectRatio,
    count: form.count,
    quality: form.quality === "auto" ? undefined : form.quality,
    outputFormat: form.outputFormat,
    responseFormat: upstreamResponseFormat.value,
    extraParams: {
      size_tier: modelSizeTier[form.model],
      aspect_ratio: form.aspectRatio,
      upstream_size: upstreamSize.value || "auto",
      computed_size: computedSize.value || "auto"
    },
    referenceImages: referenceImages.value.map(({ name, mimeType, data }) => ({ name, mimeType, data }))
  });
}

function reuseJob(job: GenerationJob) {
  form.prompt = job.prompt;
  form.model = ["gpt-image-2-4k", "gpt-image-2"].includes(job.model)
    ? job.model as ImageModel
    : "gpt-image-2";
  form.count = Math.max(1, Math.min(10, Number(job.count) || 1));
  form.quality = job.quality ?? "auto";
  form.outputFormat = typeof job.params.output_format === "string"
    ? job.params.output_format as OutputFormat
    : "png";
  const savedAspectRatio = typeof job.params.aspect_ratio === "string" ? job.params.aspect_ratio : "auto";
  form.customSizeMode = "ratio";
  form.aspectRatio = aspectRatios.includes(savedAspectRatio as AspectRatioPreset)
    ? savedAspectRatio as AspectRatioPreset
    : "auto";
  form.customAspectRatio = typeof job.params.custom_aspect_ratio === "string" ? job.params.custom_aspect_ratio : "1:1";
  form.customWidth = typeof job.params.custom_width === "number" ? job.params.custom_width : 1024;
  form.customHeight = typeof job.params.custom_height === "number" ? job.params.custom_height : 1024;
  form.responseFormat = upstreamResponseFormat.value;
}

function downloadAll() {
  latestImages.value.filter((item) => !isPendingPreview(item) && !isFailedPreview(item)).forEach((item) => {
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

async function deleteAllHistory() {
  if (!store.historyTotal) return;
  const confirmed = window.confirm(`确定删除全部 ${store.historyTotal} 条历史记录吗？本地已保存图片也会一起删除。`);
  if (!confirmed) return;
  await store.deleteAllJobs().catch(() => undefined);
  previewMode.value = "latest";
  previewImageId.value = null;
  singlePreviewImage.value = null;
  currentHistoryPage.value = 1;
}

function openPreview(id: string) {
  previewMode.value = "latest";
  singlePreviewImage.value = null;
  previewImageId.value = id;
}

async function openHistoryPreview(job: GenerationJob) {
  const requestId = requestIdForJob(job);
  let shouldShowBatchInResults = false;

  if (requestId) {
    await store.loadCurrentResults(requestId).catch((error) => {
      store.error = error instanceof Error ? error.message : "批次结果加载失败";
    });
    shouldShowBatchInResults = store.jobs.some((item) => requestIdForJob(item) === requestId && item.status === "PENDING");

    if (shouldShowBatchInResults) {
      store.currentRequestId = requestId;
      previewMode.value = "latest";
      singlePreviewImage.value = null;
      previewImageId.value = null;
    }
  }

  const image = (job.images ?? [])[0];
  if (!image) return;

  previewMode.value = shouldShowBatchInResults ? "latest" : "single";
  singlePreviewImage.value = shouldShowBatchInResults ? null : { ...image, job };
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

</script>

<template>
  <main class="shell">
    <div class="workspace">
      <aside class="panel controls">
        <section class="panel-section">
          <div class="section-title">
            <KeyRound :size="17" />
            <div>
              <h2>连接</h2>
            </div>
          </div>
          <label>
            <span>API Key</span>
            <span class="secret-field">
              <input v-model="connection.apiKey" :type="showApiKey ? 'text' : 'password'" placeholder="sk-..." autocomplete="off" />
              <button class="secret-toggle" type="button" :title="showApiKey ? '隐藏 API Key' : '显示 API Key'" @click="showApiKey = !showApiKey">
                <EyeOff v-if="showApiKey" :size="16" />
                <Eye v-else :size="16" />
              </button>
            </span>
          </label>
        </section>

        <section class="panel-section">
          <div class="section-title">
            <Brush :size="17" />
            <div>
              <h2>参数</h2>
            </div>
          </div>
          <label>
            <span>模型</span>
            <select v-model="form.model">
              <option value="gpt-image-2-4k">gpt-image-2-4k</option>
              <option value="gpt-image-2">gpt-image-2</option>
            </select>
          </label>
          <label>
            <span>图片比例</span>
            <select v-model="form.aspectRatio">
              <option value="auto">Auto</option>
              <option value="1:1">1:1</option>
              <option value="2:3">2:3</option>
              <option value="3:2">3:2</option>
              <option value="9:16">9:16</option>
              <option value="16:9">16:9</option>
            </select>
          </label>
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
            <label>
              <span>数量</span>
              <input v-model.number="form.count" type="number" min="1" max="10" @change="form.count = Math.max(1, Math.min(10, Number(form.count) || 1))" />
            </label>
          </div>
        </section>
      </aside>

      <section class="panel composer">
        <div class="section-title results-title">
          <div class="section-title-label">
            <Play :size="17" />
            <div>
              <h2>生成结果</h2>
            </div>
          </div>
          <button class="title-action" type="button" :disabled="!latestImages.some((item) => !isPendingPreview(item) && !isFailedPreview(item))" @click="downloadAll">
            <ArrowDownToLine :size="14" />
            下载全部
          </button>
        </div>

        <div class="retention-notice" role="note">
          <Clock3 :size="15" />
          <span>生成图片只保留 24 小时，请及时下载保存。</span>
        </div>

        <div :class="['results', `results-${resultsLayoutClass}`]">
          <figure v-for="item in latestImages" :key="item.id" :class="['image-tile', { failed: isFailedPreview(item) }]">
            <button class="image-preview-trigger" type="button" title="预览图片" :disabled="isPendingPreview(item) || isFailedPreview(item)" @click="openPreview(item.id)">
              <div v-if="isPendingPreview(item)" class="generation-loader" aria-label="图片生成中">
                <div class="generation-loader-grid"></div>
                <div class="generation-loader-orbit">
                  <span></span>
                </div>
                <div class="generation-loader-copy">
                  <strong>正在生成图像</strong>
                  <span>已等待 {{ durationLabel(item.job) }}</span>
                </div>
                <div class="generation-loader-corners" aria-hidden="true">
                  <span></span>
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div class="generation-loader-track"><span></span></div>
              </div>
              <div v-else-if="isFailedPreview(item)" class="generation-failed" aria-label="图片生成失败">
                <img :src="item.publicUrl" alt="图片生成失败" @error="useUnavailableImage" />
              </div>
              <img v-else :src="item.publicUrl" :alt="item.job.prompt" @error="useUnavailableImage" />
            </button>
            <figcaption>
              <p>{{ item.job.prompt }}</p>
              <div class="image-meta">
                <span>耗时：{{ durationLabel(item.job) }}</span>
                <span v-if="isFailedPreview(item)" class="failed-reason">{{ item.job.errorMessage || "上游生成失败" }}</span>
              </div>
              <div class="image-actions">
                <button type="button" title="下载图片" :disabled="isPendingPreview(item) || isFailedPreview(item)" @click="downloadImage(item)">
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
          <div v-if="!latestImages.length" class="empty">
            <div>
              <strong>等待第一张作品</strong>
              <span>填写提示词并点击生成，结果会以网格形式展示。</span>
            </div>
          </div>
        </div>

        <div
          :class="['prompt-panel', { 'is-reference-dragging': isReferenceDragging }]"
          @dragover="handleReferenceDragOver"
          @dragleave="handleReferenceDragLeave"
          @drop="handleReferenceDrop"
        >
          <div class="reference-bar">
            <div class="reference-upload-row">
              <label class="upload-button">
                <ImagePlus :size="17" />
                上传参考图
                <input type="file" accept="image/*" multiple @change="handleReferenceUpload" />
              </label>
              <span class="muted">可从右侧历史拖入</span>
            </div>
            <span class="muted">最多 4 张，每张不超过 10MB。</span>
          </div>

          <div v-if="referenceImages.length" class="reference-grid">
            <figure v-for="image, index in referenceImages" :key="image.previewUrl" class="reference-tile">
              <button class="reference-preview-button" type="button" title="预览参考图" @click="openReferencePreview(index)">
                <img :src="image.previewUrl" :alt="image.name" @error="useUnavailableImage" />
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
          <div class="side-title-row">
            <h2>历史</h2>
            <div class="side-title-actions">
              <button class="icon-btn small danger-text" type="button" title="删除全部历史" :disabled="!store.profile || store.historyLoading || !store.historyTotal" @click="deleteAllHistory">
                <Trash2 :size="15" />
              </button>
              <button class="icon-btn small" type="button" title="刷新历史" :disabled="!store.profile || store.historyLoading" @click="store.loadHistory(currentHistoryPage)">
                <Loader2 v-if="store.historyLoading" class="spin" :size="15" />
                <RefreshCw v-else :size="15" />
              </button>
            </div>
          </div>
        </div>

        <section class="list">
          <article v-for="job in pagedHistoryJobs" :key="job.id" class="list-item">
            <button
              class="history-thumb-button"
              type="button"
              :title="job.status === 'FAILED' ? failedReasonTitle(job) : '查看批次'"
              @click="openHistoryPreview(job)"
            >
              <span v-if="job.status === 'PENDING' && !job.images?.length" class="generation-loader history-thumb-loading" aria-label="图片生成中">
                <span class="generation-loader-grid"></span>
                <span class="generation-loader-orbit">
                  <span></span>
                </span>
                <span class="generation-loader-corners" aria-hidden="true">
                  <span></span>
                  <span></span>
                  <span></span>
                  <span></span>
                </span>
                <span class="generation-loader-track"><span></span></span>
              </span>
              <img
                v-else-if="historyThumbnail(job)"
                class="history-thumb"
                :src="historyThumbnail(job)"
                :alt="job.status === 'FAILED' ? failedReasonTitle(job) : job.prompt"
                :draggable="!!job.images?.length"
                :title="job.status === 'FAILED' ? failedReasonTitle(job) : job.prompt"
                @dragstart="dragHistoryImage($event, job)"
                @error="useUnavailableImage"
              />
              <span v-else class="history-thumb-placeholder">无图</span>
            </button>
            <div class="history-meta">
              <strong class="history-prompt" :title="job.prompt">{{ job.prompt }}</strong>
              <p>耗时：{{ durationLabel(job) }}</p>
              <span class="history-status-wrap">
                <span
                  :class="['history-status', job.status.toLowerCase()]"
                  :title="job.status === 'FAILED' ? failedReasonTitle(job) : undefined"
                >
                  {{ statusLabel(job.status) }}
                </span>
                <span v-if="job.status === 'FAILED'" class="history-failed-tooltip" role="tooltip">
                  {{ failedReasonTitle(job) }}
                </span>
              </span>
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
          <div v-if="!store.jobs.length" class="empty compact">
            <div>
              <strong>暂无历史</strong>
              <span>成功生成后会自动记录在这里。</span>
            </div>
          </div>
        </section>
        <div v-if="store.historyTotal > historyPageSize" class="pagination">
          <button type="button" :disabled="store.historyLoading || currentHistoryPage <= 1" @click="currentHistoryPage -= 1">上一页</button>
          <span>{{ currentHistoryPage }} / {{ historyPageCount }}</span>
          <button type="button" :disabled="store.historyLoading || currentHistoryPage >= historyPageCount" @click="currentHistoryPage += 1">下一页</button>
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
        <img :src="previewImage.publicUrl" :alt="previewImage.job.prompt" @error="useUnavailableImage" />
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

    <div v-if="docDialogOpen" class="doc-modal" role="dialog" aria-modal="true" @click.self="docDialogOpen = false">
      <div class="doc-card">
        <div class="doc-header">
          <div class="section-title-label">
            <BookOpenText :size="18" />
            <div>
              <h2>文档</h2>
            </div>
          </div>
          <button class="icon-btn small" type="button" title="关闭文档" @click="docDialogOpen = false">
            <X :size="16" />
          </button>
        </div>

        <div class="doc-tabs" role="tablist" aria-label="模型文档">
          <button
            type="button"
            :class="{ active: activeDocTab === 'gpt-image-2' }"
            role="tab"
            :aria-selected="activeDocTab === 'gpt-image-2'"
            @click="activeDocTab = 'gpt-image-2'"
          >
            gpt-image-2
          </button>
          <button
            type="button"
            :class="{ active: activeDocTab === 'gpt-image-2-4k' }"
            role="tab"
            :aria-selected="activeDocTab === 'gpt-image-2-4k'"
            @click="activeDocTab = 'gpt-image-2-4k'"
          >
            gpt-image-2-4k
          </button>
        </div>

        <div class="doc-body">
          <section v-if="activeDocTab === 'gpt-image-2'" class="doc-section">
            <p><strong>GPT-Image-2：</strong>size 请传画幅比例（如 1:1）。文生图 JSON POST /images/generations（async: true，stream: false）；带参考图/多图叠图/蒙版须 multipart POST /images/edits（image / image[]），JSON generations 传 image URL 无效；GET 轮询取 data.url。</p>

            <h3>接口</h3>
            <ul>
              <li><strong>POST</strong> https://ai.cangyuansuanli.cn/v1/images/generations：文生图（application/json，async 必须为 true）。</li>
              <li><strong>GET</strong> https://ai.cangyuansuanli.cn/v1/images/generations/{task_id}：查询文生图异步任务。</li>
              <li><strong>POST</strong> https://ai.cangyuansuanli.cn/v1/images/edits：图生图/编辑（multipart/form-data，async 必须为 true）。参考图须上传文件，JSON 传 image URL 无效。</li>
              <li><strong>GET</strong> https://ai.cangyuansuanli.cn/v1/images/edits/{task_id}：查询图生图异步任务。</li>
              <li><strong>GET</strong> https://ai.cangyuansuanli.cn/v1/images/{task_id}/content：下载图片（部分模型）。</li>
            </ul>

            <h3>请求字段</h3>
            <ul>
              <li><strong>model：</strong>必填，固定传模型广场展示名 cy-img1-gpt-image-2。</li>
              <li><strong>prompt：</strong>必填，图像描述提示词；edits 时可在 prompt 中用 @图片1 等引用参考图。</li>
              <li><strong>async：</strong>异步模式必填 true。</li>
              <li><strong>size：</strong>画幅比例（推荐），如 1:1、3:2、2:3；兼容传像素但不保证输出像素一致。1:1 @ 1K 实际约 1254x1254。</li>
              <li><strong>n：</strong>生成张数，1-10，默认 1。</li>
              <li><strong>stream：</strong>建议 false（非 SSE JSON 响应）；edits 异步同样建议 false。</li>
              <li><strong>image / image[]：</strong>edits 端点 multipart 参考图字段，最多 10 张。</li>
              <li><strong>mask：</strong>edits 端点可选蒙版 PNG，透明区域为编辑区，尺寸须与首图一致。</li>
            </ul>

            <h3>请求 JSON</h3>
            <pre><code>{
  "async": true,
  "model": "gpt-image-2",
  "n": 1,
  "prompt": "一只橘猫坐在窗台上，午后阳光",
  "size": "1:1",
  "stream": false
}</code></pre>

            <h3>返回示例</h3>
            <pre><code>{
  "created_at": 1715923200,
  "id": "task_img_01HZX8A2...",
  "model": "gpt-image-2",
  "object": "image.generation",
  "progress": "10%",
  "status": "queued"
}</code></pre>
            <pre><code>{
  "data": [
    {
      "url": "https://example.com/image.png"
    }
  ],
  "id": "task_img_01HZX8A2...",
  "object": "image.generation",
  "progress": "100%",
  "status": "completed"
}</code></pre>
          </section>

          <section v-else class="doc-section">
            <p><strong>GPT-Image-2-4K：</strong>当前项目用于高清出图，模型选择 gpt-image-2-4k。前端只选择画幅比例，系统固定按 4K 档计算最终像素尺寸；请求会走本地后端中转，不让浏览器直连上游。</p>

            <h3>当前页面参数</h3>
            <ul>
              <li><strong>模型：</strong>gpt-image-2-4k。</li>
              <li><strong>图片比例：</strong>Auto、1:1、2:3、3:2、9:16、16:9。</li>
              <li><strong>生成数量：</strong>1-10。</li>
              <li><strong>返回格式：</strong>当前项目对 4K 模型使用 URL 返回，再由后端下载到本地保存。</li>
            </ul>

            <h3>尺寸换算</h3>
            <ul>
              <li><strong>4K：</strong>1:1 为 2880x2880，16:9 为 3840x2160，9:16 为 2160x3840。</li>
              <li><strong>Auto：</strong>交给上游自动决定。</li>
            </ul>

            <h3>请求说明</h3>
            <pre><code>{
  "model": "gpt-image-2-4k",
  "prompt": "图片描述",
  "n": 1,
  "size": "3840x2160",
  "response_format": "url"
}</code></pre>
          </section>
        </div>
      </div>
    </div>
  </main>
</template>
