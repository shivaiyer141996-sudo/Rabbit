"use client";

import {
  AlertTriangle,
  BarChart3,
  Filter,
  GraduationCap,
  RotateCcw,
  Search,
  UsersRound,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { AcademicCatalog } from "@/lib/live-types";
import type {
  StudentPerformanceReport,
  StudentReport,
  StudentReportRow,
} from "@/lib/types";

interface StudentReportFilters {
  query: string;
  subjectId: string;
  assessmentType: string;
  departmentId: string;
  sectionId: string;
  submittedFrom: string;
  submittedTo: string;
}

const initialFilters: StudentReportFilters = {
  query: "",
  subjectId: "",
  assessmentType: "",
  departmentId: "",
  sectionId: "",
  submittedFrom: "",
  submittedTo: "",
};

const assessmentTypes = [
  "PRACTICE_ASSESSMENT",
  "CLASS_TEST",
  "UNIT_TEST",
  "CHAPTER_TEST",
  "MID_TERM_EXAMINATION",
  "FINAL_EXAMINATION",
  "MOCK_TEST",
];

function reportQuery(filters: StudentReportFilters) {
  const query = new URLSearchParams();
  if (filters.query.trim()) query.set("query", filters.query.trim());
  if (filters.subjectId) query.set("subjectId", filters.subjectId);
  if (filters.assessmentType) query.set("assessmentType", filters.assessmentType);
  if (filters.departmentId) query.set("departmentId", filters.departmentId);
  if (filters.sectionId) query.set("sectionId", filters.sectionId);
  if (filters.submittedFrom) {
    query.set(
      "submittedFrom",
      new Date(`${filters.submittedFrom}T00:00:00`).toISOString(),
    );
  }
  if (filters.submittedTo) {
    const exclusiveEnd = new Date(`${filters.submittedTo}T00:00:00`);
    exclusiveEnd.setDate(exclusiveEnd.getDate() + 1);
    query.set("submittedBefore", exclusiveEnd.toISOString());
  }
  return query.toString();
}

export function StudentReportsPanel() {
  const [catalog, setCatalog] = useState<AcademicCatalog | null>(null);
  const [filters, setFilters] = useState(initialFilters);
  const [report, setReport] = useState<StudentReport | null>(null);
  const [selected, setSelected] = useState<StudentReportRow | null>(null);
  const [performance, setPerformance] = useState<StudentPerformanceReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState("");

  const loadReport = useCallback(async (nextFilters: StudentReportFilters) => {
    setLoading(true);
    setError("");
    try {
      const query = reportQuery(nextFilters);
      setReport(await apiFetch<StudentReport>(`/reports/students${query ? `?${query}` : ""}`));
    } catch (requestError) {
      setReport(null);
      setError(apiErrorMessage(requestError, "Student reports could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    apiFetch<AcademicCatalog>("/academic-catalog")
      .then((value) => {
        if (active) setCatalog(value);
      })
      .catch((requestError) => {
        if (active) setError(apiErrorMessage(requestError, "Report filters could not be loaded."));
      });
    const initial = window.setTimeout(
      () => void loadReport(initialFilters),
      0,
    );
    return () => {
      active = false;
      window.clearTimeout(initial);
    };
  }, [loadReport]);

  const visibleSections = useMemo(
    () =>
      catalog?.sections.filter(
        (section) => !filters.departmentId || section.departmentId === filters.departmentId,
      ) ?? [],
    [catalog, filters.departmentId],
  );

  async function openStudent(row: StudentReportRow) {
    setSelected(row);
    setPerformance(null);
    setDetailLoading(true);
    setError("");
    try {
      setPerformance(
        await apiFetch<StudentPerformanceReport>(`/reports/students/${row.studentUserId}`),
      );
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "The individual student report could not be loaded."));
    } finally {
      setDetailLoading(false);
    }
  }

  function update<Key extends keyof StudentReportFilters>(
    key: Key,
    value: StudentReportFilters[Key],
  ) {
    setFilters((current) => ({
      ...current,
      [key]: value,
      ...(key === "departmentId" ? { sectionId: "" } : {}),
    }));
  }

  function reset() {
    setFilters(initialFilters);
    setSelected(null);
    setPerformance(null);
    void loadReport(initialFilters);
  }

  return (
    <div className="report-stack">
      <section className="panel report-filter-panel" aria-label="Student report filters">
        <div className="panel-header">
          <div>
            <h2>Student report filters</h2>
            <p>Results are limited to published evaluations in this organisation.</p>
          </div>
          <Filter size={18} />
        </div>
        <div className="report-filter-grid">
          <div className="field report-search-field">
            <label htmlFor="student-report-query">Student name or email</label>
            <div className="search-wrap">
              <Search size={15} />
              <input
                id="student-report-query"
                onChange={(event) => update("query", event.target.value)}
                placeholder="Search students"
                value={filters.query}
              />
            </div>
          </div>
          <div className="field">
            <label htmlFor="student-report-subject">Subject</label>
            <select id="student-report-subject" onChange={(event) => update("subjectId", event.target.value)} value={filters.subjectId}>
              <option value="">All subjects</option>
              {catalog?.subjects.filter((item) => item.active).map((item) => (
                <option key={item.id} value={item.id}>{item.name}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="student-report-type">Assessment type</label>
            <select id="student-report-type" onChange={(event) => update("assessmentType", event.target.value)} value={filters.assessmentType}>
              <option value="">All types</option>
              {assessmentTypes.map((item) => (
                <option key={item} value={item}>{item.replaceAll("_", " ")}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="student-report-department">Department</label>
            <select id="student-report-department" onChange={(event) => update("departmentId", event.target.value)} value={filters.departmentId}>
              <option value="">All departments</option>
              {catalog?.departments.filter((item) => item.active).map((item) => (
                <option key={item.id} value={item.id}>{item.name}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="student-report-section">Section</label>
            <select id="student-report-section" onChange={(event) => update("sectionId", event.target.value)} value={filters.sectionId}>
              <option value="">All sections</option>
              {visibleSections.filter((item) => item.active).map((item) => (
                <option key={item.id} value={item.id}>{item.departmentName} · {item.name}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="student-report-from">Submitted from</label>
            <input id="student-report-from" onChange={(event) => update("submittedFrom", event.target.value)} type="date" value={filters.submittedFrom} />
          </div>
          <div className="field">
            <label htmlFor="student-report-to">Submitted to</label>
            <input id="student-report-to" onChange={(event) => update("submittedTo", event.target.value)} type="date" value={filters.submittedTo} />
          </div>
          <div className="report-filter-actions">
            <button className="button button-primary" onClick={() => void loadReport(filters)} type="button">
              <Filter size={15} /> Apply filters
            </button>
            <button className="button button-secondary" onClick={reset} type="button">
              <RotateCcw size={15} /> Reset
            </button>
          </div>
        </div>
      </section>

      {loading && <LoadingState label="Building the filtered student report…" />}
      {!loading && error && !report && (
        <ErrorState message={error} retry={() => void loadReport(filters)} />
      )}
      {!loading && report && (
        <>
          {error && <div className="form-error" role="alert">{error}</div>}
          <section className="metrics-grid report-metrics">
            {[
              [UsersRound, "Students", report.totalStudents, `${report.studentsWithResults} with published results`],
              [GraduationCap, "Published results", report.publishedResults, "Filtered governed data"],
              [BarChart3, "Average score", `${report.averagePercentage}%`, "Across filtered results"],
              [AlertTriangle, "At-risk students", report.atRiskStudents, "Two low published scores"],
            ].map(([Icon, label, value, context]) => {
              const MetricIcon = Icon as typeof UsersRound;
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

          <section className="comparison-grid">
            {[
              ["Department comparison", report.departments],
              ["Section comparison", report.sections],
            ].map(([title, groups]) => (
              <article className="panel report-table-panel" key={String(title)}>
                <div className="panel-header"><h2>{String(title)}</h2></div>
                <div className="data-table-wrap">
                  <table className="data-table">
                    <thead><tr><th>Group</th><th>Students</th><th>Results</th><th>Average</th><th>Pass rate</th></tr></thead>
                    <tbody>
                      {(groups as StudentReport["departments"]).map((group) => (
                        <tr key={`${title}-${group.groupId ?? group.label}`}>
                          <td><strong>{group.label}</strong></td>
                          <td>{group.studentCount}</td>
                          <td>{group.publishedResults}</td>
                          <td>{group.averagePercentage}%</td>
                          <td>{group.passRate}%</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </article>
            ))}
          </section>

          <section className="panel report-table-panel">
            <div className="panel-header">
              <div><h2>Student results</h2><p>Open a student for their complete published history.</p></div>
            </div>
            {!report.students.length ? (
              <div className="empty-state">No students match the selected filters.</div>
            ) : (
              <div className="data-table-wrap">
                <table className="data-table">
                  <thead><tr><th>Student</th><th>Department / section</th><th>Results</th><th>Average</th><th>Best</th><th>Progress</th><th aria-label="Open" /></tr></thead>
                  <tbody>
                    {report.students.map((row) => (
                      <tr key={row.studentUserId}>
                        <td><strong>{row.studentName}</strong><span className="table-subtitle">{row.studentEmail}</span></td>
                        <td>{row.departmentName} · {row.sectionName}</td>
                        <td>{row.publishedResults}</td>
                        <td>{row.averagePercentage}%</td>
                        <td>{row.bestPercentage}%</td>
                        <td><span className={`badge ${row.atRisk ? "badge-danger" : "badge-info"}`}>{row.atRisk ? "NEEDS SUPPORT" : row.trajectory}</span></td>
                        <td><button className="button button-ghost" onClick={() => void openStudent(row)} type="button">Open report</button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}

      {selected && (
        <section className="panel student-detail-panel" aria-live="polite">
          <div className="panel-header">
            <div><h2>{selected.studentName}</h2><p>Individual published performance history</p></div>
            <button className="icon-button" onClick={() => { setSelected(null); setPerformance(null); }} aria-label="Close individual student report" type="button"><X size={17} /></button>
          </div>
          {detailLoading && <LoadingState label="Loading individual performance…" />}
          {!detailLoading && performance && (
            <>
              <div className="publication-strip">
                <span><strong>{performance.averagePercentage}%</strong> average</span>
                <span><strong>{performance.bestPercentage}%</strong> best</span>
                <span><strong>{performance.trajectory}</strong> trajectory</span>
                <span><strong>{performance.atRisk ? "Yes" : "No"}</strong> at risk</span>
              </div>
              <div className="data-table-wrap">
                <table className="data-table">
                  <thead><tr><th>Assessment</th><th>Submitted</th><th>Score</th><th>Percentage</th><th>Grade</th><th>Progress</th></tr></thead>
                  <tbody>
                    {performance.results.map((result) => (
                      <tr key={result.attemptId}>
                        <td><strong>{result.assessmentTitle}</strong></td>
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
            </>
          )}
        </section>
      )}
    </div>
  );
}
