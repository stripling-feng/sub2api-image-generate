type ApiErrorDetail = {
  path?: unknown[];
  message?: string;
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
  if (!response.ok) {
    const details = Array.isArray(json.details)
      ? json.details
        .map((item: ApiErrorDetail) => {
          const path = Array.isArray(item?.path) ? item.path.join(".") : "";
          const message = typeof item?.message === "string" ? item.message : "";
          return [path, message].filter(Boolean).join(": ");
        })
        .filter(Boolean)
        .join("; ")
      : "";
    const message = details || (typeof json.error === "string" ? json.error : `HTTP ${response.status}`);
    throw new Error(message);
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
