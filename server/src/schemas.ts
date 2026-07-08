import { z } from "zod";

const maxPixels = 8_294_400;
const sizeMultiple = 16;
const maxReferenceImageBytes = 10 * 1024 * 1024;
const maxReferenceImageBase64Length = Math.ceil(maxReferenceImageBytes / 3) * 4;
const modelSizeTiers: Record<string, Set<string>> = {
  "gpt-image-2": new Set(["auto", "1k"]),
  "gpt-image-2-4k": new Set(["auto", "2k", "4k"])
};

function parseSize(value: string): { width: number; height: number; ratio: number } | null {
  const match = value.match(/^(\d+)x(\d+)$/);
  if (!match) return null;

  const width = Number(match[1]);
  const height = Number(match[2]);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return null;

  return { width, height, ratio: width / height };
}

function parseAspectRatio(value: string): { width: number; height: number; ratio: number } | null {
  const match = value.match(/^(\d+(?:\.\d+)?)\s*:\s*(\d+(?:\.\d+)?)$/);
  if (!match) return null;

  const width = Number(match[1]);
  const height = Number(match[2]);
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) return null;

  return { width, height, ratio: width / height };
}

function isRatioSizeModel(model: string) {
  return model === "gpt-image-2";
}

export const bindSchema = z.object({
  baseUrl: z.string().url(),
  apiKey: z.string().min(8)
});

export const generateSchema = z.object({
  prompt: z.string().min(1),
  negativePrompt: z.string().optional(),
  model: z.string().min(1),
  size: z.string().default("auto"),
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
}).superRefine((value, ctx) => {
  if (value.size !== "auto") {
    if (isRatioSizeModel(value.model)) {
      const ratio = parseAspectRatio(value.size);
      if (!ratio || ratio.ratio < 1 / 3 || ratio.ratio > 3) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["size"],
          message: `${value.model} size must be an aspect ratio between 1:3 and 3:1`
        });
      }
    } else {
      const size = parseSize(value.size);
      if (!size || size.width % sizeMultiple !== 0 || size.height % sizeMultiple !== 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["size"],
          message: `Image width and height must be multiples of ${sizeMultiple}`
        });
      } else {
        if (size.ratio < 1 / 3 || size.ratio > 3) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["size"],
            message: "Image aspect ratio must be between 1:3 and 3:1"
          });
        }
        if (size.width * size.height > maxPixels) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["size"],
            message: `Image pixel count must not exceed ${maxPixels.toLocaleString()}`
          });
        }
      }
    }
  }

  const sizeTier = typeof value.extraParams.size_tier === "string" ? value.extraParams.size_tier : "auto";
  const allowedTiers = modelSizeTiers[value.model];
  if (allowedTiers && !allowedTiers.has(sizeTier)) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["extraParams", "size_tier"],
      message: `${value.model} only supports image size: ${[...allowedTiers].join(", ")}`
    });
  }
});

export const templateSchema = z.object({
  title: z.string().min(1).max(80),
  prompt: z.string().min(1),
  params: z.record(z.unknown()).default({})
});
