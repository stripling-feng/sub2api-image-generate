import { createWriteStream, openAsBlob } from "node:fs";
import fs from "node:fs/promises";
import path from "node:path";
import { once } from "node:events";
import { fileTypeFromBuffer } from "file-type";
import { config } from "./config.js";
import { logEvent } from "./logger.js";

const imageRequestTimeoutMs = Number(process.env.IMAGE_REQUEST_TIMEOUT_MS ?? 900_000);
const asyncImageModel = "cy-img1-gpt-image-2";

export type ImageUpload = {
  name: string;
  mimeType: string;
  data?: string;
  filePath?: string;
};

export type ImageTaskOperation = "generations" | "edits";

export type ImageTaskPayload = {
  id?: string;
  status?: string;
  progress: number;
  urls: string[];
  error?: string;
  raw: unknown;
};
const snowflakeEpoch = 1_704_067_200_000;
let snowflakeSequence = 0n;
let snowflakeLastMs = 0n;

function createSnowflakeId(): string {
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

function createImageFilename(ext: string): string {
  return `tcboys.de_${createSnowflakeId()}.${ext}`;
}

function dateFolder(date = new Date()): string {
  return date.toISOString().slice(0, 10);
}

function imageStoragePaths(jobId: string, filename: string, date = dateFolder()) {
  return {
    dir: path.join(config.uploadDir, "images", date),
    publicUrl: `/img/${date}/${filename}`
  };
}

export function normalizeImagesEndpoint(baseUrl: string): string {
  const trimmed = baseUrl.replace(/\/+$/, "");
  if (trimmed.endsWith("/images/generations")) {
    return trimmed;
  }
  if (trimmed.endsWith("/v1")) {
    return `${trimmed}/images/generations`;
  }
  return `${trimmed}/v1/images/generations`;
}

export function normalizeImageEditsEndpoint(baseUrl: string): string {
  const trimmed = baseUrl.replace(/\/+$/, "");
  if (trimmed.endsWith("/images/edits")) {
    return trimmed;
  }
  if (trimmed.endsWith("/v1")) {
    return `${trimmed}/images/edits`;
  }
  return `${trimmed}/v1/images/edits`;
}

export function normalizeImageTaskEndpoint(baseUrl: string, operation: ImageTaskOperation, taskId: string): string {
  const endpoint = operation === "edits" ? normalizeImageEditsEndpoint(baseUrl) : normalizeImagesEndpoint(baseUrl);
  return `${endpoint}/${encodeURIComponent(taskId)}`;
}

export function normalizeImageContentEndpoint(baseUrl: string, taskId: string): string {
  const generationsEndpoint = normalizeImagesEndpoint(baseUrl);
  const apiRoot = generationsEndpoint.replace(/\/images\/generations$/, "");
  return `${apiRoot}/images/${encodeURIComponent(taskId)}/content`;
}

export function buildAsyncImageRequest(body: Record<string, unknown>, count: number): Record<string, unknown> {
  return {
    model: asyncImageModel,
    prompt: body.prompt,
    size: body.size,
    n: Math.max(1, Math.min(10, Number(count) || 1)),
    async: true,
    stream: false
  };
}

export function normalizeModelsEndpoint(baseUrl: string): string {
  const trimmed = baseUrl.replace(/\/+$/, "");
  if (trimmed.endsWith("/models")) {
    return trimmed;
  }
  if (trimmed.endsWith("/v1")) {
    return `${trimmed}/models`;
  }
  return `${trimmed}/v1/models`;
}

export async function testGatewayConnection(args: {
  baseUrl: string;
  apiKey: string;
}): Promise<{ durationMs: number; models?: string[] }> {
  const startedAt = Date.now();
  const response = await fetch(normalizeModelsEndpoint(args.baseUrl), {
    headers: { Authorization: `Bearer ${args.apiKey}` },
    signal: AbortSignal.timeout(30_000)
  });
  const text = await response.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = { raw: text };
  }

  if (!response.ok) {
    const message = extractErrorMessage(json) ?? `sub2api model check failed with HTTP ${response.status}`;
    throw Object.assign(new Error(message), { status: response.status, payload: json });
  }

  const data = json && typeof json === "object" ? (json as Record<string, unknown>).data : undefined;
  const models = Array.isArray(data)
    ? data.flatMap((item) => item && typeof item === "object" && typeof (item as Record<string, unknown>).id === "string"
      ? [(item as Record<string, string>).id]
      : [])
    : undefined;

  return { durationMs: Date.now() - startedAt, models };
}

export async function callImageGeneration(args: {
  baseUrl: string;
  apiKey: string;
  body: Record<string, unknown>;
  requestId?: string;
}): Promise<{ json: unknown; durationMs: number }> {
  const startedAt = Date.now();
  const endpoint = normalizeImagesEndpoint(args.baseUrl);
  const requestId = args.requestId;
  const bodyText = JSON.stringify(args.body);
  logEvent("sub2api.image.request.start", {
    requestId,
    endpoint,
    model: args.body.model,
    size: args.body.size,
    responseFormat: args.body.response_format,
    outputFormat: args.body.output_format,
    bodyBytes: Buffer.byteLength(bodyText),
    timeoutMs: imageRequestTimeoutMs
  });

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${args.apiKey}`,
      "Content-Type": "application/json"
    },
    body: bodyText,
    signal: AbortSignal.timeout(imageRequestTimeoutMs)
  });
  const headersMs = Date.now() - startedAt;
  logEvent("sub2api.image.response.headers", {
    requestId,
    status: response.status,
    ok: response.ok,
    contentType: response.headers.get("content-type"),
    contentLength: response.headers.get("content-length"),
    headersMs
  });

  const text = await response.text();
  const bodyMs = Date.now() - startedAt - headersMs;
  logEvent("sub2api.image.response.body", {
    requestId,
    status: response.status,
    headersMs,
    bodyMs,
    totalMs: Date.now() - startedAt,
    bytes: Buffer.byteLength(text)
  });

  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = { raw: text };
  }

  if (!response.ok) {
    const message = extractErrorMessage(json) ?? `sub2api request failed with HTTP ${response.status}`;
    throw Object.assign(new Error(message), { status: response.status, payload: json });
  }

  const durationMs = Date.now() - startedAt;
  logEvent("sub2api.image.request.done", { requestId, durationMs });
  return { json, durationMs };
}

export async function callImageEdit(args: {
  baseUrl: string;
  apiKey: string;
  body: Record<string, unknown>;
  images: ImageUpload[];
  mask?: ImageUpload;
  requestId?: string;
}): Promise<{ json: unknown; durationMs: number }> {
  const startedAt = Date.now();
  const form = new FormData();
  const requestId = args.requestId;
  const endpoint = normalizeImageEditsEndpoint(args.baseUrl);
  logEvent("sub2api.edit.request.start", {
    requestId,
    endpoint,
    model: args.body.model,
    size: args.body.size,
    responseFormat: args.body.response_format,
    outputFormat: args.body.output_format,
    imageCount: args.images.length,
    imageBytes: args.images.reduce((total, image) => total + (image.data ? Buffer.byteLength(image.data, "base64") : 0), 0),
    timeoutMs: imageRequestTimeoutMs
  });

  for (const [key, value] of Object.entries(args.body)) {
    if (value !== undefined && value !== null) {
      form.append(key, String(value));
    }
  }

  const imageField = args.images.length === 1 ? "image" : "image[]";
  for (const image of args.images) {
    const blob = image.filePath
      ? await openAsBlob(image.filePath, { type: image.mimeType })
      : new Blob([Buffer.from(image.data ?? "", "base64")], { type: image.mimeType });
    form.append(imageField, blob, image.name);
  }

  if (args.mask) {
    const maskBlob = args.mask.filePath
      ? await openAsBlob(args.mask.filePath, { type: args.mask.mimeType })
      : new Blob([Buffer.from(args.mask.data ?? "", "base64")], { type: args.mask.mimeType });
    form.append("mask", maskBlob, args.mask.name);
  }

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${args.apiKey}`
    },
    body: form,
    signal: AbortSignal.timeout(imageRequestTimeoutMs)
  });
  const headersMs = Date.now() - startedAt;
  logEvent("sub2api.edit.response.headers", {
    requestId,
    status: response.status,
    ok: response.ok,
    contentType: response.headers.get("content-type"),
    contentLength: response.headers.get("content-length"),
    headersMs
  });

  const text = await response.text();
  const bodyMs = Date.now() - startedAt - headersMs;
  logEvent("sub2api.edit.response.body", {
    requestId,
    status: response.status,
    headersMs,
    bodyMs,
    totalMs: Date.now() - startedAt,
    bytes: Buffer.byteLength(text)
  });
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = { raw: text };
  }

  if (!response.ok) {
    const message = extractErrorMessage(json) ?? `sub2api edit request failed with HTTP ${response.status}`;
    throw Object.assign(new Error(message), { status: response.status, payload: json });
  }

  const durationMs = Date.now() - startedAt;
  logEvent("sub2api.edit.request.done", { requestId, durationMs });
  return { json, durationMs };
}

function parseProgress(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) return Math.max(0, Math.min(100, Math.round(value)));
  if (typeof value === "string") {
    const parsed = Number(value.replace("%", "").trim());
    if (Number.isFinite(parsed)) return Math.max(0, Math.min(100, Math.round(parsed)));
  }
  return 0;
}

export function parseImageTaskPayload(payload: unknown): ImageTaskPayload {
  const obj = payload && typeof payload === "object" ? payload as Record<string, unknown> : {};
  return {
    id: typeof obj.id === "string" ? obj.id : undefined,
    status: typeof obj.status === "string" ? obj.status.toLowerCase() : undefined,
    progress: parseProgress(obj.progress),
    urls: extractImageItems(payload).flatMap((item) => item.url ? [item.url] : []),
    error: extractErrorMessage(payload),
    raw: payload
  };
}

export async function callImageTaskStatus(args: {
  baseUrl: string;
  apiKey: string;
  operation: ImageTaskOperation;
  taskId: string;
  requestId?: string;
}): Promise<{ task: ImageTaskPayload; durationMs: number }> {
  const startedAt = Date.now();
  const endpoint = normalizeImageTaskEndpoint(args.baseUrl, args.operation, args.taskId);
  const response = await fetch(endpoint, {
    headers: { Authorization: `Bearer ${args.apiKey}` },
    signal: AbortSignal.timeout(config.imageTaskPollTimeoutMs)
  });
  const text = await response.text();
  let json: unknown;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    json = { raw: text };
  }
  if (!response.ok) {
    const message = extractErrorMessage(json) ?? `Image task query failed with HTTP ${response.status}`;
    throw Object.assign(new Error(message), { status: response.status, payload: json });
  }
  const durationMs = Date.now() - startedAt;
  logEvent("sub2api.image.task.status", {
    requestId: args.requestId,
    taskId: args.taskId,
    operation: args.operation,
    status: response.status,
    durationMs
  });
  return { task: parseImageTaskPayload(json), durationMs };
}

export async function callImageTaskContent(args: {
  baseUrl: string;
  apiKey: string;
  taskId: string;
}): Promise<{ buffer: Buffer; mimeType: string }> {
  const endpoint = normalizeImageContentEndpoint(args.baseUrl, args.taskId);
  const response = await fetch(endpoint, {
    headers: { Authorization: `Bearer ${args.apiKey}` },
    signal: AbortSignal.timeout(imageRequestTimeoutMs)
  });
  if (!response.ok) {
    throw new Error(`Image task content download failed with HTTP ${response.status}`);
  }
  return {
    buffer: Buffer.from(await response.arrayBuffer()),
    mimeType: response.headers.get("content-type") ?? "image/png"
  };
}

export function extractErrorMessage(payload: unknown): string | undefined {
  if (!payload || typeof payload !== "object") return undefined;
  const obj = payload as Record<string, unknown>;
  const error = obj.error;
  if (typeof error === "string") return error;
  if (error && typeof error === "object" && typeof (error as Record<string, unknown>).message === "string") {
    return (error as Record<string, string>).message;
  }
  if (typeof obj.message === "string") return obj.message;
  return undefined;
}

export function extractImageItems(payload: unknown): Array<{ b64?: string; url?: string }> {
  if (!payload || typeof payload !== "object") return [];
  const data = (payload as Record<string, unknown>).data;
  if (!Array.isArray(data)) return [];

  const items = data.flatMap((item) => {
    if (!item || typeof item !== "object") return [];
    const obj = item as Record<string, unknown>;
    const b64 = typeof obj.b64_json === "string" ? obj.b64_json : undefined;
    const url = typeof obj.url === "string" ? obj.url : undefined;
    return b64 || url ? [{ b64, url }] : [];
  });
  logEvent("sub2api.image.extract", {
    itemCount: items.length,
    b64Count: items.filter((item) => item.b64).length,
    urlCount: items.filter((item) => item.url).length
  });
  return items;
}

function readUInt24LE(buffer: Buffer, offset: number): number {
  return buffer[offset] + (buffer[offset + 1] << 8) + (buffer[offset + 2] << 16);
}

export function readImageDimensions(buffer: Buffer): { width?: number; height?: number } {
  if (buffer.length >= 24 && buffer.toString("ascii", 1, 4) === "PNG") {
    return {
      width: buffer.readUInt32BE(16),
      height: buffer.readUInt32BE(20)
    };
  }

  if (buffer.length >= 10 && (buffer.toString("ascii", 0, 6) === "GIF87a" || buffer.toString("ascii", 0, 6) === "GIF89a")) {
    return { width: buffer.readUInt16LE(6), height: buffer.readUInt16LE(8) };
  }

  if (buffer.length >= 26 && buffer.toString("ascii", 0, 2) === "BM") {
    return { width: Math.abs(buffer.readInt32LE(18)), height: Math.abs(buffer.readInt32LE(22)) };
  }

  if (buffer.length >= 10 && buffer[0] === 0xff && buffer[1] === 0xd8) {
    let offset = 2;
    while (offset + 9 < buffer.length) {
      if (buffer[offset] !== 0xff) {
        offset += 1;
        continue;
      }

      const marker = buffer[offset + 1];
      const length = buffer.readUInt16BE(offset + 2);
      if (length < 2) break;

      const isStartOfFrame = marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker);
      if (isStartOfFrame) {
        return {
          height: buffer.readUInt16BE(offset + 5),
          width: buffer.readUInt16BE(offset + 7)
        };
      }

      offset += 2 + length;
    }
  }

  if (buffer.length >= 30 && buffer.toString("ascii", 0, 4) === "RIFF" && buffer.toString("ascii", 8, 12) === "WEBP") {
    const chunk = buffer.toString("ascii", 12, 16);
    if (chunk === "VP8X" && buffer.length >= 30) {
      return {
        width: readUInt24LE(buffer, 24) + 1,
        height: readUInt24LE(buffer, 27) + 1
      };
    }

    if (chunk === "VP8 " && buffer.length >= 30) {
      return {
        width: buffer.readUInt16LE(26) & 0x3fff,
        height: buffer.readUInt16LE(28) & 0x3fff
      };
    }

    if (chunk === "VP8L" && buffer.length >= 25) {
      const bits = buffer.readUInt32LE(21);
      return {
        width: (bits & 0x3fff) + 1,
        height: ((bits >> 14) & 0x3fff) + 1
      };
    }
  }

  return {};
}

export async function persistImageFromBase64(jobId: string, b64: string): Promise<{
  filePath: string;
  publicUrl: string;
  mimeType: string;
  sizeBytes: number;
  width?: number;
  height?: number;
}> {
  const startedAt = Date.now();
  logEvent("image.persist.base64.start", {
    jobId,
    base64Chars: b64.length,
    estimatedBytes: Math.floor((b64.length * 3) / 4)
  });
  const decodeStartedAt = Date.now();
  const buffer = Buffer.from(b64, "base64");
  const decodeMs = Date.now() - decodeStartedAt;
  const inspectStartedAt = Date.now();
  const type = await fileTypeFromBuffer(buffer);
  const ext = type?.ext ?? "png";
  const mimeType = type?.mime ?? "image/png";
  const dimensions = readImageDimensions(buffer);
  const inspectMs = Date.now() - inspectStartedAt;
  const filename = createImageFilename(ext);
  const { dir, publicUrl } = imageStoragePaths(jobId, filename);
  const writeStartedAt = Date.now();
  await fs.mkdir(dir, { recursive: true });
  const filePath = path.join(dir, filename);
  await fs.writeFile(filePath, buffer);
  const writeMs = Date.now() - writeStartedAt;
  const durationMs = Date.now() - startedAt;
  logEvent("image.persist.base64.done", {
    jobId,
    filePath,
    publicUrl,
    mimeType,
    bytes: buffer.byteLength,
    width: dimensions.width,
    height: dimensions.height,
    decodeMs,
    inspectMs,
    writeMs,
    durationMs
  });

  return {
    filePath,
    publicUrl,
    mimeType,
    sizeBytes: buffer.byteLength,
    ...dimensions
  };
}

export async function persistImageFromBuffer(jobId: string, buffer: Buffer, declaredMimeType?: string): Promise<{
  filePath: string;
  publicUrl: string;
  mimeType: string;
  sizeBytes: number;
  width?: number;
  height?: number;
}> {
  const type = await fileTypeFromBuffer(buffer);
  const mimeType = type?.mime ?? declaredMimeType ?? "image/png";
  const ext = type?.ext ?? mimeType.split("/")[1]?.split(";")[0] ?? "png";
  const dimensions = readImageDimensions(buffer);
  const filename = createImageFilename(ext);
  const { dir, publicUrl } = imageStoragePaths(jobId, filename);
  await fs.mkdir(dir, { recursive: true });
  const filePath = path.join(dir, filename);
  await fs.writeFile(filePath, buffer);
  return { filePath, publicUrl, mimeType, sizeBytes: buffer.byteLength, ...dimensions };
}

export function persistImageUrlReference(url: string): {
  filePath: string;
  publicUrl: string;
  mimeType: string;
  sizeBytes: number;
  width?: number;
  height?: number;
} {
  logEvent("image.persist.url_reference", {
    publicUrl: url,
    urlLength: url.length
  });
  return {
    filePath: url,
    publicUrl: url,
    mimeType: "image/png",
    sizeBytes: 0
  };
}

export async function persistImageFromUrl(jobId: string, url: string): Promise<{
  filePath: string;
  publicUrl: string;
  mimeType: string;
  sizeBytes: number;
  width?: number;
  height?: number;
}> {
  const startedAt = Date.now();
  logEvent("image.persist.url_download.start", { jobId, url, urlLength: url.length });
  const response = await fetch(url, { signal: AbortSignal.timeout(imageRequestTimeoutMs) });
  const headersMs = Date.now() - startedAt;
  logEvent("image.persist.url_download.headers", {
    jobId,
    status: response.status,
    ok: response.ok,
    contentType: response.headers.get("content-type"),
    contentLength: response.headers.get("content-length"),
    headersMs
  });
  if (!response.ok) {
    logEvent("image.persist.url_download.failed", {
      jobId,
      status: response.status,
      headersMs,
      durationMs: Date.now() - startedAt
    });
    throw new Error(`Failed to download image URL with HTTP ${response.status}`);
  }
  if (!response.body) {
    throw new Error("Image URL response body is empty.");
  }

  const date = dateFolder();
  const tempFilename = `${createSnowflakeId()}.download`;
  const tempPaths = imageStoragePaths(jobId, tempFilename, date);
  await fs.mkdir(tempPaths.dir, { recursive: true });
  const tempFilePath = path.join(tempPaths.dir, tempFilename);

  const bodyStartedAt = Date.now();
  const maxInspectBytes = 512 * 1024;
  const inspectChunks: Buffer[] = [];
  let inspectBytes = 0;
  let sizeBytes = 0;

  const reader = response.body.getReader();
  const writer = createWriteStream(tempFilePath);
  let writeError: Error | undefined;
  writer.on("error", (error) => {
    writeError = error;
  });

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (writeError) throw writeError;

      const chunk = Buffer.from(value);
      sizeBytes += chunk.byteLength;
      if (inspectBytes < maxInspectBytes) {
        const remaining = maxInspectBytes - inspectBytes;
        const slice = chunk.subarray(0, Math.min(remaining, chunk.byteLength));
        inspectChunks.push(slice);
        inspectBytes += slice.byteLength;
      }

      if (!writer.write(chunk)) {
        await once(writer, "drain");
      }
    }
  } finally {
    writer.end();
  }

  if (writeError) throw writeError;
  await once(writer, "finish");
  const bodyMs = Date.now() - bodyStartedAt;
  const inspectBuffer = Buffer.concat(inspectChunks, inspectBytes);
  const inspectStartedAt = Date.now();
  const type = await fileTypeFromBuffer(inspectBuffer);
  const contentType = response.headers.get("content-type") ?? type?.mime ?? "image/png";
  const ext = type?.ext ?? contentType.split("/")[1]?.split(";")[0] ?? "png";
  const dimensions = readImageDimensions(inspectBuffer);
  const inspectMs = Date.now() - inspectStartedAt;
  const filename = createImageFilename(ext);
  const { dir, publicUrl } = imageStoragePaths(jobId, filename);
  const writeStartedAt = Date.now();
  const filePath = path.join(dir, filename);
  await fs.rename(tempFilePath, filePath).catch(async (error: NodeJS.ErrnoException) => {
    if (error.code !== "EXDEV") throw error;
    await fs.copyFile(tempFilePath, filePath);
    await fs.unlink(tempFilePath).catch(() => undefined);
  });
  const writeMs = Date.now() - writeStartedAt;
  const durationMs = Date.now() - startedAt;
  logEvent("image.persist.url_download.done", {
    jobId,
    filePath,
    publicUrl,
    mimeType: contentType,
    bytes: sizeBytes,
    width: dimensions.width,
    height: dimensions.height,
    headersMs,
    bodyMs,
    inspectMs,
    writeMs,
    durationMs
  });

  return {
    filePath,
    publicUrl,
    mimeType: contentType,
    sizeBytes,
    ...dimensions
  };
}
