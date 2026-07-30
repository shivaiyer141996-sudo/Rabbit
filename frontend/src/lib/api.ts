const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "/gateway/backend";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
  }
}

export function apiErrorMessage(
  error: unknown,
  fallback = "Rabbit could not complete the request.",
) {
  return error instanceof ApiError || error instanceof Error
    ? error.message
    : fallback;
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
    credentials: "same-origin",
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(
      body?.message ?? "Rabbit could not complete the request.",
      response.status,
      body?.code,
    );
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
