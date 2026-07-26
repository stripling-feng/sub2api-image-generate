import { defineStore } from "pinia";
import { api } from "../api";
import type { GenerationJob, Profile } from "../types";

type GeneratePayload = {
  prompt: string;
  model: string;
  async: true;
  size?: string;
  aspect_ratio?: string;
  quality?: string;
  count: number;
  referenceImages?: Array<{
    name: string;
    mimeType: string;
    file: File;
  }>;
  images?: string[];
};

let resultPollTimer: number | undefined;
const pendingDeletedJobIds = new Set<string>();
const pendingDeletedRequestIds = new Set<string>();
const mockJobPrefix = "mock-job-";
const mockImagePrefix = "mock-image-";
const localJobPrefix = "local-job-";
const localRequestPrefix = "local-request-";
const defaultHistoryPageSize = 10;

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
    negativePrompt: null,
    model: payload.model,
    size: payload.size ?? payload.aspect_ratio ?? "auto",
    quality: payload.quality ?? null,
    style: null,
    count: 1,
    responseFormat: "url",
    params: {
      request_id: requestId,
      request_index: index + 1,
      request_total: total,
      aspect_ratio: payload.aspect_ratio,
      reference_image_count: payload.referenceImages?.length ?? payload.images?.length ?? 0
    },
    status: "PENDING",
    progress: 0,
    errorMessage: null,
    durationMs: null,
    createdAt: now,
    images: []
  }));
}

function createLocalRequestId() {
  return `${localRequestPrefix}${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function logWorkbench(event: string, details: Record<string, unknown> = {}) {
  if (!import.meta.env.DEV) return;
  console.log(`[workbench] ${event}`, {
    at: new Date().toISOString(),
    ...details
  });
}

function jobRequestId(job: GenerationJob): string | undefined {
  return typeof job.params.request_id === "string" ? job.params.request_id : undefined;
}

function jobRequestIndex(job: GenerationJob): number | undefined {
  return typeof job.params.request_index === "number" ? job.params.request_index : undefined;
}

function pendingRequestIds(jobs: GenerationJob[]) {
  return [...new Set(jobs.flatMap((job) => {
    const requestId = jobRequestId(job);
    return requestId && job.status === "PENDING" && !pendingDeletedRequestIds.has(requestId)
      ? [requestId]
      : [];
  }))];
}

function latestRequestIdForResults(jobs: GenerationJob[]) {
  const pendingJob = jobs.find((job) => job.status === "PENDING" && jobRequestId(job));
  return pendingJob ? jobRequestId(pendingJob) : undefined;
}

function mergeRequestJobs(currentJobs: GenerationJob[], remoteJobs: GenerationJob[], requestId: string) {
  const remoteIndexes = new Set(remoteJobs.map(jobRequestIndex).filter((index): index is number => index != null));
  const remoteIds = new Set(remoteJobs.map((job) => job.id));
  const preservedLocalPendingJobs = currentJobs.filter((job) => {
    if (!job.id.startsWith(localJobPrefix) || jobRequestId(job) !== requestId || job.status !== "PENDING") return false;
    const requestIndex = jobRequestIndex(job);
    return requestIndex == null || !remoteIndexes.has(requestIndex);
  });

  return [
    ...remoteJobs,
    ...preservedLocalPendingJobs,
    ...currentJobs.filter((job) => {
      if (remoteIds.has(job.id)) return false;
      if (preservedLocalPendingJobs.some((pendingJob) => pendingJob.id === job.id)) return false;
      return jobRequestId(job) !== requestId;
    })
  ];
}

export const useWorkbenchStore = defineStore("workbench", {
  state: () => ({
    profile: null as Profile | null,
    jobs: [] as GenerationJob[],
    historyPage: 1,
    historyPageSize: defaultHistoryPageSize,
    historyTotal: 0,
    historyTotalPages: 1,
    historyLoading: false,
    currentRequestId: "",
    loading: false,
    status: "",
    error: ""
  }),
  actions: {
    resetWorkspace() {
      if (resultPollTimer) {
        window.clearInterval(resultPollTimer);
        resultPollTimer = undefined;
      }
      pendingDeletedJobIds.clear();
      pendingDeletedRequestIds.clear();
      this.jobs = [];
      this.historyPage = 1;
      this.historyTotal = 0;
      this.historyTotalPages = 1;
      this.historyLoading = false;
      this.currentRequestId = "";
      this.error = "";
      this.status = "";
    },
    async connect(apiKey: string) {
      this.resetWorkspace();
      this.loading = true;
      this.error = "";
      try {
        const normalizedApiKey = apiKey.trim();
        localStorage.setItem("apiKey", normalizedApiKey);
        this.profile = {
          id: normalizedApiKey,
          baseUrl: localStorage.getItem("baseUrl") ?? "",
          keyHashPreview: `${normalizedApiKey.slice(0, 4)}...`
        };
        this.status = "";
        await this.loadHistory();
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
      const startedAt = Date.now();
      const safeCount = Math.max(1, Math.min(10, Number(payload.count) || 1));
      payload = { ...payload, count: safeCount };
      const optimisticRequestId = createLocalRequestId();
      logWorkbench("generate.start", {
        optimisticRequestId,
        model: payload.model,
        count: payload.count,
        referenceImageCount: payload.referenceImages?.length ?? 0,
        promptChars: payload.prompt.length
      });
      const optimisticJobs = createLocalPendingJobs(payload, optimisticRequestId, payload.count);
      this.currentRequestId = optimisticRequestId;
      this.jobs = [
        ...optimisticJobs,
        ...this.jobs.filter((job) => job.params.request_id !== optimisticRequestId)
      ];
      try {
        const referenceImages = payload.referenceImages ?? [];
        let data: { requestId?: string; count?: number; jobs?: GenerationJob[]; job?: GenerationJob };
        const images = await Promise.all(referenceImages.map(uploadReferenceImage));
        const body: Record<string, unknown> = {
          model: payload.model,
          prompt: payload.prompt,
          async: payload.async,
          count: payload.count,
          images
        };
        if (payload.size) body.size = payload.size;
        if (payload.aspect_ratio) body.aspect_ratio = payload.aspect_ratio;
        if (payload.quality) body.quality = payload.quality;
        data = await api.post("/api/images/generate", {
          ...body
        });
        logWorkbench("generate.accepted", {
          optimisticRequestId,
          requestId: data.requestId,
          count: data.count ?? payload.count,
          durationMs: Date.now() - startedAt
        });
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
        this.startResultPolling();
      } catch (error) {
        logWorkbench("generate.error", {
          optimisticRequestId,
          durationMs: Date.now() - startedAt,
          message: error instanceof Error ? error.message : String(error)
        });
        this.error = error instanceof Error ? error.message : "生成失败";
        await this.loadHistory().catch(() => undefined);
      } finally {
        logWorkbench("generate.done", {
          optimisticRequestId,
          durationMs: Date.now() - startedAt
        });
        this.loading = false;
      }
    },
    startResultPolling() {
      if (resultPollTimer) return;

      logWorkbench("results.poll.start", {
        currentRequestId: this.currentRequestId,
        requestIds: pendingRequestIds(this.jobs)
      });
      resultPollTimer = window.setInterval(async () => {
        const requestIds = pendingRequestIds(this.jobs);
        if (!requestIds.length) {
          if (resultPollTimer) {
            window.clearInterval(resultPollTimer);
            resultPollTimer = undefined;
          }
          logWorkbench("results.poll.stop", {
            currentRequestId: this.currentRequestId,
            jobCount: this.jobs.length
          });
          return;
        }

        await Promise.all(requestIds.map((requestId) => this.loadCurrentResults(requestId).catch(() => undefined)));
      }, 5000);
    },
    async loadHistory(page?: number) {
      const startedAt = Date.now();
      const targetPage = Math.max(1, Math.floor(Number(page ?? this.historyPage) || 1));
      const targetPageSize = this.historyPageSize;
      this.historyLoading = true;
      logWorkbench("history.load.start", {
        currentRequestId: this.currentRequestId,
        page: targetPage,
        pageSize: targetPageSize
      });
      try {
        const data = await api.get<{
          jobs: GenerationJob[];
          page: number;
          pageSize: number;
          total: number;
          totalPages: number;
        }>(`/api/images/history?page=${targetPage}&pageSize=${targetPageSize}`);
        const remoteJobs = data.jobs
          .map(normalizeJob)
          .filter((job) => {
            const requestId = jobRequestId(job);
            return !pendingDeletedJobIds.has(job.id) && (!requestId || !pendingDeletedRequestIds.has(requestId));
          })
          .map((job) => ({ ...job, images: job.images ?? [] }));
        const remoteRequestIds = new Set(remoteJobs.flatMap((job) => {
          const requestId = jobRequestId(job);
          return requestId ? [requestId] : [];
        }));
        const localJobs = this.jobs.filter((job) => {
          if (!job.id.startsWith(localJobPrefix)) return false;
          const requestId = jobRequestId(job);
          return targetPage === 1 && requestId && !remoteRequestIds.has(requestId) && !pendingDeletedJobIds.has(job.id);
        });
        this.historyPage = data.page;
        this.historyPageSize = data.pageSize;
        this.historyTotal = data.total;
        this.historyTotalPages = data.totalPages;
        this.jobs = [...localJobs, ...remoteJobs];
        if (!this.currentRequestId) {
          this.currentRequestId = latestRequestIdForResults(this.jobs) ?? "";
        }
        if (pendingRequestIds(this.jobs).length) {
          this.startResultPolling();
        }
        logWorkbench("history.load.done", {
          currentRequestId: this.currentRequestId,
          page: this.historyPage,
          pageSize: this.historyPageSize,
          total: this.historyTotal,
          totalPages: this.historyTotalPages,
          remoteCount: remoteJobs.length,
          localPendingCount: localJobs.length,
          pendingCount: this.jobs.filter((job) => job.status === "PENDING").length,
          failedCount: this.jobs.filter((job) => job.status === "FAILED").length,
          succeededCount: this.jobs.filter((job) => job.status === "SUCCEEDED").length,
          durationMs: Date.now() - startedAt
        });
      } finally {
        this.historyLoading = false;
      }

    },
    async loadCurrentResults(requestId?: string) {
      const targetRequestId = requestId || this.currentRequestId;
      if (!targetRequestId) return;
      const startedAt = Date.now();
      logWorkbench("results.load.start", { requestId: targetRequestId });
      const data = await api.get<{ jobs: GenerationJob[] }>(`/api/images/results/${encodeURIComponent(targetRequestId)}`);
      const remoteJobs = data.jobs
        .map(normalizeJob)
        .filter((job) => !pendingDeletedJobIds.has(job.id))
        .map((job) => ({ ...job, images: job.images ?? [] }));
      this.jobs = mergeRequestJobs(this.jobs, remoteJobs, targetRequestId);
      logWorkbench("results.load.done", {
        requestId: targetRequestId,
        remoteCount: remoteJobs.length,
        pendingCount: remoteJobs.filter((job) => job.status === "PENDING").length,
        failedCount: remoteJobs.filter((job) => job.status === "FAILED").length,
        succeededCount: remoteJobs.filter((job) => job.status === "SUCCEEDED").length,
        durationMs: Date.now() - startedAt
      });

      if (pendingRequestIds(this.jobs).length) {
        this.startResultPolling();
      }
    },
    async deleteJob(id: string) {
      pendingDeletedJobIds.add(id);
      const previousJobs = this.jobs;
      const targetJob = this.jobs.find((job) => job.id === id);
      const requestId = targetJob ? jobRequestId(targetJob) : undefined;
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
    async deleteAllJobs() {
      const previousJobs = this.jobs;
      const previousCurrentRequestId = this.currentRequestId;
      const previousHistoryPage = this.historyPage;
      const previousHistoryTotal = this.historyTotal;
      const previousHistoryTotalPages = this.historyTotalPages;

      if (resultPollTimer) {
        window.clearInterval(resultPollTimer);
        resultPollTimer = undefined;
      }

      this.jobs = [];
      this.currentRequestId = "";
      this.historyPage = 1;
      this.historyTotal = 0;
      this.historyTotalPages = 1;
      this.error = "";

      try {
        await api.delete<{ ok: boolean; deletedCount: number }>("/api/jobs");
        pendingDeletedJobIds.clear();
        pendingDeletedRequestIds.clear();
      } catch (error) {
        this.jobs = previousJobs;
        this.currentRequestId = previousCurrentRequestId;
        this.historyPage = previousHistoryPage;
        this.historyTotal = previousHistoryTotal;
        this.historyTotalPages = previousHistoryTotalPages;
        this.error = error instanceof Error ? error.message : "删除失败";
        if (pendingRequestIds(this.jobs).length) {
          this.startResultPolling();
        }
        throw error;
      }
    }
  }
});

async function uploadReferenceImage(image: { name: string; file: File }) {
  const form = new FormData();
  form.append("file", image.file, image.name);
  const data = await api.postForm<{ url: string; publicUrl?: string }>("/api/images/uploads", form);
  const url = data.url || data.publicUrl;
  if (!url) throw new Error("参考图上传失败");
  return url;
}
