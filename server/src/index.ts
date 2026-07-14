import fs from "node:fs/promises";
import path from "node:path";
import cookieParser from "cookie-parser";
import cors from "cors";
import express, { type Request, type Response, type NextFunction } from "express";
import multer from "multer";
import { fileTypeFromBuffer } from "file-type";
import { Prisma, type GenerationJob } from "@prisma/client";
import { PrismaClientKnownRequestError } from "@prisma/client/runtime/library";
import { nanoid } from "nanoid";
import { config } from "./config.js";
import { prisma } from "./db.js";
import { decryptSecret, encryptSecret, hashSecret } from "./crypto.js";
import { bindSchema, generateSchema, templateSchema } from "./schemas.js";
import { createSession, requireSession, sessionCookie, type AuthedRequest } from "./session.js";
import { errorDetails, logEvent } from "./logger.js";
import { startImageRetentionCleanup } from "./imageRetention.js";
import { startImageTaskPolling } from "./imageTaskPoller.js";
import { finalizeFailedImageBilling, settleReservedImageBilling } from "./imageBillingPolicy.js";
import { assertSub2apiBillingReady, getBillingAccountGateway, releaseImageCharge, reserveImageCharge, settleImageCharge, validateSub2apiApiKey, type BillingReservation } from "./sub2apiBilling.js";
import {
  buildAsyncImageRequest,
  callImageGeneration,
  callImageEdit,
  extractErrorMessage,
  extractImageItems,
  parseImageTaskPayload,
  persistImageFromBase64,
  persistImageFromUrl,
  readImageDimensions,
  type ImageUpload
} from "./sub2api.js";

const app = express();
const temporaryImageDir = path.join(config.uploadDir, "temporary-image-requests");
const multipartImageUpload = multer({
  dest: temporaryImageDir,
  limits: { fileSize: 10 * 1024 * 1024, files: 11, fields: 10 }
}).fields([
  { name: "image", maxCount: 10 },
  { name: "image[]", maxCount: 10 },
  { name: "mask", maxCount: 1 }
]);

type MultipartFiles = Record<string, Express.Multer.File[]>;

async function cleanupUploadedFiles(files: Express.Multer.File[]) {
  await Promise.all(files.map((file) => fs.unlink(file.path).catch(() => undefined)));
}

async function validateUploadedImage(file: Express.Multer.File, mask = false) {
  const buffer = await fs.readFile(file.path);
  const dimensions = readImageDimensions(buffer);
  const isPng = buffer.length >= 24 && buffer.toString("ascii", 1, 4) === "PNG";
  const detectedType = await fileTypeFromBuffer(buffer);
  if (mask && (!isPng || file.mimetype !== "image/png")) {
    throw Object.assign(new Error("Mask must be a PNG image."), { status: 422 });
  }
  if (!detectedType?.mime.startsWith("image/")) {
    throw Object.assign(new Error(`Unsupported or invalid image: ${file.originalname}`), { status: 422 });
  }
  if (mask && (!dimensions.width || !dimensions.height)) {
    throw Object.assign(new Error("Unable to read mask dimensions."), { status: 422 });
  }
  return dimensions;
}

async function imageUploadBuffer(image: ImageUpload) {
  return image.filePath ? fs.readFile(image.filePath) : Buffer.from(image.data ?? "", "base64");
}

async function validateMaskDimensions(reference: ImageUpload, mask: ImageUpload) {
  const [referenceBuffer, maskBuffer] = await Promise.all([imageUploadBuffer(reference), imageUploadBuffer(mask)]);
  const referenceDimensions = readImageDimensions(referenceBuffer);
  const maskDimensions = readImageDimensions(maskBuffer);
  const isPng = maskBuffer.length >= 24 && maskBuffer.toString("ascii", 1, 4) === "PNG";
  if (!isPng || mask.mimeType !== "image/png") {
    throw Object.assign(new Error("Mask must be a PNG image."), { status: 422 });
  }
  if (!referenceDimensions.width || !referenceDimensions.height || !maskDimensions.width || !maskDimensions.height) {
    throw Object.assign(new Error("Unable to read reference image or mask dimensions."), { status: 422 });
  }
  if (referenceDimensions.width !== maskDimensions.width || referenceDimensions.height !== maskDimensions.height) {
    throw Object.assign(new Error("Mask dimensions must match the first reference image."), { status: 422 });
  }
}

function uploadedFile(file: Express.Multer.File): ImageUpload {
  return { name: file.originalname, mimeType: file.mimetype, filePath: file.path };
}

function withoutUndefined<T extends Record<string, unknown>>(value: T): Record<string, unknown> {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== undefined && item !== null));
}

function buildOpenAIImageRequest(input: ReturnType<typeof generateSchema.parse>) {
  return withoutUndefined({
    model: input.model,
    prompt: input.negativePrompt ? `${input.prompt}\n\nNegative prompt: ${input.negativePrompt}` : input.prompt,
    size: input.size,
    n: 1,
    response_format: input.responseFormat,
    quality: input.quality === "auto" ? undefined : input.quality,
    output_format: input.outputFormat
  });
}

function pickOpenAIImageRequest(params: Record<string, unknown>) {
  return withoutUndefined({
    model: params.model,
    prompt: params.prompt,
    size: params.size,
    n: params.n,
    response_format: params.response_format,
    quality: params.quality,
    output_format: params.output_format
  });
}

function upstreamBaseUrl(profileBaseUrl: string) {
  return config.upstreamBaseUrl ?? profileBaseUrl;
}

function isLocalUploadPath(filePath: string) {
  const uploadRoot = path.resolve(config.uploadDir);
  const resolvedPath = path.resolve(filePath);
  return resolvedPath === uploadRoot || resolvedPath.startsWith(`${uploadRoot}${path.sep}`);
}

async function deleteGeneratedImageFiles(images: Array<{ filePath: string }>) {
  const imageDirs = new Set<string>();

  for (const image of images) {
    if (!isLocalUploadPath(image.filePath)) continue;
    imageDirs.add(path.dirname(path.resolve(image.filePath)));
    await fs.unlink(image.filePath).catch(() => undefined);
  }

  for (const dir of imageDirs) {
    await fs.rm(dir, { recursive: false, force: true }).catch(() => undefined);
  }
}

function noStore(_req: Request, res: Response, next: NextFunction) {
  res.set("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate");
  res.set("Pragma", "no-cache");
  res.set("Expires", "0");
  next();
}

function generationErrorMessage(error: unknown) {
  const details = errorDetails(error);
  const parts = [
    error instanceof Error ? error.message : "Unknown generation error.",
    details.status ? `HTTP ${details.status}` : "",
    details.code ? `code ${details.code}` : "",
    details.payload ? `payload ${JSON.stringify(details.payload).slice(0, 500)}` : ""
  ].filter(Boolean);
  return parts.join(" | ");
}

app.use((req, res, next) => {
  const startedAt = Date.now();
  res.on("finish", () => {
    logEvent("http.request", {
      method: req.method,
      path: req.path,
      status: res.statusCode,
      durationMs: Date.now() - startedAt
    });
  });
  next();
});

app.use(cors({ origin: config.clientOrigin, credentials: true }));
app.use(express.json({ limit: "25mb" }));
app.use(cookieParser());
app.use("/uploads", express.static(config.uploadDir));
app.use("/img", express.static(path.join(config.uploadDir, "images")));
app.use("/api/images/history", noStore);
app.use("/api/images/results", noStore);

app.get("/api/health", (_req, res) => {
  res.json({ ok: true });
});

app.post("/api/session/bind", async (req, res, next) => {
  const startedAt = Date.now();
  try {
    const input = bindSchema.parse(req.body);
    logEvent("session.bind.start", {
      baseUrl: input.baseUrl,
      apiKeyChars: input.apiKey.length
    });
    const billingAccount = await validateSub2apiApiKey(input.apiKey);
    const keyHash = hashSecret(input.apiKey);
    const profile = await prisma.apiProfile.upsert({
      where: { keyHash },
      create: {
        baseUrl: input.baseUrl,
        keyHash,
        encryptedKey: encryptSecret(input.apiKey)
      },
      update: {
        baseUrl: input.baseUrl,
        encryptedKey: encryptSecret(input.apiKey)
      }
    });

    await createSession(profile.id, res);
    logEvent("session.bind.done", {
      profileId: profile.id,
      keyHashPreview: `${profile.keyHash.slice(0, 8)}...`,
      upstreamOverride: Boolean(config.upstreamBaseUrl),
      durationMs: Date.now() - startedAt
    });

    res.json({
      profile: {
        id: profile.id,
        baseUrl: profile.baseUrl,
        keyHashPreview: `${profile.keyHash.slice(0, 8)}...`,
        balanceUsd: billingAccount.balanceUsd,
        availableBalanceUsd: billingAccount.availableBalanceUsd
      }
    });
  } catch (error) {
    logEvent("session.bind.error", {
      durationMs: Date.now() - startedAt,
      ...errorDetails(error)
    });
    next(error);
  }
});

app.post("/api/session/logout", async (req, res, next) => {
  try {
    const token = req.cookies?.[sessionCookie];
    if (token) {
      await prisma.apiSession.deleteMany({ where: { tokenHash: hashSecret(token) } });
    }
    res.clearCookie(sessionCookie);
    res.json({ ok: true });
  } catch (error) {
    next(error);
  }
});

app.get("/api/session/me", async (req, res, next) => {
  const apiKey = req.header("X-API-Key");
  if (apiKey) {
    const profile = await prisma.apiProfile.findUnique({
      where: { keyHash: hashSecret(apiKey) }
    });
    if (!profile) {
      res.json({ profile: null });
      return;
    }
    res.json({
      profile: {
        id: profile.id,
        baseUrl: profile.baseUrl,
        keyHashPreview: `${profile.keyHash.slice(0, 8)}...`
      }
    });
    return;
  }

  const token = req.cookies?.[sessionCookie];
  if (!token) {
    res.json({ profile: null });
    return;
  }

  requireSession(req, res, () => next());
}, async (req, res) => {
  const profile = (req as AuthedRequest).profile;
  res.json({
    profile: {
      id: profile.id,
      baseUrl: profile.baseUrl,
      keyHashPreview: `${profile.keyHash.slice(0, 8)}...`
    }
  });
});

async function runGenerationJob(args: {
  profile: Pick<AuthedRequest["profile"], "baseUrl" | "encryptedKey">;
  job: GenerationJob;
  params: Record<string, unknown>;
  referenceImages: ImageUpload[];
}) {
  const startedAt = Date.now();
  const requestId = typeof args.params.request_id === "string" ? args.params.request_id : undefined;
  const upstreamParams = pickOpenAIImageRequest(args.params);
  logEvent("generation.job.start", {
    requestId,
    jobId: args.job.id,
    model: args.job.model,
    size: args.job.size,
    responseFormat: args.job.responseFormat,
    referenceImageCount: args.referenceImages.length
  });

  try {
    logEvent("generation.job.gateway_call.start", {
      requestId,
      jobId: args.job.id,
      mode: args.referenceImages.length ? "edit" : "generation"
    });
    const result = args.referenceImages.length
      ? await callImageEdit({
        baseUrl: upstreamBaseUrl(args.profile.baseUrl),
        apiKey: decryptSecret(args.profile.encryptedKey),
        body: upstreamParams,
        images: args.referenceImages,
        requestId
      })
      : await callImageGeneration({
        baseUrl: upstreamBaseUrl(args.profile.baseUrl),
        apiKey: decryptSecret(args.profile.encryptedKey),
        body: upstreamParams,
        requestId
      });
    logEvent("generation.job.gateway_call.done", {
      requestId,
      jobId: args.job.id,
      durationMs: result.durationMs
    });
    const items = extractImageItems(result.json);
    logEvent("generation.job.images.extracted", {
      requestId,
      jobId: args.job.id,
      itemCount: items.length,
      b64Count: items.filter((item) => item.b64).length,
      urlCount: items.filter((item) => item.url).length
    });
    if (items.length === 0) {
      throw Object.assign(new Error("sub2api returned no images."), { payload: result.json });
    }

    for (const [index, item] of items.entries()) {
      const persistStartedAt = Date.now();
      logEvent("generation.job.image.persist.start", {
        requestId,
        jobId: args.job.id,
        index: index + 1,
        source: item.b64 ? "b64_json" : item.url ? "url" : "unknown"
      });
      const file = item.b64
        ? await persistImageFromBase64(args.job.id, item.b64)
        : item.url
          ? await persistImageFromUrl(args.job.id, item.url)
          : undefined;

      if (file) {
        await prisma.generatedImage.create({
          data: {
            jobId: args.job.id,
            filePath: file.filePath,
            publicUrl: file.publicUrl,
            mimeType: file.mimeType,
            width: file.width,
            height: file.height,
            sizeBytes: file.sizeBytes
          }
        });
        logEvent("generation.job.image.persist.done", {
          requestId,
          jobId: args.job.id,
          index: index + 1,
          publicUrl: file.publicUrl,
          mimeType: file.mimeType,
          sizeBytes: file.sizeBytes,
          width: file.width,
          height: file.height,
          durationMs: Date.now() - persistStartedAt
        });
      }
    }

    const totalDurationMs = Date.now() - startedAt;
    const persistDurationMs = Math.max(0, totalDurationMs - result.durationMs);
    await prisma.generationJob.update({
      where: { id: args.job.id },
      data: {
        status: "SUCCEEDED",
        durationMs: totalDurationMs,
        params: {
          ...args.params,
          gateway_duration_ms: result.durationMs,
          persist_duration_ms: persistDurationMs,
          total_duration_ms: totalDurationMs
        } as Prisma.InputJsonObject
      }
    });
    logEvent("generation.job.succeeded", {
      requestId,
      jobId: args.job.id,
      gatewayDurationMs: result.durationMs,
      persistDurationMs,
      totalDurationMs
    });
  } catch (error) {
    const message = generationErrorMessage(error);
    await prisma.generationJob.update({
      where: { id: args.job.id },
      data: {
        status: "FAILED",
        errorMessage: message,
        durationMs: Date.now() - startedAt,
        params: {
          ...args.params,
          failure: errorDetails(error)
        } as Prisma.InputJsonObject
      }
    }).catch(() => undefined);
    logEvent("generation.job.failed", {
      requestId,
      jobId: args.job.id,
      durationMs: Date.now() - startedAt,
      ...errorDetails(error)
    });
  }
}

app.post("/api/images/generate", requireSession, multipartImageUpload, async (req, res, next) => {
  const startedAt = Date.now();
  const multipartFiles = (req.files ?? {}) as MultipartFiles;
  const referenceFiles = [...(multipartFiles.image ?? []), ...(multipartFiles["image[]"] ?? [])];
  const maskFiles = multipartFiles.mask ?? [];
  const allUploadedFiles = [...referenceFiles, ...maskFiles];
  let cleanupInRoute = true;
  try {
    const profile = (req as AuthedRequest).profile;
    if (referenceFiles.length > 10) {
      throw Object.assign(new Error("At most 10 reference images are allowed."), { status: 422 });
    }
    if (maskFiles.length > 1) {
      throw Object.assign(new Error("Only one mask is allowed."), { status: 422 });
    }
    await Promise.all(referenceFiles.map((file) => validateUploadedImage(file)));
    if (maskFiles[0]) await validateUploadedImage(maskFiles[0], true);

    let rawPayload: unknown = req.body;
    if (typeof req.body?.payload === "string") {
      try {
        rawPayload = JSON.parse(req.body.payload);
      } catch {
        throw Object.assign(new Error("Multipart payload must be valid JSON."), { status: 422 });
      }
    }
    const input = generateSchema.parse(rawPayload);
    const referenceImages: ImageUpload[] = referenceFiles.length
      ? referenceFiles.map(uploadedFile)
      : input.referenceImages;
    const mask: ImageUpload | undefined = maskFiles[0] ? uploadedFile(maskFiles[0]) : input.mask;
    if (mask && referenceImages.length === 0) {
      throw Object.assign(new Error("A mask requires at least one reference image."), { status: 422 });
    }
    if (mask && referenceImages[0]) await validateMaskDimensions(referenceImages[0], mask);

    const requestId = nanoid(16);
    logEvent("api.images.generate.accepted", {
      requestId,
      profileId: profile.id,
      model: input.model,
      size: input.size,
      count: input.count,
      responseFormat: input.responseFormat,
      outputFormat: input.outputFormat,
      referenceImageCount: referenceImages.length,
      hasMask: Boolean(mask),
      promptChars: input.prompt.length
    });
    const jobMetadata = withoutUndefined({
      ...input.extraParams,
      request_id: requestId,
      response_format: input.responseFormat,
      output_format: input.outputFormat,
      aspect_ratio: input.aspectRatio,
      custom_aspect_ratio: input.customAspectRatio,
      size_tier: input.extraParams.size_tier,
      custom_size_mode: input.extraParams.custom_size_mode,
      custom_width: input.extraParams.custom_width,
      custom_height: input.extraParams.custom_height,
      reference_width: input.extraParams.reference_width,
      reference_height: input.extraParams.reference_height,
      computed_size: input.size
    });

    if (input.model === "gpt-image-2") {
      const prompt = input.negativePrompt ? `${input.prompt}\n\nNegative prompt: ${input.negativePrompt}` : input.prompt;
      const upstreamParams = buildAsyncImageRequest({ prompt, size: input.size }, input.count);
      const job = await prisma.generationJob.create({
        data: {
          profileId: profile.id,
          prompt: input.prompt,
          negativePrompt: input.negativePrompt,
          model: input.model,
          size: input.size,
          quality: input.quality,
          style: input.style,
          count: input.count,
          responseFormat: input.responseFormat,
          params: {
            ...upstreamParams,
            ...jobMetadata,
            request_index: 1,
            request_total: 1,
            reference_image_count: referenceImages.length,
            has_mask: Boolean(mask)
          } as Prisma.InputJsonObject,
          status: "PENDING"
        }
      });
      let billingReservation: BillingReservation | undefined;
      const operation: "generations" | "edits" = referenceImages.length ? "edits" : "generations";
      logEvent("generation.job.created", {
        requestId,
        jobId: job.id,
        total: 1,
        model: job.model,
        size: job.size,
        responseFormat: job.responseFormat
      });

      try {
        billingReservation = await reserveImageCharge(decryptSecret(profile.encryptedKey), input.count);
        const billingParams = {
          ...(job.params as Prisma.InputJsonObject),
          billing_unit_price_usd: config.gptImage2UnitPriceUsd,
          billing_amount_usd: billingReservation.amountUsd,
          billing_available_after_reserve_usd: billingReservation.availableBalanceUsd
        } as Prisma.InputJsonObject;
        await prisma.generationJob.update({
          where: { id: job.id },
          data: {
            billingStatus: "RESERVED",
            billingAmount: billingReservation.amountUsd,
            billingApiKeyId: billingReservation.apiKeyId,
            billingUserId: billingReservation.userId,
            billingAccountId: billingReservation.accountId,
            billingReservedAt: new Date(),
            params: billingParams
          }
        });
        const billingContext = {
          jobId: job.id,
          apiKeyId: billingReservation.apiKeyId,
          userId: billingReservation.userId,
          accountId: billingReservation.accountId,
          amountUsd: billingReservation.amountUsd,
          count: input.count,
          size: input.size,
          operation,
          durationMs: Date.now() - startedAt
        };
        const immediateBilling = await settleReservedImageBilling(billingContext, {
          chargeImmediately: config.image2ChargeOnFailure,
          settle: settleImageCharge
        });
        await prisma.generationJob.update({
          where: { id: job.id },
          data: {
            billingStatus: immediateBilling.billingStatus,
            billingUsageLogId: immediateBilling.billingUsageLogId,
            billingSettledAt: immediateBilling.billingSettledAt
          }
        });
        const billingGateway = await getBillingAccountGateway(billingReservation.accountId);
        const result = referenceImages.length
          ? await callImageEdit({
            baseUrl: billingGateway.baseUrl,
            apiKey: billingGateway.apiKey,
            body: upstreamParams,
            images: referenceImages,
            mask,
            requestId
          })
          : await callImageGeneration({
            baseUrl: billingGateway.baseUrl,
            apiKey: billingGateway.apiKey,
            body: upstreamParams,
            requestId
          });
        const task = parseImageTaskPayload(result.json);
        if (!task.id) {
          throw Object.assign(new Error("Upstream image task response is missing id."), { payload: result.json });
        }
        await prisma.generationJob.update({
          where: { id: job.id },
          data: {
            upstreamTaskId: task.id,
            upstreamOperation: operation,
            upstreamStatus: task.status ?? "queued",
            progress: task.progress,
            nextPollAt: new Date(),
            params: {
              ...billingParams,
              upstream_create_duration_ms: result.durationMs,
              upstream_create_payload: result.json as Prisma.InputJsonValue
            } as Prisma.InputJsonObject
          }
        });
      } catch (error) {
        const message = generationErrorMessage(error);
        let billingStatus = billingReservation ? "RELEASE_FAILED" : "FAILED";
        let billingUsageLogId: string | undefined;
        let billingSettledAt: Date | undefined;
        let billingError = billingReservation ? message : undefined;
        if (billingReservation) {
          try {
            const result = await finalizeFailedImageBilling({
              jobId: job.id,
              apiKeyId: billingReservation.apiKeyId,
              userId: billingReservation.userId,
              accountId: billingReservation.accountId,
              amountUsd: billingReservation.amountUsd,
              count: input.count,
              size: input.size,
              operation,
              durationMs: Date.now() - startedAt
            }, {
              chargeOnFailure: config.image2ChargeOnFailure,
              settle: settleImageCharge,
              release: releaseImageCharge
            });
            billingStatus = result.billingStatus;
            billingUsageLogId = result.billingUsageLogId;
            billingSettledAt = result.billingSettledAt;
            billingError = undefined;
          } catch (billingFailure) {
            billingStatus = config.image2ChargeOnFailure ? "CHARGE_FAILED" : "RELEASE_FAILED";
            billingError = billingFailure instanceof Error ? billingFailure.message : "Failed to finalize image billing.";
          }
        }
        await prisma.generationJob.update({
          where: { id: job.id },
          data: {
            status: "FAILED",
            upstreamStatus: "create_failed",
            errorMessage: message,
            durationMs: Date.now() - startedAt,
            completedAt: new Date(),
            billingStatus,
            billingUsageLogId,
            billingSettledAt,
            billingError
          }
        }).catch(() => undefined);
        throw error;
      }
    } else {
      const upstreamParams = buildOpenAIImageRequest(input);
      cleanupInRoute = false;
      void (async () => {
        try {
          await Promise.all(Array.from({ length: input.count }, async (_item, index) => {
            const job = await prisma.generationJob.create({
              data: {
                profileId: profile.id,
                prompt: input.prompt,
                negativePrompt: input.negativePrompt,
                model: input.model,
                size: input.size,
                quality: input.quality,
                style: input.style,
                count: 1,
                responseFormat: input.responseFormat,
                params: {
                  ...upstreamParams,
                  ...jobMetadata,
                  request_index: index + 1,
                  request_total: input.count
                } as Prisma.InputJsonObject,
                status: "PENDING"
              }
            });
            await runGenerationJob({ profile, job, params: job.params as Record<string, unknown>, referenceImages });
          }));
        } catch (error) {
          logEvent("generation.enqueue.failed", { requestId, ...errorDetails(error) });
        } finally {
          await cleanupUploadedFiles(allUploadedFiles);
        }
      })();
    }

    logEvent("api.images.generate.response", {
      requestId,
      status: 202,
      durationMs: Date.now() - startedAt
    });
    res.status(202).json({ requestId, count: input.count });
  } catch (error) {
    logEvent("api.images.generate.error", {
      durationMs: Date.now() - startedAt,
      ...errorDetails(error)
    });
    next(error);
  } finally {
    if (cleanupInRoute) await cleanupUploadedFiles(allUploadedFiles);
  }
});

app.get("/api/images/history", requireSession, async (req, res, next) => {
  const startedAt = Date.now();
  try {
    const profile = (req as AuthedRequest).profile;
    const page = Math.max(1, Math.floor(Number(req.query.page) || 1));
    const pageSize = Math.min(50, Math.max(1, Math.floor(Number(req.query.pageSize) || 10)));
    const where = { profileId: profile.id };
    const [total, jobs] = await prisma.$transaction([
      prisma.generationJob.count({ where }),
      prisma.generationJob.findMany({
        where,
        orderBy: { createdAt: "desc" },
        skip: (page - 1) * pageSize,
        take: pageSize,
        include: { images: true }
      })
    ]);
    const totalPages = Math.max(1, Math.ceil(total / pageSize));
    logEvent("api.images.history", {
      profileId: profile.id,
      page,
      pageSize,
      total,
      totalPages,
      jobCount: jobs.length,
      pendingCount: jobs.filter((job) => job.status === "PENDING").length,
      failedCount: jobs.filter((job) => job.status === "FAILED").length,
      succeededCount: jobs.filter((job) => job.status === "SUCCEEDED").length,
      durationMs: Date.now() - startedAt
    });
    res.json({ jobs, page, pageSize, total, totalPages });
  } catch (error) {
    logEvent("api.images.history.error", {
      durationMs: Date.now() - startedAt,
      ...errorDetails(error)
    });
    next(error);
  }
});

app.get("/api/images/results/:requestId", requireSession, async (req, res, next) => {
  const startedAt = Date.now();
  try {
    const profile = (req as AuthedRequest).profile;
    const requestId = req.params.requestId;
    const jobs = await prisma.generationJob.findMany({
      where: {
        profileId: profile.id,
        params: {
          path: ["request_id"],
          equals: requestId
        }
      },
      orderBy: { createdAt: "asc" },
      include: { images: true }
    });
    logEvent("api.images.results", {
      requestId,
      profileId: profile.id,
      jobCount: jobs.length,
      pendingCount: jobs.filter((job) => job.status === "PENDING").length,
      failedCount: jobs.filter((job) => job.status === "FAILED").length,
      succeededCount: jobs.filter((job) => job.status === "SUCCEEDED").length,
      durationMs: Date.now() - startedAt
    });
    res.json({ jobs });
  } catch (error) {
    logEvent("api.images.results.error", {
      requestId: req.params.requestId,
      durationMs: Date.now() - startedAt,
      ...errorDetails(error)
    });
    next(error);
  }
});

app.delete("/api/images/:id", requireSession, async (req, res, next) => {
  try {
    const profile = (req as AuthedRequest).profile;
    const image = await prisma.generatedImage.findFirst({
      where: { id: req.params.id, job: { profileId: profile.id } }
    });
    if (!image) {
      res.status(404).json({ error: "Image not found." });
      return;
    }
    await deleteGeneratedImageFiles([image]);
    await prisma.generatedImage.delete({ where: { id: image.id } });
    res.json({ ok: true });
  } catch (error) {
    next(error);
  }
});

app.delete("/api/jobs", requireSession, async (req, res, next) => {
  try {
    const profile = (req as AuthedRequest).profile;
    const jobs = await prisma.generationJob.findMany({
      where: { profileId: profile.id },
      include: { images: true }
    });
    const images = jobs.flatMap((job) => job.images);
    await deleteGeneratedImageFiles(images);
    const result = await prisma.generationJob.deleteMany({
      where: { profileId: profile.id }
    });
    res.json({ ok: true, deletedCount: result.count });
  } catch (error) {
    next(error);
  }
});

app.delete("/api/jobs/:id", requireSession, async (req, res, next) => {
  try {
    const profile = (req as AuthedRequest).profile;
    const job = await prisma.generationJob.findFirst({
      where: { id: req.params.id, profileId: profile.id },
      include: { images: true }
    });
    if (!job) {
      res.status(404).json({ error: "Job not found." });
      return;
    }
    await deleteGeneratedImageFiles(job.images);
    await prisma.generationJob.delete({ where: { id: job.id } });
    res.json({ ok: true });
  } catch (error) {
    next(error);
  }
});

app.get("/api/templates", requireSession, async (req, res, next) => {
  try {
    const profile = (req as AuthedRequest).profile;
    const templates = await prisma.promptTemplate.findMany({
      where: { profileId: profile.id },
      orderBy: { updatedAt: "desc" }
    });
    res.json({ templates });
  } catch (error) {
    next(error);
  }
});

app.post("/api/templates", requireSession, async (req, res, next) => {
  try {
    const profile = (req as AuthedRequest).profile;
    const input = templateSchema.parse(req.body);
    const template = await prisma.promptTemplate.create({
      data: {
        profileId: profile.id,
        title: input.title,
        prompt: input.prompt,
        params: input.params as Prisma.InputJsonObject
      }
    });
    res.status(201).json({ template });
  } catch (error) {
    next(error);
  }
});

app.delete("/api/templates/:id", requireSession, async (req, res, next) => {
  try {
    const profile = (req as AuthedRequest).profile;
    await prisma.promptTemplate.deleteMany({
      where: { id: req.params.id, profileId: profile.id }
    });
    res.json({ ok: true });
  } catch (error) {
    next(error);
  }
});

app.use((error: unknown, _req: Request, res: Response, _next: NextFunction) => {
  if (error instanceof multer.MulterError) {
    const status = error.code === "LIMIT_FILE_SIZE" || error.code === "LIMIT_FILE_COUNT" ? 413 : 422;
    res.status(status).json({ error: error.message, code: error.code });
    return;
  }

  if (error instanceof Prisma.PrismaClientInitializationError) {
    res.status(503).json({ error: "Database is unavailable. Check DATABASE_URL and PostgreSQL status." });
    return;
  }

  if (error instanceof PrismaClientKnownRequestError) {
    res.status(400).json({ error: error.message });
    return;
  }

  if (error && typeof error === "object" && "issues" in error) {
    res.status(422).json({ error: "Invalid request.", details: (error as { issues: unknown }).issues });
    return;
  }

  const status = typeof error === "object" && error && "status" in error
    ? Number((error as { status: unknown }).status)
    : 500;
  const payload = typeof error === "object" && error && "payload" in error
    ? (error as { payload: unknown }).payload
    : undefined;
  const code = typeof error === "object" && error && "code" in error
    ? String((error as { code: unknown }).code)
    : undefined;
  const message = error instanceof Error ? error.message : extractErrorMessage(payload) ?? "Internal server error.";
  res.status(Number.isFinite(status) ? status : 500).json({ error: message, code, payload });
});

await fs.mkdir(path.join(config.uploadDir, "images"), { recursive: true });
await fs.mkdir(temporaryImageDir, { recursive: true });
await assertSub2apiBillingReady();
startImageRetentionCleanup();
startImageTaskPolling();

app.listen(config.port, () => {
  console.log(`sub2api image workbench API listening on http://localhost:${config.port}`);
});
