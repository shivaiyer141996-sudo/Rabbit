"use client";

import { useRouter } from "next/navigation";
import { LoaderCircle, Plus, Save, Send, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import {
  type AcademicCatalog,
  type ApiQuestion,
} from "@/lib/live-types";
import {
  validateQuestionDraft,
  type QuestionDraft,
} from "@/lib/question-validation";
import type { BloomLevel, QuestionType } from "@/lib/types";

const blankOption = () => ({ text: "", correct: false });

export function QuestionAuthorForm({ questionId }: { questionId?: string }) {
  const router = useRouter();
  const [catalog, setCatalog] = useState<AcademicCatalog | null>(null);
  const [code, setCode] = useState("");
  const [bloomLevel, setBloomLevel] = useState<BloomLevel>("REMEMBER");
  const [subTopic, setSubTopic] = useState("");
  const [explanation, setExplanation] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<"draft" | "review" | null>(null);
  const [error, setError] = useState("");
  const [draft, setDraft] = useState<QuestionDraft>({
    stem: "",
    type: "SINGLE_CORRECT",
    subject: "",
    topic: "",
    difficulty: "",
    marks: 4,
    negativeMarks: 1,
    options: Array.from({ length: 4 }, blankOption),
  });
  const errors = useMemo(() => validateQuestionDraft(draft), [draft]);
  const topics = useMemo(
    () =>
      catalog?.topics.filter(
        (topic) => topic.active && topic.subjectId === draft.subject,
      ) ?? [],
    [catalog, draft.subject],
  );

  useEffect(() => {
    let active = true;
    const requests: Promise<AcademicCatalog | ApiQuestion>[] = [
      apiFetch<AcademicCatalog>("/academic-catalog"),
    ];
    if (questionId) requests.push(apiFetch<ApiQuestion>(`/questions/${questionId}`));
    Promise.all(requests)
      .then(([nextCatalog, existing]) => {
        if (!active) return;
        setCatalog(nextCatalog as AcademicCatalog);
        if (existing) {
          const question = existing as ApiQuestion;
          setCode(question.code);
          setBloomLevel(question.bloomLevel);
          setSubTopic(question.subTopic ?? "");
          setExplanation(question.explanation ?? "");
          setDraft({
            stem: question.stem,
            type: question.type,
            subject: question.subjectId,
            topic: question.topicId,
            difficulty: question.difficulty,
            marks: Number(question.marks),
            negativeMarks: Number(question.negativeMarks),
            options: question.options
              .toSorted((left, right) => left.sortOrder - right.sortOrder)
              .map((option) => ({
                text: option.text,
                correct: option.correct,
              })),
          });
        }
      })
      .catch((requestError) => {
        if (active) {
          setError(apiErrorMessage(requestError, "Authoring data could not be loaded."));
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [questionId]);

  function setType(type: QuestionType) {
    setDraft((current) => ({
      ...current,
      type,
      options: current.options.map((option) => ({ ...option, correct: false })),
    }));
  }

  function updateOption(
    index: number,
    update: Partial<{ text: string; correct: boolean }>,
  ) {
    setDraft((current) => {
      const options = current.options.map((option, optionIndex) => {
        if (optionIndex !== index) {
          return current.type === "SINGLE_CORRECT" && update.correct
            ? { ...option, correct: false }
            : option;
        }
        return { ...option, ...update };
      });
      return { ...current, options };
    });
  }

  async function save(submitForReview: boolean) {
    setSubmitted(true);
    setError("");
    if (errors.length) return;
    setBusy(submitForReview ? "review" : "draft");
    try {
      const payload = {
        code: code.trim() || null,
        stem: draft.stem.trim(),
        type: draft.type,
        subjectId: draft.subject,
        topicId: draft.topic,
        subTopic: subTopic.trim() || null,
        difficulty: draft.difficulty,
        bloomLevel,
        marks: draft.marks,
        negativeMarks: draft.negativeMarks,
        explanation: explanation.trim() || null,
        language: "en",
        options: draft.options.map((option) => ({
          text: option.text.trim(),
          correct: option.correct,
        })),
      };
      const saved = await apiFetch<ApiQuestion>(
        questionId ? `/questions/${questionId}` : "/questions",
        {
          method: questionId ? "PUT" : "POST",
          body: JSON.stringify(payload),
        },
      );
      if (submitForReview) {
        await apiFetch<ApiQuestion>(`/questions/${saved.id}/submit`, {
          method: "POST",
        });
      }
      router.push(`/question-bank/${saved.id}`);
      router.refresh();
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Question could not be saved."));
    } finally {
      setBusy(null);
    }
  }

  if (loading) return <LoadingState label="Loading the live academic catalog…" />;
  if (!catalog) {
    return <ErrorState message={error || "The academic catalog is unavailable."} />;
  }

  return (
    <div className="form-layout">
      <div>
        {error && <div className="form-error" role="alert">{error}</div>}
        <section className="form-section">
          <h2>Question</h2>
          <p>Release 1.0 supports only Single Correct and Multiple Correct MCQ.</p>
          <div className="field-row">
            <div className="field">
              <label htmlFor="question-code">Question code</label>
              <input
                id="question-code"
                onChange={(event) => setCode(event.target.value)}
                placeholder="Generated automatically when blank"
                value={code}
              />
            </div>
            <div className="field">
              <label htmlFor="question-type">Question type</label>
              <select
                id="question-type"
                value={draft.type}
                onChange={(event) => setType(event.target.value as QuestionType)}
              >
                <option value="SINGLE_CORRECT">Single Correct MCQ</option>
                <option value="MULTIPLE_CORRECT">Multiple Correct MCQ</option>
              </select>
            </div>
          </div>
          <div className="field">
            <label htmlFor="stem">Question stem</label>
            <textarea
              id="stem"
              placeholder="Write a clear, unambiguous question…"
              value={draft.stem}
              onChange={(event) =>
                setDraft((current) => ({ ...current, stem: event.target.value }))
              }
            />
          </div>
        </section>

        <section className="form-section">
          <h2>Answer options</h2>
          <p>
            Add 4–6 options. Mark exactly one answer for Single Correct, or two
            or more for Multiple Correct.
          </p>
          {draft.options.map((option, index) => (
            <div className="option-editor" key={index}>
              <span className="option-label">{String.fromCharCode(65 + index)}</span>
              <div className="field" style={{ margin: 0 }}>
                <input
                  aria-label={`Option ${String.fromCharCode(65 + index)}`}
                  placeholder="Option text"
                  value={option.text}
                  onChange={(event) =>
                    updateOption(index, { text: event.target.value })
                  }
                />
              </div>
              <label className="correct-control">
                <input
                  type={draft.type === "SINGLE_CORRECT" ? "radio" : "checkbox"}
                  name={draft.type === "SINGLE_CORRECT" ? "correct-option" : undefined}
                  checked={option.correct}
                  onChange={(event) =>
                    updateOption(index, { correct: event.target.checked })
                  }
                />
                Correct
              </label>
              {draft.options.length > 4 && (
                <button
                  className="icon-button"
                  onClick={() =>
                    setDraft((current) => ({
                      ...current,
                      options: current.options.filter((_, item) => item !== index),
                    }))
                  }
                  aria-label={`Remove option ${index + 1}`}
                  type="button"
                >
                  <Trash2 size={15} />
                </button>
              )}
            </div>
          ))}
          {draft.options.length < 6 && (
            <button
              className="button button-secondary"
              onClick={() =>
                setDraft((current) => ({
                  ...current,
                  options: [...current.options, blankOption()],
                }))
              }
              type="button"
            >
              <Plus size={15} /> Add option
            </button>
          )}
        </section>

        <section className="form-section">
          <h2>Academic metadata</h2>
          <p>Live organisation masters make questions searchable and reusable.</p>
          <div className="field-row">
            <div className="field">
              <label htmlFor="subject">Subject</label>
              <select
                id="subject"
                value={draft.subject}
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    subject: event.target.value,
                    topic: "",
                  }))
                }
              >
                <option value="">Select subject</option>
                {catalog.subjects
                  .filter((subject) => subject.active)
                  .map((subject) => (
                    <option key={subject.id} value={subject.id}>
                      {subject.code} · {subject.name}
                    </option>
                  ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="topic">Topic</label>
              <select
                disabled={!draft.subject}
                id="topic"
                value={draft.topic}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, topic: event.target.value }))
                }
              >
                <option value="">Select topic</option>
                {topics.map((topic) => (
                  <option key={topic.id} value={topic.id}>{topic.name}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="field">
            <label htmlFor="sub-topic">Sub-topic</label>
            <input
              id="sub-topic"
              onChange={(event) => setSubTopic(event.target.value)}
              placeholder="Optional"
              value={subTopic}
            />
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="difficulty">Difficulty</label>
              <select
                id="difficulty"
                value={draft.difficulty}
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    difficulty: event.target.value,
                  }))
                }
              >
                <option value="">Select difficulty</option>
                <option value="EASY">Easy</option>
                <option value="MEDIUM">Medium</option>
                <option value="HARD">Hard</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="bloom">Bloom’s taxonomy</label>
              <select
                id="bloom"
                value={bloomLevel}
                onChange={(event) => setBloomLevel(event.target.value as BloomLevel)}
              >
                {["REMEMBER", "UNDERSTAND", "APPLY", "ANALYSE", "EVALUATE", "CREATE"].map(
                  (level) => (
                    <option key={level} value={level}>
                      {level.charAt(0) + level.slice(1).toLowerCase()}
                    </option>
                  ),
                )}
              </select>
            </div>
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="marks">Marks</label>
              <input
                id="marks"
                type="number"
                min="0.1"
                step="0.5"
                value={draft.marks}
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    marks: Number(event.target.value),
                  }))
                }
              />
            </div>
            <div className="field">
              <label htmlFor="negative-marks">Negative marks</label>
              <input
                id="negative-marks"
                type="number"
                min="0"
                step="0.25"
                value={draft.negativeMarks}
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    negativeMarks: Number(event.target.value),
                  }))
                }
              />
            </div>
          </div>
          <div className="field">
            <label htmlFor="explanation">Answer explanation</label>
            <textarea
              id="explanation"
              placeholder="Explain why the marked answer is correct…"
              value={explanation}
              onChange={(event) => setExplanation(event.target.value)}
            />
          </div>
        </section>
      </div>

      <aside className="sticky-panel">
        <section className="panel">
          <div className="panel-header"><h2>Validation</h2></div>
          {errors.length === 0 ? (
            <div className="success-banner">Question is ready to save.</div>
          ) : (
            <ul className={submitted ? "validation-list visible" : "validation-list"}>
              {errors.map((validationError) => (
                <li key={validationError}>{validationError}</li>
              ))}
            </ul>
          )}
          <div className="button-stack">
            <button
              className="button button-secondary button-full"
              disabled={busy !== null}
              onClick={() => void save(false)}
              type="button"
            >
              {busy === "draft" ? <LoaderCircle className="spin" size={15} /> : <Save size={15} />}
              {busy === "draft" ? "Saving…" : "Save draft"}
            </button>
            <button
              className="button button-primary button-full"
              disabled={busy !== null}
              onClick={() => void save(true)}
              type="button"
            >
              {busy === "review" ? <LoaderCircle className="spin" size={15} /> : <Send size={15} />}
              {busy === "review" ? "Submitting…" : "Save & submit for review"}
            </button>
          </div>
          <p className="panel-note">
            All saves and workflow transitions are persisted and audited.
          </p>
        </section>
      </aside>
    </div>
  );
}
