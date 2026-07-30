"use client";

import Image from "next/image";
import {
  Bookmark,
  BookmarkCheck,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  LoaderCircle,
} from "lucide-react";
import { useRouter } from "next/navigation";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { AttemptView } from "@/lib/live-types";

type AnswerMap = Record<string, string[]>;

export function AssessmentPlayer({ assessmentId }: { assessmentId: string }) {
  const router = useRouter();
  const [attempt, setAttempt] = useState<AttemptView | null>(null);
  const [current, setCurrent] = useState(0);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [answers, setAnswers] = useState<AnswerMap>({});
  const [flagged, setFlagged] = useState<string[]>([]);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [saveError, setSaveError] = useState("");
  const automaticSubmission = useRef(false);
  const saveQueues = useRef<Record<string, Promise<boolean>>>({});

  const start = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const value = await apiFetch<AttemptView>(
        `/student/assessments/${assessmentId}/attempts`,
        { method: "POST" },
      );
      const restoredAnswers: AnswerMap = {};
      const restoredFlags: string[] = [];
      value.responses.forEach((response) => {
        restoredAnswers[response.questionId] = response.selectedOptionIds;
        if (response.flagged) restoredFlags.push(response.questionId);
      });
      setAttempt(value);
      setAnswers(restoredAnswers);
      setFlagged(restoredFlags);
      setSecondsLeft(
        Math.max(0, Math.ceil((new Date(value.expiresAt).getTime() - Date.now()) / 1000)),
      );
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "The assessment attempt could not be started."));
    } finally {
      setLoading(false);
    }
  }, [assessmentId]);

  useEffect(() => {
    const initial = window.setTimeout(() => void start(), 0);
    return () => window.clearTimeout(initial);
  }, [start]);

  const persist = useCallback(
    (
      questionId: string,
      selectedOptionIds: string[],
      isFlagged: boolean,
    ): Promise<boolean> => {
      if (!attempt) return Promise.resolve(false);
      const save = async () => {
        try {
          await apiFetch(`/student/attempts/${attempt.attemptId}/responses`, {
            method: "PUT",
            body: JSON.stringify({
              questionId,
              selectedOptionIds,
              flagged: isFlagged,
              timeSpentSeconds: 0,
            }),
          });
          setSavedAt(new Date());
          setSaveError("");
          return true;
        } catch (requestError) {
          setSaveError(
            apiErrorMessage(
              requestError,
              "This response is still on screen but has not reached the server.",
            ),
          );
          return false;
        }
      };
      const queued = (saveQueues.current[questionId] ?? Promise.resolve(true))
        .then(save, save);
      saveQueues.current[questionId] = queued;
      return queued;
    },
    [attempt],
  );

  const persistAll = useCallback(async () => {
    if (!attempt) return false;
    const results = await Promise.all(
      attempt.questions.map((question) =>
        persist(
          question.id,
          answers[question.id] ?? [],
          flagged.includes(question.id),
        ),
      ),
    );
    return results.every(Boolean);
  }, [answers, attempt, flagged, persist]);

  useEffect(() => {
    if (!attempt) return;
    const interval = window.setInterval(() => void persistAll(), 30_000);
    return () => window.clearInterval(interval);
  }, [attempt, persistAll]);

  useEffect(() => {
    if (!attempt) return;
    const interval = window.setInterval(() => {
      setSecondsLeft(
        Math.max(
          0,
          Math.ceil((new Date(attempt.expiresAt).getTime() - Date.now()) / 1000),
        ),
      );
    }, 1_000);
    return () => window.clearInterval(interval);
  }, [attempt]);

  const submit = useCallback(
    async (automatic: boolean) => {
      if (!attempt || submitting) return;
      setSubmitting(true);
      setSaveError("");
      try {
        const responsesSaved = await persistAll();
        if (!automatic && !responsesSaved) {
          throw new Error(
            "Submission paused because one or more responses did not reach the server. Check your connection and try again.",
          );
        }
        const result = await apiFetch<{ attemptId: string }>(
          `/student/attempts/${attempt.attemptId}/submit?automatic=${automatic}`,
          { method: "POST" },
        );
        router.replace(`/results/${result.attemptId}`);
      } catch (requestError) {
        setSaveError(apiErrorMessage(requestError, "Assessment could not be submitted."));
        if (automatic) automaticSubmission.current = false;
        setSubmitting(false);
      }
    },
    [attempt, persistAll, router, submitting],
  );

  useEffect(() => {
    if (
      attempt &&
      secondsLeft === 0 &&
      !automaticSubmission.current &&
      !submitting
    ) {
      automaticSubmission.current = true;
      void submit(true);
    }
  }, [attempt, secondsLeft, submit, submitting]);

  const answeredCount = useMemo(
    () => Object.values(answers).filter((value) => value.length > 0).length,
    [answers],
  );

  if (loading) {
    return (
      <div className="assessment-player player-state">
        <LoadingState label="Starting or restoring your live attempt…" />
      </div>
    );
  }
  if (!attempt || !attempt.questions.length) {
    return (
      <div className="assessment-player player-state">
        <ErrorState message={error || "No questions are available."} retry={() => void start()} />
      </div>
    );
  }

  const activeAttempt = attempt;
  const question = activeAttempt.questions[current];
  const minutes = String(Math.floor(secondsLeft / 60)).padStart(2, "0");
  const seconds = String(secondsLeft % 60).padStart(2, "0");

  function selectOption(optionId: string) {
    const existing = answers[question.id] ?? [];
    const selected =
      question.type === "SINGLE_CORRECT"
        ? [optionId]
        : existing.includes(optionId)
          ? existing.filter((id) => id !== optionId)
          : [...existing, optionId];
    setAnswers((currentAnswers) => ({
      ...currentAnswers,
      [question.id]: selected,
    }));
    void persist(question.id, selected, flagged.includes(question.id));
  }

  function toggleFlag() {
    const next = !flagged.includes(question.id);
    setFlagged((currentFlags) =>
      next
        ? [...currentFlags, question.id]
        : currentFlags.filter((id) => id !== question.id),
    );
    void persist(question.id, answers[question.id] ?? [], next);
  }

  function navigate(index: number) {
    void persist(
      question.id,
      answers[question.id] ?? [],
      flagged.includes(question.id),
    );
    setCurrent(Math.max(0, Math.min(activeAttempt.questions.length - 1, index)));
  }

  function confirmSubmit() {
    if (
      window.confirm(
        `Submit now? You answered ${answeredCount} of ${activeAttempt.questions.length} questions.`,
      )
    ) {
      void submit(false);
    }
  }

  return (
    <div className="assessment-player">
      <header className="player-header">
        <div className="player-brand">
          <Image src="/rabbit-mark.svg" width={38} height={38} alt="" />
          <div className="player-title">
            <strong>{attempt.title}</strong>
            <span>Question {current + 1} of {attempt.questions.length}</span>
          </div>
        </div>
        <div className="topbar-actions">
          {savedAt && (
            <span className="save-status">
              <CheckCircle2 size={13} /> Saved to server{" "}
              {savedAt.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            </span>
          )}
          <span
            className="timer"
            aria-label={`${minutes} minutes ${seconds} seconds remaining`}
          >
            <Clock3 size={17} /> {minutes}:{seconds}
          </span>
        </div>
      </header>
      {saveError && <div className="player-save-error" role="alert">{saveError}</div>}

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
            <button className="button button-secondary" onClick={toggleFlag} type="button">
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
                type="button"
              >
                <ChevronLeft size={15} /> Previous
              </button>
              {current < attempt.questions.length - 1 ? (
                <button
                  className="button button-primary"
                  onClick={() => navigate(current + 1)}
                  type="button"
                >
                  Save & next <ChevronRight size={15} />
                </button>
              ) : (
                <button
                  className="button button-primary"
                  disabled={submitting}
                  onClick={confirmSubmit}
                  type="button"
                >
                  {submitting && <LoaderCircle className="spin" size={15} />}
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
              <span className="muted">
                {answeredCount}/{attempt.questions.length} answered
              </span>
            </div>
            <div className="navigator-grid">
              {attempt.questions.map((item, index) => (
                <button
                  className={[
                    "nav-number",
                    index === current ? "current" : "",
                    answers[item.id]?.length ? "answered" : "",
                    flagged.includes(item.id) ? "flagged" : "",
                  ].join(" ")}
                  key={item.id}
                  onClick={() => navigate(index)}
                  type="button"
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
              <div className="definition-row"><dt>Unanswered</dt><dd>{attempt.questions.length - answeredCount}</dd></div>
              <div className="definition-row"><dt>Flagged</dt><dd>{flagged.length}</dd></div>
            </dl>
            <button
              className="button button-primary button-full"
              disabled={submitting}
              onClick={confirmSubmit}
              style={{ marginTop: 16 }}
              type="button"
            >
              {submitting && <LoaderCircle className="spin" size={15} />}
              Submit assessment
            </button>
          </section>
        </aside>
      </div>
    </div>
  );
}
