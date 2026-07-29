"use client";

import { Check, LoaderCircle } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

interface OrganisationChoice {
  id: string;
  name: string;
  code: string;
  role: string;
}

const fallbackChoices: OrganisationChoice[] = [
  {
    id: "11111111-1111-1111-1111-111111111111",
    name: "Rabbit Demo Academy",
    code: "DEMO",
    role: "ORG_ADMIN",
  },
];

export function OrganisationSelector() {
  const router = useRouter();
  const [choices, setChoices] = useState(fallbackChoices);
  const [selected, setSelected] = useState(fallbackChoices[0].id);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const stored = sessionStorage.getItem("rabbit_org_choices");
    if (!stored) return;
    try {
      const parsed = JSON.parse(stored) as OrganisationChoice[];
      if (parsed.length) {
        queueMicrotask(() => {
          setChoices(parsed);
          setSelected(parsed[0].id);
        });
      }
    } catch {
      sessionStorage.removeItem("rabbit_org_choices");
    }
  }, []);

  async function continueToWorkspace() {
    setSubmitting(true);
    setError("");
    try {
      const response = await fetch("/gateway/auth/select-organisation", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ organisationId: selected }),
      });
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(body?.message ?? "The workspace could not be selected.");
      }
      sessionStorage.removeItem("rabbit_org_choices");
      router.replace("/dashboard");
      router.refresh();
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "The workspace could not be selected.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      {error && <div className="form-error" role="alert">{error}</div>}
      <div className="org-list">
        {choices.map((choice) => (
          <button
            className={`org-choice ${selected === choice.id ? "selected" : ""}`}
            key={choice.id}
            onClick={() => setSelected(choice.id)}
            type="button"
          >
            <span className="org-avatar">{choice.code.slice(0, 2)}</span>
            <div>
              <strong>{choice.name}</strong>
              <small>{choice.code} · {choice.role.replaceAll("_", " ")}</small>
            </div>
            {selected === choice.id && <Check size={18} color="#5936c8" />}
          </button>
        ))}
      </div>
      <button
        className="button button-primary button-full"
        disabled={submitting}
        onClick={continueToWorkspace}
        style={{ marginTop: 20 }}
      >
        {submitting && <LoaderCircle size={17} />}
        {submitting ? "Opening workspace…" : "Continue"}
      </button>
    </>
  );
}
