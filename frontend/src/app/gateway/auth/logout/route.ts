import { NextResponse } from "next/server";
import { clearSessionCookies, getPortalSession } from "@/lib/server-auth";

const backend = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080/api/v1";

export async function POST() {
  const { refreshToken } = await getPortalSession();
  if (refreshToken) {
    await fetch(`${backend}/auth/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    }).catch(() => undefined);
  }
  const response = NextResponse.json({ signedOut: true });
  clearSessionCookies(response);
  return response;
}
