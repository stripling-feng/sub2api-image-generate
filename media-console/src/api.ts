type ApiErrorDetail = {
  path?: unknown[];
  message?: string;
};

type ApiResponseEnvelope = {
  code?: unknown;
  message?: unknown;
  data?: unknown;
};

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const apiKey = localStorage.getItem("apiKey") ?? "";
  const hasFormBody = options.body instanceof FormData;
  const response = await fetch(path, {
    credentials: "include",
    headers: {
      ...(hasFormBody ? {} : { "Content-Type": "application/json" }),
      ...(apiKey ? { "X-API-Key": apiKey } : {}),
      ...(options.headers ?? {})
    },
    ...options
  });

  const json = await response.json().catch(() => ({}));
  const envelope = json as ApiResponseEnvelope;
  const apiCode = typeof envelope.code === "number" ? envelope.code : undefined;
  if (!response.ok) {
    const detailItems = Array.isArray(json.details) ? json.details : (Array.isArray(envelope.data) ? envelope.data : []);
    const details = detailItems.length
      ? detailItems
        .map((item: ApiErrorDetail) => {
          const path = Array.isArray(item?.path) ? item.path.join(".") : "";
          const message = typeof item?.message === "string" ? item.message : "";
          return [path, message].filter(Boolean).join(": ");
        })
        .filter(Boolean)
        .join("; ")
      : "";
    const message = details
      || (typeof envelope.message === "string" ? envelope.message : "")
      || (typeof json.error === "string" ? json.error : `HTTP ${response.status}`);
    throw new Error(message);
  }
  if (apiCode !== undefined) {
    if (apiCode !== 200) throw new Error(typeof envelope.message === "string" ? envelope.message : "请求失败");
    return envelope.data as T;
  }
  return json as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, {
    method: "POST",
    body: body === undefined ? undefined : JSON.stringify(body)
  }),
  postForm: <T>(path: string, body: FormData) => request<T>(path, { method: "POST", body }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" })
};
