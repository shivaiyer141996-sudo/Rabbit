"use client";

import Link from "next/link";
import { CheckCircle2, Eye, EyeOff, LoaderCircle } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { apiErrorMessage, apiFetch } from "@/lib/api";

interface InvitationDetails {
  email: string;
  firstName: string;
  lastName: string;
  organisationName: string;
  role: string;
  expiresAt: string;
}

export function ActivationForm() {
  const [token, setToken] = useState("");
  const [details, setDetails] = useState<InvitationDetails | null>(null);
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [activated, setActivated] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const rawToken = new URLSearchParams(
      window.location.hash.replace(/^#/, ""),
    ).get("token");
    window.history.replaceState(null, "", window.location.pathname);
    const initialize = window.setTimeout(() => {
      if (!rawToken) {
        setError(
          "This activation link is incomplete. Ask your administrator for a new invitation.",
        );
        setLoading(false);
        return;
      }
      setToken(rawToken);
      void apiFetch<InvitationDetails>("/auth/invitations/validate", {
        method: "POST",
        body: JSON.stringify({ token: rawToken }),
      })
        .then(setDetails)
        .catch((requestError) =>
          setError(
            apiErrorMessage(
              requestError,
              "This invitation is invalid, expired, or already used.",
            ),
          ),
        )
        .finally(() => setLoading(false));
    }, 0);
    return () => window.clearTimeout(initialize);
  }, []);

  async function activate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (password !== confirmation) {
      setError("The passwords do not match.");
      return;
    }
    setSubmitting(true);
    try {
      await apiFetch("/auth/invitations/activate", {
        method: "POST",
        body: JSON.stringify({ token, password }),
      });
      setToken("");
      setPassword("");
      setConfirmation("");
      setActivated(true);
    } catch (requestError) {
      setError(
        apiErrorMessage(
          requestError,
          "Rabbit could not activate this invitation.",
        ),
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="activation-state" aria-live="polite">
        <LoaderCircle className="spin" size={22} />
        <span>Checking your secure invitation…</span>
      </div>
    );
  }

  if (activated) {
    return (
      <div className="activation-success" role="status">
        <CheckCircle2 size={34} />
        <h2>Account activated</h2>
        <p>Your password is set. You can now sign in to Rabbit.</p>
        <Link className="button button-primary button-full" href="/login">
          Continue to sign in
        </Link>
      </div>
    );
  }

  if (!details) {
    return (
      <>
        <div className="form-error" role="alert">{error}</div>
        <Link className="button button-ghost button-full" href="/login">
          Back to sign in
        </Link>
      </>
    );
  }

  return (
    <form onSubmit={activate}>
      <div className="invitation-identity">
        <strong>{details.firstName} {details.lastName}</strong>
        <span>{details.email}</span>
        <small>
          {details.organisationName} · {details.role.replaceAll("_", " ")}
        </small>
      </div>
      {error && <div className="form-error" role="alert">{error}</div>}
      <div className="field">
        <label htmlFor="activation-password">Create password</label>
        <div className="password-wrap">
          <input
            autoComplete="new-password"
            id="activation-password"
            maxLength={72}
            minLength={12}
            onChange={(event) => setPassword(event.target.value)}
            required
            type={showPassword ? "text" : "password"}
            value={password}
          />
          <button
            aria-label={showPassword ? "Hide password" : "Show password"}
            className="icon-button"
            onClick={() => setShowPassword((current) => !current)}
            type="button"
          >
            {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
          </button>
        </div>
        <small className="field-hint">
          12–72 characters with uppercase, lowercase, number, and symbol.
        </small>
      </div>
      <div className="field">
        <label htmlFor="activation-confirmation">Confirm password</label>
        <input
          autoComplete="new-password"
          id="activation-confirmation"
          maxLength={72}
          minLength={12}
          onChange={(event) => setConfirmation(event.target.value)}
          required
          type={showPassword ? "text" : "password"}
          value={confirmation}
        />
      </div>
      <button
        className="button button-primary button-full"
        disabled={submitting}
        type="submit"
      >
        {submitting && <LoaderCircle className="spin" size={17} />}
        {submitting ? "Activating…" : "Activate account"}
      </button>
    </form>
  );
}
