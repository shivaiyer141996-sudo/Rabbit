import { NextResponse } from "next/server";
import { setSessionCookies, type TokenResponse } from "@/lib/server-auth";

const backend = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080/api/v1";

export async function POST(request: Request) {
  const body = await request.text();
  const upstream = await fetch(`${backend}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
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

  const response = NextResponse.json({
    requiresOrganisationSelection: payload.requiresOrganisationSelection,
    organisations: payload.organisations ?? [],
  });

  if (payload.requiresOrganisationSelection) {
    response.cookies.set("rabbit_selection_token", payload.selectionToken, {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "strict",
      path: "/",
      maxAge: 300,
    });
  } else {
    await setSessionCookies(response, payload as TokenResponse);
  }
  return response;
}
