import Link from "next/link";
import { CalendarClock, Plus } from "lucide-react";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { assessments } from "@/lib/demo-data";

export default function AssessmentsPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="Assessment lifecycle"
        title="Assessments"
        description="Create governed assessments from approved questions, publish them, and schedule eligible students."
        actions={
          <Link className="button button-primary" href="/assessments/new">
            <Plus size={15} /> Create assessment
          </Link>
        }
      />

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
              <div>
                <strong>{assessment.questionCount}</strong>
                <span>Questions</span>
              </div>
              <div>
                <strong>{assessment.totalMarks}</strong>
                <span>Marks</span>
              </div>
              <div>
                <strong>{assessment.durationMinutes}m</strong>
                <span>Duration</span>
              </div>
            </div>
            <div className="assessment-window">
              <CalendarClock size={14} />
              {assessment.startAt
                ? `${assessment.startAt} – ${assessment.endAt}`
                : "Not scheduled"}
            </div>
            {assessment.status === "SCHEDULED" && (
              <Link
                className="button button-secondary button-full"
                href={`/student/assessments/${assessment.id}`}
                style={{ marginTop: 16 }}
              >
                Preview assessment player
              </Link>
            )}
          </article>
        ))}
      </section>
    </div>
  );
}
