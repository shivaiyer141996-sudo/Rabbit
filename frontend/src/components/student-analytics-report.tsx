"use client";

import Image from "next/image";
import Link from "next/link";
import {
  ArrowLeft,
  BarChart3,
  BookOpenCheck,
  CheckCircle2,
  Clock3,
  Gauge,
  Search,
  Target,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type {
  StudentAnalysisBreakdown,
  StudentAnalyticsReport,
} from "@/lib/types";

type AnalyticsTab = "subjects" | "topics" | "difficulties" | "time" | "questions";

function duration(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const remaining = seconds % 60;
  return minutes ? `${minutes}m ${remaining}s` : `${remaining}s`;
}

function BreakdownTable({
  rows,
  label,
}: {
  rows: StudentAnalysisBreakdown[];
  label: string;
}) {
  if (!rows.length) {
    return <div className="empty-state">Published results will build this analysis.</div>;
  }
  return (
    <div className="data-table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>{label}</th>
            <th>Questions</th>
            <th>Answered</th>
            <th>Correct</th>
            <th>Marks</th>
            <th>Performance</th>
            <th>Avg. time</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.key}>
              <td><strong>{row.label.replaceAll("_", " ")}</strong></td>
              <td>{row.questionCount}</td>
              <td>{row.answeredQuestions}</td>
              <td>{row.correctAnswers}</td>
              <td>{row.awardedMarks} / {row.maxMarks}</td>
              <td>{row.percentage}%</td>
              <td>{duration(row.averageTimeSeconds)}</td>
              <td>
                <span className={`badge ${row.weak ? "badge-danger" : "badge-success"}`}>
                  {row.weak ? "NEEDS FOCUS" : "ON TRACK"}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function StudentAnalyticsReportView() {
  const [report, setReport] = useState<StudentAnalyticsReport | null>(null);
  const [tab, setTab] = useState<AnalyticsTab>("subjects");
  const [questionQuery, setQuestionQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setReport(await apiFetch<StudentAnalyticsReport>("/reports/students/me/analytics"));
    } catch (requestError) {
      setReport(null);
      setError(apiErrorMessage(requestError, "Your performance report could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  const visibleQuestions = useMemo(() => {
    const query = questionQuery.trim().toLowerCase();
    if (!report || !query) return report?.questionReview ?? [];
    return report.questionReview.filter((item) =>
      `${item.questionCode} ${item.stem} ${item.assessmentTitle} ${item.subjectName} ${item.topicName}`
        .toLowerCase()
        .includes(query),
    );
  }, [questionQuery, report]);

  return (
    <div className="student-home student-report-page">
      <header className="student-home-header">
        <Link className="player-brand" href="/dashboard">
          <Image src="/rabbit-mark.svg" width={42} height={42} alt="" />
          <div className="player-title"><strong>Rabbit AiP</strong><span>Student performance report</span></div>
        </Link>
        <div className="header-actions">
          <Link className="button button-secondary" href="/student/history">Attempt history</Link>
          <Link className="button button-secondary" href="/dashboard"><ArrowLeft size={15} /> Dashboard</Link>
        </div>
      </header>
      <main className="student-home-main">
        <div>
          <span className="visual-eyebrow">Published academic intelligence</span>
          <h1>{report ? `${report.studentName}'s performance` : "Your performance report"}</h1>
          <p>Understand where you are strong, where to focus, and how you use assessment time.</p>
        </div>

        {loading && <LoadingState label="Building your published performance report…" />}
        {!loading && !report && <ErrorState message={error} retry={() => void load()} />}
        {!loading && report && (
          <>
            <section className="metrics-grid report-metrics student-report-metrics">
              {[
                [BarChart3, "Published attempts", report.publishedAttempts, "Released by your institution"],
                [Target, "Average score", `${report.averagePercentage}%`, "Across published attempts"],
                [BookOpenCheck, "Questions analysed", report.analysedQuestions, "Published answer data"],
                [Clock3, "Total assessment time", duration(report.totalTimeSeconds), "Across published attempts"],
              ].map(([Icon, label, value, context]) => {
                const MetricIcon = Icon as typeof BarChart3;
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

            <div className="segmented-control student-analysis-tabs" role="tablist" aria-label="Student analysis view">
              {([
                ["subjects", "Subject-wise"],
                ["topics", "Topic-wise"],
                ["difficulties", "Difficulty-wise"],
                ["time", "Time analysis"],
                ["questions", "Question review"],
              ] as Array<[AnalyticsTab, string]>).map(([value, label]) => (
                <button key={value} className={tab === value ? "active" : ""} onClick={() => setTab(value)} role="tab" aria-selected={tab === value} type="button">
                  {label}
                </button>
              ))}
            </div>

            {tab === "subjects" && <section className="panel report-table-panel student-analysis-panel"><div className="panel-header"><div><h2>Subject-wise analysis</h2><p>Marks, accuracy, and time across each subject.</p></div></div><BreakdownTable rows={report.subjects} label="Subject" /></section>}
            {tab === "topics" && <section className="panel report-table-panel student-analysis-panel"><div className="panel-header"><div><h2>Topic-wise analysis</h2><p>Topics below the institution support threshold are highlighted.</p></div></div><BreakdownTable rows={report.topics} label="Topic" /></section>}
            {tab === "difficulties" && <section className="panel report-table-panel student-analysis-panel"><div className="panel-header"><div><h2>Difficulty-wise analysis</h2><p>Compare your performance across Easy, Medium, and Hard questions.</p></div></div><BreakdownTable rows={report.difficulties} label="Difficulty" /></section>}

            {tab === "time" && (
              <section className="panel report-table-panel student-analysis-panel">
                <div className="panel-header"><div><h2>Time analysis</h2><p>Actual attempt duration and question-level response time.</p></div><Gauge size={18} /></div>
                {!report.timeAnalysis.length ? <div className="empty-state">No published attempt time is available.</div> : (
                  <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Assessment</th><th>Submitted</th><th>Time used</th><th>Allowed</th><th>Utilisation</th><th>Avg. / question</th><th>Slowest question</th></tr></thead><tbody>
                    {report.timeAnalysis.map((item) => <tr key={item.attemptId}><td><strong>{item.assessmentTitle}</strong></td><td>{new Date(item.submittedAt).toLocaleString()}</td><td>{duration(item.timeTakenSeconds)}</td><td>{duration(item.allowedSeconds)}</td><td>{item.utilisationPercentage}%</td><td>{duration(item.averageQuestionSeconds)}</td><td>{duration(item.slowestQuestionSeconds)}</td></tr>)}
                  </tbody></table></div>
                )}
              </section>
            )}

            {tab === "questions" && (
              <section className="panel student-question-review">
                <div className="panel-header">
                  <div><h2>Question review</h2><p>Answer keys and explanations from published results only.</p></div>
                  <div className="search-wrap compact-search"><Search size={15} /><input aria-label="Search question review" value={questionQuery} onChange={(event) => setQuestionQuery(event.target.value)} placeholder="Search question, assessment, subject, or topic" /></div>
                </div>
                {!visibleQuestions.length ? <div className="empty-state">No published questions match your search.</div> : visibleQuestions.map((question, index) => (
                  <article className={`result-question ${question.correct ? "correct" : "incorrect"}`} key={`${question.attemptId}-${question.questionId}`}>
                    <div className="result-question-heading"><span>{index + 1}</span><div><strong>{question.questionCode} · {question.stem}</strong><span className="table-subtitle">{question.assessmentTitle} · {question.subjectName} · {question.topicName} · {question.difficulty}</span></div><span>{question.awardedMarks}/{question.maxMarks}</span></div>
                    <div className="review-answer-grid">
                      <div><span>Your answer</span>{question.selectedOptions.length ? question.selectedOptions.map((option) => <strong key={option.optionId}>{option.label}. {option.text}</strong>) : <strong>Not answered</strong>}</div>
                      <div><span>Correct answer</span>{question.correctOptions.map((option) => <strong key={option.optionId}>{option.label}. {option.text}</strong>)}</div>
                    </div>
                    <div className="question-review-meta">
                      <span className={`badge ${question.correct ? "badge-success" : "badge-danger"}`}>{question.correct ? <CheckCircle2 size={12} /> : <XCircle size={12} />}{question.correct ? "CORRECT" : question.answered ? "INCORRECT" : "UNANSWERED"}</span>
                      <span><Clock3 size={13} /> {duration(question.timeSpentSeconds)}</span>
                      <Link href={`/results/${question.attemptId}`}>Open full result</Link>
                    </div>
                    {question.explanation && <p>{question.explanation}</p>}
                  </article>
                ))}
              </section>
            )}
          </>
        )}
      </main>
    </div>
  );
}
