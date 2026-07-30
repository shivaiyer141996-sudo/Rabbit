"use client";

import Image from "next/image";
import Link from "next/link";
import {
  ArrowLeft,
  CheckCircle2,
  Clock3,
  Eye,
  Hourglass,
  Trophy,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { ResultView } from "@/lib/live-types";

export function StudentResultView({ attemptId }: { attemptId: string }) {
  const [result, setResult] = useState<ResultView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setResult(await apiFetch<ResultView>(`/student/results/${attemptId}`));
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Result could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, [attemptId]);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  if (loading) {
    return (
      <div className="result-page">
        <div className="result-wrap">
          <LoadingState label="Loading your governed result…" />
        </div>
      </div>
    );
  }
  if (!result) {
    return (
      <div className="result-page">
        <div className="result-wrap">
          <ErrorState message={error} retry={() => void load()} />
        </div>
      </div>
    );
  }

  const minutes = Math.floor(result.timeTakenSeconds / 60);
  const seconds = result.timeTakenSeconds % 60;

  if (result.publicationStatus === "PENDING_PUBLICATION") {
    return (
      <div className="result-page">
        <div className="result-wrap pending-result">
          <Link className="player-brand" href="/dashboard">
            <Image src="/rabbit-mark.svg" width={42} height={42} alt="" />
            <div className="player-title">
              <strong>Rabbit AiP</strong>
              <span>Assessment submitted</span>
            </div>
          </Link>
          <section className="pending-result-card">
            <span className="pending-result-icon"><Hourglass size={28} /></span>
            <span className="badge badge-info">Evaluation completed</span>
            <h1>Your result is awaiting publication</h1>
            <p>
              Your responses were persisted and automatically evaluated. Faculty
              or the Organisation Admin must publish the result before its score
              and answer key become visible.
            </p>
            <div className="result-stats">
              <div className="result-stat"><strong>{result.answered}/{result.questionCount}</strong><span>Answered</span></div>
              <div className="result-stat"><strong>{minutes}m {seconds}s</strong><span>Time taken</span></div>
              <div className="result-stat"><strong>v{result.evaluationVersion}</strong><span>Evaluation</span></div>
            </div>
            <Link className="button button-primary" href="/dashboard">
              <ArrowLeft size={15} /> Return to dashboard
            </Link>
          </section>
        </div>
      </div>
    );
  }

  return (
    <div className="result-page">
      <div className="result-wrap">
        <div className="result-topbar">
          <Link className="player-brand" href="/dashboard">
            <Image src="/rabbit-mark.svg" width={40} height={40} alt="" />
            <div className="player-title">
              <strong>Rabbit AiP</strong>
              <span>Published live result</span>
            </div>
          </Link>
        </div>

        <section className="result-hero">
          <div className="score-ring">
            <div>
              <strong>{result.percentage ?? 0}%</strong>
              <span>Grade {result.grade ?? "—"}</span>
            </div>
          </div>
          <div className="result-copy">
            <span className="badge badge-success">
              <CheckCircle2 size={12} /> Result published
            </span>
            <h1>{result.assessmentTitle}</h1>
            <p>
              This is the persisted, published evaluation for your submitted
              attempt.
            </p>
            <div className="result-stats">
              <div className="result-stat"><strong>{result.score ?? 0} / {result.maxScore ?? 0}</strong><span>Total score</span></div>
              <div className="result-stat"><strong>{result.correctAnswers} / {result.questionCount}</strong><span>Correct</span></div>
              <div className="result-stat"><strong>#{result.rank ?? "—"}</strong><span>Rank</span></div>
              <div className="result-stat"><strong>{minutes}m {seconds}s</strong><span>Time taken</span></div>
            </div>
          </div>
        </section>

        <section className="result-breakdown">
          <article><CheckCircle2 size={18} /><strong>{result.correctAnswers}</strong><span>Correct</span></article>
          <article><XCircle size={18} /><strong>{result.wrongAnswers}</strong><span>Incorrect</span></article>
          <article><Eye size={18} /><strong>{result.unansweredAnswers}</strong><span>Unanswered</span></article>
          <article><Trophy size={18} /><strong>{result.grade ?? "—"}</strong><span>Grade</span></article>
        </section>

        {result.questions.length > 0 && (
          <section className="panel question-review-list">
            <div className="panel-header">
              <div>
                <h2>Question review</h2>
                <p>Answer keys and explanations are visible only after publication.</p>
              </div>
            </div>
            {result.questions.map((question, index) => (
              <article
                className={`result-question ${question.correct ? "correct" : "incorrect"}`}
                key={question.questionId}
              >
                <div className="result-question-heading">
                  <span>Q{index + 1}</span>
                  <strong>{question.stem}</strong>
                  <span>{question.awardedMarks}/{question.maxMarks}</span>
                </div>
                <div className="answer-comparison">
                  <span>
                    Your selection:{" "}
                    {question.selectedOptionIds.length
                      ? question.selectedOptionIds.length
                      : "Not answered"}
                  </span>
                  <span>Correct option(s): {question.correctOptionIds.length}</span>
                </div>
                {question.explanation && <p>{question.explanation}</p>}
              </article>
            ))}
          </section>
        )}

        <div className="result-footer">
          <span><Clock3 size={14} /> Published evaluations are read-only.</span>
          <Link className="button button-primary" href="/dashboard">
            <ArrowLeft size={15} /> Return to dashboard
          </Link>
        </div>
      </div>
    </div>
  );
}
