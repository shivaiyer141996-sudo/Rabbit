"use client";

import Link from "next/link";
import {
  ArrowLeft,
  CheckCircle2,
  Download,
  Eye,
  RefreshCw,
  Save,
  Send,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type {
  AssessmentEvaluationSummary,
  AssessmentReport,
  ManualAttemptReview,
} from "@/lib/types";

export function AssessmentReportDetail({ assessmentId }: { assessmentId: string }) {
  const [report, setReport] = useState<AssessmentReport | null>(null);
  const [evaluation, setEvaluation] = useState<AssessmentEvaluationSummary | null>(null);
  const [reEvaluationAttemptId, setReEvaluationAttemptId] = useState("");
  const [reEvaluationReason, setReEvaluationReason] = useState("");
  const [manualReview, setManualReview] = useState<ManualAttemptReview | null>(null);
  const [markDraft, setMarkDraft] = useState<Record<string, string>>({});
  const [manualReason, setManualReason] = useState("");
  const [reviewLoading, setReviewLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [nextReport, nextEvaluation] = await Promise.all([
        apiFetch<AssessmentReport>(`/reports/assessments/${assessmentId}`),
        apiFetch<AssessmentEvaluationSummary>(
          `/evaluation/assessments/${assessmentId}/results`,
        ),
      ]);
      setReport(nextReport);
      setEvaluation(nextEvaluation);
    } catch (requestError) {
      setReport(null);
      setError(apiErrorMessage(requestError, "Assessment report could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, [assessmentId]);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  async function publish() {
    setBusy(true);
    setMessage("");
    setError("");
    try {
      const response = await apiFetch<{ publishedCount: number }>(
        `/evaluation/assessments/${assessmentId}/publish`,
        { method: "POST" },
      );
      setMessage(`${response.publishedCount} result(s) published to students.`);
      await load();
    } catch (error) {
      setError(apiErrorMessage(error, "Results could not be published."));
    } finally {
      setBusy(false);
    }
  }

  async function reEvaluate() {
    if (reEvaluationReason.trim().length < 10) {
      setError("Provide a re-evaluation reason of at least 10 characters.");
      return;
    }
    setBusy(true);
    setMessage("");
    setError("");
    try {
      await apiFetch(
        `/evaluation/attempts/${reEvaluationAttemptId}/re-evaluate`,
        {
          method: "POST",
          body: JSON.stringify({ reason: reEvaluationReason.trim() }),
        },
      );
      setMessage(
        "Attempt re-evaluated. The new version is awaiting publication before the student can see it.",
      );
      setReEvaluationAttemptId("");
      setReEvaluationReason("");
      await load();
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "The attempt could not be re-evaluated."));
    } finally {
      setBusy(false);
    }
  }

  async function openManualReview(attemptId: string) {
    setReviewLoading(true);
    setError("");
    setMessage("");
    setReEvaluationAttemptId("");
    try {
      const review = await apiFetch<ManualAttemptReview>(
        `/evaluation/attempts/${attemptId}/review`,
      );
      setManualReview(review);
      setMarkDraft(Object.fromEntries(
        review.questions.map((question) => [question.questionId, String(question.awardedMarks)]),
      ));
      setManualReason("");
    } catch (requestError) {
      setManualReview(null);
      setError(apiErrorMessage(requestError, "The attempt could not be opened for manual review."));
    } finally {
      setReviewLoading(false);
    }
  }

  async function saveManualScore() {
    if (!manualReview) return;
    if (manualReason.trim().length < 10) {
      setError("Provide a manual score-update reason of at least 10 characters.");
      return;
    }
    const adjustments = manualReview.questions
      .map((question) => {
        const rawValue = markDraft[question.questionId] ?? "";
        return {
          questionId: question.questionId,
          rawValue,
          awardedMarks: Number(rawValue),
          previousMarks: question.awardedMarks,
          minimumMarks: question.minimumMarks,
          maximumMarks: question.maximumMarks,
        };
      })
      .filter((item) => item.awardedMarks !== item.previousMarks);
    if (!adjustments.length) {
      setError("Change at least one awarded mark before saving.");
      return;
    }
    const invalid = adjustments.find((item) =>
      !item.rawValue.trim()
      || !Number.isFinite(item.awardedMarks)
      || item.awardedMarks < item.minimumMarks
      || item.awardedMarks > item.maximumMarks,
    );
    if (invalid) {
      setError("Every adjusted mark must stay within the range shown for its question.");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const updated = await apiFetch<ManualAttemptReview>(
        `/evaluation/attempts/${manualReview.attemptId}/score`,
        {
          method: "POST",
          body: JSON.stringify({
            reason: manualReason.trim(),
            adjustments: adjustments.map(({ questionId, awardedMarks }) => ({
              questionId,
              awardedMarks,
            })),
          }),
        },
      );
      setManualReview(updated);
      setMarkDraft(Object.fromEntries(
        updated.questions.map((question) => [question.questionId, String(question.awardedMarks)]),
      ));
      setManualReason("");
      setMessage(
        `Score updated to ${updated.score}/${updated.maxScore}. Version ${updated.evaluationVersion} is awaiting publication.`,
      );
      await load();
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "The governed score update could not be saved."));
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <div className="page"><LoadingState label="Loading live assessment report…" /></div>;
  }
  if (!report) {
    return <div className="page"><ErrorState message={error} retry={() => void load()} /></div>;
  }

  return (
    <div className="page">
      <Link className="button button-ghost" href="/reports">
        <ArrowLeft size={15} /> Back to reports
      </Link>
      <PageHeader
        eyebrow="Assessment intelligence · Live"
        title={report.title}
        description={`Generated ${new Date(report.generatedAt).toLocaleString()} · ${report.generatedBy}`}
        actions={
          <>
            <a
              className="button button-secondary"
              href={`/gateway/backend/reports/assessments/${assessmentId}/export`}
            >
              <Download size={15} /> Export CSV
            </a>
            <a
              className="button button-secondary"
              href={`/gateway/backend/reports/assessments/${assessmentId}/export.pdf`}
            >
              <Download size={15} /> Export PDF
            </a>
            <a
              className="button button-secondary"
              href={`/gateway/backend/reports/assessments/${assessmentId}/export.xlsx`}
            >
              <Download size={15} /> Export Excel
            </a>
            <button
              className="button button-primary"
              disabled={busy || !evaluation?.pendingPublicationCount}
              onClick={publish}
            >
              {busy ? <RefreshCw className="spin" size={15} /> : <Send size={15} />}
              Publish pending results
            </button>
          </>
        }
      />

      {message && <div className="success-banner">{message}</div>}
      {error && <div className="form-error" role="alert">{error}</div>}

      <section className="metrics-grid report-metrics">
        {[
          ["Submissions", report.submissions],
          ["Average", `${report.averagePercentage}%`],
          ["Highest", `${report.highestPercentage}%`],
          ["Pass rate", `${report.passRate}%`],
        ].map(([label, value]) => (
          <article className="metric-card" key={String(label)}>
            <span className="metric-label">{label}</span>
            <strong>{value}</strong>
            <small>Published evaluations</small>
          </article>
        ))}
      </section>

      {evaluation && (
        <div className="publication-strip">
          <CheckCircle2 size={18} />
          <span><strong>{evaluation.publishedCount}</strong> published</span>
          <span><strong>{evaluation.pendingPublicationCount}</strong> awaiting publication</span>
          <span><strong>{evaluation.evaluatedCount}</strong> evaluated</span>
        </div>
      )}

      {evaluation && (
        <section className="panel report-table-panel">
          <div className="panel-header">
            <div>
              <h2>Evaluation controls</h2>
              <p>Re-evaluation is versioned, audited, and returns the result to pending publication.</p>
            </div>
          </div>
          {!evaluation.results.length ? (
            <div className="empty-state">No completed attempts are available for evaluation.</div>
          ) : (
            <div className="data-table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Student</th>
                    <th>Attempt</th>
                    <th>Score</th>
                    <th>Publication</th>
                    <th>Version</th>
                    <th aria-label="Action" />
                  </tr>
                </thead>
                <tbody>
                  {evaluation.results.map((row) => (
                    <tr key={row.attemptId}>
                      <td><strong>{row.studentName}</strong><span className="table-subtitle">{row.attemptId.slice(0, 8)}</span></td>
                      <td><span className="badge badge-neutral">{row.attemptStatus.replaceAll("_", " ")}</span></td>
                      <td>{row.score} / {row.maxScore} · {row.percentage}%</td>
                      <td><span className={`badge ${row.publicationStatus === "PUBLISHED" ? "badge-success" : "badge-info"}`}>{row.publicationStatus.replaceAll("_", " ")}</span></td>
                      <td>v{row.evaluationVersion}</td>
                      <td>
                        <div className="table-actions">
                          <button
                            className="button button-ghost"
                            disabled={busy || reviewLoading}
                            onClick={() => void openManualReview(row.attemptId)}
                            type="button"
                          >
                            <Eye size={14} /> Manual review
                          </button>
                          <button
                            className="button button-ghost"
                            disabled={busy || reviewLoading}
                            onClick={() => {
                              setManualReview(null);
                              setReEvaluationAttemptId(row.attemptId);
                              setReEvaluationReason("");
                              setError("");
                            }}
                            type="button"
                          >
                            <RefreshCw size={14} /> Recalculate
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {reEvaluationAttemptId && (
            <div className="re-evaluation-form">
              <div>
                <strong>Reason for governed re-evaluation</strong>
                <span>Attempt {reEvaluationAttemptId.slice(0, 8)} · minimum 10 characters</span>
              </div>
              <textarea
                aria-label="Re-evaluation reason"
                maxLength={500}
                onChange={(event) => setReEvaluationReason(event.target.value)}
                placeholder="Explain why this completed attempt must be scored again."
                rows={3}
                value={reEvaluationReason}
              />
              <div className="header-actions">
                <span className="muted">{reEvaluationReason.trim().length}/500</span>
                <button
                  className="button button-secondary"
                  disabled={busy}
                  onClick={() => {
                    setReEvaluationAttemptId("");
                    setReEvaluationReason("");
                  }}
                  type="button"
                >
                  Cancel
                </button>
                <button
                  className="button button-primary"
                  disabled={busy || reEvaluationReason.trim().length < 10}
                  onClick={() => void reEvaluate()}
                  type="button"
                >
                  {busy ? <RefreshCw className="spin" size={15} /> : <RefreshCw size={15} />}
                  Confirm re-evaluation
                </button>
              </div>
            </div>
          )}
        </section>
      )}

      {reviewLoading && <LoadingState label="Opening the persisted answers and score history…" />}

      {manualReview && !reviewLoading && (
        <section className="panel manual-review-panel" aria-live="polite">
          <div className="panel-header manual-review-header">
            <div>
              <span className="visual-eyebrow">Governed manual review</span>
              <h2>{manualReview.studentName}</h2>
              <p>
                Attempt {manualReview.attemptId.slice(0, 8)} · {manualReview.score}/{manualReview.maxScore}
                {" · "}{manualReview.percentage}% · v{manualReview.evaluationVersion}
              </p>
            </div>
            <div className="header-actions">
              <span className={`badge ${manualReview.publicationStatus === "PUBLISHED" ? "badge-success" : "badge-info"}`}>
                {manualReview.publicationStatus.replaceAll("_", " ")}
              </span>
              <button className="button button-secondary" onClick={() => setManualReview(null)} type="button">Close review</button>
            </div>
          </div>

          <div className="manual-question-list">
            {manualReview.questions.map((question, index) => (
              <article className={`manual-question-card ${question.correct ? "correct" : "incorrect"}`} key={question.questionId}>
                <div className="manual-question-heading">
                  <span>Q{index + 1}</span>
                  <div>
                    <strong>{question.code} · {question.stem}</strong>
                    <span>{question.subjectName} · {question.topicName} · {question.difficulty} · {question.timeSpentSeconds}s</span>
                  </div>
                  <label>
                    Awarded marks
                    <input
                      aria-label={`Awarded marks for ${question.code}`}
                      max={question.maximumMarks}
                      min={question.minimumMarks}
                      onChange={(event) => setMarkDraft((current) => ({
                        ...current,
                        [question.questionId]: event.target.value,
                      }))}
                      step="0.01"
                      type="number"
                      value={markDraft[question.questionId] ?? ""}
                    />
                    <span>{question.minimumMarks} to {question.maximumMarks}</span>
                  </label>
                </div>
                <div className="manual-option-list">
                  {question.options.map((option) => (
                    <div className={`${option.selected ? "selected" : ""} ${option.correct ? "correct" : ""}`} key={option.optionId}>
                      <span>{option.label}</span>
                      <strong>{option.text}</strong>
                      <small>{option.selected ? "Student selected" : ""}{option.selected && option.correct ? " · " : ""}{option.correct ? "Correct option" : ""}</small>
                    </div>
                  ))}
                </div>
                {question.explanation && <p className="manual-explanation"><strong>Explanation</strong>{question.explanation}</p>}
              </article>
            ))}
          </div>

          <div className="manual-score-savebar">
            <div className="field">
              <label htmlFor="manual-score-reason">Reason for score update</label>
              <textarea
                id="manual-score-reason"
                maxLength={500}
                onChange={(event) => setManualReason(event.target.value)}
                placeholder="Explain the academic or administrative reason for changing the awarded marks."
                rows={3}
                value={manualReason}
              />
              <span>{manualReason.trim().length}/500 · minimum 10 characters</span>
            </div>
            <button className="button button-primary" disabled={busy || manualReason.trim().length < 10} onClick={() => void saveManualScore()} type="button">
              {busy ? <RefreshCw className="spin" size={15} /> : <Save size={15} />}
              Save score update
            </button>
          </div>

          <div className="evaluation-audit">
            <div className="panel-header"><div><h3>Evaluation audit trail</h3><p>Automatic scoring, manual changes, recalculation, and publication events.</p></div></div>
            {!manualReview.auditTrail.length ? <div className="empty-state">No evaluation event has been recorded.</div> : (
              <ol>
                {manualReview.auditTrail.map((event) => (
                  <li key={event.eventId}>
                    <span />
                    <div><strong>{event.action.replaceAll("_", " ")}</strong><small>{new Date(event.timestamp).toLocaleString()} · {event.actorEmail ?? "System"} · {event.actorRole ?? "SYSTEM"}</small><p>{event.beforeValue ?? "Initial evaluation"} → {event.afterValue ?? "Recorded"}</p></div>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </section>
      )}

      <section className="panel report-table-panel">
        <div className="panel-header"><h2>Student results</h2></div>
        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Student</th>
                <th>Submitted</th>
                <th>Score</th>
                <th>Percentage</th>
                <th>Grade</th>
                <th>Progress</th>
              </tr>
            </thead>
            <tbody>
              {report.studentResults.map((result) => (
                <tr key={result.attemptId}>
                  <td>
                    <strong>{result.studentName}</strong>
                    <span className="table-subtitle">
                      Attempt {result.attemptId.slice(0, 8)}
                    </span>
                  </td>
                  <td>{new Date(result.submittedAt).toLocaleString()}</td>
                  <td>{result.score} / {result.maxScore}</td>
                  <td>{result.percentage}%</td>
                  <td><span className="grade-chip">{result.grade}</span></td>
                  <td><span className="badge badge-info">{result.trajectory}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel report-table-panel">
        <div className="panel-header">
          <div>
            <h2>Question performance</h2>
            <p>Items below 0.20 discrimination are flagged for review.</p>
          </div>
        </div>
        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Correct rate</th>
                <th>Difficulty index</th>
                <th>Discrimination</th>
                <th>Quality</th>
              </tr>
            </thead>
            <tbody>
              {report.questionAnalytics.map((question) => (
                <tr key={question.questionId}>
                  <td><strong>{question.code}</strong></td>
                  <td>{question.correctRate}%</td>
                  <td>{question.difficultyIndex.toFixed(2)}</td>
                  <td>{question.discriminationIndex.toFixed(2)}</td>
                  <td>
                    <span className={`badge ${question.poorQuality ? "badge-danger" : "badge-success"}`}>
                      {question.poorQuality ? "Review" : "Healthy"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
