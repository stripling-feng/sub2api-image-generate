import { defineStore } from "pinia";
import { api } from "../api";
import type { GenerationJob, Profile, PromptTemplate } from "../types";

type GeneratePayload = {
  prompt: string;
  negativePrompt?: string;
  model: string;
  size: string;
  aspectRatio?: string;
  customAspectRatio?: string;
  quality?: string;
  outputFormat?: "png" | "jpeg" | "webp";
  count: number;
  responseFormat: "url";
  extraParams: Record<string, unknown>;
  referenceImages?: Array<{
    name: string;
    mimeType: string;
    data: string;
  }>;
};

let historyPollTimer: number | undefined;
const pendingDeletedJobIds = new Set<string>();
const pendingDeletedImageIds = new Set<string>();
const pendingDeletedRequestIds = new Set<string>();
const mockJobPrefix = "mock-job-";
const mockImagePrefix = "mock-image-";
const localJobPrefix = "local-job-";
const localRequestPrefix = "local-request-";

function isMockId(id: string) {
  return id.startsWith(mockJobPrefix) || id.startsWith(mockImagePrefix) || id.startsWith(localJobPrefix);
}

function normalizeJob(job: GenerationJob): GenerationJob {
  return {
    ...job,
    images: Array.isArray(job.images) ? job.images : []
  };
}

function createLocalPendingJobs(payload: GeneratePayload, requestId: string, count: number): GenerationJob[] {
  const now = new Date().toISOString();
  const total = Math.max(1, Math.min(10, Number(count) || 1));

  return Array.from({ length: total }, (_item, index) => ({
    id: `${localJobPrefix}${requestId}-${index + 1}`,
    prompt: payload.prompt,
    negativePrompt: payload.negativePrompt ?? null,
    model: payload.model,
    size: payload.size,
    quality: payload.quality ?? null,
    style: null,
    count: 1,
    responseFormat: payload.responseFormat,
    params: {
      ...payload.extraParams,
      request_id: requestId,
      request_index: index + 1,
      request_total: total,
      output_format: payload.outputFormat,
      aspect_ratio: payload.aspectRatio,
      custom_aspect_ratio: payload.customAspectRatio
    },
    status: "PENDING",
    errorMessage: null,
    durationMs: null,
    createdAt: now,
    images: []
  }));
}

function createLocalRequestId() {
  return `${localRequestPrefix}${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export const useWorkbenchStore = defineStore("workbench", {
  state: () => ({
    profile: null as Profile | null,
    jobs: [] as GenerationJob[],
    templates: [] as PromptTemplate[],
    currentRequestId: "",
    loading: false,
    status: "",
    error: ""
  }),
  actions: {
    resetWorkspace() {
      if (historyPollTimer) {
        window.clearInterval(historyPollTimer);
        historyPollTimer = undefined;
      }
      pendingDeletedJobIds.clear();
      pendingDeletedImageIds.clear();
      pendingDeletedRequestIds.clear();
      this.jobs = [];
      this.templates = [];
      this.currentRequestId = "";
      this.error = "";
      this.status = "";
    },
    async bind(baseUrl: string, apiKey: string) {
      this.resetWorkspace();
      this.loading = true;
      this.error = "";
      try {
        localStorage.setItem("baseUrl", baseUrl);
        localStorage.setItem("apiKey", apiKey);
        const data = await api.post<{ profile: Profile }>("/api/session/bind", { baseUrl, apiKey });
        this.profile = data.profile;
        this.status = "";
        await Promise.all([this.loadHistory(), this.loadTemplates()]);
      } catch (error) {
        this.error = error instanceof Error ? error.message : "连接失败";
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async generate(payload: GeneratePayload) {
      this.loading = true;
      this.error = "";
      const optimisticRequestId = createLocalRequestId();
      const optimisticJobs = createLocalPendingJobs(payload, optimisticRequestId, payload.count);
      this.currentRequestId = optimisticRequestId;
      this.jobs = [
        ...optimisticJobs,
        ...this.jobs.filter((job) => job.params.request_id !== optimisticRequestId)
      ];
      try {
        const data = await api.post<{ requestId?: string; count?: number; jobs?: GenerationJob[]; job?: GenerationJob }>("/api/images/generate", payload);
        if (data.requestId) {
          this.currentRequestId = data.requestId;
        }
        const jobs = data.requestId
          ? createLocalPendingJobs(payload, data.requestId, data.count ?? payload.count)
          : (data.jobs ?? (data.job ? [data.job] : [])).map(normalizeJob);
        const incomingIds = new Set(jobs.map((job) => job.id));
        this.jobs = [
          ...jobs,
          ...this.jobs.filter((job) => job.params.request_id !== optimisticRequestId && !incomingIds.has(job.id))
        ];
        this.startHistoryPolling();
      } catch (error) {
        this.error = error instanceof Error ? error.message : "生成失败";
        await this.loadHistory().catch(() => undefined);
      } finally {
        this.loading = false;
      }
    },
    startHistoryPolling() {
      if (historyPollTimer) {
        window.clearInterval(historyPollTimer);
      }

      historyPollTimer = window.setInterval(async () => {
        const hasPending = this.jobs.some((job) => job.status === "PENDING");
        if (!hasPending) {
          if (historyPollTimer) {
            window.clearInterval(historyPollTimer);
            historyPollTimer = undefined;
          }
          return;
        }

        await this.loadHistory().catch(() => undefined);
      }, 2000);
    },
    async loadHistory() {
      const data = await api.get<{ jobs: GenerationJob[] }>("/api/images/history");
      const remoteJobs = data.jobs
        .map(normalizeJob)
        .filter((job) => {
          const requestId = typeof job.params.request_id === "string" ? job.params.request_id : undefined;
          return !pendingDeletedJobIds.has(job.id) && (!requestId || !pendingDeletedRequestIds.has(requestId));
        })
        .map((job) => ({
          ...job,
          images: (job.images ?? []).filter((image) => !pendingDeletedImageIds.has(image.id))
        }));
      const remoteRequestIds = new Set(remoteJobs.flatMap((job) => {
        const requestId = typeof job.params.request_id === "string" ? job.params.request_id : undefined;
        return requestId ? [requestId] : [];
      }));
      const localJobs = this.jobs.filter((job) => {
        if (!job.id.startsWith(localJobPrefix)) return false;
        const requestId = typeof job.params.request_id === "string" ? job.params.request_id : undefined;
        return requestId && !remoteRequestIds.has(requestId) && !pendingDeletedJobIds.has(job.id);
      });
      this.jobs = [...localJobs, ...remoteJobs];

      if (this.jobs.some((job) => job.status === "PENDING") && !historyPollTimer) {
        this.startHistoryPolling();
      }
    },
    async deleteImage(id: string) {
      pendingDeletedImageIds.add(id);
      const previousJobs = this.jobs;
      this.jobs = this.jobs.flatMap((job) => {
        const currentImages = job.images ?? [];
        const images = currentImages.filter((image) => image.id !== id);
        return images.length || !currentImages.some((image) => image.id === id)
          ? [{ ...job, images, count: images.length || job.count }]
          : [];
      });

      if (isMockId(id)) {
        pendingDeletedImageIds.delete(id);
        return;
      }

      api.delete<{ ok: boolean }>(`/api/images/${id}`)
        .then(() => pendingDeletedImageIds.delete(id))
        .catch(async (error) => {
          pendingDeletedImageIds.delete(id);
          this.jobs = previousJobs;
          this.error = error instanceof Error ? error.message : "删除失败";
          await this.loadHistory().catch(() => undefined);
        });
    },
    async deleteJob(id: string) {
      pendingDeletedJobIds.add(id);
      const previousJobs = this.jobs;
      const targetJob = this.jobs.find((job) => job.id === id);
      const requestId = typeof targetJob?.params.request_id === "string" ? targetJob.params.request_id : undefined;
      if (id.startsWith(localJobPrefix) && requestId) {
        pendingDeletedRequestIds.add(requestId);
      }
      this.jobs = this.jobs.filter((job) => job.id !== id);

      if (isMockId(id)) {
        pendingDeletedJobIds.delete(id);
        return;
      }

      api.delete<{ ok: boolean }>(`/api/jobs/${id}`)
        .then(() => pendingDeletedJobIds.delete(id))
        .catch(async (error) => {
          pendingDeletedJobIds.delete(id);
          this.jobs = previousJobs;
          this.error = error instanceof Error ? error.message : "删除失败";
          await this.loadHistory().catch(() => undefined);
        });
    },
    async loadTemplates() {
      const data = await api.get<{ templates: PromptTemplate[] }>("/api/templates");
      this.templates = data.templates;
    },
    async createTemplate(title: string, prompt: string, params: Record<string, unknown>) {
      const data = await api.post<{ template: PromptTemplate }>("/api/templates", { title, prompt, params });
      this.templates = [data.template, ...this.templates];
    },
    async deleteTemplate(id: string) {
      await api.delete<{ ok: boolean }>(`/api/templates/${id}`);
      this.templates = this.templates.filter((template) => template.id !== id);
    }
  }
});
