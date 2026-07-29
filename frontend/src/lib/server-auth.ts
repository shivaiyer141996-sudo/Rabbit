import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import type { UserRole } from "./types";

const secure = process.env.NODE_ENV === "production";

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresIn: number;
  role: UserRole;
}

export async function setSessionCookies(
  response: NextResponse,
  tokens: TokenResponse,
) {
  response.cookies.set("rabbit_access_token", tokens.accessToken, {
    httpOnly: true,
    secure,
    sameSite: "strict",
    path: "/",
    maxAge: tokens.accessTokenExpiresIn,
  });
  response.cookies.set("rabbit_refresh_token", tokens.refreshToken, {
    httpOnly: true,
    secure,
    sameSite: "strict",
    path: "/",
    maxAge: 60 * 60 * 24 * 7,
  });
  response.cookies.set("rabbit_role", tokens.role, {
    httpOnly: true,
    secure,
    sameSite: "strict",
    path: "/",
    maxAge: 60 * 60 * 24 * 7,
  });
}

export async function getPortalSession() {
  const store = await cookies();
  return {
    accessToken: store.get("rabbit_access_token")?.value,
    refreshToken: store.get("rabbit_refresh_token")?.value,
    role: (store.get("rabbit_role")?.value ?? "ORG_ADMIN") as UserRole,
  };
}

export function clearSessionCookies(response: NextResponse) {
  for (const name of [
    "rabbit_access_token",
    "rabbit_refresh_token",
    "rabbit_selection_token",
    "rabbit_role",
  ]) {
    response.cookies.set(name, "", {
      httpOnly: true,
      secure,
      sameSite: "strict",
      path: "/",
      maxAge: 0,
    });
  }
}
