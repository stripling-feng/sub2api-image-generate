<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { ArrowDownToLine, BookOpenText, Brush, Clock3, Eye, EyeOff, ImagePlus, KeyRound, Loader2, Play, RefreshCw, Trash2, X } from "lucide-vue-next";
import { useWorkbenchStore } from "./stores/workbench";
import { api } from "./api";
import type { GeneratedImage, GenerationJob, ImageModelConfig } from "./types";

type ReferenceImage = {
  name: string;
  mimeType: string;
  file: File;
  previewUrl: string;
  width: number;
  height: number;
};

type PreviewImage = GeneratedImage & { job: GenerationJob };
type HistoryBatch = {
  requestId: string;
  jobs: GenerationJob[];
  prompt: string;
  model: string;
  status: GenerationJob["status"];
  createdAt: string;
  totalCount: number;
  completedCount: number;
  generatedCount: number;
  thumbnailUrl: string;
  thumbnailJob: GenerationJob;
};
const maxReferenceImageBytes = 10 * 1024 * 1024;
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
  model: "gpt-image-2",
  count: 1
});
const modelConfigs = ref<ImageModelConfig[]>([]);
const modelParameters = reactive<Record<string, unknown>>({});
const activeModel = computed(() => modelConfigs.value.find(item => item.model === form.model) ?? null);
const referenceLimit = computed(() => activeModel.value?.maxReferenceImages ?? 0);

const autoBindTimer = ref<number | undefined>();
const referenceImages = ref<ReferenceImage[]>([]);
const maskImage = ref<ReferenceImage | null>(null);
const isReferenceDragging = ref(false);
const isReferenceImporting = ref(false);
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

  return sortJobsByRequestIndex(store.jobs.filter((job) => requestIdForJob(job) === store.currentRequestId));
});
const latestImages = computed(() => {
  const jobs = currentResultJobs.value;
  if (!jobs.length) return [];

  const totalCount = requestTotalForJobs(jobs);
  const slots: Array<PreviewImage | undefined> = Array.from({ length: Math.max(1, totalCount) });
  const overflow: PreviewImage[] = [];
  const firstJob = jobs[0];
  const failedJob = jobs.find((job) => job.status === "FAILED");
  const hasPendingJobs = jobs.some((job) => job.status === "PENDING");

  jobs.forEach((job) => {
    const images = job.images ?? [];
    const requestIndex = requestIndexForJob(job);
    const targetIndex = requestIndex != null ? requestIndex - 1 : -1;
    const previews: PreviewImage[] = images.length
      ? images.map((image) => ({ ...image, job }))
      : job.status === "FAILED"
        ? [{
            id: `failed-${job.id}`,
            jobId: job.id,
            publicUrl: failedImageUrl,
            mimeType: "application/x-failed",
            sizeBytes: 0,
            createdAt: job.createdAt,
            job
          }]
        : job.status === "PENDING"
          ? [{
              id: `pending-${job.id}`,
              jobId: job.id,
              publicUrl: "",
              mimeType: "application/x-pending",
              sizeBytes: 0,
              createdAt: job.createdAt,
              job
            }]
          : [];

    previews.forEach((preview, offset) => {
      const index = targetIndex >= 0 ? targetIndex + offset : slots.findIndex((slot) => !slot);
      if (index >= 0 && index < slots.length && !slots[index]) {
        slots[index] = preview;
      } else {
        overflow.push(preview);
      }
    });
  });

  slots.forEach((slot, index) => {
    if (!slot && firstJob) {
      const job = hasPendingJobs ? firstJob : (failedJob ?? firstJob);
      slots[index] = {
        id: hasPendingJobs ? `pending-${store.currentRequestId}-${index + 1}` : `failed-${store.currentRequestId}-${index + 1}`,
        jobId: job.id,
        publicUrl: hasPendingJobs ? "" : failedImageUrl,
        mimeType: hasPendingJobs ? "application/x-pending" : "application/x-failed",
        sizeBytes: 0,
        createdAt: job.createdAt,
        job: { ...job, status: hasPendingJobs ? "PENDING" : "FAILED", images: [] }
      };
    }
  });

  return [...slots.filter((slot): slot is PreviewImage => Boolean(slot)), ...overflow];
});
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
const pagedHistoryBatches = computed(() => {
  const groups = new Map<string, GenerationJob[]>();
  store.jobs.forEach((job) => {
    const requestId = requestIdForJob(job);
    groups.set(requestId, [...(groups.get(requestId) ?? []), job]);
  });

  return [...groups.entries()]
    .map(([requestId, jobs]) => buildHistoryBatch(requestId, jobs))
    .sort((left, right) => {
      if (left.status === "PENDING" && right.status !== "PENDING") return -1;
      if (left.status !== "PENDING" && right.status === "PENDING") return 1;
      return Date.parse(right.createdAt) - Date.parse(left.createdAt);
    });
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
const estimatedChargeUsd = computed(() => {
  const price = Number(activeModel.value?.unitPriceUsd ?? 0);
  return (Math.max(1, Number(form.count) || 1) * price).toFixed(2);
});

function statusLabel(status: GenerationJob["status"]) {
  return {
    PENDING: "生成中",
    SUCCEEDED: "已完成",
    FAILED: "失败"
  }[status];
}

function requestIdForJob(job: GenerationJob) {
  return typeof job.params.request_id === "string" ? job.params.request_id : job.id;
}

function requestIndexForJob(job: GenerationJob) {
  return typeof job.params.request_index === "number" ? job.params.request_index : undefined;
}

function requestTotalForJobs(jobs: GenerationJob[]) {
  const declaredTotal = Math.max(0, ...jobs.map((job) => (
    typeof job.params.request_total === "number" ? job.params.request_total : 0
  )));
  if (declaredTotal > 0) return declaredTotal;
  return jobs.reduce((sum, job) => sum + Math.max(1, job.images?.length || job.count || 1), 0);
}

function sortJobsByRequestIndex(jobs: GenerationJob[]) {
  return [...jobs].sort((left, right) => {
    const indexDiff = (requestIndexForJob(left) ?? Number.MAX_SAFE_INTEGER) - (requestIndexForJob(right) ?? Number.MAX_SAFE_INTEGER);
    if (indexDiff !== 0) return indexDiff;
    return Date.parse(left.createdAt) - Date.parse(right.createdAt);
  });
}

function failedCountForJobs(jobs: GenerationJob[]) {
  return jobs
    .filter((job) => job.status === "FAILED")
    .reduce((sum, job) => sum + Math.max(1, job.count || 1), 0);
}

function historyBatchStatus(jobs: GenerationJob[], totalCount: number, completedCount: number): GenerationJob["status"] {
  if (jobs.some((job) => job.status === "PENDING") && completedCount < totalCount) return "PENDING";
  if (jobs.some((job) => job.status === "FAILED")) return "FAILED";
  return "SUCCEEDED";
}

function buildHistoryBatch(requestId: string, jobs: GenerationJob[]): HistoryBatch {
  const orderedJobs = sortJobsByRequestIndex(jobs);
  const thumbnailJob = orderedJobs.find((job) => job.images?.length) ?? orderedJobs[0];
  const totalCount = requestTotalForJobs(orderedJobs);
  const generatedCount = orderedJobs.reduce((sum, job) => sum + (job.images?.length ?? 0), 0);
  const completedCount = Math.min(totalCount, generatedCount + failedCountForJobs(orderedJobs));
  const status = historyBatchStatus(orderedJobs, totalCount, completedCount);
  const createdAt = orderedJobs
    .map((job) => job.createdAt)
    .sort((left, right) => Date.parse(right) - Date.parse(left))[0] ?? new Date().toISOString();

  return {
    requestId,
    jobs: orderedJobs,
    prompt: orderedJobs[0]?.prompt ?? "",
    model: orderedJobs[0]?.model ?? "",
    status,
    createdAt,
    totalCount,
    completedCount,
    generatedCount,
    thumbnailUrl: thumbnailJob ? historyThumbnail(thumbnailJob) : "",
    thumbnailJob: thumbnailJob ?? orderedJobs[0]
  };
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

function apiKeyHeaders(): Record<string, string> {
  const apiKey = localStorage.getItem("apiKey") ?? "";
  return apiKey ? { "X-API-Key": apiKey } : {};
}

function applyTheme() {
  document.documentElement.dataset.theme = theme;
  localStorage.setItem("theme", theme);
  localStorage.setItem("baseUrl", fixedBaseUrl);
}

function applyModelDefaults() {
  const model = activeModel.value;
  for (const key of Object.keys(modelParameters)) delete modelParameters[key];
  if (!model) return;
  for (const field of model.parameters) {
    modelParameters[field.key] = model.defaults[field.key] ?? field.default ?? "";
  }
  form.count = Math.max(1, Math.min(model.maxCount, Number(form.count) || 1));
  if (!model.supportsMask) removeMaskImage();
  if (referenceImages.value.length > model.maxReferenceImages) {
    referenceImages.value.splice(model.maxReferenceImages).forEach(image => URL.revokeObjectURL(image.previewUrl));
  }
}

async function loadModelConfigs() {
  const data = await api.get<{ models: ImageModelConfig[] }>("/api/images/models");
  modelConfigs.value = data.models;
  if (!modelConfigs.value.some(item => item.model === form.model)) form.model = modelConfigs.value[0]?.model ?? "";
  applyModelDefaults();
}

async function connect() {
  connection.baseUrl = fixedBaseUrl;
  const apiKey = connection.apiKey.trim();
  if (apiKey.length < 8) return;
  connection.apiKey = apiKey;
  await store.connect(apiKey);
}

onMounted(async () => {
  applyTheme();
  await loadModelConfigs().catch(error => { store.error = error instanceof Error ? error.message : "模型配置加载失败"; });
  historyClockTimer = window.setInterval(() => {
    currentTimeMs.value = Date.now();
  }, 1000);

  if (connection.baseUrl && connection.apiKey) {
    await connect().catch((error) => {
      store.error = error instanceof Error ? error.message : "连接失败";
    });
  }
});

onUnmounted(() => {
  if (historyClockTimer) {
    window.clearInterval(historyClockTimer);
    historyClockTimer = undefined;
  }
  referenceImages.value.forEach((image) => URL.revokeObjectURL(image.previewUrl));
  if (maskImage.value) URL.revokeObjectURL(maskImage.value.previewUrl);
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
      connect().catch((error) => {
        store.error = error instanceof Error ? error.message : "连接失败";
      });
    }, 700);
  }
);

watch(() => form.model, applyModelDefaults);

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
    const previewUrl = URL.createObjectURL(file);
    const image = new Image();
    image.onerror = () => {
      URL.revokeObjectURL(previewUrl);
      reject(new Error("图片尺寸读取失败"));
    };
    image.onload = () => resolve({
      name: file.name,
      mimeType: file.type || "image/png",
      file,
      previewUrl,
      width: image.naturalWidth,
      height: image.naturalHeight
    });
    image.src = previewUrl;
  });
}

async function urlToReferenceImage(url: string, name: string, fallbackMimeType?: string, imageId?: string): Promise<ReferenceImage> {
  const downloadUrl = imageId ? `/api/images/${encodeURIComponent(imageId)}/download` : url;
  const response = await fetch(downloadUrl, imageId ? {
    credentials: "include",
    headers: apiKeyHeaders()
  } : undefined);
  if (!response.ok) throw new Error("图片读取失败");

  const blob = await response.blob();
  if (!blob.type.startsWith("image/")) throw new Error("只能添加图片格式");
  if (blob.size > maxReferenceImageBytes) throw new Error("参考图不能超过 10MB");

  return await fileToReferenceImage(new File([blob], name, { type: blob.type || fallbackMimeType || "image/png" }));
}

function addReferenceImages(images: ReferenceImage[]) {
  const remaining = Math.max(0, referenceLimit.value - referenceImages.value.length);
  const accepted = images.slice(0, remaining);
  images.slice(remaining).forEach((image) => URL.revokeObjectURL(image.previewUrl));
  referenceImages.value = [...referenceImages.value, ...accepted];
}

async function handleReferenceUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  const validFiles = files.filter((file) => file.type.startsWith("image/") && file.size <= maxReferenceImageBytes);
  const remaining = Math.max(0, referenceLimit.value - referenceImages.value.length);
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
    id: image.id,
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
  if (!event.dataTransfer || referenceImages.value.length >= referenceLimit.value) return;

  isReferenceImporting.value = true;
  try {
    const droppedFiles = Array.from(event.dataTransfer.files ?? [])
      .filter((file) => file.type.startsWith("image/") && file.size <= maxReferenceImageBytes);
    if (droppedFiles.length) {
      const remaining = Math.max(0, referenceLimit.value - referenceImages.value.length);
      const selected = droppedFiles.slice(0, remaining);
      const images = await Promise.all(selected.map(fileToReferenceImage));
      addReferenceImages(images);
      return;
    }

    const raw = event.dataTransfer.getData("application/x-tcboys-reference-image");
    const uri = event.dataTransfer.getData("text/uri-list").split("\n").find((line) => line && !line.startsWith("#"));
    let payload: { id?: string; url: string; name?: string; mimeType?: string } | null = null;

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
      const image = await urlToReferenceImage(url, payload?.name || "reference.png", payload?.mimeType, payload?.id);
      addReferenceImages([image]);
    } catch (error) {
      store.error = error instanceof Error ? error.message : "参考图添加失败";
    }
  } finally {
    isReferenceImporting.value = false;
  }
}

function removeReferenceImage(index: number) {
  const removed = referenceImages.value[index];
  if (removed) URL.revokeObjectURL(removed.previewUrl);
  referenceImages.value = referenceImages.value.filter((_, itemIndex) => itemIndex !== index);
  if (index === 0 && maskImage.value) removeMaskImage();
}

async function handleMaskUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  if (file.type !== "image/png" || !file.name.toLowerCase().endsWith(".png")) {
    store.error = "蒙版仅支持 PNG 格式";
    return;
  }
  if (file.size > maxReferenceImageBytes) {
    store.error = "蒙版不能超过 10MB";
    return;
  }
  const firstReference = referenceImages.value[0];
  if (!firstReference) {
    store.error = "请先上传第一张参考图，再添加蒙版";
    return;
  }
  const image = await fileToReferenceImage(file);
  if (image.width !== firstReference.width || image.height !== firstReference.height) {
    URL.revokeObjectURL(image.previewUrl);
    store.error = "蒙版尺寸必须与第一张参考图一致";
    return;
  }
  if (maskImage.value) URL.revokeObjectURL(maskImage.value.previewUrl);
  maskImage.value = image;
  store.error = "";
}

function removeMaskImage() {
  if (maskImage.value) URL.revokeObjectURL(maskImage.value.previewUrl);
  maskImage.value = null;
}

function showNotice(title: string, message: string) {
  noticeDialog.title = title;
  noticeDialog.message = message;
  noticeDialog.open = true;
}

function closeNotice() {
  noticeDialog.open = false;
}

function textParameter(key: string) {
  const value = modelParameters[key];
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function generationPayloadFields() {
  const value: { size?: string; aspect_ratio?: string; quality?: string } = {};
  const size = textParameter("size");
  const aspectRatio = textParameter("aspect_ratio");
  const quality = textParameter("quality");

  if (form.model === "gpt-image-2") {
    if (size) value.size = size;
    return value;
  }

  if (size) value.size = size;
  if (aspectRatio) value.aspect_ratio = aspectRatio;
  if (quality) value.quality = quality;
  return value;
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
    await connect();
  }

  if (!store.profile) {
    store.error = "请先填写中转 URL 和 API Key。";
    return;
  }

  previewMode.value = "latest";
  previewImageId.value = null;
  singlePreviewImage.value = null;
  const model = activeModel.value;
  if (!model) { store.error = "没有可用的图片模型"; return; }
  form.count = Math.max(1, Math.min(model.maxCount, Number(form.count) || 1));

  const payloadFields = generationPayloadFields();
  if (form.model === "gpt-image-2" && !payloadFields.size) {
    store.error = "size is required.";
    return;
  }
  if (form.model !== "gpt-image-2" && !payloadFields.size && !payloadFields.aspect_ratio) {
    store.error = "size or aspect_ratio is required.";
    return;
  }

  await store.generate({
    prompt: form.prompt,
    model: form.model,
    async: true,
    count: form.count,
    ...payloadFields,
    referenceImages: referenceImages.value.map(({ name, mimeType, file }) => ({ name, mimeType, file }))
  });
}

function reuseJob(job: GenerationJob) {
  form.prompt = job.prompt;
  form.model = modelConfigs.value.some(item => item.model === job.model) ? job.model : (modelConfigs.value[0]?.model ?? "");
  applyModelDefaults();
  form.count = Math.max(1, Math.min(activeModel.value?.maxCount ?? 1, Number(job.count) || 1));
  for (const field of activeModel.value?.parameters ?? []) {
    if (job.params[field.key] != null) modelParameters[field.key] = job.params[field.key];
  }
}

async function saveImageFile(image: GeneratedImage) {
  const filename = downloadName(image);
  const downloadUrl = `/api/images/${encodeURIComponent(image.id)}/download`;
  try {
    const response = await fetch(downloadUrl, {
      credentials: "include",
      headers: apiKeyHeaders()
    });
    if (!response.ok) throw new Error("download failed");
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch {
    try {
      const response = await fetch(image.publicUrl, { mode: "cors" });
      if (!response.ok) throw new Error("download failed");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      return;
    } catch {
      // Fall through to opening the original image URL when both download paths fail.
    }
    window.open(image.publicUrl, "_blank", "noopener,noreferrer");
    store.error = "图片下载失败，已在新窗口打开图片，请手动保存。";
  }
}

function downloadAll() {
  latestImages.value
    .filter((item) => !isPendingPreview(item) && !isFailedPreview(item))
    .forEach((item, index) => {
      window.setTimeout(() => { void saveImageFile(item); }, index * 120);
    });
}

function downloadBatchImages(batch: HistoryBatch) {
  batch.jobs.flatMap((job) => job.images ?? []).forEach((image, index) => {
    window.setTimeout(() => { void saveImageFile(image); }, index * 120);
  });
}

async function deleteHistoryBatch(batch: HistoryBatch) {
  await Promise.all(batch.jobs.map((job) => store.deleteJob(job.id))).catch(() => undefined);
  if (store.currentRequestId === batch.requestId) {
    store.currentRequestId = "";
  }
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

function openHistoryPreview(batch: HistoryBatch) {
  const requestId = batch.requestId;

  if (requestId) {
    const onlyImageJob = batch.jobs.find((job) => job.images?.length);
    const onlyImage = onlyImageJob?.images?.[0];

    if (batch.totalCount === 1 && onlyImage && onlyImageJob) {
      previewMode.value = "single";
      singlePreviewImage.value = { ...onlyImage, job: onlyImageJob };
      previewImageId.value = onlyImage.id;
      return;
    }

    store.currentRequestId = requestId;
    previewMode.value = "latest";
    singlePreviewImage.value = null;
    previewImageId.value = null;
  }
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
  void saveImageFile(image);
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
          <p v-if="store.profile?.availableBalanceUsd" class="muted">
            可用余额：${{ Number(store.profile.availableBalanceUsd).toFixed(2) }}
          </p>
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
              <option v-for="model in modelConfigs" :key="model.id" :value="model.model">{{ model.name }}</option>
            </select>
          </label>
          <label v-for="field in activeModel?.parameters || []" :key="field.key">
            <span>{{ field.label }}</span>
            <select v-if="field.type === 'select'" v-model="modelParameters[field.key]">
              <option v-for="option in field.options || []" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <input v-else-if="field.type === 'number'" v-model.number="modelParameters[field.key]" type="number" :placeholder="field.placeholder" />
            <input v-else v-model="modelParameters[field.key]" type="text" :placeholder="field.placeholder" />
          </label>
          <label>
            <span>数量</span>
            <input v-model.number="form.count" type="number" min="1" :max="activeModel?.maxCount || 1" @change="form.count = Math.max(1, Math.min(activeModel?.maxCount || 1, Number(form.count) || 1))" />
          </label>
          <p class="muted">预计扣费：${{ estimatedChargeUsd }}</p>
        </section>

        <div
          :class="['prompt-panel sidebar-prompt-panel', { 'is-reference-dragging': isReferenceDragging, 'is-reference-importing': isReferenceImporting }]"
          @dragover="handleReferenceDragOver"
          @dragleave="handleReferenceDragLeave"
          @drop="handleReferenceDrop"
        >
          <div v-if="isReferenceImporting" class="reference-import-overlay" role="status" aria-live="polite">
            <div class="reference-import-card">
              <Loader2 class="spin" :size="24" />
              <strong>正在导入参考图</strong>
              <span>图片读取中</span>
            </div>
          </div>

          <div class="reference-bar">
            <div class="reference-upload-row">
              <label class="upload-button">
                <ImagePlus :size="17" />
                上传参考图
                <input type="file" accept="image/*" multiple @change="handleReferenceUpload" />
              </label>
              <label v-if="activeModel?.supportsMask" class="upload-button" :class="{ disabled: !referenceImages.length }">
                <ImagePlus :size="17" />
                上传 PNG 蒙版
                <input type="file" accept="image/png,.png" :disabled="!referenceImages.length" @change="handleMaskUpload" />
              </label>
              <span class="muted">可从右侧历史拖入</span>
            </div>
            <span class="muted">参考图最大 {{ referenceLimit }} 张，每张不超过 10MB。</span>
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

          <div v-if="maskImage" class="mask-preview-row">
            <span class="muted">蒙版</span>
            <figure class="reference-tile">
              <button class="reference-preview-button" type="button" title="蒙版预览">
                <img :src="maskImage.previewUrl" :alt="maskImage.name" />
              </button>
              <button class="reference-remove" type="button" title="移除蒙版" @click="removeMaskImage">
                <X :size="13" />
              </button>
            </figure>
          </div>

          <label>
            <span>描述</span>
            <textarea v-model="form.prompt" class="prompt connection-prompt" placeholder="描述你要生成的画面、主体、镜头、材质和光线。"></textarea>
          </label>

          <div class="button-row composer-actions">
            <button class="primary big" type="button" :disabled="store.loading" @click="generate">
              <Loader2 v-if="store.loading" class="spin" :size="18" />
              <Play v-else :size="18" />
              生成图片
            </button>
          </div>
        </div>
      </aside>

      <section class="panel composer">
        <div class="retention-notice" role="note">
          <span class="retention-copy">
            <Clock3 :size="15" />
            <span>24 小时，请及时下载保存。</span>
          </span>
          <button class="title-action" type="button" :disabled="!latestImages.some((item) => !isPendingPreview(item) && !isFailedPreview(item))" @click="downloadAll">
            下载全部
          </button>
        </div>

        <div :class="['results', `results-${resultsLayoutClass}`]">
          <figure v-for="item in latestImages" :key="item.id" :class="['image-tile', { failed: isFailedPreview(item) }]">
            <button class="image-preview-trigger" type="button" title="预览图片" :disabled="isPendingPreview(item) || isFailedPreview(item)" @click="openPreview(item.id)">
              <div v-if="isPendingPreview(item)" class="generation-loader" aria-label="图片生成中">
                <div class="generation-loader-grid"></div>
                <div class="generation-loader-copy">
                  <strong>正在生成图像</strong>
                  <span>已等待 {{ durationLabel(item.job) }}<template v-if="item.job.progress"> · {{ item.job.progress }}%</template></span>
                </div>
                <div class="generation-loader-track"><span></span></div>
              </div>
              <div v-else-if="isFailedPreview(item)" class="generation-failed" aria-label="图片生成失败">
                <img :src="item.publicUrl" alt="图片生成失败" @error="useUnavailableImage" />
              </div>
              <img v-else :src="item.publicUrl" :alt="item.job.prompt" @error="useUnavailableImage" />
            </button>
          </figure>
          <div v-if="!latestImages.length" class="empty">
            <div>
              <strong>等待第一张作品</strong>
              <span>填写提示词并点击生成，结果会以网格形式展示。</span>
            </div>
          </div>
        </div>

        <div
          :class="['prompt-panel composer-prompt-panel', { 'is-reference-dragging': isReferenceDragging, 'is-reference-importing': isReferenceImporting }]"
          @dragover="handleReferenceDragOver"
          @dragleave="handleReferenceDragLeave"
          @drop="handleReferenceDrop"
        >
          <div v-if="isReferenceImporting" class="reference-import-overlay" role="status" aria-live="polite">
            <div class="reference-import-card">
              <Loader2 class="spin" :size="24" />
              <strong>正在导入参考图</strong>
              <span>图片读取中</span>
            </div>
          </div>

          <div class="reference-bar">
            <div class="reference-upload-row">
              <label class="upload-button">
                <ImagePlus :size="17" />
                上传参考图
                <input type="file" accept="image/*" multiple @change="handleReferenceUpload" />
              </label>
              <label v-if="activeModel?.supportsMask" class="upload-button" :class="{ disabled: !referenceImages.length }">
                <ImagePlus :size="17" />
                上传 PNG 蒙版
                <input type="file" accept="image/png,.png" :disabled="!referenceImages.length" @change="handleMaskUpload" />
              </label>
              <span class="muted">可从右侧历史拖入</span>
            </div>
            <span class="muted">参考图最多 {{ referenceLimit }} 张，每张不超过 10MB。</span>
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

          <div v-if="maskImage" class="mask-preview-row">
            <span class="muted">蒙版</span>
            <figure class="reference-tile">
              <button class="reference-preview-button" type="button" title="蒙版预览">
                <img :src="maskImage.previewUrl" :alt="maskImage.name" />
              </button>
              <button class="reference-remove" type="button" title="移除蒙版" @click="removeMaskImage">
                <X :size="13" />
              </button>
            </figure>
          </div>

          <div class="button-row composer-actions">
            <button class="primary big" type="button" :disabled="store.loading" @click="generate">
              <Loader2 v-if="store.loading" class="spin" :size="18" />
              <Play v-else :size="18" />
              生成图片
            </button>
          </div>
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
          <article v-for="batch in pagedHistoryBatches" :key="batch.requestId" class="list-item">
            <div
              class="history-thumb-button"
              :title="batch.status === 'FAILED' ? failedReasonTitle(batch.thumbnailJob) : '查看批次'"
              role="button"
              tabindex="0"
              @click="openHistoryPreview(batch)"
              @keydown.enter.prevent="openHistoryPreview(batch)"
              @keydown.space.prevent="openHistoryPreview(batch)"
            >
              <span v-if="batch.status === 'PENDING' && !batch.thumbnailUrl" class="generation-loader history-thumb-loading" aria-label="图片生成中">
                <span class="generation-loader-grid"></span>
                <span class="generation-loader-track"><span></span></span>
              </span>
              <img
                v-else-if="batch.thumbnailUrl"
                class="history-thumb"
                :src="batch.thumbnailUrl"
                :alt="batch.status === 'FAILED' ? failedReasonTitle(batch.thumbnailJob) : batch.prompt"
                :draggable="!!batch.generatedCount"
                :title="batch.status === 'FAILED' ? failedReasonTitle(batch.thumbnailJob) : batch.prompt"
                @dragstart="dragHistoryImage($event, batch.thumbnailJob)"
                @error="useUnavailableImage"
              />
              <span v-else class="history-thumb-placeholder">无图</span>
              <span class="history-count-mask">{{ batch.completedCount }} / {{ batch.totalCount }}</span>
            </div>
            <div class="history-meta">
              <strong class="history-prompt" :title="batch.prompt">{{ batch.prompt }}</strong>
              <p>已完成 {{ batch.completedCount }} / {{ batch.totalCount }}</p>
              <span class="history-status-wrap">
                <span
                  :class="['history-status', batch.status.toLowerCase()]"
                  :title="batch.status === 'FAILED' ? failedReasonTitle(batch.thumbnailJob) : undefined"
                >
                  {{ statusLabel(batch.status) }}
                </span>
                <span v-if="batch.status === 'FAILED'" class="history-failed-tooltip" role="tooltip">
                  {{ failedReasonTitle(batch.thumbnailJob) }}
                </span>
              </span>
            </div>
            <div class="item-actions">
              <button class="icon-btn small" title="下载图片" type="button" :disabled="!batch.generatedCount" @click="downloadBatchImages(batch)">
                <ArrowDownToLine :size="15" />
              </button>
              <button class="icon-btn small" title="复用参数" type="button" @click="reuseJob(batch.thumbnailJob)">
                <RefreshCw :size="15" />
              </button>
              <button class="icon-btn small danger-text" title="删除任务" type="button" @click="deleteHistoryBatch(batch)">
                <Trash2 :size="15" />
              </button>
            </div>
          </article>
          <div v-if="!pagedHistoryBatches.length" class="empty compact">
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
        <button class="preview-close" type="button" title="关闭预览" @click="closePreview">
          <X :size="20" />
        </button>
        <button v-if="previewImages.length > 1" class="preview-nav previous" type="button" title="上一张" @click="movePreview(-1)">‹</button>
        <img :src="previewImage.publicUrl" :alt="previewImage.job.prompt" @error="useUnavailableImage" />
        <button v-if="previewImages.length > 1" class="preview-nav next" type="button" title="下一张" @click="movePreview(1)">›</button>
        <button v-if="previewMode !== 'reference'" class="preview-download" type="button" title="下载图片" @click="downloadImage(previewImage)">
          <ArrowDownToLine :size="24" stroke-width="2.6" />
        </button>
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
            <p><strong>GPT-Image-2：</strong>size 请传画幅比例（如 1:1）。文生图和图生图统一 JSON POST /images/generations（async: true），参考图固定通过 images URL 数组提交；GET 轮询取 data.url。</p>
            <p><strong>计费：</strong>按所选模型后台配置的单张价格计费。任务创建前冻结对应额度，生成成功后结算，失败或超时自动释放。</p>

            <h3>接口</h3>
            <ul>
              <li><strong>POST</strong> https://ai.cangyuansuanli.cn/v1/images/generations：文生图/图生图统一入口（application/json，async 必须为 true）。</li>
              <li><strong>GET</strong> https://ai.cangyuansuanli.cn/v1/images/generations/{task_id}：查询图片异步任务。</li>
              <li><strong>GET</strong> https://ai.cangyuansuanli.cn/v1/images/{task_id}/content：下载图片（部分模型）。</li>
            </ul>

            <h3>请求字段</h3>
            <ul>
              <li><strong>model：</strong>必填，固定传模型广场展示名 cy-img1-gpt-image-2。</li>
              <li><strong>prompt：</strong>必填，图像描述提示词；可在 prompt 中用 @图片1 等引用参考图。</li>
              <li><strong>async：</strong>异步模式必填 true。</li>
              <li><strong>size：</strong>画幅比例（推荐），如 1:1、3:2、2:3；兼容传像素但不保证输出像素一致。1:1 @ 1K 实际约 1254x1254。</li>
              <li><strong>n：</strong>生成张数，1-10，默认 1。</li>
              <li><strong>stream：</strong>建议 false（非 SSE JSON 响应）。</li>
              <li><strong>images：</strong>参考图 URL 数组，文生图传空数组，图生图最多 10 张。</li>
            </ul>

            <h3>请求 JSON</h3>
            <pre><code>{
  "async": true,
  "model": "cy-img1-gpt-image-2",
  "count": 1,
  "images": [],
  "prompt": "一只橘猫坐在窗台上，午后阳光",
  "size": "1:1"
}</code></pre>

            <h3>返回示例</h3>
            <pre><code>{
  "created_at": 1715923200,
  "id": "task_img_01HZX8A2...",
  "model": "cy-img1-gpt-image-2",
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
  "async": true,
  "count": 1,
  "images": [],
  "size": "3840x2160"
}</code></pre>
          </section>
        </div>
      </div>
    </div>
  </main>
</template>
