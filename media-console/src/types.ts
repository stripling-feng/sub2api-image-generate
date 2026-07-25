export type Profile = {
  id: string;
  baseUrl: string;
  keyHashPreview: string;
  balanceUsd?: string;
  availableBalanceUsd?: string;
};

export type GeneratedImage = {
  id: string;
  jobId: string;
  publicUrl: string;
  mimeType: string;
  width?: number | null;
  height?: number | null;
  sizeBytes: number;
  createdAt: string;
};

export type GenerationJob = {
  id: string;
  prompt: string;
  negativePrompt?: string | null;
  model: string;
  size: string;
  quality?: string | null;
  style?: string | null;
  count: number;
  responseFormat: "url";
  params: Record<string, unknown>;
  status: "PENDING" | "SUCCEEDED" | "FAILED";
  progress?: number;
  upstreamStatus?: string | null;
  billingStatus?: string | null;
  billingAmount?: string | number | null;
  errorMessage?: string | null;
  durationMs?: number | null;
  createdAt: string;
  images: GeneratedImage[];
};

export type PromptTemplate = {
  id: string;
  title: string;
  prompt: string;
  params: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
};

export type ImageModelParameter = {
  key: string;
  label: string;
  type: "select" | "text" | "size" | "number" | "boolean";
  default?: unknown;
  placeholder?: string;
  options?: Array<{ label: string; value: string }>;
};

export type ImageModelConfig = {
  id: number;
  model: string;
  name: string;
  provider: string;
  unitPriceUsd: number | string;
  maxCount: number;
  maxReferenceImages: number;
  supportsMask: boolean;
  parameters: ImageModelParameter[];
  defaults: Record<string, unknown>;
};

export type VideoModelParameter = {
  key: string;
  label: string;
  type: "select" | "boolean";
  default?: string | number | boolean;
  options?: Array<string | number | { label: string; value: string | number }>;
};

export type VideoModelConfig = {
  id: number;
  model: string;
  name: string;
  provider: string;
  billingMode: "PER_REQUEST" | "PER_SECOND";
  unitPriceUsd: number | string;
  maxCount: number;
  maxReferenceImages: number;
  parameters: VideoModelParameter[];
  defaults: Record<string, unknown>;
};

export type GeneratedVideo = {
  id: string;
  jobId: string;
  publicUrl: string;
  mimeType: string;
  createdAt: string;
};

export type VideoGenerationJob = {
  id: string;
  requestId: string;
  prompt: string;
  model: string;
  duration: number;
  aspectRatio: string;
  resolution: string;
  generateAudio: boolean;
  params: Record<string, unknown>;
  status: "PENDING" | "SUCCEEDED" | "FAILED";
  progress: number;
  upstreamStatus?: string | null;
  billingStatus?: string | null;
  billingAmount?: string | number | null;
  errorMessage?: string | null;
  durationMs?: number | null;
  createdAt: string;
  videos: GeneratedVideo[];
};
