import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { setSessionCookies, type TokenResponse } from "@/lib/server-auth";

const backend = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080/api/v1";

export async function POST(request: Request) {
  const store = await cookies();
  const selectionToken = store.get("rabbit_selection_token")?.value;
  if (!selectionToken) {
    return NextResponse.json(
      { message: "Your organisation selection has expired. Please sign in again." },
      { status: 401 },
    );
  }

  const requestBody = await request.json();
  const forwardedFor = request.headers.get("x-forwarded-for");
  const traceId = request.headers.get("x-trace-id");
  const upstream = await fetch(`${backend}/auth/select-organisation`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(forwardedFor ? { "X-Forwarded-For": forwardedFor } : {}),
      ...(traceId ? { "X-Trace-Id": traceId } : {}),
    },
    body: JSON.stringify({ ...requestBody, selectionToken }),
    cache: "no-store",
  }).catch(() => null);

  if (!upstream) {
    return NextResponse.json(
      { message: "Rabbit API is unavailable. Start the backend and try again." },
      { status: 503 },
    );
  }
  const payload = await upstream.json().catch(() => ({}));
  if (!upstream.ok) {
    return NextResponse.json(payload, { status: upstream.status });
  }

  const response = NextResponse.json({ selected: true });
  response.cookies.delete("rabbit_selection_token");
  await setSessionCookies(response, payload as TokenResponse);
  return response;
}
