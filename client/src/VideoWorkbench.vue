<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { ArrowLeft, ChevronLeft, ChevronRight, CircleDollarSign, Film, ImagePlus, KeyRound, Loader2, PanelRightClose, PanelRightOpen, Play, RefreshCw, Trash2, Type, Video, X } from "lucide-vue-next";
import { api } from "./api";
import { useWorkbenchStore } from "./stores/workbench";
import type { Profile, VideoGenerationJob, VideoModelConfig, VideoModelParameter } from "./types";

const fixedBaseUrl = "https://image.tcboys.de";
const store = useWorkbenchStore();
const apiKey = ref(localStorage.getItem("apiKey") ?? "");
const models = ref<VideoModelConfig[]>([]);
const jobs = ref<VideoGenerationJob[]>([]);
const history = ref<VideoGenerationJob[]>([]);
const historyPage = ref(1);
const historyTotalPages = ref(1);
const historyTotal = ref(0);
const loading = ref(false);
const historyLoading = ref(false);
const error = ref("");
const currentRequestId = ref("");
const referenceImages = ref<File[]>([]);
const firstFrame = ref<File | null>(null);
const lastFrame = ref<File | null>(null);
type CreationMode = "text" | "image" | "frames";
const creationMode = ref<CreationMode>("text");
const historyOpen = ref(window.innerWidth >= 1180);
const materialsOpen = ref(false);
let pollTimer: number | undefined;
let autoBindTimer: number | undefined;

const form = reactive({
  model: "",
  prompt: "",
  count: 1,
  duration: 8,
  aspectRatio: "16:9",
  resolution: "720p",
  generateAudio: true,
  referenceVideoUrls: "",
  referenceAudioUrls: ""
});

const activeModel = computed(() => models.value.find(item => item.model === form.model) ?? null);
const capabilities = computed(() => activeModel.value?.defaults ?? {});
const maxImages = computed(() => Number(capabilities.value.images ?? activeModel.value?.maxReferenceImages ?? 0));
const supportsFrames = computed(() => Boolean(capabilities.value.frameInputs));
const supportsVideos = computed(() => Number(capabilities.value.videos ?? 0) > 0);
const supportsAudios = computed(() => Number(capabilities.value.audios ?? 0) > 0);
const parameters = computed(() => activeModel.value?.parameters ?? []);
const pendingCount = computed(() => history.value.filter(job => job.status === "PENDING").length);
const estimatedPrice = computed(() => {
  const model = activeModel.value;
  if (!model) return "0.00";
  const unit = Number(model.unitPriceUsd || 0);
  const multiplier = model.billingMode === "PER_SECOND" ? Number(form.duration || 0) : 1;
  return (unit * multiplier * Math.max(1, Number(form.count) || 1)).toFixed(4).replace(/0+$/, "").replace(/\.$/, "");
});

function optionValue(option: string | number | { label: string; value: string | number }) {
  return typeof option === "object" ? option.value : option;
}
function optionLabel(option: string | number | { label: string; value: string | number }) {
  return typeof option === "object" ? option.label : String(option);
}
function fieldValue(field: VideoModelParameter) {
  if (field.key === "duration") return form.duration;
  if (field.key === "aspectRatio") return form.aspectRatio;
  if (field.key === "resolution") return form.resolution;
  return form.generateAudio;
}
function setField(field: VideoModelParameter, event: Event) {
  const target = event.target as HTMLInputElement | HTMLSelectElement;
  if (field.key === "duration") form.duration = Number(target.value);
  else if (field.key === "aspectRatio") form.aspectRatio = target.value;
  else if (field.key === "resolution") form.resolution = target.value;
  else form.generateAudio = (target as HTMLInputElement).checked;
}

function setCreationMode(mode: CreationMode) {
  creationMode.value = mode;
  materialsOpen.value = mode !== "text";
  if (mode === "text") {
    referenceImages.value = [];
    firstFrame.value = null;
    lastFrame.value = null;
    form.referenceVideoUrls = "";
    form.referenceAudioUrls = "";
  } else if (mode === "image") {
    firstFrame.value = null;
    lastFrame.value = null;
  } else {
    referenceImages.value = [];
    form.referenceVideoUrls = "";
    form.referenceAudioUrls = "";
  }
}

function applyDefaults() {
  const model = activeModel.value;
  if (!model) return;
  for (const field of model.parameters) {
    const value = field.default ?? (field.options?.length ? optionValue(field.options[0]) : undefined);
    if (field.key === "duration") form.duration = Number(value ?? 8);
    if (field.key === "aspectRatio") form.aspectRatio = String(value ?? "16:9");
    if (field.key === "resolution") form.resolution = String(value ?? "720p");
    if (field.key === "generateAudio") form.generateAudio = Boolean(value);
  }
  form.count = Math.min(4, model.maxCount || 4);
  form.count = 1;
  referenceImages.value = referenceImages.value.slice(0, maxImages.value);
  if (!supportsFrames.value) { firstFrame.value = null; lastFrame.value = null; }
  if (!supportsVideos.value) form.referenceVideoUrls = "";
  if (!supportsAudios.value) form.referenceAudioUrls = "";
  if (creationMode.value === "frames" && !supportsFrames.value) setCreationMode(maxImages.value > 0 ? "image" : "text");
  if (creationMode.value === "image" && maxImages.value <= 0) setCreationMode("text");
}

async function connect() {
  const normalizedApiKey = apiKey.value.trim();
  if (normalizedApiKey.length < 8) { error.value = "请输入有效的 API Key"; return; }
  error.value = "";
  localStorage.setItem("apiKey", normalizedApiKey);
  localStorage.setItem("baseUrl", fixedBaseUrl);
  const data = await api.post<{ profile: Profile }>("/api/session/bind", { baseUrl: fixedBaseUrl, apiKey: normalizedApiKey });
  store.profile = data.profile;
  await loadHistory(1);
}

async function loadModels() {
  const data = await api.get<{ models: VideoModelConfig[] }>("/api/videos/models");
  models.value = data.models ?? [];
  if (!models.value.some(item => item.model === form.model)) form.model = models.value[0]?.model ?? "";
  applyDefaults();
}

async function generate() {
  if (!form.prompt.trim() || !activeModel.value) { error.value = "请选择模型并填写提示词"; return; }
  if (!store.profile) await connect();
  if (!store.profile) return;
  loading.value = true; error.value = "";
  const payload = {
    model: form.model, prompt: form.prompt.trim(), count: Math.max(1, Math.min(4, Number(form.count) || 1)),
    duration: form.duration, aspectRatio: form.aspectRatio, resolution: form.resolution,
    generateAudio: form.generateAudio,
    referenceVideoUrls: lines(form.referenceVideoUrls), referenceAudioUrls: lines(form.referenceAudioUrls)
  };
  try {
    let data: { requestId: string; count: number };
    if (referenceImages.value.length || firstFrame.value || lastFrame.value) {
      const body = new FormData(); body.append("payload", JSON.stringify(payload));
      referenceImages.value.forEach(file => body.append("referenceImage", file, file.name));
      if (firstFrame.value) body.append("firstFrame", firstFrame.value, firstFrame.value.name);
      if (lastFrame.value) body.append("lastFrame", lastFrame.value, lastFrame.value.name);
      data = await api.postForm("/api/videos/generate", body);
    } else data = await api.post("/api/videos/generate", payload);
    currentRequestId.value = data.requestId;
    await loadResults(data.requestId);
    startPolling();
    await loadHistory(1);
  } catch (cause) { error.value = cause instanceof Error ? cause.message : "视频生成失败"; }
  finally { loading.value = false; }
}

async function loadResults(requestId: string) {
  const data = await api.get<{ jobs: VideoGenerationJob[] }>(`/api/videos/results/${encodeURIComponent(requestId)}`);
  jobs.value = data.jobs ?? [];
  if (jobs.value.some(job => job.status === "PENDING")) startPolling(); else stopPolling();
}
function startPolling() {
  if (pollTimer) return;
  pollTimer = window.setInterval(async () => {
    if (!currentRequestId.value) return stopPolling();
    await loadResults(currentRequestId.value).catch(cause => { error.value = cause instanceof Error ? cause.message : "任务查询失败"; });
    await loadHistory(historyPage.value).catch(() => undefined);
  }, 5000);
}
function stopPolling() { if (pollTimer) window.clearInterval(pollTimer); pollTimer = undefined; }

async function loadHistory(page = historyPage.value) {
  if (!store.profile) return;
  historyLoading.value = true;
  try {
    const data = await api.get<{ jobs: VideoGenerationJob[]; page: number; total: number; totalPages: number }>(`/api/videos/history?page=${page}&pageSize=10`);
    history.value = data.jobs ?? []; historyPage.value = data.page; historyTotal.value = data.total; historyTotalPages.value = data.totalPages;
    if (!currentRequestId.value) {
      const pending = history.value.find(job => job.status === "PENDING");
      if (pending) { currentRequestId.value = pending.requestId; await loadResults(pending.requestId); }
    }
  } finally { historyLoading.value = false; }
}
async function openHistory(job: VideoGenerationJob) { currentRequestId.value = job.requestId; await loadResults(job.requestId); }
async function deleteJob(job: VideoGenerationJob) {
  if (job.status === "PENDING" || !window.confirm("确认删除该视频任务吗？")) return;
  await api.delete(`/api/video-jobs/${job.id}`); await loadHistory(historyPage.value);
  jobs.value = jobs.value.filter(item => item.id !== job.id);
}
async function deleteAll() {
  if (!historyTotal.value || !window.confirm("确认清空所有已完成的视频历史吗？")) return;
  await api.delete("/api/video-jobs"); await loadHistory(1);
}

function selectImages(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []).filter(file => file.type.startsWith("image/") && file.size <= 30 * 1024 * 1024);
  referenceImages.value = [...referenceImages.value, ...files].slice(0, maxImages.value);
  if (files.length) { firstFrame.value = null; lastFrame.value = null; }
  input.value = "";
}
function selectFrame(event: Event, target: "first" | "last") {
  const input = event.target as HTMLInputElement; const file = input.files?.[0] ?? null;
  if (target === "first") firstFrame.value = file; else lastFrame.value = file;
  if (file) { referenceImages.value = []; form.referenceVideoUrls = ""; form.referenceAudioUrls = ""; }
  input.value = "";
}
function lines(value: string) { return value.split(/\r?\n/).map(item => item.trim()).filter(Boolean); }
function statusText(job: VideoGenerationJob) { return job.status === "PENDING" ? `生成中 ${job.progress || 0}%` : job.status === "SUCCEEDED" ? "已完成" : "失败"; }
function date(value: string) { return new Date(value).toLocaleString("zh-CN", { hour12: false }); }

watch(() => form.model, applyDefaults);
watch(apiKey, (value) => {
  const normalizedApiKey = value.trim();
  localStorage.setItem("baseUrl", fixedBaseUrl);
  localStorage.setItem("apiKey", normalizedApiKey);
  store.profile = null;
  if (autoBindTimer) window.clearTimeout(autoBindTimer);
  if (normalizedApiKey.length < 8) return;
  autoBindTimer = window.setTimeout(() => {
    connect().catch(cause => { error.value = cause instanceof Error ? cause.message : "连接失败"; });
  }, 700);
});
onMounted(async () => {
  await loadModels().catch(cause => { error.value = cause instanceof Error ? cause.message : "模型加载失败"; });
  if (apiKey.value.length >= 8) await connect().catch(cause => { error.value = cause instanceof Error ? cause.message : "连接失败"; });
});
onUnmounted(() => {
  stopPolling();
  if (autoBindTimer) window.clearTimeout(autoBindTimer);
});
</script>

<template>
  <main class="video-studio" :class="{ 'history-collapsed': !historyOpen }">
    <header class="studio-toolbar">
      <div class="toolbar-brand">
        <RouterLink class="icon-btn" to="/" title="返回图片工作台"><ArrowLeft :size="17" /></RouterLink>
        <span class="brand-mark"><Video :size="19" /></span>
        <div><h1>视频创作</h1><p>Seedance · Grok</p></div>
      </div>
      <div class="connect-cluster">
        <KeyRound :size="15" />
        <input v-model="apiKey" type="password" placeholder="输入 API Key" autocomplete="off" aria-label="API Key" />
      </div>
      <div class="toolbar-actions">
        <span v-if="store.profile?.availableBalanceUsd" class="balance"><CircleDollarSign :size="15" />${{ Number(store.profile.availableBalanceUsd).toFixed(2) }}</span>
        <button class="icon-btn history-toggle-top" type="button" :title="historyOpen ? '收起生成记录' : '展开生成记录'" @click="historyOpen = !historyOpen">
          <PanelRightClose v-if="historyOpen" :size="17" /><PanelRightOpen v-else :size="17" />
          <span v-if="pendingCount" class="pending-badge">{{ pendingCount }}</span>
        </button>
      </div>
    </header>

    <aside class="mode-rail" aria-label="创作模式">
      <button type="button" :class="{ active: creationMode === 'text' }" @click="setCreationMode('text')"><Type :size="19" /><span>文生视频</span></button>
      <button type="button" :class="{ active: creationMode === 'image' }" :disabled="maxImages <= 0" @click="setCreationMode('image')"><ImagePlus :size="19" /><span>图生视频</span></button>
      <button type="button" :class="{ active: creationMode === 'frames' }" :disabled="!supportsFrames" @click="setCreationMode('frames')"><Film :size="19" /><span>首尾帧</span></button>
    </aside>

    <section class="stage">
      <div class="stage-toolbar">
        <div><strong>本次创作</strong><span v-if="jobs.length">{{ jobs.length }} 个任务</span></div>
        <button class="icon-btn" type="button" title="刷新当前任务" :disabled="!currentRequestId" @click="loadResults(currentRequestId)"><RefreshCw :size="16" /></button>
      </div>

      <div class="video-results" :class="{ 'no-results': !jobs.length }">
        <article v-for="job in jobs" :key="job.id" class="video-result">
          <video v-if="job.videos?.[0]" :src="job.videos[0].publicUrl" controls preload="metadata" playsinline />
          <div v-else-if="job.status === 'PENDING'" class="video-placeholder"><Loader2 class="spin" :size="32" /><strong>{{ statusText(job) }}</strong><span>{{ job.model }} · {{ job.duration }} 秒 · {{ job.resolution }}</span></div>
          <div v-else class="video-placeholder failed"><Film :size="28" /><strong>生成失败</strong><span>{{ job.errorMessage || '上游未返回视频' }}</span></div>
          <footer><span>{{ job.aspectRatio }} · {{ job.duration }} 秒 · {{ job.resolution }}</span><strong>${{ Number(job.billingAmount || 0).toFixed(4) }}</strong></footer>
        </article>
        <div v-if="!jobs.length" class="video-empty"><span class="empty-icon"><Film :size="34" /></span><strong>从一个想法开始创作</strong><span>在下方描述画面，选择模型后生成视频</span></div>
      </div>

      <section class="creation-dock">
        <div v-if="materialsOpen && creationMode !== 'text'" class="materials-panel">
          <div class="materials-head"><strong>{{ creationMode === 'frames' ? '首尾帧' : '参考素材' }}</strong><button class="icon-btn small" type="button" title="收起素材" @click="materialsOpen = false"><X :size="15" /></button></div>
          <template v-if="creationMode === 'image'">
            <div class="material-row">
              <label class="material-upload"><ImagePlus :size="18" /><span>添加图片</span><small>{{ referenceImages.length }}/{{ maxImages }}</small><input type="file" accept="image/png,image/jpeg,image/webp" multiple @change="selectImages" /></label>
              <span v-for="(file, index) in referenceImages" :key="file.name + index" class="file-chip">{{ file.name }}<button type="button" title="移除图片" @click="referenceImages.splice(index, 1)"><X :size="13" /></button></span>
            </div>
            <div v-if="supportsVideos || supportsAudios" class="url-fields">
              <label v-if="supportsVideos"><span>参考视频 URL（每行一个）</span><textarea v-model="form.referenceVideoUrls" rows="2" placeholder="https://..." /></label>
              <label v-if="supportsAudios"><span>参考音频 URL（每行一个）</span><textarea v-model="form.referenceAudioUrls" rows="2" placeholder="https://..." /></label>
            </div>
          </template>
          <div v-else class="frame-inputs">
            <label class="frame-upload"><span>首帧</span><strong>{{ firstFrame?.name || '上传图片' }}</strong><input type="file" accept="image/png,image/jpeg,image/webp" @change="selectFrame($event, 'first')" /></label>
            <ChevronRight :size="18" />
            <label class="frame-upload"><span>尾帧</span><strong>{{ lastFrame?.name || '上传图片' }}</strong><input type="file" accept="image/png,image/jpeg,image/webp" @change="selectFrame($event, 'last')" /></label>
          </div>
        </div>

        <textarea v-model="form.prompt" class="prompt-input" maxlength="5000" placeholder="描述主体、动作、镜头运动、光线和声音……" />
        <p v-if="error" class="video-error">{{ error }}</p>
        <div class="dock-controls">
          <button v-if="creationMode !== 'text'" class="dock-control material-trigger" :class="{ active: materialsOpen }" type="button" @click="materialsOpen = !materialsOpen"><ImagePlus :size="15" />素材</button>
          <label class="dock-field model-field"><span>模型</span><select v-model="form.model" :disabled="!models.length"><option v-for="model in models" :key="model.id" :value="model.model">{{ model.name }}</option></select></label>
          <template v-for="field in parameters" :key="field.key">
            <label v-if="field.type === 'select'" class="dock-field"><span>{{ field.label }}</span><select :value="fieldValue(field)" @change="setField(field, $event)"><option v-for="option in field.options || []" :key="String(optionValue(option))" :value="optionValue(option)">{{ optionLabel(option) }}</option></select></label>
            <label v-else class="audio-toggle"><input type="checkbox" :checked="Boolean(fieldValue(field))" @change="setField(field, $event)" /><span>{{ field.label }}</span></label>
          </template>
          <label class="dock-field count-field"><span>数量</span><input v-model.number="form.count" type="number" min="1" max="4" /></label>
          <div class="price-summary"><span>{{ activeModel?.billingMode === 'PER_SECOND' ? '按秒计费' : '按次计费' }}</span><strong>${{ estimatedPrice }}</strong></div>
          <button class="primary generate-button" type="button" :disabled="loading || !models.length" @click="generate"><Loader2 v-if="loading" class="spin" :size="17" /><Play v-else :size="17" />生成 {{ form.count }} 个</button>
        </div>
      </section>
    </section>

    <aside class="task-drawer" :class="{ collapsed: !historyOpen }">
      <button v-if="!historyOpen" class="drawer-restore" type="button" title="展开生成记录" @click="historyOpen = true"><ChevronLeft :size="18" /><span>记录</span><b v-if="pendingCount">{{ pendingCount }}</b></button>
      <template v-else>
        <div class="history-head">
          <div><h2>生成记录</h2><p>{{ historyTotal }} 条任务</p></div>
          <div><button class="icon-btn danger-text" type="button" title="清空已完成历史" :disabled="!historyTotal" @click="deleteAll"><Trash2 :size="15" /></button><button class="icon-btn" type="button" title="收起生成记录" @click="historyOpen = false"><ChevronRight :size="17" /></button></div>
        </div>
        <div class="history-list">
          <article v-for="job in history" :key="job.id" class="history-video-item">
            <button class="history-main" type="button" @click="openHistory(job)">
              <video v-if="job.videos?.[0]" :src="job.videos[0].publicUrl" muted preload="metadata" />
              <span v-else class="history-video-placeholder"><Loader2 v-if="job.status === 'PENDING'" class="spin" :size="18" /><Film v-else :size="18" /></span>
              <span class="history-video-copy"><strong>{{ job.prompt }}</strong><small>{{ date(job.createdAt) }}</small><em>{{ statusText(job) }}</em></span>
            </button>
            <button class="icon-btn small danger-text history-delete" type="button" title="删除任务" :disabled="job.status === 'PENDING'" @click="deleteJob(job)"><Trash2 :size="14" /></button>
          </article>
          <div v-if="!history.length && !historyLoading" class="history-empty">暂无视频历史</div>
        </div>
        <div class="history-pages"><button :disabled="historyPage <= 1" @click="loadHistory(historyPage - 1)">上一页</button><span>{{ historyPage }} / {{ historyTotalPages }}</span><button :disabled="historyPage >= historyTotalPages" @click="loadHistory(historyPage + 1)">下一页</button></div>
      </template>
    </aside>
  </main>
</template>

<style scoped>
.video-studio {
  color-scheme: light;
  --bg: #f5f7fa;
  --bg-soft: #f8fafc;
  --panel: #ffffff;
  --panel-strong: #f3f5f7;
  --panel-soft: #f7f8fa;
  --text: #171a21;
  --muted: #697386;
  --line: rgba(23, 26, 33, 0.12);
  --line-strong: rgba(23, 26, 33, 0.22);
  --accent: #0891b2;
  --accent-2: #059669;
  --accent-strong: #0e7490;
  --danger: #dc2626;
  --input: #ffffff;
  --shadow: rgba(23, 26, 33, 0.12);
  height: 100dvh;
  min-width: 320px;
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr) 320px;
  grid-template-rows: 58px minmax(0, 1fr);
  overflow: hidden;
  background: var(--bg);
  color: var(--text);
  transition: grid-template-columns 180ms ease;
}

.video-studio.history-collapsed { grid-template-columns: 68px minmax(0, 1fr) 52px; }
.studio-toolbar { grid-column: 1 / -1; z-index: 20; min-width: 0; padding: 8px 12px; display: flex; align-items: center; gap: 16px; border-bottom: 1px solid var(--line); background: var(--panel); }
.toolbar-brand, .toolbar-actions, .connect-cluster, .history-head, .history-head > div, .stage-toolbar, .stage-toolbar > div, .dock-controls, .material-row, .materials-head { display: flex; align-items: center; }
.toolbar-brand { min-width: 205px; gap: 9px; }
.brand-mark { width: 36px; height: 36px; flex: 0 0 36px; display: grid; place-items: center; border-radius: 7px; background: color-mix(in srgb, var(--accent), transparent 84%); color: var(--accent); }
.toolbar-brand h1 { margin: 0; font-size: 16px; font-weight: 850; letter-spacing: 0; }
.toolbar-brand p { margin: 2px 0 0; color: var(--muted); font-size: 10px; }
.connect-cluster { width: min(420px, 36vw); gap: 8px; margin-left: auto; color: var(--muted); }
.connect-cluster input { min-width: 0; height: 36px; padding: 7px 10px; }
.toolbar-actions { gap: 8px; }
.balance { min-height: 36px; padding: 0 10px; display: inline-flex; align-items: center; gap: 6px; border: 1px solid var(--line); border-radius: 7px; color: var(--accent-2); font-size: 12px; font-weight: 850; white-space: nowrap; }
.history-toggle-top { position: relative; }
.pending-badge { position: absolute; top: -5px; right: -5px; min-width: 18px; height: 18px; padding: 0 5px; display: grid; place-items: center; border-radius: 9px; background: var(--danger); color: #fff; font-size: 10px; }

.mode-rail { grid-column: 1; grid-row: 2; padding: 12px 8px; display: flex; flex-direction: column; gap: 8px; border-right: 1px solid var(--line); background: var(--panel); }
.mode-rail button { width: 51px; min-height: 58px; padding: 7px 3px; flex-direction: column; gap: 5px; border-color: transparent; background: transparent; color: var(--muted); font-size: 10px; line-height: 1.25; }
.mode-rail button:hover:not(:disabled) { transform: none; box-shadow: none; background: var(--panel-soft); }
.mode-rail button.active { border-color: color-mix(in srgb, var(--accent), var(--line) 50%); background: color-mix(in srgb, var(--accent), transparent 88%); color: var(--accent); }

.stage { position: relative; grid-column: 2; grid-row: 2; min-width: 0; min-height: 0; overflow: auto; background: var(--bg); }
.stage-toolbar { position: absolute; z-index: 4; top: 12px; left: 18px; right: 18px; justify-content: space-between; pointer-events: none; }
.stage-toolbar > div { gap: 8px; }
.stage-toolbar strong { font-size: 13px; }
.stage-toolbar span { color: var(--muted); font-size: 11px; }
.stage-toolbar button { pointer-events: auto; }
.video-results { min-height: 100%; padding: 64px 28px 245px; display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); align-content: center; gap: 16px; }
.video-results.no-results { grid-template-columns: 1fr; }
.video-result { width: 100%; max-width: 760px; margin: 0 auto; overflow: hidden; border: 1px solid var(--line); border-radius: 8px; background: var(--panel-soft); box-shadow: 0 16px 48px var(--shadow); }
.video-result video { width: 100%; aspect-ratio: 16 / 9; display: block; background: #050608; object-fit: contain; }
.video-placeholder { aspect-ratio: 16 / 9; padding: 24px; display: grid; place-items: center; align-content: center; gap: 9px; color: var(--muted); text-align: center; }
.video-placeholder.failed { color: var(--danger); }
.video-placeholder span { max-width: 88%; font-size: 12px; }
.video-result footer { min-height: 42px; padding: 0 12px; display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--muted); font-size: 11px; }
.video-result footer strong { color: var(--accent-2); }
.video-empty { min-height: 320px; display: grid; place-items: center; align-content: center; gap: 9px; color: var(--muted); text-align: center; }
.empty-icon { width: 64px; height: 64px; display: grid; place-items: center; border: 1px solid var(--line); border-radius: 8px; background: var(--panel-soft); color: var(--accent); }
.video-empty strong { color: var(--text); font-size: 15px; }
.video-empty > span:last-child { font-size: 12px; }

.creation-dock { position: absolute; z-index: 10; left: 50%; bottom: 18px; width: min(920px, calc(100% - 42px)); transform: translateX(-50%); overflow: hidden; border: 1px solid var(--line-strong); border-radius: 8px; background: var(--panel); box-shadow: 0 20px 70px var(--shadow); }
.materials-panel { max-height: 230px; padding: 12px 14px; overflow: auto; border-bottom: 1px solid var(--line); }
.materials-head { justify-content: space-between; margin-bottom: 10px; }
.materials-head strong { font-size: 12px; }
.material-row { flex-wrap: wrap; gap: 8px; }
.material-upload, .frame-upload { margin: 0; cursor: pointer; }
.material-upload { width: 116px; min-height: 42px; padding: 0 10px; display: flex; grid-template-columns: none; align-items: center; gap: 7px; border: 1px dashed var(--line-strong); border-radius: 7px; color: var(--text); }
.material-upload small { margin-left: auto; color: var(--muted); }
.material-upload input, .frame-upload input { display: none; }
.file-chip { max-width: 190px; min-height: 36px; padding: 0 4px 0 10px; display: inline-flex; align-items: center; gap: 6px; overflow: hidden; border: 1px solid var(--line); border-radius: 7px; background: var(--panel-soft); color: var(--muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.file-chip button { width: 28px; min-height: 28px; padding: 0; flex: 0 0 28px; border-color: transparent; background: transparent; }
.url-fields { margin-top: 10px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.url-fields label { margin: 0; }
.url-fields textarea { min-height: 62px; resize: none; }
.frame-inputs { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); align-items: center; gap: 10px; }
.frame-upload { min-width: 0; min-height: 58px; padding: 9px 12px; display: grid; gap: 4px; border: 1px dashed var(--line-strong); border-radius: 7px; }
.frame-upload span { color: var(--muted); font-size: 10px; }
.frame-upload strong { overflow: hidden; color: var(--text); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.prompt-input { min-height: 82px; max-height: 150px; padding: 14px 16px 8px; resize: none; border: 0; border-radius: 0; background: transparent; box-shadow: none; font-size: 15px; line-height: 1.6; }
.prompt-input:focus { border: 0; box-shadow: none; }
.video-error { margin: 0 14px 8px; padding: 7px 10px; border-left: 3px solid var(--danger); background: color-mix(in srgb, var(--danger), transparent 92%); color: var(--danger); font-size: 11px; }
.dock-controls { min-width: 0; padding: 9px 10px; flex-wrap: wrap; gap: 7px; border-top: 1px solid var(--line); }
.dock-control { min-height: 36px; padding: 0 10px; font-size: 11px; }
.dock-control.active { border-color: var(--accent); color: var(--accent); }
.dock-field { width: 108px; margin: 0; display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 6px; }
.dock-field.model-field { width: 172px; }
.dock-field.count-field { width: 82px; }
.dock-field span { color: var(--muted); font-size: 10px; white-space: nowrap; }
.dock-field select, .dock-field input { min-width: 0; height: 36px; padding: 6px 26px 6px 8px; border-radius: 7px; font-size: 11px; }
.dock-field input { padding-right: 7px; }
.audio-toggle { min-height: 36px; margin: 0; padding: 0 9px; display: inline-flex; grid-template-columns: none; align-items: center; gap: 6px; border: 1px solid var(--line); border-radius: 7px; color: var(--text); font-size: 11px; cursor: pointer; }
.audio-toggle input { width: 16px; height: 16px; margin: 0; padding: 0; accent-color: var(--accent); }
.price-summary { margin-left: auto; display: grid; justify-items: end; }
.price-summary span { color: var(--muted); font-size: 9px; }
.price-summary strong { color: var(--accent-2); font-size: 13px; }
.generate-button { min-height: 38px; padding: 0 15px; white-space: nowrap; }

.task-drawer { grid-column: 3; grid-row: 2; min-width: 0; min-height: 0; display: grid; grid-template-rows: auto minmax(0, 1fr) auto; overflow: hidden; border-left: 1px solid var(--line); background: var(--panel); }
.task-drawer.collapsed { display: block; }
.drawer-restore { width: 100%; min-height: 100%; padding: 16px 0; flex-direction: column; justify-content: flex-start; gap: 8px; border: 0; border-radius: 0; background: transparent; color: var(--muted); font-size: 10px; }
.drawer-restore:hover:not(:disabled) { transform: none; box-shadow: none; background: var(--panel-soft); }
.drawer-restore b { min-width: 20px; height: 20px; display: grid; place-items: center; border-radius: 10px; background: var(--danger); color: #fff; font-size: 10px; }
.history-head { min-height: 62px; padding: 10px 12px; justify-content: space-between; border-bottom: 1px solid var(--line); }
.history-head > div { gap: 6px; }
.history-head h2 { margin: 0; font-size: 14px; }
.history-head p { margin: 3px 0 0; color: var(--muted); font-size: 10px; }
.history-list { min-height: 0; padding: 9px; overflow: auto; display: grid; align-content: start; gap: 7px; }
.history-video-item { position: relative; min-width: 0; padding: 6px; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 4px; border: 1px solid transparent; border-radius: 7px; }
.history-video-item:hover { border-color: var(--line); background: var(--panel-soft); }
.history-main { min-width: 0; min-height: 62px; padding: 0; display: grid; grid-template-columns: 78px minmax(0, 1fr); gap: 9px; border: 0; background: transparent; text-align: left; }
.history-main:hover:not(:disabled) { transform: none; border-color: transparent; box-shadow: none; }
.history-main > video, .history-video-placeholder { width: 78px; height: 58px; border-radius: 6px; background: #08090c; object-fit: cover; }
.history-video-placeholder { display: grid; place-items: center; color: var(--muted); }
.history-video-copy { min-width: 0; display: grid; align-content: center; gap: 4px; }
.history-video-copy strong { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.history-video-copy small { color: var(--muted); font-size: 9px; }
.history-video-copy em { color: var(--accent-2); font-size: 10px; font-style: normal; }
.history-delete { align-self: center; }
.history-empty { padding: 40px 10px; color: var(--muted); text-align: center; font-size: 12px; }
.history-pages { padding: 9px; display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 6px; border-top: 1px solid var(--line); color: var(--muted); font-size: 10px; }
.history-pages button { min-height: 30px; padding: 0 7px; font-size: 10px; }

@media (max-width: 1180px) {
  .video-studio { grid-template-columns: 68px minmax(0, 1fr) 52px; }
  .task-drawer:not(.collapsed) { position: fixed; z-index: 40; top: 58px; right: 0; bottom: 0; width: 320px; box-shadow: -16px 0 48px var(--shadow); }
  .video-results { grid-template-columns: minmax(0, 760px); justify-content: center; }
}

@media (max-width: 900px) {
  .video-studio, .video-studio.history-collapsed { grid-template-columns: minmax(0, 1fr); grid-template-rows: auto 58px minmax(0, 1fr); }
  .studio-toolbar { grid-column: 1; min-height: 98px; flex-wrap: wrap; gap: 8px; padding: 8px 10px; }
  .toolbar-brand { min-width: 0; }
  .toolbar-brand p { display: none; }
  .brand-mark { display: none; }
  .connect-cluster { order: 3; width: 100%; margin: 0; }
  .connect-cluster input { flex: 1; }
  .toolbar-actions { margin-left: auto; }
  .balance { padding: 0 8px; }
  .mode-rail { grid-column: 1; grid-row: 2; padding: 7px 10px; flex-direction: row; align-items: center; border-right: 0; border-bottom: 1px solid var(--line); overflow-x: auto; }
  .mode-rail button { width: auto; min-width: 98px; min-height: 42px; padding: 0 11px; flex-direction: row; }
  .stage { grid-column: 1; grid-row: 3; }
  .stage-toolbar { top: 10px; left: 12px; right: 12px; }
  .video-results { min-height: 320px; padding: 56px 12px 14px; grid-template-columns: minmax(0, 1fr); align-content: start; }
  .video-empty { min-height: 250px; }
  .creation-dock { position: static; width: auto; margin: 0 10px 12px; transform: none; }
  .dock-controls { align-items: flex-end; }
  .dock-field.model-field { width: min(100%, 190px); }
  .price-summary { margin-left: 0; }
  .generate-button { margin-left: auto; }
  .task-drawer.collapsed { display: none; }
  .task-drawer:not(.collapsed) { position: fixed; z-index: 50; inset: 0 0 0 auto; width: min(340px, 92vw); grid-column: auto; grid-row: auto; box-shadow: -16px 0 48px var(--shadow); }
}

@media (max-width: 560px) {
  .toolbar-brand h1 { font-size: 14px; }
  .toolbar-brand .icon-btn { width: 36px; min-height: 36px; }
  .history-toggle-top { width: 36px; min-height: 36px; }
  .mode-rail button { min-width: 92px; }
  .video-results { padding-inline: 8px; }
  .creation-dock { margin-inline: 8px; }
  .prompt-input { min-height: 96px; font-size: 14px; }
  .url-fields { grid-template-columns: 1fr; }
  .dock-controls { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dock-control, .dock-field, .dock-field.model-field, .dock-field.count-field, .audio-toggle { width: 100%; }
  .price-summary { justify-items: start; }
  .generate-button { width: 100%; margin-left: 0; }
  .frame-inputs { grid-template-columns: 1fr; }
  .frame-inputs > svg { display: none; }
  .video-result footer { align-items: flex-start; flex-direction: column; justify-content: center; padding-block: 8px; }
}
</style>
