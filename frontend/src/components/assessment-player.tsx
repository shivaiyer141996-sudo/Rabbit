"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import {
  Bookmark,
  BookmarkCheck,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { AssessmentQuestion } from "@/lib/types";

type AnswerMap = Record<string, string[]>;

export function AssessmentPlayer({
  assessmentId,
  title,
  durationMinutes,
  questions,
}: {
  assessmentId: string;
  title: string;
  durationMinutes: number;
  questions: AssessmentQuestion[];
}) {
  const router = useRouter();
  const [current, setCurrent] = useState(0);
  const [secondsLeft, setSecondsLeft] = useState(durationMinutes * 60);
  const [answers, setAnswers] = useState<AnswerMap>({});
  const [flagged, setFlagged] = useState<string[]>([]);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const question = questions[current];

  useEffect(() => {
    const stored = localStorage.getItem(`rabbit-attempt-${assessmentId}`);
    if (!stored) return;
    try {
      const saved = JSON.parse(stored) as { answers: AnswerMap; flagged: string[] };
      queueMicrotask(() => {
        setAnswers(saved.answers ?? {});
        setFlagged(saved.flagged ?? []);
      });
    } catch {
      localStorage.removeItem(`rabbit-attempt-${assessmentId}`);
    }
  }, [assessmentId]);

  const save = useCallback(() => {
    localStorage.setItem(
      `rabbit-attempt-${assessmentId}`,
      JSON.stringify({ answers, flagged }),
    );
    setSavedAt(new Date());
  }, [answers, assessmentId, flagged]);

  useEffect(() => {
    const interval = window.setInterval(save, 30_000);
    return () => window.clearInterval(interval);
  }, [save]);

  useEffect(() => {
    const interval = window.setInterval(
      () => setSecondsLeft((currentSeconds) => Math.max(0, currentSeconds - 1)),
      1_000,
    );
    return () => window.clearInterval(interval);
  }, []);

  useEffect(() => {
    if (secondsLeft === 0) {
      localStorage.setItem(
        `rabbit-attempt-${assessmentId}`,
        JSON.stringify({ answers, flagged }),
      );
      router.replace(`/results/${assessmentId}?autoSubmitted=true`);
    }
  }, [answers, assessmentId, flagged, router, secondsLeft]);

  const answeredCount = useMemo(
    () => Object.values(answers).filter((value) => value.length > 0).length,
    [answers],
  );
  const minutes = String(Math.floor(secondsLeft / 60)).padStart(2, "0");
  const seconds = String(secondsLeft % 60).padStart(2, "0");

  function selectOption(optionId: string) {
    setAnswers((currentAnswers) => {
      const existing = currentAnswers[question.id] ?? [];
      const selected =
        question.type === "SINGLE_CORRECT"
          ? [optionId]
          : existing.includes(optionId)
            ? existing.filter((id) => id !== optionId)
            : [...existing, optionId];
      return { ...currentAnswers, [question.id]: selected };
    });
  }

  function navigate(index: number) {
    save();
    setCurrent(Math.max(0, Math.min(questions.length - 1, index)));
  }

  function submit() {
    if (
      window.confirm(
        `Submit now? You answered ${answeredCount} of ${questions.length} questions.`,
      )
    ) {
      save();
      router.push(`/results/${assessmentId}`);
    }
  }

  return (
    <div className="assessment-player">
      <header className="player-header">
        <div className="player-brand">
          <Image src="/rabbit-mark.svg" width={38} height={38} alt="" />
          <div className="player-title">
            <strong>{title}</strong>
            <span>Question {current + 1} of {questions.length}</span>
          </div>
        </div>
        <div className="topbar-actions">
          {savedAt && (
            <span className="save-status">
              <CheckCircle2 size={13} /> Saved {savedAt.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            </span>
          )}
          <span className="timer" aria-label={`${minutes} minutes ${seconds} seconds remaining`}>
            <Clock3 size={17} /> {minutes}:{seconds}
          </span>
        </div>
      </header>

      <div className="player-grid">
        <section className="player-question">
          <div className="player-question-top">
            <span>
              {question.type === "SINGLE_CORRECT"
                ? "Select one answer"
                : "Select all that apply"}
            </span>
            <span>{question.marks} marks</span>
          </div>
          <h1>{question.stem}</h1>
          <div>
            {question.options.map((option) => {
              const selected = (answers[question.id] ?? []).includes(option.id);
              return (
                <button
                  className={`answer-choice ${selected ? "selected" : ""}`}
                  key={option.id}
                  onClick={() => selectOption(option.id)}
                  type="button"
                  aria-pressed={selected}
                >
                  <span className="option-label">{option.label}</span>
                  <span>{option.text}</span>
                </button>
              );
            })}
          </div>
          <div className="player-actions">
            <button
              className="button button-secondary"
              onClick={() =>
                setFlagged((currentFlags) =>
                  currentFlags.includes(question.id)
                    ? currentFlags.filter((id) => id !== question.id)
                    : [...currentFlags, question.id],
                )
              }
            >
              {flagged.includes(question.id) ? (
                <BookmarkCheck size={15} />
              ) : (
                <Bookmark size={15} />
              )}
              {flagged.includes(question.id) ? "Flagged" : "Flag for review"}
            </button>
            <div className="header-actions">
              <button
                className="button button-secondary"
                onClick={() => navigate(current - 1)}
                disabled={current === 0}
              >
                <ChevronLeft size={15} /> Previous
              </button>
              {current < questions.length - 1 ? (
                <button
                  className="button button-primary"
                  onClick={() => navigate(current + 1)}
                >
                  Save & next <ChevronRight size={15} />
                </button>
              ) : (
                <button className="button button-primary" onClick={submit}>
                  Submit assessment
                </button>
              )}
            </div>
          </div>
        </section>

        <aside className="player-side">
          <section className="panel">
            <div className="panel-header">
              <h2>Question navigator</h2>
              <span className="muted">{answeredCount}/{questions.length} answered</span>
            </div>
            <div className="navigator-grid">
              {questions.map((item, index) => (
                <button
                  className={[
                    "nav-number",
                    index === current ? "current" : "",
                    answers[item.id]?.length ? "answered" : "",
                    flagged.includes(item.id) ? "flagged" : "",
                  ].join(" ")}
                  key={item.id}
                  onClick={() => navigate(index)}
                >
                  {index + 1}
                </button>
              ))}
            </div>
            <div className="legend">
              <span><i style={{ background: "#5936c8" }} /> Current</span>
              <span><i style={{ background: "#e8f7ef" }} /> Answered</span>
              <span><i style={{ boxShadow: "inset 0 -3px 0 #efad2d" }} /> Flagged</span>
            </div>
          </section>
          <section className="panel">
            <div className="panel-header"><h2>Attempt summary</h2></div>
            <dl className="definition-list">
              <div className="definition-row"><dt>Answered</dt><dd>{answeredCount}</dd></div>
              <div className="definition-row"><dt>Unanswered</dt><dd>{questions.length - answeredCount}</dd></div>
              <div className="definition-row"><dt>Flagged</dt><dd>{flagged.length}</dd></div>
            </dl>
            <button className="button button-primary button-full" onClick={submit} style={{ marginTop: 16 }}>
              Submit assessment
            </button>
          </section>
        </aside>
      </div>
    </div>
  );
}
