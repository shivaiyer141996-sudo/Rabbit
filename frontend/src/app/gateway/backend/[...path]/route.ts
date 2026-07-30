import { cookies } from "next/headers";
import { NextResponse } from "next/server";

const backend = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080/api/v1";
const secureCookies = process.env.SESSION_COOKIE_SECURE === "true";

type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(request: Request, context: RouteContext) {
  const { path } = await context.params;
  const store = await cookies();
  let accessToken = store.get("rabbit_access_token")?.value;
  const refreshToken = store.get("rabbit_refresh_token")?.value;
  const url = new URL(request.url);
  const requestBody =
    request.method === "GET" || request.method === "HEAD"
      ? undefined
      : await request.arrayBuffer();

  async function send(token?: string) {
    const traceId = request.headers.get("x-trace-id");
    const forwardedFor = request.headers.get("x-forwarded-for");
    return fetch(`${backend}/${path.join("/")}${url.search}`, {
      method: request.method,
      headers: {
        "Content-Type": request.headers.get("content-type") ?? "application/json",
        Accept: request.headers.get("accept") ?? "*/*",
        ...(traceId ? { "X-Trace-Id": traceId } : {}),
        ...(forwardedFor ? { "X-Forwarded-For": forwardedFor } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: requestBody,
      cache: "no-store",
    });
  }

  let upstream = await send(accessToken).catch(() => null);
  if (!upstream) {
    return NextResponse.json(
      { message: "Rabbit API is unavailable." },
      { status: 503 },
    );
  }

  if (upstream.status === 401 && refreshToken) {
    const refreshResponse = await fetch(`${backend}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
      cache: "no-store",
    }).catch(() => null);
    if (refreshResponse?.ok) {
      const refreshed = await refreshResponse.json();
      accessToken = refreshed.accessToken;
      store.set("rabbit_access_token", refreshed.accessToken, {
        httpOnly: true,
        secure: secureCookies,
        sameSite: "strict",
        path: "/",
        maxAge: refreshed.accessTokenExpiresIn,
      });
      store.set("rabbit_refresh_token", refreshed.refreshToken, {
        httpOnly: true,
        secure: secureCookies,
        sameSite: "strict",
        path: "/",
        maxAge: 60 * 60 * 24 * 7,
      });
      upstream = await send(accessToken);
    }
  }

  const responseBody =
    upstream.status === 204 ? null : await upstream.arrayBuffer();
  const responseHeaders = new Headers();
  [
    "content-type",
    "content-disposition",
    "content-length",
    "cache-control",
    "x-content-type-options",
    "x-trace-id",
    "x-ratelimit-limit",
    "x-ratelimit-remaining",
    "x-ratelimit-reset",
    "retry-after",
  ].forEach((name) => {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  });
  if (!responseHeaders.has("content-type")) {
    responseHeaders.set("content-type", "application/json");
  }
  return new NextResponse(responseBody, {
    status: upstream.status,
    headers: responseHeaders,
  });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
