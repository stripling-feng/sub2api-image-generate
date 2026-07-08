import fs from "node:fs/promises";
import path from "node:path";
import { config } from "./config.js";
import { prisma } from "./db.js";
import { errorDetails, logEvent } from "./logger.js";

const imageRetentionMs = 24 * 60 * 60 * 1000;
const imageCleanupIntervalMs = Number(process.env.IMAGE_CLEANUP_INTERVAL_MS ?? 60 * 60 * 1000);
let cleanupTimer: NodeJS.Timeout | undefined;
let cleanupRunning = false;

function isLocalUploadPath(filePath: string) {
  const uploadRoot = path.resolve(config.uploadDir);
  const resolvedPath = path.resolve(filePath);
  return resolvedPath === uploadRoot || resolvedPath.startsWith(`${uploadRoot}${path.sep}`);
}

async function removeEmptyParents(filePath: string) {
  const uploadImagesRoot = path.resolve(config.uploadDir, "images");
  let currentDir = path.dirname(path.resolve(filePath));

  while (currentDir.startsWith(`${uploadImagesRoot}${path.sep}`)) {
    try {
      await fs.rm(currentDir, { recursive: false, force: true });
    } catch {
      return;
    }
    currentDir = path.dirname(currentDir);
  }
}

export async function cleanupExpiredImages(now = new Date()) {
  if (cleanupRunning) {
    logEvent("image.retention.cleanup.skipped", { reason: "already_running" });
    return;
  }

  cleanupRunning = true;
  const startedAt = Date.now();
  const expiresBefore = new Date(now.getTime() - imageRetentionMs);

  try {
    const images = await prisma.generatedImage.findMany({
      where: { createdAt: { lt: expiresBefore } },
      select: { id: true, filePath: true, publicUrl: true, createdAt: true }
    });

    let localFileDeleteCount = 0;
    let remoteReferenceCount = 0;

    for (const image of images) {
      if (isLocalUploadPath(image.filePath)) {
        await fs.unlink(image.filePath).then(() => {
          localFileDeleteCount += 1;
        }).catch((error: NodeJS.ErrnoException) => {
          if (error.code !== "ENOENT") {
            logEvent("image.retention.file_delete.error", {
              imageId: image.id,
              filePath: image.filePath,
              ...errorDetails(error)
            });
          }
        });
        await removeEmptyParents(image.filePath);
      } else {
        remoteReferenceCount += 1;
      }
    }

    if (images.length) {
      await prisma.generatedImage.deleteMany({
        where: { id: { in: images.map((image) => image.id) } }
      });
    }

    logEvent("image.retention.cleanup.done", {
      expiresBefore: expiresBefore.toISOString(),
      expiredImageCount: images.length,
      localFileDeleteCount,
      remoteReferenceCount,
      durationMs: Date.now() - startedAt
    });
  } catch (error) {
    logEvent("image.retention.cleanup.error", {
      expiresBefore: expiresBefore.toISOString(),
      durationMs: Date.now() - startedAt,
      ...errorDetails(error)
    });
  } finally {
    cleanupRunning = false;
  }
}

export function startImageRetentionCleanup() {
  if (cleanupTimer) return;

  void cleanupExpiredImages();
  cleanupTimer = setInterval(() => {
    void cleanupExpiredImages();
  }, imageCleanupIntervalMs);
  cleanupTimer.unref();

  logEvent("image.retention.cleanup.started", {
    retentionHours: imageRetentionMs / 60 / 60 / 1000,
    intervalMs: imageCleanupIntervalMs
  });
}
