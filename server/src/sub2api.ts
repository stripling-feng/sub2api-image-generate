import fs from "node:fs/promises";
import path from "node:path";
import { fileTypeFromBuffer } from "file-type";
import { config } from "./config.js";

const imageRequestTimeoutMs = 300_000;
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
}): Promise<{ json: unknown; durationMs: number }> {
  const startedAt = Date.now();
  const response = await fetch(normalizeImagesEndpoint(args.baseUrl), {
    method: "POST",
    headers: {
      Authorization: `Bearer ${args.apiKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(args.body),
    signal: AbortSignal.timeout(imageRequestTimeoutMs)
  });

  const text = await response.text();
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

  return { json, durationMs: Date.now() - startedAt };
}

export async function callImageEdit(args: {
  baseUrl: string;
  apiKey: string;
  body: Record<string, unknown>;
  images: Array<{ name: string; mimeType: string; data: string }>;
}): Promise<{ json: unknown; durationMs: number }> {
  const startedAt = Date.now();
  const form = new FormData();

  for (const [key, value] of Object.entries(args.body)) {
    if (value !== undefined && value !== null) {
      form.append(key, String(value));
    }
  }

  for (const image of args.images) {
    const buffer = Buffer.from(image.data, "base64");
    const blob = new Blob([buffer], { type: image.mimeType });
    form.append("image", blob, image.name);
  }

  const response = await fetch(normalizeImageEditsEndpoint(args.baseUrl), {
    method: "POST",
    headers: {
      Authorization: `Bearer ${args.apiKey}`
    },
    body: form,
    signal: AbortSignal.timeout(imageRequestTimeoutMs)
  });

  const text = await response.text();
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

  return { json, durationMs: Date.now() - startedAt };
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

  return data.flatMap((item) => {
    if (!item || typeof item !== "object") return [];
    const obj = item as Record<string, unknown>;
    return [{
      b64: typeof obj.b64_json === "string" ? obj.b64_json : undefined,
      url: typeof obj.url === "string" ? obj.url : undefined
    }];
  });
}

function readUInt24LE(buffer: Buffer, offset: number): number {
  return buffer[offset] + (buffer[offset + 1] << 8) + (buffer[offset + 2] << 16);
}

function readImageDimensions(buffer: Buffer): { width?: number; height?: number } {
  if (buffer.length >= 24 && buffer.toString("ascii", 1, 4) === "PNG") {
    return {
      width: buffer.readUInt32BE(16),
      height: buffer.readUInt32BE(20)
    };
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
  const buffer = Buffer.from(b64, "base64");
  const type = await fileTypeFromBuffer(buffer);
  const ext = type?.ext ?? "png";
  const mimeType = type?.mime ?? "image/png";
  const dimensions = readImageDimensions(buffer);
  const dir = path.join(config.uploadDir, "images", jobId);
  await fs.mkdir(dir, { recursive: true });
  const filename = createImageFilename(ext);
  const filePath = path.join(dir, filename);
  await fs.writeFile(filePath, buffer);

  return {
    filePath,
    publicUrl: `/uploads/images/${jobId}/${filename}`,
    mimeType,
    sizeBytes: buffer.byteLength,
    ...dimensions
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
  const response = await fetch(url, { signal: AbortSignal.timeout(imageRequestTimeoutMs) });
  if (!response.ok) {
    throw new Error(`Failed to download image URL with HTTP ${response.status}`);
  }
  const arrayBuffer = await response.arrayBuffer();
  const buffer = Buffer.from(arrayBuffer);
  const type = await fileTypeFromBuffer(buffer);
  const contentType = response.headers.get("content-type") ?? type?.mime ?? "image/png";
  const ext = type?.ext ?? contentType.split("/")[1]?.split(";")[0] ?? "png";
  const dimensions = readImageDimensions(buffer);
  const dir = path.join(config.uploadDir, "images", jobId);
  await fs.mkdir(dir, { recursive: true });
  const filename = createImageFilename(ext);
  const filePath = path.join(dir, filename);
  await fs.writeFile(filePath, buffer);

  return {
    filePath,
    publicUrl: `/uploads/images/${jobId}/${filename}`,
    mimeType: contentType,
    sizeBytes: buffer.byteLength,
    ...dimensions
  };
}
