"use client";

import Link from "next/link";
import {
  ArrowLeft,
  CheckCircle2,
  Download,
  RefreshCw,
  Send,
} from "lucide-react";
import { useEffect, useState } from "react";
import { PageHeader } from "@/components/page-header";
import { apiFetch, ApiError } from "@/lib/api";
import { demoAssessmentReport } from "@/lib/intelligence-demo";
import type { AssessmentReport } from "@/lib/types";

interface EvaluationSummary {
  assessmentId: string;
  evaluatedCount: number;
  pendingPublicationCount: number;
  publishedCount: number;
}

export function AssessmentReportDetail({ assessmentId }: { assessmentId: string }) {
  const [report, setReport] =
    useState<AssessmentReport>({ ...demoAssessmentReport, assessmentId });
  const [evaluation, setEvaluation] = useState<EvaluationSummary | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function load() {
    const [nextReport, nextEvaluation] = await Promise.all([
      apiFetch<AssessmentReport>(`/reports/assessments/${assessmentId}`),
      apiFetch<EvaluationSummary>(
        `/evaluation/assessments/${assessmentId}/results`,
      ),
    ]);
    setReport(nextReport);
    setEvaluation(nextEvaluation);
  }

  useEffect(() => {
    let active = true;
    Promise.all([
      apiFetch<AssessmentReport>(`/reports/assessments/${assessmentId}`),
      apiFetch<EvaluationSummary>(
        `/evaluation/assessments/${assessmentId}/results`,
      ),
    ])
      .then(([nextReport, nextEvaluation]) => {
        if (!active) return;
        setReport(nextReport);
        setEvaluation(nextEvaluation);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [assessmentId]);

  async function publish() {
    setBusy(true);
    setMessage("");
    try {
      const response = await apiFetch<{ publishedCount: number }>(
        `/evaluation/assessments/${assessmentId}/publish`,
        { method: "POST" },
      );
      setMessage(`${response.publishedCount} result(s) published to students.`);
      await load();
    } catch (error) {
      setMessage(
        error instanceof ApiError ? error.message : "Results could not be published.",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <Link className="button button-ghost" href="/reports">
        <ArrowLeft size={15} /> Back to reports
      </Link>
      <PageHeader
        eyebrow="Assessment intelligence"
        title={report.title}
        description={`Generated ${new Date(report.generatedAt).toLocaleString()} · ${report.generatedBy}`}
        actions={
          <>
            <a
              className="button button-secondary"
              href={`/gateway/backend/reports/assessments/${assessmentId}/export`}
            >
              <Download size={15} /> Export CSV
            </a>
            <button
              className="button button-primary"
              disabled={busy || !evaluation?.pendingPublicationCount}
              onClick={publish}
            >
              {busy ? <RefreshCw className="spin" size={15} /> : <Send size={15} />}
              Publish pending results
            </button>
          </>
        }
      />

      {message && <div className="success-banner">{message}</div>}

      <section className="metrics-grid report-metrics">
        {[
          ["Submissions", report.submissions],
          ["Average", `${report.averagePercentage}%`],
          ["Highest", `${report.highestPercentage}%`],
          ["Pass rate", `${report.passRate}%`],
        ].map(([label, value]) => (
          <article className="metric-card" key={String(label)}>
            <span className="metric-label">{label}</span>
            <strong>{value}</strong>
            <small>Published evaluations</small>
          </article>
        ))}
      </section>

      {evaluation && (
        <div className="publication-strip">
          <CheckCircle2 size={18} />
          <span><strong>{evaluation.publishedCount}</strong> published</span>
          <span><strong>{evaluation.pendingPublicationCount}</strong> awaiting publication</span>
          <span><strong>{evaluation.evaluatedCount}</strong> evaluated</span>
        </div>
      )}

      <section className="panel report-table-panel">
        <div className="panel-header"><h2>Student results</h2></div>
        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Attempt</th>
                <th>Submitted</th>
                <th>Score</th>
                <th>Percentage</th>
                <th>Grade</th>
                <th>Progress</th>
              </tr>
            </thead>
            <tbody>
              {report.studentResults.map((result) => (
                <tr key={result.attemptId}>
                  <td><code>{result.attemptId.slice(0, 8)}</code></td>
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
      </section>

      <section className="panel report-table-panel">
        <div className="panel-header">
          <div>
            <h2>Question performance</h2>
            <p>Items below 0.20 discrimination are flagged for review.</p>
          </div>
        </div>
        <div className="data-table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Correct rate</th>
                <th>Difficulty index</th>
                <th>Discrimination</th>
                <th>Quality</th>
              </tr>
            </thead>
            <tbody>
              {report.questionAnalytics.map((question) => (
                <tr key={question.questionId}>
                  <td><strong>{question.code}</strong></td>
                  <td>{question.correctRate}%</td>
                  <td>{question.difficultyIndex.toFixed(2)}</td>
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
    </div>
  );
}
