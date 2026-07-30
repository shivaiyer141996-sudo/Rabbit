"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Eye, EyeOff, LoaderCircle } from "lucide-react";
import { FormEvent, useState } from "react";

interface LoginResponse {
  requiresOrganisationSelection: boolean;
  firstLogin?: boolean;
  role?: string;
  organisations: Array<{
    id: string;
    name: string;
    code: string;
    role: string;
  }>;
}

export function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState("admin@demo.rabbit.local");
  const [password, setPassword] = useState("Rabbit@123");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const response = await fetch("/gateway/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(body?.message ?? "Invalid email or password. Please try again.");
      }
      const result = body as LoginResponse;
      if (result.requiresOrganisationSelection) {
        sessionStorage.setItem(
          "rabbit_org_choices",
          JSON.stringify(result.organisations),
        );
        router.push("/select-organisation");
      } else {
        router.push(result.role === "STUDENT" ? "/student/assessments" : "/dashboard");
      }
      router.refresh();
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Rabbit could not sign you in.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit}>
      {error && <div className="form-error" role="alert">{error}</div>}
      <div className="field">
        <label htmlFor="email">Email address</label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />
      </div>
      <div className="field">
        <label htmlFor="password">Password</label>
        <div className="password-wrap">
          <input
            id="password"
            name="password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
          <button
            className="icon-button"
            type="button"
            onClick={() => setShowPassword((current) => !current)}
            aria-label={showPassword ? "Hide password" : "Show password"}
          >
            {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
          </button>
        </div>
      </div>
      <div className="auth-meta">
        <label className="check-row">
          <input type="checkbox" /> Keep me signed in
        </label>
        <Link className="text-link" href="/forgot-password">
          Forgot password?
        </Link>
      </div>
      <button
        className="button button-primary button-full"
        disabled={submitting}
        type="submit"
      >
        {submitting && <LoaderCircle size={17} className="spin" />}
        {submitting ? "Signing in…" : "Sign in"}
      </button>
      <div className="demo-note">
        Demo: use any account listed in the repository README with password{" "}
        <strong>Rabbit@123</strong>.
      </div>
    </form>
  );
}
