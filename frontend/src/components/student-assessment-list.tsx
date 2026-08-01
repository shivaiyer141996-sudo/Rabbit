"use client";

import Image from "next/image";
import Link from "next/link";
import { ArrowLeft, CalendarClock, Clock3, PlayCircle } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { StudentAssessmentSummary } from "@/lib/live-types";

export function StudentAssessmentList() {
  const [assessments, setAssessments] = useState<StudentAssessmentSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setAssessments(await apiFetch<StudentAssessmentSummary[]>("/student/assessments"));
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Available assessments could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  return (
    <div className="student-home">
      <header className="student-home-header">
        <Link className="player-brand" href="/dashboard">
          <Image src="/rabbit-mark.svg" width={42} height={42} alt="" />
          <div className="player-title">
            <strong>Rabbit AiP</strong>
            <span>Student assessment workspace</span>
          </div>
        </Link>
        <Link className="button button-secondary" href="/dashboard">
          <ArrowLeft size={15} /> Dashboard
        </Link>
      </header>
      <main className="student-home-main">
        <div>
          <span className="visual-eyebrow">Live assessment windows</span>
          <h1>Available assessments</h1>
          <p>Starting an assessment creates or resumes your persisted attempt.</p>
        </div>
        {loading && <LoadingState label="Checking your eligible assessments…" />}
        {!loading && error && <ErrorState message={error} retry={() => void load()} />}
        {!loading && !error && !assessments.length && (
          <div className="empty-state">
            No assessment is open for your section right now.
          </div>
        )}
        <section className="assessment-grid">
          {assessments.map((assessment) => (
            <article className="assessment-card" key={assessment.id}>
              <div className="assessment-card-top">
                <span className="assessment-code">{assessment.code}</span>
                <span className="badge badge-success">OPEN</span>
              </div>
              <h2>{assessment.title}</h2>
              <p>{assessment.type.replaceAll("_", " ")}</p>
              <div className="assessment-stats">
                <div><strong>{assessment.questionCount}</strong><span>Questions</span></div>
                <div><strong>{assessment.totalMarks}</strong><span>Marks</span></div>
                <div><strong>{assessment.durationMinutes}m</strong><span>Duration</span></div>
              </div>
              <div className="assessment-window">
                <CalendarClock size={14} />
                Closes {new Date(assessment.endAt).toLocaleString()}
              </div>
              <Link
                className="button button-primary button-full"
                href={`/student/assessments/${assessment.id}/instructions`}
                style={{ marginTop: 16 }}
              >
                <PlayCircle size={15} /> Read instructions
              </Link>
              <span className="panel-note">
                <Clock3 size={13} /> Timer is enforced by the server.
              </span>
            </article>
          ))}
        </section>
      </main>
    </div>
  );
}
