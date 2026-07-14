import { Prisma, type GenerationJob } from "@prisma/client";
import { config } from "./config.js";
import { decryptSecret } from "./crypto.js";
import { prisma } from "./db.js";
import { finalizeFailedImageBilling } from "./imageBillingPolicy.js";
import { errorDetails, logEvent } from "./logger.js";
import { getBillingAccountGateway, releaseImageCharge, settleImageCharge } from "./sub2apiBilling.js";
import { decideImageTaskState } from "./imageTaskState.js";
import {
  callImageTaskContent,
  callImageTaskStatus,
  persistImageFromBuffer,
  persistImageFromUrl,
  type ImageTaskOperation
} from "./sub2api.js";

const pendingStatuses = new Set(["queued", "pending", "processing", "running", "in_progress"]);
let timer: NodeJS.Timeout | undefined;
let polling = false;

function upstreamBaseUrl(profileBaseUrl: string) {
  return config.upstreamBaseUrl ?? profileBaseUrl;
}

function paramsObject(job: Pick<GenerationJob, "params">): Record<string, unknown> {
  return job.params && typeof job.params === "object" && !Array.isArray(job.params)
    ? job.params as Record<string, unknown>
    : {};
}

function nextBackoffMs(errorCount: number) {
  return Math.min(30_000, config.imageTaskPollIntervalMs * (2 ** Math.min(4, Math.max(0, errorCount - 1))));
}

async function persistCompletedTask(job: GenerationJob, urls: string[], baseUrl: string, apiKey: string) {
  let persistedCount = 0;
  if (!urls.length && job.count === 1 && job.upstreamTaskId) {
    const content = await callImageTaskContent({ baseUrl, apiKey, taskId: job.upstreamTaskId });
    const existing = await prisma.generatedImage.findUnique({
      where: { jobId_sourceIndex: { jobId: job.id, sourceIndex: 0 } }
    });
    if (!existing) {
      const file = await persistImageFromBuffer(job.id, content.buffer, content.mimeType);
      await prisma.generatedImage.create({
        data: { jobId: job.id, sourceIndex: 0, ...file }
      });
    }
    persistedCount = 1;
  } else {
    for (const [sourceIndex, url] of urls.entries()) {
      const existing = await prisma.generatedImage.findUnique({
        where: { jobId_sourceIndex: { jobId: job.id, sourceIndex } }
      });
      if (existing) {
        persistedCount += 1;
        continue;
      }
      const file = await persistImageFromUrl(job.id, url);
      await prisma.generatedImage.create({
        data: { jobId: job.id, sourceIndex, ...file }
      });
      persistedCount += 1;
    }
  }

  if (!persistedCount) {
    throw new Error("Completed image task returned no downloadable images.");
  }

  const completedAt = new Date();
  let billingUsageLogId = job.billingUsageLogId;
  if (job.billingStatus === "RESERVED") {
    if (!job.billingApiKeyId || !job.billingUserId || !job.billingAccountId || !job.billingAmount) {
      throw new Error("Image task billing reservation is incomplete.");
    }
    const settled = await settleImageCharge({
      jobId: job.id,
      apiKeyId: job.billingApiKeyId,
      userId: job.billingUserId,
      accountId: job.billingAccountId,
      amountUsd: job.billingAmount.toString(),
      count: job.count,
      size: job.size,
      operation: job.upstreamOperation === "edits" ? "edits" : "generations",
      durationMs: completedAt.getTime() - job.createdAt.getTime()
    });
    billingUsageLogId = settled.usageLogId;
  }
  await prisma.generationJob.update({
    where: { id: job.id },
    data: {
      status: "SUCCEEDED",
      progress: 100,
      upstreamStatus: "completed",
      durationMs: completedAt.getTime() - job.createdAt.getTime(),
      completedAt,
      billingStatus: job.billingStatus === "RESERVED" ? "CHARGED" : job.billingStatus,
      billingUsageLogId,
      billingSettledAt: job.billingStatus === "RESERVED" ? completedAt : job.billingSettledAt,
      billingError: null,
      pollLeaseUntil: null,
      nextPollAt: null,
      params: {
        ...paramsObject(job),
        expected_image_count: job.count,
        actual_image_count: persistedCount,
        missing_image_count: Math.max(0, job.count - persistedCount)
      } as Prisma.InputJsonObject
    }
  });
}

async function failJob(job: GenerationJob, message: string, upstreamStatus = "failed") {
  const completedAt = new Date();
  let billingStatus = job.billingStatus;
  let billingUsageLogId = job.billingUsageLogId;
  let billingSettledAt = job.billingSettledAt;
  let billingError: string | null = null;
  if (job.billingStatus === "RESERVED") {
    if (!job.billingApiKeyId || !job.billingUserId || !job.billingAccountId || !job.billingAmount) {
      throw new Error("Image task billing reservation is incomplete and cannot be finalized.");
    }
    try {
      const result = await finalizeFailedImageBilling({
        jobId: job.id,
        apiKeyId: job.billingApiKeyId,
        userId: job.billingUserId,
        accountId: job.billingAccountId,
        amountUsd: job.billingAmount.toString(),
        count: job.count,
        size: job.size,
        operation: job.upstreamOperation === "edits" ? "edits" : "generations",
        durationMs: completedAt.getTime() - job.createdAt.getTime()
      }, {
        chargeOnFailure: config.image2ChargeOnFailure,
        settle: settleImageCharge,
        release: releaseImageCharge
      });
      billingStatus = result.billingStatus;
      billingUsageLogId = result.billingUsageLogId ?? billingUsageLogId;
      billingSettledAt = result.billingSettledAt ?? billingSettledAt;
    } catch (billingFailure) {
      billingStatus = config.image2ChargeOnFailure ? "CHARGE_FAILED" : "RELEASE_FAILED";
      billingError = billingFailure instanceof Error ? billingFailure.message : "Failed to finalize image billing.";
    }
  }
  await prisma.generationJob.update({
    where: { id: job.id },
    data: {
      status: "FAILED",
      upstreamStatus,
      errorMessage: message,
      durationMs: completedAt.getTime() - job.createdAt.getTime(),
      completedAt,
      billingStatus,
      billingUsageLogId,
      billingSettledAt,
      billingError,
      pollLeaseUntil: null,
      nextPollAt: null
    }
  }).catch(() => undefined);
}

async function pollClaimedJob(jobId: string) {
  const job = await prisma.generationJob.findUnique({
    where: { id: jobId },
    include: { profile: true }
  });
  if (!job || job.status !== "PENDING" || !job.upstreamTaskId) return;

  const operation: ImageTaskOperation = job.upstreamOperation === "edits" ? "edits" : "generations";
  const billingGateway = job.billingAccountId ? await getBillingAccountGateway(job.billingAccountId) : undefined;
  const baseUrl = billingGateway?.baseUrl ?? upstreamBaseUrl(job.profile.baseUrl);
  const apiKey = billingGateway?.apiKey ?? decryptSecret(job.profile.encryptedKey);

  try {
    const { task } = await callImageTaskStatus({
      baseUrl,
      apiKey,
      operation,
      taskId: job.upstreamTaskId,
      requestId: typeof paramsObject(job).request_id === "string" ? paramsObject(job).request_id as string : undefined
    });
    const status = task.status ?? "unknown";
    const decision = decideImageTaskState(status, Date.now() - job.createdAt.getTime(), config.imageTaskMaxDurationMs);
    if (decision === "completed") {
      await persistCompletedTask(job, task.urls, baseUrl, apiKey);
      logEvent("generation.async.completed", { jobId: job.id, taskId: job.upstreamTaskId, imageCount: task.urls.length || 1 });
      return;
    }
    if (decision === "failed") {
      await failJob(job, task.error ?? `Upstream image task ${status}.`, status);
      return;
    }
    if (decision === "timeout") {
      await failJob(job, "Image generation task timed out while still pending upstream.", "timeout");
      return;
    }

    await prisma.generationJob.update({
      where: { id: job.id },
      data: {
        upstreamStatus: status,
        progress: task.progress,
        pollErrorCount: 0,
        pollLeaseUntil: null,
        nextPollAt: new Date(Date.now() + config.imageTaskPollIntervalMs),
        params: {
          ...paramsObject(job),
          upstream_last_payload: task.raw as Prisma.InputJsonValue
        } as Prisma.InputJsonObject
      }
    });
    if (!pendingStatuses.has(status)) {
      logEvent("generation.async.unknown_status", { jobId: job.id, taskId: job.upstreamTaskId, status });
    }
  } catch (error) {
    const errorCount = job.pollErrorCount + 1;
    if (Date.now() - job.createdAt.getTime() > config.imageTaskMaxDurationMs) {
      await failJob(job, error instanceof Error ? error.message : "Image generation task timed out.", "timeout");
      return;
    }
    await prisma.generationJob.update({
      where: { id: job.id },
      data: {
        pollErrorCount: errorCount,
        pollLeaseUntil: null,
        nextPollAt: new Date(Date.now() + nextBackoffMs(errorCount)),
        params: {
          ...paramsObject(job),
          poll_failure: errorDetails(error)
        } as Prisma.InputJsonObject
      }
    }).catch(() => undefined);
    logEvent("generation.async.poll_failed", { jobId: job.id, taskId: job.upstreamTaskId, errorCount, ...errorDetails(error) });
  }
}

async function pollDueTasks() {
  if (polling) return;
  polling = true;
  try {
    const abandonedReservations = await prisma.generationJob.findMany({
      where: {
        model: "gpt-image-2",
        billingStatus: { in: ["CHARGE_FAILED", "RELEASE_FAILED", "RESERVED"] },
        OR: [
          { status: "FAILED" },
          {
            status: "PENDING",
            upstreamTaskId: null,
            createdAt: { lt: new Date(Date.now() - config.imageTaskMaxDurationMs) }
          }
        ]
      },
      take: 20
    });
    await Promise.all(abandonedReservations.map(async (job) => {
      if (!job.billingApiKeyId || !job.billingUserId || !job.billingAccountId || !job.billingAmount) return;
      try {
        const completedAt = job.completedAt ?? new Date();
        const result = await finalizeFailedImageBilling({
          jobId: job.id,
          apiKeyId: job.billingApiKeyId,
          userId: job.billingUserId,
          accountId: job.billingAccountId,
          amountUsd: job.billingAmount.toString(),
          count: job.count,
          size: job.size,
          operation: job.upstreamOperation === "edits" ? "edits" : "generations",
          durationMs: completedAt.getTime() - job.createdAt.getTime()
        }, {
          chargeOnFailure: config.image2ChargeOnFailure,
          settle: settleImageCharge,
          release: releaseImageCharge
        });
        await prisma.generationJob.update({
          where: { id: job.id },
          data: {
            status: job.status === "PENDING" ? "FAILED" : job.status,
            errorMessage: job.errorMessage ?? "Image task submission did not complete.",
            billingStatus: result.billingStatus,
            billingUsageLogId: result.billingUsageLogId ?? job.billingUsageLogId,
            billingSettledAt: result.billingSettledAt ?? job.billingSettledAt,
            billingError: null,
            completedAt
          }
        });
      } catch (billingFailure) {
        await prisma.generationJob.update({
          where: { id: job.id },
          data: {
            billingStatus: config.image2ChargeOnFailure ? "CHARGE_FAILED" : "RELEASE_FAILED",
            billingError: billingFailure instanceof Error ? billingFailure.message : "Failed to finalize image billing."
          }
        }).catch(() => undefined);
      }
    }));

    const now = new Date();
    const candidates = await prisma.generationJob.findMany({
      where: {
        model: "gpt-image-2",
        status: "PENDING",
        upstreamTaskId: { not: null },
        AND: [
          { OR: [{ nextPollAt: null }, { nextPollAt: { lte: now } }] },
          { OR: [{ pollLeaseUntil: null }, { pollLeaseUntil: { lt: now } }] }
        ]
      },
      orderBy: { createdAt: "asc" },
      take: 20,
      select: { id: true }
    });

    await Promise.all(candidates.map(async ({ id }) => {
      const claimed = await prisma.generationJob.updateMany({
        where: {
          id,
          status: "PENDING",
          OR: [{ pollLeaseUntil: null }, { pollLeaseUntil: { lt: now } }]
        },
        data: { pollLeaseUntil: new Date(Date.now() + config.imageTaskLeaseMs) }
      });
      if (claimed.count === 1) await pollClaimedJob(id);
    }));
  } catch (error) {
    logEvent("generation.async.poller_failed", errorDetails(error));
  } finally {
    polling = false;
  }
}

export function startImageTaskPolling() {
  if (timer) return;
  void pollDueTasks();
  timer = setInterval(() => void pollDueTasks(), config.imageTaskPollIntervalMs);
  timer.unref();
  logEvent("generation.async.poller_started", { intervalMs: config.imageTaskPollIntervalMs });
}
