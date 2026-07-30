"use client";

import {
  Ban,
  CheckCircle2,
  CircleDashed,
  ExternalLink,
  FileCheck2,
  Save,
  ShieldCheck,
  XCircle,
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type {
  PilotCheckStatus,
  PilotReadiness,
} from "@/lib/live-types";

interface CheckDraft {
  status: PilotCheckStatus;
  testerName: string;
  evidenceUrl: string;
  defectId: string;
  notes: string;
}

const statusIcon = {
  NOT_RUN: CircleDashed,
  PASS: CheckCircle2,
  FAIL: XCircle,
  BLOCKED: Ban,
};

export function PilotReadinessConsole() {
  const [readiness, setReadiness] = useState<PilotReadiness | null>(null);
  const [drafts, setDrafts] = useState<Record<string, CheckDraft>>({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [signOff, setSignOff] = useState({
    releaseVersion: "1.0.0",
    authorisedBy: "",
    authoriserTitle: "",
    supportContact: "",
    rollbackOwner: "",
    notes: "",
  });

  const hydrate = useCallback((value: PilotReadiness) => {
    setReadiness(value);
    setDrafts(
      Object.fromEntries(
        value.checks.map((check) => [
          check.key,
          {
            status: check.status,
            testerName: check.testerName ?? "",
            evidenceUrl: check.evidenceUrl ?? "",
            defectId: check.defectId ?? "",
            notes: check.notes ?? "",
          },
        ]),
      ),
    );
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      hydrate(await apiFetch<PilotReadiness>("/pilot-readiness"));
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Pilot readiness could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, [hydrate]);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  const categories = useMemo(
    () => [...new Set(readiness?.checks.map((check) => check.category) ?? [])],
    [readiness],
  );

  function updateDraft(key: string, update: Partial<CheckDraft>) {
    setDrafts((current) => ({
      ...current,
      [key]: { ...current[key], ...update },
    }));
  }

  async function saveCheck(key: string) {
    const draft = drafts[key];
    setBusy(key);
    setError("");
    setMessage("");
    try {
      const value = await apiFetch<PilotReadiness>(
        `/pilot-readiness/checks/${key}`,
        {
          method: "PUT",
          body: JSON.stringify({
            ...draft,
            executedAt: draft.status === "NOT_RUN" ? null : new Date().toISOString(),
          }),
        },
      );
      hydrate(value);
      setMessage(`${key.replaceAll("_", " ")} evidence saved.`);
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Pilot evidence could not be saved."));
    } finally {
      setBusy("");
    }
  }

  async function submitSignOff(event: FormEvent) {
    event.preventDefault();
    setBusy("sign-off");
    setError("");
    setMessage("");
    try {
      const value = await apiFetch<PilotReadiness>("/pilot-readiness/sign-off", {
        method: "POST",
        body: JSON.stringify(signOff),
      });
      hydrate(value);
      setMessage("Institutional pilot sign-off has been locked and audited.");
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Pilot could not be signed off."));
    } finally {
      setBusy("");
    }
  }

  if (loading) return <div className="page"><LoadingState label="Loading pilot evidence…" /></div>;
  if (!readiness) {
    return <div className="page"><ErrorState message={error} retry={() => void load()} /></div>;
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Milestone 4 · Controlled pilot"
        title="Pilot readiness & sign-off"
        description="Record institution-owned UAT evidence, defects, operating ownership, and final Release 1.0 authorisation."
      />
      {message && <div className="success-banner">{message}</div>}
      {error && <div className="form-error" role="alert">{error}</div>}

      <section className="metrics-grid pilot-metrics">
        {[
          ["Passed", readiness.passedChecks, "SUCCESS"],
          ["Failed", readiness.failedChecks, "DANGER"],
          ["Blocked", readiness.blockedChecks, "WARNING"],
          ["Not run", readiness.notRunChecks, "NEUTRAL"],
        ].map(([label, value, tone]) => (
          <article className={`metric-card tone-${String(tone).toLowerCase()}`} key={String(label)}>
            <span className="metric-label">{label}</span>
            <strong>{value}</strong>
            <small>of {readiness.totalChecks} controlled-pilot checks</small>
          </article>
        ))}
      </section>

      <div className={`pilot-gate ${readiness.signedOff ? "signed" : readiness.mandatoryChecksPassed ? "ready" : ""}`}>
        <ShieldCheck size={22} />
        <div>
          <strong>
            {readiness.signedOff
              ? "Institutional sign-off complete"
              : readiness.mandatoryChecksPassed
                ? "Mandatory evidence complete"
                : "Pilot evidence is incomplete"}
          </strong>
          <span>
            {readiness.signedOff
              ? `Release ${readiness.signOff?.releaseVersion} authorised by ${readiness.signOff?.authorisedBy}.`
              : "Production expansion remains blocked until every mandatory row passes."}
          </span>
        </div>
      </div>

      {categories.map((category) => (
        <section className="pilot-category" key={category}>
          <div className="panel-header">
            <div>
              <h2>{category}</h2>
              <p>Evidence is tenant-scoped and each update writes an audit event.</p>
            </div>
          </div>
          <div className="pilot-check-list">
            {readiness.checks
              .filter((check) => check.category === category)
              .map((check) => {
                const draft = drafts[check.key];
                const Icon = statusIcon[check.status];
                return (
                  <article className={`pilot-check status-${check.status.toLowerCase()}`} key={check.key}>
                    <div className="pilot-check-heading">
                      <span className="pilot-check-icon"><Icon size={18} /></span>
                      <div>
                        <strong>{check.label}</strong>
                        <span>
                          {check.key.replaceAll("_", " ")}
                          {check.mandatory && " · Mandatory"}
                        </span>
                      </div>
                    </div>
                    <div className="pilot-check-fields">
                      <div className="field">
                        <label htmlFor={`${check.key}-status`}>Result</label>
                        <select
                          disabled={readiness.signedOff}
                          id={`${check.key}-status`}
                          onChange={(event) =>
                            updateDraft(check.key, {
                              status: event.target.value as PilotCheckStatus,
                            })
                          }
                          value={draft.status}
                        >
                          <option value="NOT_RUN">Not run</option>
                          <option value="PASS">Pass</option>
                          <option value="FAIL">Fail</option>
                          <option value="BLOCKED">Blocked</option>
                        </select>
                      </div>
                      <div className="field">
                        <label htmlFor={`${check.key}-tester`}>Tester</label>
                        <input
                          disabled={readiness.signedOff}
                          id={`${check.key}-tester`}
                          onChange={(event) =>
                            updateDraft(check.key, { testerName: event.target.value })
                          }
                          placeholder="Name and team"
                          value={draft.testerName}
                        />
                      </div>
                      <div className="field">
                        <label htmlFor={`${check.key}-evidence`}>Evidence link</label>
                        <input
                          disabled={readiness.signedOff}
                          id={`${check.key}-evidence`}
                          onChange={(event) =>
                            updateDraft(check.key, { evidenceUrl: event.target.value })
                          }
                          placeholder="https://…"
                          type="url"
                          value={draft.evidenceUrl}
                        />
                      </div>
                      <div className="field">
                        <label htmlFor={`${check.key}-defect`}>Defect ID</label>
                        <input
                          disabled={readiness.signedOff}
                          id={`${check.key}-defect`}
                          onChange={(event) =>
                            updateDraft(check.key, { defectId: event.target.value })
                          }
                          placeholder="Required for a failure"
                          value={draft.defectId}
                        />
                      </div>
                    </div>
                    <div className="pilot-check-footer">
                      <div className="field pilot-notes">
                        <label htmlFor={`${check.key}-notes`}>Notes</label>
                        <input
                          disabled={readiness.signedOff}
                          id={`${check.key}-notes`}
                          onChange={(event) =>
                            updateDraft(check.key, { notes: event.target.value })
                          }
                          placeholder="Finding, owner, or due date"
                          value={draft.notes}
                        />
                      </div>
                      {check.evidenceUrl && (
                        <a
                          className="button button-ghost"
                          href={check.evidenceUrl}
                          rel="noreferrer"
                          target="_blank"
                        >
                          <ExternalLink size={14} /> Open evidence
                        </a>
                      )}
                      {!readiness.signedOff && (
                        <button
                          className="button button-primary"
                          disabled={busy === check.key}
                          onClick={() => void saveCheck(check.key)}
                          type="button"
                        >
                          <Save size={14} /> {busy === check.key ? "Saving…" : "Save evidence"}
                        </button>
                      )}
                    </div>
                  </article>
                );
              })}
          </div>
        </section>
      ))}

      {!readiness.signedOff && readiness.mandatoryChecksPassed && (
        <form className="form-section pilot-signoff" onSubmit={submitSignOff}>
          <div className="panel-header">
            <div>
              <h2>Institutional sign-off</h2>
              <p>This final action locks the evidence register and writes an immutable audit event.</p>
            </div>
            <FileCheck2 size={22} />
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="release-version">Release version</label>
              <input
                id="release-version"
                onChange={(event) =>
                  setSignOff((current) => ({ ...current, releaseVersion: event.target.value }))
                }
                required
                value={signOff.releaseVersion}
              />
            </div>
            <div className="field">
              <label htmlFor="authorised-by">Authorised by</label>
              <input
                id="authorised-by"
                onChange={(event) =>
                  setSignOff((current) => ({ ...current, authorisedBy: event.target.value }))
                }
                required
                value={signOff.authorisedBy}
              />
            </div>
            <div className="field">
              <label htmlFor="authoriser-title">Authoriser title</label>
              <input
                id="authoriser-title"
                onChange={(event) =>
                  setSignOff((current) => ({ ...current, authoriserTitle: event.target.value }))
                }
                required
                value={signOff.authoriserTitle}
              />
            </div>
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="support-contact">Pilot support contact</label>
              <input
                id="support-contact"
                onChange={(event) =>
                  setSignOff((current) => ({ ...current, supportContact: event.target.value }))
                }
                required
                value={signOff.supportContact}
              />
            </div>
            <div className="field">
              <label htmlFor="rollback-owner">Rollback owner</label>
              <input
                id="rollback-owner"
                onChange={(event) =>
                  setSignOff((current) => ({ ...current, rollbackOwner: event.target.value }))
                }
                required
                value={signOff.rollbackOwner}
              />
            </div>
          </div>
          <div className="field">
            <label htmlFor="signoff-notes">Authorisation notes</label>
            <textarea
              id="signoff-notes"
              onChange={(event) =>
                setSignOff((current) => ({ ...current, notes: event.target.value }))
              }
              value={signOff.notes}
            />
          </div>
          <button className="button button-primary" disabled={busy === "sign-off"} type="submit">
            <FileCheck2 size={15} /> {busy === "sign-off" ? "Signing off…" : "Authorise controlled pilot"}
          </button>
        </form>
      )}
    </div>
  );
}
