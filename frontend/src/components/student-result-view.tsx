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
import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";

interface ResultQuestion {
  questionId: string;
  stem: string;
  selectedOptionIds: string[];
  correctOptionIds: string[];
  awardedMarks: number;
  maxMarks: number;
  correct: boolean;
  explanation?: string;
}

interface ResultView {
  attemptId: string;
  assessmentId: string;
  assessmentTitle: string;
  publicationStatus: "PENDING_PUBLICATION" | "PUBLISHED";
  score?: number;
  maxScore?: number;
  percentage?: number;
  grade?: string;
  submittedAt: string;
  answered: number;
  questionCount: number;
  correctAnswers: number;
  wrongAnswers: number;
  unansweredAnswers: number;
  rank?: number;
  timeTakenSeconds: number;
  evaluationVersion: number;
  questions: ResultQuestion[];
}

const demoResult: ResultView = {
  attemptId: "99999999-9999-9999-9999-999999999902",
  assessmentId: "77777777-7777-7777-7777-777777777703",
  assessmentTitle: "Physics Motion Progress Check",
  publicationStatus: "PUBLISHED",
  score: 8,
  maxScore: 8,
  percentage: 100,
  grade: "A",
  submittedAt: "2026-07-15T04:54:00Z",
  answered: 2,
  questionCount: 2,
  correctAnswers: 2,
  wrongAnswers: 0,
  unansweredAnswers: 0,
  rank: 1,
  timeTakenSeconds: 1380,
  evaluationVersion: 1,
  questions: [],
};

export function StudentResultView({ attemptId }: { attemptId: string }) {
  const [result, setResult] = useState<ResultView>({
    ...demoResult,
    attemptId,
  });
  const [live, setLive] = useState(false);

  useEffect(() => {
    let active = true;
    apiFetch<ResultView>(`/student/results/${attemptId}`)
      .then((value) => {
        if (!active) return;
        setResult(value);
        setLive(true);
      })
      .catch(() => setLive(false));
    return () => {
      active = false;
    };
  }, [attemptId]);

  const minutes = Math.floor(result.timeTakenSeconds / 60);
  const seconds = result.timeTakenSeconds % 60;

  if (result.publicationStatus === "PENDING_PUBLICATION") {
    return (
      <div className="result-page">
        <div className="result-wrap pending-result">
          <Link className="player-brand" href="/dashboard">
            <Image src="/rabbit-mark.svg" width={42} height={42} alt="" />
            <div className="player-title"><strong>Rabbit AiP</strong><span>Assessment submitted</span></div>
          </Link>
          <section className="pending-result-card">
            <span className="pending-result-icon"><Hourglass size={28} /></span>
            <span className="badge badge-info">Evaluation completed</span>
            <h1>Your result is awaiting publication</h1>
            <p>
              Your responses were saved and automatically evaluated. Faculty or
              the Organisation Admin must publish all results before scores become
              visible.
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
            <div className="player-title"><strong>Rabbit AiP</strong><span>Published result</span></div>
          </Link>
          {!live && <span className="badge badge-neutral">Preview</span>}
        </div>

        <section className="result-hero">
          <div className="score-ring">
            <div><strong>{result.percentage}%</strong><span>Grade {result.grade}</span></div>
          </div>
          <div className="result-copy">
            <span className="badge badge-success">
              <CheckCircle2 size={12} /> Result published
            </span>
            <h1>{result.assessmentTitle}</h1>
            <p>
              Your governed evaluation is now available. Use the question review
              below to focus the next practice session.
            </p>
            <div className="result-stats">
              <div className="result-stat"><strong>{result.score} / {result.maxScore}</strong><span>Total score</span></div>
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
          <article><Trophy size={18} /><strong>{result.grade}</strong><span>Grade</span></article>
        </section>

        {result.questions.length > 0 && (
          <section className="panel question-review-list">
            <div className="panel-header">
              <div><h2>Question review</h2><p>Answer keys and explanations are visible after publication.</p></div>
            </div>
            {result.questions.map((question, index) => (
              <article className={`result-question ${question.correct ? "correct" : "incorrect"}`} key={question.questionId}>
                <div className="result-question-heading">
                  <span>Q{index + 1}</span>
                  <strong>{question.stem}</strong>
                  <span>{question.awardedMarks}/{question.maxMarks}</span>
                </div>
                <div className="answer-comparison">
                  <span>Your selection: {question.selectedOptionIds.length || "Not answered"}</span>
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
