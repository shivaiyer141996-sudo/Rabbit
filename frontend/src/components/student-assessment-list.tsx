"use client";

import Image from "next/image";
import Link from "next/link";
import { ArrowLeft, CalendarClock, Clock3, PlayCircle } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { StudentAssessmentSummary } from "@/lib/live-types";

type Tab = "AVAILABLE" | "UPCOMING" | "COMPLETED";

export function StudentAssessmentList() {
  const [assessments, setAssessments] = useState<StudentAssessmentSummary[]>([]);
  const [tab, setTab] = useState<Tab>("AVAILABLE");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try { setAssessments(await apiFetch<StudentAssessmentSummary[]>("/student/assessments")); }
    catch (requestError) { setError(apiErrorMessage(requestError, "Assessments could not be loaded.")); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { const initial = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(initial); }, [load]);

  const rows = useMemo(() => assessments.filter((assessment) => tab === "AVAILABLE" ? assessment.status === "AVAILABLE_NOW" : tab === "UPCOMING" ? assessment.status === "UPCOMING" : ["COMPLETED", "MISSED_CLOSED"].includes(assessment.status)), [assessments, tab]);
  const counts = { AVAILABLE: assessments.filter((item) => item.status === "AVAILABLE_NOW").length, UPCOMING: assessments.filter((item) => item.status === "UPCOMING").length, COMPLETED: assessments.filter((item) => ["COMPLETED", "MISSED_CLOSED"].includes(item.status)).length };

  return (
    <div className="student-home">
      <header className="student-home-header"><Link className="player-brand" href="/dashboard"><Image src="/rabbit-mark.svg" width={42} height={42} alt="" /><div className="player-title"><strong>Rabbit AiP</strong><span>Student assessment workspace</span></div></Link><Link className="button button-secondary" href="/dashboard"><ArrowLeft size={15} /> Dashboard</Link></header>
      <main className="student-home-main">
        <div><span className="visual-eyebrow">Your assessment schedule</span><h1>Assessments</h1><p>Available, upcoming, completed, and missed assessments use the same server status as your dashboard.</p></div>
        <div className="segmented-control" role="tablist" aria-label="Assessment status">
          {(["AVAILABLE", "UPCOMING", "COMPLETED"] as Tab[]).map((value) => <button aria-selected={tab === value} className={tab === value ? "active" : ""} key={value} onClick={() => setTab(value)} role="tab" type="button">{value.charAt(0) + value.slice(1).toLowerCase()} <span className="tab-count">{counts[value]}</span></button>)}
        </div>
        {loading && <LoadingState label="Reconciling your assessment schedule…" />}
        {!loading && error && <ErrorState message={error} retry={() => void load()} />}
        {!loading && !error && !rows.length && <div className="empty-state">No {tab.toLowerCase()} assessments.</div>}
        <section className="assessment-grid">{rows.map((assessment) => <article className="assessment-card" key={assessment.id}><div className="assessment-card-top"><span className="assessment-code">{assessment.code}</span><span className={`badge ${assessment.status === "AVAILABLE_NOW" ? "badge-success" : assessment.status === "UPCOMING" ? "badge-info" : "badge-neutral"}`}>{assessment.status.replaceAll("_", " ")}</span></div><h2>{assessment.title}</h2><p>{assessment.type.replaceAll("_", " ")}</p><div className="assessment-stats"><div><strong>{assessment.questionCount}</strong><span>Questions</span></div><div><strong>{assessment.totalMarks}</strong><span>Marks</span></div><div><strong>{assessment.durationMinutes}m</strong><span>Duration</span></div></div><div className="assessment-window"><CalendarClock size={14} />{assessment.status === "UPCOMING" ? <> {new Date(assessment.startAt).toLocaleDateString()} · {new Date(assessment.startAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })} · {assessment.remainingDays} day{assessment.remainingDays === 1 ? "" : "s"} remaining</> : assessment.status === "AVAILABLE_NOW" ? <> Closes {new Date(assessment.endAt).toLocaleString()}</> : <> Closed {new Date(assessment.endAt).toLocaleString()}</>}</div>{assessment.status === "AVAILABLE_NOW" && <Link className="button button-primary button-full" href={`/student/assessments/${assessment.id}/instructions`} style={{ marginTop: 16 }}><PlayCircle size={15} /> Read instructions</Link>}<span className="panel-note"><Clock3 size={13} /> Times are enforced by the server.</span></article>)}</section>
      </main>
    </div>
  );
}
