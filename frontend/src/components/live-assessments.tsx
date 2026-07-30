"use client";

import Link from "next/link";
import { CalendarClock, Plus } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import {
  mapAssessment,
  type AcademicCatalog,
  type ApiAssessment,
} from "@/lib/live-types";
import type { Assessment } from "@/lib/types";

export function LiveAssessments() {
  const [assessments, setAssessments] = useState<Assessment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [rows, catalog] = await Promise.all([
        apiFetch<ApiAssessment[]>("/assessments"),
        apiFetch<AcademicCatalog>("/academic-catalog"),
      ]);
      setAssessments(rows.map((row) => mapAssessment(row, catalog)));
    } catch (requestError) {
      setAssessments([]);
      setError(apiErrorMessage(requestError, "Assessments could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  return (
    <div className="page">
      <PageHeader
        eyebrow="Assessment lifecycle · Live"
        title="Assessments"
        description="Create governed assessments from approved questions, publish them, and schedule eligible students."
        actions={
          <Link className="button button-primary" href="/assessments/new">
            <Plus size={15} /> Create assessment
          </Link>
        }
      />
      {loading && <LoadingState label="Loading live assessments…" />}
      {!loading && error && <ErrorState message={error} retry={() => void load()} />}
      {!loading && !error && !assessments.length && (
        <div className="empty-state">No assessments have been created yet.</div>
      )}
      {!loading && !error && (
        <section className="assessment-grid">
          {assessments.map((assessment) => (
            <article className="assessment-card" key={assessment.id}>
              <div className="assessment-card-top">
                <span className="assessment-code">{assessment.code}</span>
                <StatusBadge status={assessment.status} />
              </div>
              <h2>{assessment.title}</h2>
              <p>{assessment.type} · {assessment.subject}</p>
              <div className="assessment-stats">
                <div><strong>{assessment.questionCount}</strong><span>Questions</span></div>
                <div><strong>{assessment.totalMarks}</strong><span>Marks</span></div>
                <div><strong>{assessment.durationMinutes}m</strong><span>Duration</span></div>
              </div>
              <div className="assessment-window">
                <CalendarClock size={14} />
                {assessment.startAt
                  ? `${assessment.startAt} – ${assessment.endAt}`
                  : "Not scheduled"}
              </div>
              <Link
                className="button button-secondary button-full"
                href={`/assessments/${assessment.id}`}
                style={{ marginTop: 16 }}
              >
                Open lifecycle
              </Link>
            </article>
          ))}
        </section>
      )}
    </div>
  );
}
