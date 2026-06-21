import { z } from "zod";

const maxPixels = 8_294_400;
const sizeMultiple = 16;
const maxReferenceImageBytes = 10 * 1024 * 1024;
const maxReferenceImageBase64Length = Math.ceil(maxReferenceImageBytes / 3) * 4;

function parseSize(value: string): { width: number; height: number; ratio: number } | null {
  const match = value.match(/^(\d+)x(\d+)$/);
  if (!match) return null;

  const width = Number(match[1]);
  const height = Number(match[2]);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return null;

  return { width, height, ratio: width / height };
}

export const bindSchema = z.object({
  baseUrl: z.string().url(),
  apiKey: z.string().min(8)
});

export const generateSchema = z.object({
  prompt: z.string().min(1),
  negativePrompt: z.string().optional(),
  model: z.string().min(1),
  size: z.string().regex(/^\d+x\d+$/).default("1024x1024")
    .refine((value) => {
      const size = parseSize(value);
      return !!size && size.width % sizeMultiple === 0 && size.height % sizeMultiple === 0;
    }, `宽高必须是 ${sizeMultiple} 的倍数`)
    .refine((value) => {
      const size = parseSize(value);
      return !!size && size.ratio >= 1 / 3 && size.ratio <= 3;
    }, "宽高比例不能超过 1:3 或 3:1")
    .refine((value) => {
      const size = parseSize(value);
      return !!size && size.width * size.height <= maxPixels;
    }, `最大像素数不能超过 ${maxPixels.toLocaleString()}`),
  aspectRatio: z.string().optional(),
  customAspectRatio: z.string().optional(),
  quality: z.string().optional(),
  outputFormat: z.enum(["png", "jpeg", "webp"]).default("png"),
  style: z.string().optional(),
  count: z.number().int().min(1).max(10).default(1),
  responseFormat: z.enum(["b64_json", "url"]).default("b64_json"),
  extraParams: z.record(z.unknown()).default({}),
  referenceImages: z.array(z.object({
    name: z.string().min(1),
    mimeType: z.string().regex(/^image\//),
    data: z.string().min(1).max(maxReferenceImageBase64Length)
  })).max(4).default([])
});

export const templateSchema = z.object({
  title: z.string().min(1).max(80),
  prompt: z.string().min(1),
  params: z.record(z.unknown()).default({})
});
