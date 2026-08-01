"use client";

import Image from "next/image";
import Link from "next/link";
import {
  ArrowLeft,
  CheckCircle2,
  Clock3,
  History,
  Hourglass,
  PlayCircle,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { AttemptHistoryItem } from "@/lib/live-types";

export function StudentAttemptHistory() {
  const [attempts, setAttempts] = useState<AttemptHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setAttempts(await apiFetch<AttemptHistoryItem[]>("/student/attempts/history"));
    } catch (requestError) {
      setAttempts([]);
      setError(apiErrorMessage(requestError, "Attempt history could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  return (
    <div className="student-home history-page">
      <header className="student-home-header">
        <Link className="player-brand" href="/dashboard">
          <Image src="/rabbit-mark.svg" width={42} height={42} alt="" />
          <div className="player-title"><strong>Rabbit AiP</strong><span>Student attempt history</span></div>
        </Link>
        <Link className="button button-secondary" href="/dashboard"><ArrowLeft size={15} /> Dashboard</Link>
      </header>
      <main className="student-home-main">
        <div><span className="visual-eyebrow">Persisted attempt record</span><h1>Attempt history</h1><p>Track in-progress attempts, publication status, and every released result.</p></div>
        {loading && <LoadingState label="Loading your attempt history…" />}
        {!loading && error && <ErrorState message={error} retry={() => void load()} />}
        {!loading && !error && !attempts.length && <div className="empty-state">You have not started an assessment yet.</div>}
        {!loading && !error && attempts.length > 0 && (
          <section className="history-grid">
            {attempts.map((attempt) => {
              const inProgress = attempt.status === "IN_PROGRESS";
              const published = attempt.publicationStatus === "PUBLISHED";
              const href = inProgress
                ? `/student/assessments/${attempt.assessmentId}`
                : `/results/${attempt.attemptId}`;
              return (
                <article className="panel history-card" key={attempt.attemptId}>
                  <div className="assessment-card-top">
                    <span className={`badge ${inProgress ? "badge-warning" : published ? "badge-success" : "badge-info"}`}>
                      {inProgress ? "IN PROGRESS" : published ? "RESULT PUBLISHED" : "AWAITING PUBLICATION"}
                    </span>
                    <span className="muted">v{attempt.evaluationVersion}</span>
                  </div>
                  <h2>{attempt.assessmentTitle}</h2>
                  <p>{attempt.assessmentType.replaceAll("_", " ")}</p>
                  <dl className="definition-list">
                    <div className="definition-row"><dt>Started</dt><dd>{new Date(attempt.startedAt).toLocaleString()}</dd></div>
                    <div className="definition-row"><dt>Answered</dt><dd>{attempt.answered}/{attempt.questionCount}</dd></div>
                    <div className="definition-row"><dt>Submission</dt><dd>{inProgress ? "Not submitted" : attempt.status.replaceAll("_", " ")}</dd></div>
                    {published && <div className="definition-row"><dt>Published score</dt><dd>{attempt.score} / {attempt.maxScore} · {attempt.percentage}% · Grade {attempt.grade}</dd></div>}
                  </dl>
                  {!inProgress && !published && (
                    <div className="insight-callout"><Hourglass size={16} /><div><strong>Evaluation complete</strong><span>Your score remains hidden until authorised publication.</span></div></div>
                  )}
                  <Link className="button button-primary button-full" href={href}>
                    {inProgress ? <PlayCircle size={15} /> : published ? <CheckCircle2 size={15} /> : <Clock3 size={15} />}
                    {inProgress ? "Resume attempt" : published ? "View result" : "View submission status"}
                  </Link>
                </article>
              );
            })}
          </section>
        )}
        <div className="result-footer"><span><History size={14} /> Unpublished scores are never shown in history.</span><Link className="button button-secondary" href="/student/assessments">Available assessments</Link></div>
      </main>
    </div>
  );
}
