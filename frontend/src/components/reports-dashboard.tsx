"use client";

import Link from "next/link";
import {
  AlertTriangle,
  ArrowRight,
  BarChart3,
  Download,
  GraduationCap,
  LineChart,
  UsersRound,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { StudentReportsPanel } from "@/components/student-reports-panel";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { CommercialAccess, MeProfile } from "@/lib/live-types";
import type {
  FacultyPerformance,
  IntelligenceOverview,
  QuestionPerformance,
} from "@/lib/types";

type ReportTab = "overview" | "students" | "questions" | "faculty";

export function ReportsDashboard() {
  const [tab, setTab] = useState<ReportTab>("overview");
  const [overview, setOverview] = useState<IntelligenceOverview | null>(null);
  const [questionRows, setQuestionRows] = useState<QuestionPerformance[]>([]);
  const [facultyRows, setFacultyRows] = useState<FacultyPerformance[]>([]);
  const [role, setRole] = useState<MeProfile["role"] | null>(null);
  const [commercialAccess, setCommercialAccess] = useState<CommercialAccess | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [profile, access] = await Promise.all([
        apiFetch<MeProfile>("/auth/me"),
        apiFetch<CommercialAccess>("/commercial-access"),
      ]);
      setRole(profile.role);
      setCommercialAccess(access);
      const institutionAnalytics = !access.enforcementEnabled
        || access.entitlements.includes("INSTITUTION_ANALYTICS");
      if (institutionAnalytics) {
        const [nextOverview, nextQuestions, nextFaculty] = await Promise.all([
          apiFetch<IntelligenceOverview>("/reports/overview"),
          apiFetch<QuestionPerformance[]>("/reports/questions"),
          ["SUPER_ADMIN", "ORG_ADMIN", "ACADEMIC_HEAD"].includes(profile.role)
            && (!access.enforcementEnabled
              || access.entitlements.includes("TEACHER_ANALYTICS"))
            ? apiFetch<FacultyPerformance[]>("/reports/faculty")
            : Promise.resolve([]),
        ]);
        setOverview(nextOverview);
        setQuestionRows(nextQuestions);
        setFacultyRows(nextFaculty);
      } else if (access.entitlements.includes("STUDENT_EVALUATION")) {
        setTab("students");
        setOverview(null);
        setQuestionRows([]);
        setFacultyRows([]);
      } else {
        throw new Error("Student evaluation is not included in the current plan.");
      }
    } catch (requestError) {
      setOverview(null);
      setError(apiErrorMessage(requestError, "Reports could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  const distributionMax = useMemo(
    () =>
      Math.max(
        ...(overview?.scoreDistribution.map((item) => item.value) ?? []),
        1,
      ),
    [overview],
  );
  const tabs = useMemo(
    () => {
      const institutionAnalytics = !commercialAccess?.enforcementEnabled
        || commercialAccess.entitlements.includes("INSTITUTION_ANALYTICS");
      if (!institutionAnalytics) return ["students"] as ReportTab[];
      return (["overview", "students", "questions", "faculty"] as ReportTab[]).filter(
        (item) =>
          item !== "faculty"
          || ((!commercialAccess?.enforcementEnabled
            || commercialAccess.entitlements.includes("TEACHER_ANALYTICS"))
            && (role === "SUPER_ADMIN"
              || role === "ORG_ADMIN"
              || role === "ACADEMIC_HEAD")),
      );
    },
    [commercialAccess, role],
  );

  const studentOnly = commercialAccess?.enforcementEnabled
    && commercialAccess.entitlements.includes("STUDENT_EVALUATION")
    && !commercialAccess.entitlements.includes("INSTITUTION_ANALYTICS");

  if (loading) {
    return <div className="page"><LoadingState label="Loading published report data…" /></div>;
  }
  if (!overview && !studentOnly) {
    return <div className="page"><ErrorState message={error} retry={() => void load()} /></div>;
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow={studentOnly ? "Pro · Student evaluation" : "Reports & academic intelligence · Live"}
        title={studentOnly ? "Understand every student's progress" : "Turn results into action"}
        description={studentOnly
          ? "Detailed student evaluation is included in Pro; institution and teacher analytics require Legend."
          : "Every insight is calculated from published MCQ evaluations within your organisation."}
        actions={
          <>
            {(!commercialAccess?.enforcementEnabled
              || commercialAccess.entitlements.includes("TEACHER_ANALYTICS")) && (
              <Link className="button button-primary" href="/reports/teacher">
                <UsersRound size={15} /> Teacher reports
              </Link>
            )}
            <button className="button button-secondary" onClick={() => window.print()}>
              <Download size={15} /> Print view
            </button>
          </>
        }
      />

      <div className="segmented-control" role="tablist" aria-label="Report category">
        {tabs.map((item) => (
          <button
            aria-selected={tab === item}
            className={tab === item ? "active" : ""}
            key={item}
            onClick={() => setTab(item)}
            role="tab"
          >
            {item === "overview"
              ? "Institution overview"
              : item === "students"
                ? "Student reports"
              : item === "questions"
                ? "Question analytics"
                : "Faculty contribution"}
          </button>
        ))}
      </div>

      {tab === "overview" && overview && (
        <>
          <section className="metrics-grid report-metrics">
            {[
              [GraduationCap, "Published results", overview.publishedResults, "Governed data set"],
              [LineChart, "Average score", `${overview.averageScore}%`, "Across published results"],
              [BarChart3, "Pass rate", `${overview.passRate}%`, "Organisation threshold"],
              [AlertTriangle, "At-risk students", overview.atRiskStudents, "Two low scores in sequence"],
            ].map(([Icon, label, value, context]) => {
              const IconComponent = Icon as typeof GraduationCap;
              return (
                <article className="metric-card" key={String(label)}>
                  <span className="metric-icon"><IconComponent size={18} /></span>
                  <span className="metric-label">{String(label)}</span>
                  <strong>{String(value)}</strong>
                  <small>{String(context)}</small>
                </article>
              );
            })}
          </section>

          <section className="intelligence-grid">
            <article className="panel">
              <div className="panel-header">
                <div>
                  <h2>Score distribution</h2>
                  <p>Students grouped by percentage</p>
                </div>
              </div>
              <div className="horizontal-chart">
                {overview.scoreDistribution.map((item) => (
                  <div className="horizontal-row" key={item.label}>
                    <span>{item.label}</span>
                    <div className="horizontal-track">
                      <div
                        className="horizontal-fill"
                        style={{ width: `${(item.value / distributionMax) * 100}%` }}
                      />
                    </div>
                    <strong>{item.value}</strong>
                  </div>
                ))}
              </div>
              <div className="insight-callout">
                <UsersRound size={17} />
                <div>
                  <strong>{overview.completionRate}% completion rate</strong>
                  <span>Calculated from completed schedules and submissions.</span>
                </div>
              </div>
            </article>

            <article className="panel">
              <div className="panel-header">
                <div>
                  <h2>Performance over time</h2>
                  <p>Organisation average by month</p>
                </div>
              </div>
              <div className="trend-line" aria-label="Performance trend">
                {overview.performanceTrend.map((point) => (
                  <div className="trend-point" key={point.label}>
                    <strong>{point.value}%</strong>
                    <span>{point.label}</span>
                  </div>
                ))}
              </div>
            </article>
          </section>

          <section className="panel report-table-panel">
            <div className="panel-header">
              <div>
                <h2>Assessment reports</h2>
                <p>Open an assessment for student and question-level analysis.</p>
              </div>
            </div>
            <div className="data-table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Assessment</th>
                    <th>Status</th>
                    <th>Submissions</th>
                    <th>Average</th>
                    <th>Pass rate</th>
                    <th aria-label="Open" />
                  </tr>
                </thead>
                <tbody>
                  {overview.recentAssessments.map((assessment) => (
                    <tr key={assessment.assessmentId}>
                      <td><strong>{assessment.title}</strong></td>
                      <td><StatusBadge status={assessment.status} /></td>
                      <td>{assessment.submissions}</td>
                      <td>{assessment.averagePercentage}%</td>
                      <td>{assessment.passRate}%</td>
                      <td>
                        <Link
                          className="table-link"
                          href={`/reports/assessments/${assessment.assessmentId}`}
                          aria-label={`Open ${assessment.title}`}
                        >
                          <ArrowRight size={16} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}

      {tab === "students" && (
        <StudentReportsPanel
          showAssessmentLinks={!commercialAccess?.enforcementEnabled
            || commercialAccess.entitlements.includes("INSTITUTION_ANALYTICS")}
        />
      )}

      {tab === "questions" && overview && (
        <section className="panel report-table-panel">
          <div className="panel-header">
            <div>
              <h2>Question quality</h2>
              <p>Difficulty and discrimination are computed from published responses.</p>
            </div>
          </div>
          <div className="data-table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Question</th>
                  <th>Difficulty</th>
                  <th>Responses</th>
                  <th>Correct rate</th>
                  <th>Discrimination</th>
                  <th>Quality</th>
                </tr>
              </thead>
              <tbody>
                {questionRows.map((question) => (
                  <tr key={question.questionId}>
                    <td>
                      <strong>{question.code}</strong>
                      <span className="table-subtitle">{question.stem}</span>
                    </td>
                    <td><span className="badge badge-neutral">{question.difficulty}</span></td>
                    <td>{question.responseCount}</td>
                    <td>{question.correctRate}%</td>
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
      )}

      {tab === "faculty" && overview && (
        <section className="faculty-grid">
          {facultyRows.map((faculty) => (
            <article className="panel faculty-card" key={faculty.facultyUserId}>
              <div className="faculty-heading">
                <span className="avatar">
                  {faculty.facultyName.split(" ").map((part) => part[0]).join("").slice(0, 2)}
                </span>
                <div>
                  <strong>{faculty.facultyName}</strong>
                  <span>{faculty.averageStudentPercentage}% student average</span>
                </div>
              </div>
              <dl className="definition-list">
                <div className="definition-row"><dt>Questions authored</dt><dd>{faculty.questionsAuthored}</dd></div>
                <div className="definition-row"><dt>Approved</dt><dd>{faculty.approvedQuestions}</dd></div>
                <div className="definition-row"><dt>Assessments</dt><dd>{faculty.assessmentsCreated}</dd></div>
                <div className="definition-row"><dt>Submissions</dt><dd>{faculty.studentSubmissions}</dd></div>
              </dl>
            </article>
          ))}
          {!facultyRows.length && (
            <div className="empty-state">
              Faculty contribution is available to Organisation Admin and Academic Head roles.
            </div>
          )}
        </section>
      )}
    </div>
  );
}
