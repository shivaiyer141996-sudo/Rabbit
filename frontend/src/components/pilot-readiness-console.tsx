"use client";

import {
  AlertTriangle,
  Ban,
  CheckCircle2,
  CircleDashed,
  ExternalLink,
  FileCheck2,
  History,
  LockKeyhole,
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
  PilotDecisionOutcome,
  PilotReadiness,
} from "@/lib/live-types";

interface CheckDraft {
  status: PilotCheckStatus;
  testerName: string;
  evidenceUrl: string;
  defectId: string;
  notes: string;
}

interface DecisionDraft {
  outcome: PilotDecisionOutcome | "";
  releaseVersion: string;
  releaseCommit: string;
  institutionName: string;
  authorisedBy: string;
  authoriserTitle: string;
  uatLead: string;
  technicalOwner: string;
  supportContact: string;
  monitoringOwner: string;
  backupRestoreOwner: string;
  incidentOwner: string;
  rollbackOwner: string;
  dataPrivacyOwner: string;
  handoverRecipient: string;
  evidenceReference: string;
  evidenceSha256: string;
  knownIssueCount: string;
  knownIssuesReference: string;
  decisionReason: string;
  retestBy: string;
  localDataConfirmed: boolean;
  localOnlyConfirmed: boolean;
  ownershipAccepted: boolean;
  scopeFreezeAccepted: boolean;
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
  const [decisionConfirmed, setDecisionConfirmed] = useState(false);
  const [decision, setDecision] = useState<DecisionDraft>({
    outcome: "",
    releaseVersion: "1.0.0",
    releaseCommit: "",
    institutionName: "",
    authorisedBy: "",
    authoriserTitle: "",
    uatLead: "",
    technicalOwner: "",
    supportContact: "",
    monitoringOwner: "",
    backupRestoreOwner: "",
    incidentOwner: "",
    rollbackOwner: "",
    dataPrivacyOwner: "",
    handoverRecipient: "",
    evidenceReference: "",
    evidenceSha256: "",
    knownIssueCount: "0",
    knownIssuesReference: "",
    decisionReason: "",
    retestBy: "",
    localDataConfirmed: false,
    localOnlyConfirmed: false,
    ownershipAccepted: false,
    scopeFreezeAccepted: false,
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

  function isWebEvidence(value?: string) {
    return value?.startsWith("http://") || value?.startsWith("https://");
  }

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

  function updateDecision(update: Partial<DecisionDraft>) {
    setDecision((current) => ({ ...current, ...update }));
  }

  async function submitDecision(event: FormEvent) {
    event.preventDefault();
    if (!decision.outcome || !decisionConfirmed) {
      setError("Select and explicitly confirm the institutional decision.");
      return;
    }
    setBusy("decision");
    setError("");
    setMessage("");
    try {
      const value = await apiFetch<PilotReadiness>("/pilot-readiness/decisions", {
        method: "POST",
        body: JSON.stringify({
          ...decision,
          knownIssueCount: Number(decision.knownIssueCount),
          retestBy: decision.outcome === "CONDITIONAL_RETEST" && decision.retestBy
            ? new Date(decision.retestBy).toISOString()
            : null,
        }),
      });
      hydrate(value);
      setDecisionConfirmed(false);
      setMessage(
        decision.outcome === "GO"
          ? "Go decision recorded. The evidence register is now locked."
          : `${decision.outcome.replaceAll("_", " ")} decision recorded; the register remains open for governed follow-up.`,
      );
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "The pilot decision could not be recorded."));
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
        eyebrow="Milestone 5 · Institutional pilot"
        title="Pilot readiness & sign-off"
        description="Record institution-owned UAT, rehearsal, live-assessment, reconciliation, incident, and final Release 1.0 evidence."
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
              ? "Go decision locked"
              : readiness.latestDecision?.outcome === "CONDITIONAL_RETEST"
                ? "Conditional retest recorded"
                : readiness.latestDecision?.outcome === "NO_GO"
                  ? "No-Go recorded"
              : readiness.mandatoryChecksPassed
                ? "Ready for final institutional decision"
                : "Pilot evidence is incomplete"}
          </strong>
          <span>
            {readiness.signedOff
              ? `Release ${readiness.latestDecision?.releaseVersion} authorised by ${readiness.latestDecision?.authorisedBy}.`
              : readiness.latestDecision?.outcome === "CONDITIONAL_RETEST"
                ? `Retest due ${new Date(readiness.latestDecision.retestBy ?? "").toLocaleString()}. Expansion remains blocked.`
                : readiness.latestDecision?.outcome === "NO_GO"
                  ? "Release expansion is blocked. Read the immutable decision record below."
                  : "Release expansion remains blocked until a named institution records a Go decision."}
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
                          placeholder="urn:rabbit-evidence:… or approved local URL"
                          type="text"
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
                      {check.evidenceUrl && isWebEvidence(check.evidenceUrl) && (
                        <a
                          className="button button-ghost"
                          href={check.evidenceUrl}
                          rel="noreferrer"
                          target="_blank"
                        >
                          <ExternalLink size={14} /> Open evidence
                        </a>
                      )}
                      {check.evidenceUrl && !isWebEvidence(check.evidenceUrl) && (
                        <span className="button button-ghost" title={check.evidenceUrl}>
                          <FileCheck2 size={14} /> Local evidence recorded
                        </span>
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

      {readiness.decisions.length > 0 && (
        <section className="form-section pilot-decision-history">
          <div className="panel-header">
            <div>
              <h2>Immutable decision history</h2>
              <p>Conditional Retest and No-Go preserve the evidence state without locking follow-up work. Go is final.</p>
            </div>
            <History size={22} />
          </div>
          <div className="pilot-decision-list">
            {readiness.decisions.map((item) => (
              <article className={`pilot-decision outcome-${item.outcome.toLowerCase().replaceAll("_", "-")}`} key={item.id}>
                <div className="pilot-decision-heading">
                  <strong>{item.outcome.replaceAll("_", " ")}</strong>
                  <span>{new Date(item.decidedAt).toLocaleString()}</span>
                </div>
                <p>{item.decisionReason}</p>
                <dl className="pilot-decision-facts">
                  <div><dt>Release</dt><dd>{item.releaseVersion} · {item.releaseCommit.slice(0, 12)}</dd></div>
                  <div><dt>Institution</dt><dd>{item.institutionName}</dd></div>
                  <div><dt>Authorised by</dt><dd>{item.authorisedBy}, {item.authoriserTitle}</dd></div>
                  <div><dt>Known issues</dt><dd>{item.knownIssueCount}</dd></div>
                  {item.retestBy && <div><dt>Retest by</dt><dd>{new Date(item.retestBy).toLocaleString()}</dd></div>}
                </dl>
                <span className="pilot-local-reference" title={item.evidenceReference}>
                  <FileCheck2 size={14} /> Local evidence · SHA-256 {item.evidenceSha256.slice(0, 12)}…
                </span>
              </article>
            ))}
          </div>
        </section>
      )}

      {!readiness.signedOff && (
        <form className="form-section pilot-signoff" onSubmit={submitDecision}>
          <div className="panel-header">
            <div>
              <h2>Final approval and handover</h2>
              <p>Prepare the checksummed M5.5 bundle first. This action creates an immutable institutional decision; only Go locks the register.</p>
            </div>
            <LockKeyhole size={22} />
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="decision-outcome">Decision</label>
              <select
                id="decision-outcome"
                onChange={(event) => {
                  const outcome = event.target.value as PilotDecisionOutcome | "";
                  updateDecision({ outcome, retestBy: outcome === "CONDITIONAL_RETEST" ? decision.retestBy : "" });
                }}
                required
                value={decision.outcome}
              >
                <option value="">Select decision</option>
                <option disabled={!readiness.mandatoryChecksPassed} value="GO">Go</option>
                <option value="CONDITIONAL_RETEST">Conditional Retest</option>
                <option value="NO_GO">No-Go</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="release-version">Release version</label>
              <input
                id="release-version"
                onChange={(event) => updateDecision({ releaseVersion: event.target.value })}
                required
                value={decision.releaseVersion}
              />
            </div>
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="release-commit">Exact release commit</label>
              <input
                id="release-commit"
                minLength={7}
                onChange={(event) => updateDecision({ releaseCommit: event.target.value })}
                pattern="[0-9a-fA-F]{7,40}"
                placeholder="Git commit from the local release"
                required
                value={decision.releaseCommit}
              />
            </div>
            <div className="field">
              <label htmlFor="institution-name">Institution name</label>
              <input
                id="institution-name"
                onChange={(event) => updateDecision({ institutionName: event.target.value })}
                required
                value={decision.institutionName}
              />
            </div>
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="authorised-by">Authorised by</label>
              <input
                id="authorised-by"
                onChange={(event) => updateDecision({ authorisedBy: event.target.value })}
                required
                value={decision.authorisedBy}
              />
            </div>
            <div className="field">
              <label htmlFor="authoriser-title">Authoriser title</label>
              <input
                id="authoriser-title"
                onChange={(event) => updateDecision({ authoriserTitle: event.target.value })}
                required
                value={decision.authoriserTitle}
              />
            </div>
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="uat-lead">Institution UAT lead</label>
              <input
                id="uat-lead"
                onChange={(event) => updateDecision({ uatLead: event.target.value })}
                required
                value={decision.uatLead}
              />
            </div>
            <div className="field">
              <label htmlFor="handover-recipient">Handover recipient</label>
              <input
                id="handover-recipient"
                onChange={(event) => updateDecision({ handoverRecipient: event.target.value })}
                required
                value={decision.handoverRecipient}
              />
            </div>
          </div>

          <div className="panel-header pilot-subsection-heading">
            <div><h3>Accepted operating ownership</h3><p>Use named people, not teams or automated agents.</p></div>
          </div>
          <div className="field-row">
            <div className="field"><label htmlFor="technical-owner">Technical/release owner</label><input id="technical-owner" onChange={(event) => updateDecision({ technicalOwner: event.target.value })} required value={decision.technicalOwner} /></div>
            <div className="field"><label htmlFor="support-contact">Support contact/channel</label><input id="support-contact" onChange={(event) => updateDecision({ supportContact: event.target.value })} required value={decision.supportContact} /></div>
            <div className="field"><label htmlFor="monitoring-owner">Monitoring owner</label><input id="monitoring-owner" onChange={(event) => updateDecision({ monitoringOwner: event.target.value })} required value={decision.monitoringOwner} /></div>
            <div className="field"><label htmlFor="backup-owner">Backup/restore owner</label><input id="backup-owner" onChange={(event) => updateDecision({ backupRestoreOwner: event.target.value })} required value={decision.backupRestoreOwner} /></div>
            <div className="field"><label htmlFor="incident-owner">Incident owner</label><input id="incident-owner" onChange={(event) => updateDecision({ incidentOwner: event.target.value })} required value={decision.incidentOwner} /></div>
            <div className="field"><label htmlFor="rollback-owner">Rollback owner</label><input id="rollback-owner" onChange={(event) => updateDecision({ rollbackOwner: event.target.value })} required value={decision.rollbackOwner} /></div>
            <div className="field"><label htmlFor="privacy-owner">Data/privacy owner</label><input id="privacy-owner" onChange={(event) => updateDecision({ dataPrivacyOwner: event.target.value })} required value={decision.dataPrivacyOwner} /></div>
          </div>

          <div className="panel-header pilot-subsection-heading">
            <div><h3>Local evidence and known issues</h3><p>References must come from the protected, checksummed M5.5 bundle on approved local media.</p></div>
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="handover-evidence">Handover evidence reference</label>
              <input id="handover-evidence" onChange={(event) => updateDecision({ evidenceReference: event.target.value })} placeholder="urn:rabbit-evidence:m5-5:…" required value={decision.evidenceReference} />
            </div>
            <div className="field">
              <label htmlFor="handover-sha">Evidence SHA-256</label>
              <input id="handover-sha" maxLength={64} minLength={64} onChange={(event) => updateDecision({ evidenceSha256: event.target.value })} pattern="[0-9a-fA-F]{64}" required value={decision.evidenceSha256} />
            </div>
            <div className="field">
              <label htmlFor="known-issue-count">Known S3/S4 issues</label>
              <input id="known-issue-count" min={0} onChange={(event) => updateDecision({ knownIssueCount: event.target.value })} required type="number" value={decision.knownIssueCount} />
            </div>
            <div className="field">
              <label htmlFor="known-issues-reference">Known-issue evidence reference</label>
              <input id="known-issues-reference" onChange={(event) => updateDecision({ knownIssuesReference: event.target.value })} placeholder="Required when count is above zero" required={Number(decision.knownIssueCount) > 0} value={decision.knownIssuesReference} />
            </div>
          </div>
          {decision.outcome === "CONDITIONAL_RETEST" && (
            <div className="field">
              <label htmlFor="retest-by">Retest deadline</label>
              <input id="retest-by" onChange={(event) => updateDecision({ retestBy: event.target.value })} required type="datetime-local" value={decision.retestBy} />
            </div>
          )}
          <div className="field">
            <label htmlFor="decision-reason">Decision reason and restrictions</label>
            <textarea
              id="decision-reason"
              onChange={(event) => updateDecision({ decisionReason: event.target.value })}
              required
              value={decision.decisionReason}
            />
          </div>

          <div className="pilot-attestations">
            <label className="switch-row"><span><strong>Approved local media only</strong><small>PostgreSQL, MinIO, evidence, and backups remain on approved local devices.</small></span><input checked={decision.localDataConfirmed} onChange={(event) => updateDecision({ localDataConfirmed: event.target.checked })} type="checkbox" /></label>
            <label className="switch-row"><span><strong>Zero-cost local infrastructure</strong><small>No cloud hosting, managed service, public endpoint, or public tunnel was introduced.</small></span><input checked={decision.localOnlyConfirmed} onChange={(event) => updateDecision({ localOnlyConfirmed: event.target.checked })} type="checkbox" /></label>
            <label className="switch-row"><span><strong>Operating ownership accepted</strong><small>Support, monitoring, backup/restore, incident, rollback, and privacy owners accepted handover.</small></span><input checked={decision.ownershipAccepted} onChange={(event) => updateDecision({ ownershipAccepted: event.target.checked })} type="checkbox" /></label>
            <label className="switch-row"><span><strong>Release 1.0 scope remains frozen</strong><small>This decision does not approve new features or production-scale expansion.</small></span><input checked={decision.scopeFreezeAccepted} onChange={(event) => updateDecision({ scopeFreezeAccepted: event.target.checked })} type="checkbox" /></label>
          </div>

          <label className="pilot-final-confirmation">
            <input checked={decisionConfirmed} onChange={(event) => setDecisionConfirmed(event.target.checked)} required type="checkbox" />
            <span><strong>I am recording the institution&apos;s deliberate decision.</strong><small>The record is immutable. Go also permanently locks this evidence register.</small></span>
          </label>
          {decision.outcome === "GO" && !readiness.mandatoryChecksPassed && (
            <div className="form-error" role="alert"><AlertTriangle size={16} /> Go is unavailable until every mandatory check passes.</div>
          )}
          <button className="button button-primary" disabled={busy === "decision" || !decision.outcome || !decisionConfirmed} type="submit">
            <FileCheck2 size={15} /> {busy === "decision" ? "Recording…" : "Record immutable decision"}
          </button>
        </form>
      )}
    </div>
  );
}
