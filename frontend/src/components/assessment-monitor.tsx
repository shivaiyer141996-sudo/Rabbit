"use client";

import Link from "next/link";
import {
  Activity,
  ArrowLeft,
  CheckCircle2,
  Clock3,
  RefreshCw,
  Send,
  UsersRound,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { AssessmentMonitor as AssessmentMonitorView } from "@/lib/types";

function timeRemaining(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`;
}

export function AssessmentMonitor({ assessmentId }: { assessmentId: string }) {
  const [monitor, setMonitor] = useState<AssessmentMonitorView | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async (background = false) => {
    if (background) setRefreshing(true);
    else setLoading(true);
    setError("");
    try {
      setMonitor(
        await apiFetch<AssessmentMonitorView>(`/evaluation/assessments/${assessmentId}/monitor`),
      );
    } catch (requestError) {
      if (!background) setMonitor(null);
      setError(apiErrorMessage(requestError, "Live assessment monitoring could not be loaded."));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [assessmentId]);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    const poll = window.setInterval(() => void load(true), 10_000);
    return () => {
      window.clearTimeout(initial);
      window.clearInterval(poll);
    };
  }, [load]);

  if (loading) {
    return <div className="page"><LoadingState label="Opening the live assessment monitor…" /></div>;
  }
  if (!monitor) {
    return <div className="page"><ErrorState message={error} retry={() => void load()} /></div>;
  }

  return (
    <div className="page">
      <Link className="button button-ghost" href={`/assessments/${assessmentId}`}>
        <ArrowLeft size={15} /> Back to assessment
      </Link>
      <PageHeader
        eyebrow="Assessment delivery · Live monitor"
        title={monitor.assessmentTitle}
        description={`Automatically refreshed every 10 seconds · Last updated ${new Date(monitor.generatedAt).toLocaleTimeString()}`}
        actions={
          <button className="button button-secondary" disabled={refreshing} onClick={() => void load(true)} type="button">
            <RefreshCw className={refreshing ? "spin" : ""} size={15} /> Refresh now
          </button>
        }
      />
      {error && <div className="form-error" role="alert">{error}</div>}
      <section className="metrics-grid report-metrics">
        {[
          [UsersRound, "Total attempts", monitor.totalAttempts, "Started in this assessment"],
          [Activity, "In progress", monitor.inProgress, "Currently timed"],
          [Send, "Submitted", monitor.submitted, "Student submitted"],
          [Clock3, "Auto-submitted", monitor.autoSubmitted, "Server time limit"],
        ].map(([Icon, label, value, context]) => {
          const MetricIcon = Icon as typeof Activity;
          return (
            <article className="metric-card" key={String(label)}>
              <span className="metric-icon"><MetricIcon size={18} /></span>
              <span className="metric-label">{String(label)}</span>
              <strong>{String(value)}</strong>
              <small>{String(context)}</small>
            </article>
          );
        })}
      </section>
      <section className="panel report-table-panel">
        <div className="panel-header"><div><h2>Student attempt status</h2><p>Answer counts show persisted server responses, not browser-only state.</p></div></div>
        {!monitor.attempts.length ? (
          <div className="empty-state">No student has started this assessment yet.</div>
        ) : (
          <div className="data-table-wrap">
            <table className="data-table">
              <thead><tr><th>Student</th><th>Status</th><th>Started</th><th>Progress</th><th>Time remaining</th><th>Publication</th></tr></thead>
              <tbody>
                {monitor.attempts.map((attempt) => (
                  <tr key={attempt.attemptId}>
                    <td><strong>{attempt.studentName}</strong><span className="table-subtitle">Attempt {attempt.attemptId.slice(0, 8)}</span></td>
                    <td><span className={`badge ${attempt.attemptStatus === "IN_PROGRESS" ? "badge-warning" : "badge-success"}`}>{attempt.attemptStatus.replaceAll("_", " ")}</span></td>
                    <td>{new Date(attempt.startedAt).toLocaleString()}</td>
                    <td>
                      <div className="monitor-progress"><div className="monitor-progress-track"><span style={{ width: `${attempt.progressPercentage}%` }} /></div><strong>{attempt.answered}/{attempt.questionCount}</strong></div>
                    </td>
                    <td>{attempt.attemptStatus === "IN_PROGRESS" ? <span className="timer compact"><Clock3 size={14} /> {timeRemaining(attempt.secondsRemaining)}</span> : <span className="muted"><CheckCircle2 size={14} /> Complete</span>}</td>
                    <td><span className={`badge ${attempt.publicationStatus === "PUBLISHED" ? "badge-success" : "badge-info"}`}>{attempt.publicationStatus.replaceAll("_", " ")}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
