"use client";

import Image from "next/image";
import Link from "next/link";
import {
  ArrowLeft,
  CheckCircle2,
  Clock3,
  FileCheck2,
  ListChecks,
  PlayCircle,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { StudentAssessmentInstructions } from "@/lib/live-types";

export function AssessmentInstructions({ assessmentId }: { assessmentId: string }) {
  const [assessment, setAssessment] = useState<StudentAssessmentInstructions | null>(null);
  const [serverClock, setServerClock] = useState(0);
  const [acknowledged, setAcknowledged] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const value = await apiFetch<StudentAssessmentInstructions>(
        `/student/assessments/${assessmentId}`,
      );
      setAssessment(value);
      setServerClock(new Date(value.serverNow).getTime());
    } catch (requestError) {
      setAssessment(null);
      setError(apiErrorMessage(requestError, "Assessment instructions could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, [assessmentId]);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  useEffect(() => {
    const clock = window.setInterval(
      () => setServerClock((current) => current + 1_000),
      1_000,
    );
    return () => window.clearInterval(clock);
  }, []);

  if (loading) {
    return <div className="student-home"><main className="student-home-main"><LoadingState label="Loading assessment instructions…" /></main></div>;
  }
  if (!assessment) {
    return <div className="student-home"><main className="student-home-main"><ErrorState message={error} retry={() => void load()} /></main></div>;
  }

  const windowOpen = serverClock >= new Date(assessment.startAt).getTime()
    && serverClock < new Date(assessment.endAt).getTime();
  const attemptAvailable = Boolean(assessment.inProgressAttemptId)
    || assessment.attemptsUsed < assessment.attemptsAllowed;
  const canContinue = acknowledged && windowOpen && attemptAvailable;

  return (
    <div className="student-home instructions-page">
      <header className="student-home-header">
        <Link className="player-brand" href="/dashboard">
          <Image src="/rabbit-mark.svg" width={42} height={42} alt="" />
          <div className="player-title"><strong>Rabbit AiP</strong><span>Assessment readiness</span></div>
        </Link>
        <Link className="button button-secondary" href="/student/assessments">
          <ArrowLeft size={15} /> Available assessments
        </Link>
      </header>
      <main className="student-home-main instructions-main">
        <section className="instructions-hero">
          <div>
            <span className="assessment-code">{assessment.code}</span>
            <h1>{assessment.title}</h1>
            <p>{assessment.type.replaceAll("_", " ")} · Read every instruction before entering the timed player.</p>
          </div>
          <span className={`badge ${windowOpen ? "badge-success" : "badge-danger"}`}>
            {windowOpen ? "WINDOW OPEN" : "WINDOW CLOSED"}
          </span>
        </section>

        <section className="metrics-grid report-metrics">
          {[
            [ListChecks, "Questions", assessment.questionCount, "MCQ items"],
            [FileCheck2, "Total marks", assessment.totalMarks, "Objective evaluation"],
            [Clock3, "Time limit", `${assessment.durationMinutes}m`, "Server enforced"],
            [RefreshCw, "Attempts", `${assessment.attemptsUsed}/${assessment.attemptsAllowed}`, assessment.inProgressAttemptId ? "Attempt in progress" : "Used / allowed"],
          ].map(([Icon, label, value, context]) => {
            const MetricIcon = Icon as typeof ListChecks;
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

        <div className="instructions-grid">
          <section className="panel">
            <div className="panel-header"><div><h2>Before you start</h2><p>The timer begins only when you enter the assessment player.</p></div><ShieldCheck size={19} /></div>
            <ol className="instruction-list">
              <li><CheckCircle2 size={17} /><span>Use a stable internet connection and keep this browser tab open during the attempt.</span></li>
              <li><CheckCircle2 size={17} /><span>Each answer is saved to the server immediately and again every 30 seconds.</span></li>
              <li><CheckCircle2 size={17} /><span>You may move between questions and flag items for review before submission.</span></li>
              <li><CheckCircle2 size={17} /><span>The server automatically submits and evaluates the attempt when time expires, even if the browser closes.</span></li>
              <li><CheckCircle2 size={17} /><span>Scores and answer explanations remain hidden until authorised staff publish the result.</span></li>
            </ol>
          </section>
          <aside className="panel readiness-card">
            <div className="panel-header"><h2>Delivery settings</h2></div>
            <dl className="definition-list">
              <div className="definition-row"><dt>Window closes</dt><dd>{new Date(assessment.endAt).toLocaleString()}</dd></div>
              <div className="definition-row"><dt>Question order</dt><dd>{assessment.shuffleQuestions ? "Shuffled per attempt" : "Fixed"}</dd></div>
              <div className="definition-row"><dt>Option order</dt><dd>{assessment.shuffleOptions ? "Shuffled per attempt" : "Fixed"}</dd></div>
              <div className="definition-row"><dt>Partial marking</dt><dd>{assessment.partialMarking ? "Enabled" : "Disabled"}</dd></div>
            </dl>
            {assessment.inProgressAttemptId && assessment.inProgressExpiresAt && (
              <div className="insight-callout"><RefreshCw size={16} /><div><strong>Resuming saved attempt</strong><span>Current timer ends {new Date(assessment.inProgressExpiresAt).toLocaleString()}.</span></div></div>
            )}
            {!attemptAvailable && (
              <div className="form-error" role="alert">All permitted attempts have been used.</div>
            )}
            <label className="check-row readiness-acknowledgement">
              <input checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} type="checkbox" />
              I understand the timer, auto-save, submission, and result-publication rules.
            </label>
            {canContinue ? (
              <Link className="button button-primary button-full" href={`/student/assessments/${assessment.id}`}>
                <PlayCircle size={16} /> {assessment.inProgressAttemptId ? "Resume assessment" : "Start assessment"}
              </Link>
            ) : (
              <button className="button button-primary button-full" disabled type="button">
                <PlayCircle size={16} /> {!windowOpen ? "Assessment window closed" : !attemptAvailable ? "No attempts remaining" : "Acknowledge to continue"}
              </button>
            )}
          </aside>
        </div>
      </main>
    </div>
  );
}
