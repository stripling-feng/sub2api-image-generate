export type Profile = {
  id: string;
  baseUrl: string;
  keyHashPreview: string;
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
  responseFormat: "b64_json" | "url";
  params: Record<string, unknown>;
  status: "PENDING" | "SUCCEEDED" | "FAILED";
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
