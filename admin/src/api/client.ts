const ADMIN_API_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? "";
const SERVER_URL = (import.meta.env.VITE_SERVER_URL as string | undefined) ?? "";

class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function request<T>(
  baseUrl: string,
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const res = await fetch(`${baseUrl}${path}`, {
    headers: { "Content-Type": "application/json", ...options.headers },
    ...options,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new ApiError(res.status, text || `HTTP ${res.status}`);
  }

  const text = await res.text();
  return text ? (JSON.parse(text) as T) : ({} as T);
}

export const api = {
  get: <T>(path: string) => request<T>(ADMIN_API_URL, path),
  post: <T>(path: string, body: unknown) =>
    request<T>(ADMIN_API_URL, path, { method: "POST", body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(ADMIN_API_URL, path, { method: "DELETE" }),
};

export const serverApi = {
  get: <T>(path: string) => request<T>(SERVER_URL, path),
  post: <T>(path: string, body: unknown) =>
    request<T>(SERVER_URL, path, { method: "POST", body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(SERVER_URL, path, { method: "DELETE" }),
};

export function createClient(baseUrl: string) {
  return {
    get: <T>(path: string) => request<T>(baseUrl, path),
    post: <T>(path: string, body: unknown) =>
      request<T>(baseUrl, path, { method: "POST", body: JSON.stringify(body) }),
    delete: <T>(path: string) => request<T>(baseUrl, path, { method: "DELETE" }),
  };
}

export { ApiError };
