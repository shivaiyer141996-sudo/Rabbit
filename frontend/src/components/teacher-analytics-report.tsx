"use client";

import Link from "next/link";
import {
  AlertTriangle,
  ArrowLeft,
  BarChart3,
  Download,
  GraduationCap,
  Search,
  UsersRound,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { MeProfile } from "@/lib/live-types";
import type { FacultyPerformance, TeacherAnalyticsReport } from "@/lib/types";

function exportHref(extension: "pdf" | "xlsx", teacherUserId: string) {
  const query = teacherUserId ? `?teacherUserId=${encodeURIComponent(teacherUserId)}` : "";
  return `/gateway/backend/reports/teacher/export.${extension}${query}`;
}

export function TeacherAnalyticsReportView() {
  const [profile, setProfile] = useState<MeProfile | null>(null);
  const [teachers, setTeachers] = useState<FacultyPerformance[]>([]);
  const [teacherUserId, setTeacherUserId] = useState("");
  const [report, setReport] = useState<TeacherAnalyticsReport | null>(null);
  const [studentQuery, setStudentQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadReport = useCallback(async (selectedTeacher = "") => {
    setLoading(true);
    setError("");
    try {
      const query = selectedTeacher
        ? `?teacherUserId=${encodeURIComponent(selectedTeacher)}`
        : "";
      setReport(await apiFetch<TeacherAnalyticsReport>(`/reports/teacher${query}`));
    } catch (requestError) {
      setReport(null);
      setError(apiErrorMessage(requestError, "Teacher analytics could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    const initial = window.setTimeout(async () => {
      try {
        const nextProfile = await apiFetch<MeProfile>("/auth/me");
        if (!active) return;
        setProfile(nextProfile);
        if (["SUPER_ADMIN", "ORG_ADMIN", "ACADEMIC_HEAD"].includes(nextProfile.role)) {
          const nextTeachers = await apiFetch<FacultyPerformance[]>("/reports/faculty");
          if (active) setTeachers(nextTeachers);
        }
        if (active) await loadReport("");
      } catch (requestError) {
        if (active) {
          setLoading(false);
          setError(apiErrorMessage(requestError, "Teacher report access could not be verified."));
        }
      }
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(initial);
    };
  }, [loadReport]);

  const visibleStudents = useMemo(() => {
    const query = studentQuery.trim().toLowerCase();
    if (!report || !query) return report?.students ?? [];
    return report.students.filter((item) =>
      `${item.studentName} ${item.batchName}`.toLowerCase().includes(query),
    );
  }, [report, studentQuery]);

  const canSelectTeacher = profile
    ? ["SUPER_ADMIN", "ORG_ADMIN", "ACADEMIC_HEAD"].includes(profile.role)
    : false;

  return (
    <div className="page">
      <Link className="button button-ghost" href="/reports"><ArrowLeft size={15} /> Back to reports</Link>
      <PageHeader
        eyebrow="Teacher reports · Published results"
        title={report?.teacherName ?? "Teacher analytics"}
        description="Compare batch performance, individual students, and weak topics using governed published evaluations."
        actions={report ? <>
          <a className="button button-secondary" href={exportHref("pdf", teacherUserId)}><Download size={15} /> Export PDF</a>
          <a className="button button-secondary" href={exportHref("xlsx", teacherUserId)}><Download size={15} /> Export Excel</a>
        </> : undefined}
      />

      {canSelectTeacher && (
        <section className="panel teacher-scope-panel">
          <div className="field">
            <label htmlFor="teacher-report-scope">Teacher scope</label>
            <select
              id="teacher-report-scope"
              value={teacherUserId}
              onChange={(event) => {
                const value = event.target.value;
                setTeacherUserId(value);
                void loadReport(value);
              }}
            >
              <option value="">All teachers</option>
              {teachers.map((teacher) => <option value={teacher.facultyUserId} key={teacher.facultyUserId}>{teacher.facultyName}</option>)}
            </select>
          </div>
          <span>Faculty users are automatically restricted to their own assessments.</span>
        </section>
      )}

      {loading && <LoadingState label="Building teacher analytics…" />}
      {!loading && !report && <ErrorState message={error} retry={() => void loadReport(teacherUserId)} />}
      {!loading && report && (
        <>
          <section className="metrics-grid report-metrics">
            {[
              [BarChart3, "Assessments", report.assessmentCount, "In the selected teacher scope"],
              [GraduationCap, "Published submissions", report.publishedSubmissions, "Governed results"],
              [UsersRound, "Average score", `${report.averagePercentage}%`, "Across published submissions"],
              [AlertTriangle, "Weak topics", report.weakTopicCount, "Below the support threshold"],
            ].map(([Icon, label, value, context]) => {
              const MetricIcon = Icon as typeof BarChart3;
              return <article className="metric-card" key={String(label)}><span className="metric-icon"><MetricIcon size={18} /></span><span className="metric-label">{String(label)}</span><strong>{String(value)}</strong><small>{String(context)}</small></article>;
            })}
          </section>

          <section className="panel report-table-panel">
            <div className="panel-header"><div><h2>Batch analytics</h2><p>Completion, average, and pass rate by department and section.</p></div></div>
            {!report.batches.length ? <div className="empty-state">No eligible batch data is available for this teacher scope.</div> : <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Batch</th><th>Students</th><th>Assessments</th><th>Submissions</th><th>Students attempted</th><th>Completion</th><th>Average</th><th>Pass rate</th></tr></thead><tbody>
              {report.batches.map((batch) => <tr key={batch.sectionId ?? batch.batchName}><td><strong>{batch.batchName}</strong></td><td>{batch.studentCount}</td><td>{batch.assessmentCount}</td><td>{batch.submissionCount}</td><td>{batch.studentsAttempted}</td><td>{batch.completionRate}%</td><td>{batch.averagePercentage}%</td><td>{batch.passRate}%</td></tr>)}
            </tbody></table></div>}
          </section>

          <section className="panel report-table-panel">
            <div className="panel-header"><div><h2>Student comparison</h2><p>Ranked within the selected teacher scope using published results.</p></div><div className="search-wrap compact-search"><Search size={15} /><input aria-label="Search student comparison" value={studentQuery} onChange={(event) => setStudentQuery(event.target.value)} placeholder="Search student or batch" /></div></div>
            {!visibleStudents.length ? <div className="empty-state">No published student result matches this scope.</div> : <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Rank</th><th>Student</th><th>Batch</th><th>Attempts</th><th>Average</th><th>Best</th><th>Pass rate</th><th>Progress</th></tr></thead><tbody>
              {visibleStudents.map((student) => <tr key={student.studentUserId}><td><strong>#{student.rank}</strong></td><td><strong>{student.studentName}</strong></td><td>{student.batchName}</td><td>{student.publishedAttempts}</td><td>{student.averagePercentage}%</td><td>{student.bestPercentage}%</td><td>{student.passRate}%</td><td><span className={`badge ${student.atRisk ? "badge-danger" : "badge-info"}`}>{student.atRisk ? "NEEDS SUPPORT" : student.trajectory}</span></td></tr>)}
            </tbody></table></div>}
          </section>

          <section className="panel report-table-panel">
            <div className="panel-header"><div><h2>Weak-topic analysis</h2><p>Topics are flagged when marks fall below the organisation support threshold.</p></div></div>
            {!report.weakTopics.length ? <div className="empty-state">Published responses will build topic intelligence.</div> : <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Subject / topic</th><th>Questions</th><th>Responses</th><th>Marks performance</th><th>Correct rate</th><th>Avg. time</th><th>Status</th></tr></thead><tbody>
              {report.weakTopics.map((topic) => <tr key={topic.topicId}><td><strong>{topic.topicName}</strong><span className="table-subtitle">{topic.subjectName}</span></td><td>{topic.questionCount}</td><td>{topic.responseCount}</td><td>{topic.averageMarksPercentage}%</td><td>{topic.correctRate}%</td><td>{topic.averageTimeSeconds}s</td><td><span className={`badge ${topic.weak ? "badge-danger" : "badge-success"}`}>{topic.weak ? "WEAK" : "ON TRACK"}</span></td></tr>)}
            </tbody></table></div>}
          </section>
        </>
      )}
    </div>
  );
}
