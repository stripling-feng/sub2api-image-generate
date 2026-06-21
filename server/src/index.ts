import fs from "node:fs/promises";
import path from "node:path";
import cookieParser from "cookie-parser";
import cors from "cors";
import express, { type Request, type Response, type NextFunction } from "express";
import { Prisma, type GenerationJob } from "@prisma/client";
import { PrismaClientKnownRequestError } from "@prisma/client/runtime/library";
import { nanoid } from "nanoid";
import { config } from "./config.js";
import { prisma } from "./db.js";
import { decryptSecret, encryptSecret, hashSecret } from "./crypto.js";
import { bindSchema, generateSchema, templateSchema } from "./schemas.js";
import { createSession, requireSession, sessionCookie, type AuthedRequest } from "./session.js";
import {
  callImageGeneration,
  callImageEdit,
  extractErrorMessage,
  extractImageItems,
  persistImageFromBase64,
  persistImageFromUrl
} from "./sub2api.js";

const app = express();

app.use(cors({ origin: config.clientOrigin, credentials: true }));
app.use(express.json({ limit: "25mb" }));
app.use(cookieParser());
app.use("/uploads", express.static(config.uploadDir));

app.get("/api/health", (_req, res) => {
  res.json({ ok: true });
});

app.post("/api/session/bind", async (req, res, next) => {
  try {
    const input = bindSchema.parse(req.body);
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

    res.json({
      profile: {
        id: profile.id,
        baseUrl: profile.baseUrl,
        keyHashPreview: `${profile.keyHash.slice(0, 8)}...`
      }
    });
  } catch (error) {
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
  referenceImages: Array<{ name: string; mimeType: string; data: string }>;
}) {
  const startedAt = Date.now();

  try {
    const result = args.referenceImages.length
      ? await callImageEdit({
        baseUrl: args.profile.baseUrl,
        apiKey: decryptSecret(args.profile.encryptedKey),
        body: args.params,
        images: args.referenceImages
      })
      : await callImageGeneration({
        baseUrl: args.profile.baseUrl,
        apiKey: decryptSecret(args.profile.encryptedKey),
        body: args.params
      });
    const items = extractImageItems(result.json);
    if (items.length === 0) {
      throw Object.assign(new Error("sub2api returned no images."), { payload: result.json });
    }

    for (const item of items) {
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
      }
    }

    await prisma.generationJob.update({
      where: { id: args.job.id },
      data: { status: "SUCCEEDED", durationMs: result.durationMs }
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown generation error.";
    await prisma.generationJob.update({
      where: { id: args.job.id },
      data: { status: "FAILED", errorMessage: message, durationMs: Date.now() - startedAt }
    }).catch(() => undefined);
  }
}

app.post("/api/images/generate", requireSession, async (req, res, next) => {
  try {
    const profile = (req as AuthedRequest).profile;
    const input = generateSchema.parse(req.body);
    const requestId = nanoid(16);
    const baseParams = {
      ...input.extraParams,
      request_id: requestId,
      model: input.model,
      prompt: input.negativePrompt ? `${input.prompt}\n\nNegative prompt: ${input.negativePrompt}` : input.prompt,
      size: input.size,
      n: 1,
      response_format: input.responseFormat,
      output_format: input.outputFormat,
      ...(input.aspectRatio ? { aspect_ratio: input.aspectRatio } : {}),
      ...(input.customAspectRatio ? { custom_aspect_ratio: input.customAspectRatio } : {}),
      ...(input.quality ? { quality: input.quality } : {}),
      ...(input.style ? { style: input.style } : {})
    };

    void Promise.all(Array.from({ length: input.count }, async (_item, index) => {
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
            ...baseParams,
            request_index: index + 1,
            request_total: input.count
          } as Prisma.InputJsonObject,
          status: "PENDING"
        }
      });
      const params = job.params as Record<string, unknown>;
      void runGenerationJob({ profile, job, params, referenceImages: input.referenceImages });
    })).catch((error) => {
      console.error("Failed to enqueue generation jobs", error);
    });

    res.status(202).json({ requestId, count: input.count });
  } catch (error) {
    next(error);
  }
});

app.get("/api/images/history", requireSession, async (req, res, next) => {
  try {
    const profile = (req as AuthedRequest).profile;
    const jobs = await prisma.generationJob.findMany({
      where: { profileId: profile.id },
      orderBy: { createdAt: "desc" },
      take: 80,
      include: { images: true }
    });
    res.json({ jobs });
  } catch (error) {
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
    await fs.unlink(image.filePath).catch(() => undefined);
    await prisma.generatedImage.delete({ where: { id: image.id } });
    res.json({ ok: true });
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
    for (const image of job.images) {
      await fs.unlink(image.filePath).catch(() => undefined);
    }
    await fs.rm(path.join(config.uploadDir, "images", job.id), { recursive: true, force: true }).catch(() => undefined);
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
  const message = error instanceof Error ? error.message : extractErrorMessage(payload) ?? "Internal server error.";
  res.status(Number.isFinite(status) ? status : 500).json({ error: message, payload });
});

await fs.mkdir(path.join(config.uploadDir, "images"), { recursive: true });

app.listen(config.port, () => {
  console.log(`sub2api image workbench API listening on http://localhost:${config.port}`);
});
