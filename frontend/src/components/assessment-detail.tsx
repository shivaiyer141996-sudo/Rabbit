"use client";

import Link from "next/link";
import {
  ArrowLeft,
  Activity,
  CalendarClock,
  CheckCircle2,
  Send,
  UploadCloud,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import {
  subjectName,
  type AcademicCatalog,
  type ApiAssessment,
} from "@/lib/live-types";

function localInput(value?: string) {
  if (!value) return "";
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 16);
}

export function AssessmentDetail({ assessmentId }: { assessmentId: string }) {
  const [assessment, setAssessment] = useState<ApiAssessment | null>(null);
  const [catalog, setCatalog] = useState<AcademicCatalog | null>(null);
  const [startAt, setStartAt] = useState("");
  const [endAt, setEndAt] = useState("");
  const [sectionIds, setSectionIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [row, nextCatalog] = await Promise.all([
        apiFetch<ApiAssessment>(`/assessments/${assessmentId}`),
        apiFetch<AcademicCatalog>("/academic-catalog"),
      ]);
      setAssessment(row);
      setCatalog(nextCatalog);
      setStartAt(localInput(row.startAt));
      setEndAt(localInput(row.endAt));
      setSectionIds(row.eligibleSectionIds);
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Assessment details could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, [assessmentId]);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  const sectionNames = useMemo(
    () =>
      catalog?.sections
        .filter((section) => sectionIds.includes(section.id))
        .map((section) => section.name)
        .join(", ") ?? "",
    [catalog, sectionIds],
  );

  async function transition(path: string, success: string) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const next = await apiFetch<ApiAssessment>(path, { method: "POST" });
      setAssessment(next);
      setMessage(success);
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Lifecycle action could not be completed."));
    } finally {
      setBusy(false);
    }
  }

  async function schedule() {
    if (!startAt || !endAt || !sectionIds.length) {
      setError("Choose a valid start, end, and at least one eligible section.");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const next = await apiFetch<ApiAssessment>(
        `/assessments/${assessmentId}/schedule`,
        {
          method: "POST",
          body: JSON.stringify({
            startAt: new Date(startAt).toISOString(),
            endAt: new Date(endAt).toISOString(),
            eligibleSectionIds: sectionIds,
          }),
        },
      );
      setAssessment(next);
      setMessage("Assessment schedule saved for eligible students.");
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Schedule could not be saved."));
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <div className="page"><LoadingState label="Loading assessment lifecycle…" /></div>;
  }
  if (!assessment || !catalog) {
    return <div className="page"><ErrorState message={error} retry={() => void load()} /></div>;
  }

  return (
    <div className="page">
      <Link className="button button-ghost" href="/assessments">
        <ArrowLeft size={15} /> Back to assessments
      </Link>
      <PageHeader
        eyebrow={`${assessment.code} · Live lifecycle`}
        title={assessment.title}
        description={`${assessment.type.replaceAll("_", " ")} · ${subjectName(catalog, assessment.subjectId)}`}
        actions={
          <>
            {assessment.startAt && (
              <Link className="button button-secondary" href={`/assessments/${assessment.id}/monitor`}>
                <Activity size={15} /> Monitor attempts
              </Link>
            )}
            {assessment.status === "DRAFT" && (
              <button
                className="button button-primary"
                disabled={busy}
                onClick={() =>
                  void transition(
                    `/assessments/${assessment.id}/submit`,
                    "Assessment submitted to the review queue.",
                  )
                }
                type="button"
              >
                <Send size={15} /> Submit for review
              </button>
            )}
            {assessment.status === "APPROVED" && (
              <button
                className="button button-primary"
                disabled={busy}
                onClick={() =>
                  void transition(
                    `/assessments/${assessment.id}/publish`,
                    "Assessment published. Add its delivery window below.",
                  )
                }
                type="button"
              >
                <UploadCloud size={15} /> Publish assessment
              </button>
            )}
          </>
        }
      />
      {message && <div className="success-banner">{message}</div>}
      {error && <div className="form-error" role="alert">{error}</div>}

      <section className="metrics-grid">
        {[
          ["Questions", assessment.questionCount],
          ["Total marks", assessment.totalMarks],
          ["Duration", `${assessment.durationMinutes}m`],
          ["Attempts", assessment.attemptsAllowed],
        ].map(([label, value]) => (
          <article className="metric-card" key={String(label)}>
            <span className="metric-label">{label}</span>
            <strong>{value}</strong>
            <small>Persisted configuration</small>
          </article>
        ))}
      </section>

      <div className="content-grid">
        <section className="panel">
          <div className="panel-header">
            <div><h2>Governance state</h2><p>Creator and reviewer separation is enforced.</p></div>
            <StatusBadge status={assessment.status} />
          </div>
          <dl className="definition-list">
            <div className="definition-row"><dt>Question shuffle</dt><dd>{assessment.shuffleQuestions ? "Enabled" : "Disabled"}</dd></div>
            <div className="definition-row"><dt>Option shuffle</dt><dd>{assessment.shuffleOptions ? "Enabled" : "Disabled"}</dd></div>
            <div className="definition-row"><dt>Partial marking</dt><dd>{assessment.partialMarking ? "Enabled" : "Disabled"}</dd></div>
            <div className="definition-row"><dt>Created</dt><dd>{new Date(assessment.createdAt).toLocaleString()}</dd></div>
            <div className="definition-row"><dt>Last updated</dt><dd>{new Date(assessment.updatedAt).toLocaleString()}</dd></div>
          </dl>
          {assessment.status === "READY_FOR_REVIEW" && (
            <div className="explanation">
              <CheckCircle2 size={15} /> Waiting for a reviewer in the approval workspace.
            </div>
          )}
        </section>

        <aside className="panel">
          <div className="panel-header">
            <div><h2>Delivery window</h2><p>Available after publication.</p></div>
            <CalendarClock size={18} />
          </div>
          {["PUBLISHED", "SCHEDULED"].includes(assessment.status) ? (
            <>
              <div className="field">
                <label htmlFor="schedule-start">Start date and time</label>
                <input
                  id="schedule-start"
                  onChange={(event) => setStartAt(event.target.value)}
                  type="datetime-local"
                  value={startAt}
                />
              </div>
              <div className="field">
                <label htmlFor="schedule-end">End date and time</label>
                <input
                  id="schedule-end"
                  onChange={(event) => setEndAt(event.target.value)}
                  type="datetime-local"
                  value={endAt}
                />
              </div>
              <div className="field">
                <label>Eligible sections</label>
                <div className="checkbox-list">
                  {catalog.sections.filter((section) => section.active).map((section) => (
                    <label className="check-row" key={section.id}>
                      <input
                        checked={sectionIds.includes(section.id)}
                        onChange={(event) =>
                          setSectionIds((current) =>
                            event.target.checked
                              ? [...current, section.id]
                              : current.filter((id) => id !== section.id),
                          )
                        }
                        type="checkbox"
                      />
                      {section.departmentName} · {section.name}
                    </label>
                  ))}
                </div>
              </div>
              <button
                className="button button-primary button-full"
                disabled={busy}
                onClick={() => void schedule()}
                type="button"
              >
                <CalendarClock size={15} /> {busy ? "Saving…" : "Save schedule"}
              </button>
              {assessment.status === "SCHEDULED" && (
                <p className="panel-note">Current eligibility: {sectionNames}</p>
              )}
            </>
          ) : (
            <div className="empty-state">
              Complete review and publication before scheduling.
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
