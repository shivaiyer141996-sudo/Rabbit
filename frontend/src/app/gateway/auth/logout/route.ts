import { NextResponse } from "next/server";
import { clearSessionCookies, getPortalSession } from "@/lib/server-auth";

const backend = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080/api/v1";

export async function POST(request: Request) {
  const { refreshToken } = await getPortalSession();
  if (refreshToken) {
    await fetch(`${backend}/auth/logout`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(request.headers.get("x-forwarded-for")
          ? { "X-Forwarded-For": request.headers.get("x-forwarded-for")! }
          : {}),
        ...(request.headers.get("x-trace-id")
          ? { "X-Trace-Id": request.headers.get("x-trace-id")! }
          : {}),
      },
      body: JSON.stringify({ refreshToken }),
    }).catch(() => undefined);
  }
  const response = NextResponse.json({ signedOut: true });
  clearSessionCookies(response);
  return response;
}
