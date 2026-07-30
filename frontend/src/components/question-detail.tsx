"use client";

import Link from "next/link";
import { ArrowLeft, Check, Pencil, Send } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import {
  mapQuestion,
  type AcademicCatalog,
  type ApiQuestion,
} from "@/lib/live-types";
import type { Question } from "@/lib/types";

export function QuestionDetail({ questionId }: { questionId: string }) {
  const [question, setQuestion] = useState<Question | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [row, catalog] = await Promise.all([
        apiFetch<ApiQuestion>(`/questions/${questionId}`),
        apiFetch<AcademicCatalog>("/academic-catalog"),
      ]);
      setQuestion(mapQuestion(row, catalog));
    } catch (requestError) {
      setQuestion(null);
      setError(apiErrorMessage(requestError, "Question details could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, [questionId]);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  async function submitForReview() {
    setBusy(true);
    setMessage("");
    setError("");
    try {
      const [row, catalog] = await Promise.all([
        apiFetch<ApiQuestion>(`/questions/${questionId}/submit`, {
          method: "POST",
        }),
        apiFetch<AcademicCatalog>("/academic-catalog"),
      ]);
      setQuestion(mapQuestion(row, catalog));
      setMessage("Question submitted to the live review queue.");
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Question could not be submitted."));
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <div className="page">
        <LoadingState label="Loading live question details…" />
      </div>
    );
  }
  if (!question) {
    return (
      <div className="page">
        <ErrorState message={error} retry={() => void load()} />
      </div>
    );
  }

  return (
    <div className="page">
      <Link className="button button-ghost" href="/question-bank">
        <ArrowLeft size={15} /> Back to Question Bank
      </Link>
      <PageHeader
        eyebrow={`${question.code} · Live`}
        title="Question details"
        description={`Version ${question.version} · ${question.author}`}
        actions={
          <>
            {question.status === "DRAFT" && (
              <Link
                className="button button-secondary"
                href={`/question-bank/${question.id}/edit`}
              >
                <Pencil size={15} /> Edit draft
              </Link>
            )}
            {question.status === "DRAFT" && (
              <button
                className="button button-primary"
                disabled={busy}
                onClick={submitForReview}
                type="button"
              >
                <Send size={15} /> {busy ? "Submitting…" : "Submit for review"}
              </button>
            )}
          </>
        }
      />
      {message && <div className="success-banner">{message}</div>}
      {error && <div className="form-error" role="alert">{error}</div>}

      <div className="content-grid">
        <article className="question-card">
          <div className="question-meta-row">
            <StatusBadge status={question.status} />
            <span className="badge badge-neutral">
              {question.type === "SINGLE_CORRECT"
                ? "Single Correct"
                : "Multiple Correct"}
            </span>
            <span className="badge badge-info">{question.difficulty}</span>
            <span className="badge badge-neutral">{question.marks} marks</span>
          </div>
          <p className="question-stem">{question.stem}</p>
          <div className="option-list">
            {question.options.map((option) => (
              <div
                className={`option-row ${option.correct ? "correct" : ""}`}
                key={option.id}
              >
                <span className="option-label">{option.label}</span>
                <p>{option.text}</p>
                {option.correct && <Check size={17} color="#157347" />}
              </div>
            ))}
          </div>
          {question.explanation && (
            <div className="explanation">
              <strong>Explanation</strong>
              <br />
              {question.explanation}
            </div>
          )}
        </article>

        <aside>
          <section className="panel">
            <div className="panel-header"><h2>Metadata</h2></div>
            <dl className="definition-list">
              {[
                ["Subject", question.subject],
                ["Topic", question.topic],
                ["Sub-topic", question.subTopic ?? "—"],
                ["Bloom’s level", question.bloomLevel],
                ["Marks", String(question.marks)],
                ["Negative marks", String(question.negativeMarks)],
                ["Language", "English"],
                ["Last updated", question.updatedAt],
              ].map(([label, value]) => (
                <div className="definition-row" key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
          </section>
          <section className="panel" style={{ marginTop: 16 }}>
            <div className="panel-header"><h2>Governance</h2></div>
            <div className="activity-list">
              <div className="activity-item">
                <span className="activity-dot" />
                <div className="activity-copy">
                  <strong>Version {question.version} persisted</strong>
                  <span>{question.updatedAt}</span>
                </div>
              </div>
              {question.status !== "DRAFT" && (
                <div className="activity-item">
                  <span className="activity-dot" />
                  <div className="activity-copy">
                    <strong>Academic governance active</strong>
                    <span>Every decision is retained in audit history.</span>
                  </div>
                </div>
              )}
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}
