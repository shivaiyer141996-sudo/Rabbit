"use client";

import { Plus, Save, Send, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import {
  validateQuestionDraft,
  type QuestionDraft,
} from "@/lib/question-validation";
import type { QuestionType } from "@/lib/types";

const blankOption = () => ({ text: "", correct: false });

export function QuestionAuthorForm() {
  const [submitted, setSubmitted] = useState(false);
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

  function save(submitForReview = false) {
    setSubmitted(true);
    if (errors.length) return;
    window.alert(
      submitForReview
        ? "Question is valid and ready for the review API."
        : "Draft is valid and ready to save.",
    );
  }

  return (
    <div className="form-layout">
      <div>
        <section className="form-section">
          <h2>Question</h2>
          <p>Release 1.0 supports only Single Correct and Multiple Correct MCQ.</p>
          <div className="field-row">
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
            <div className="field">
              <label htmlFor="language">Language</label>
              <select id="language"><option>English</option></select>
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
            >
              <Plus size={15} /> Add option
            </button>
          )}
        </section>

        <section className="form-section">
          <h2>Academic metadata</h2>
          <p>Metadata makes questions governable, searchable, and reusable.</p>
          <div className="field-row">
            <div className="field">
              <label htmlFor="subject">Subject</label>
              <select
                id="subject"
                value={draft.subject}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, subject: event.target.value }))
                }
              >
                <option value="">Select subject</option>
                <option>Physics</option>
                <option>Chemistry</option>
                <option>Mathematics</option>
                <option>Biology</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="topic">Topic</label>
              <input
                id="topic"
                value={draft.topic}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, topic: event.target.value }))
                }
                placeholder="e.g. Motion in a Straight Line"
              />
            </div>
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
              <select id="bloom">
                <option>Remember</option>
                <option>Understand</option>
                <option>Apply</option>
                <option>Analyse</option>
                <option>Evaluate</option>
                <option>Create</option>
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
            />
          </div>
        </section>
      </div>

      <aside className="sticky-panel">
        <section className="panel">
          <div className="panel-header"><h2>Validation</h2></div>
          {errors.length === 0 ? (
            <div className="badge badge-success">Ready to save</div>
          ) : (
            <>
              <span className="badge badge-warning">
                {errors.length} item{errors.length === 1 ? "" : "s"} to complete
              </span>
              {submitted && (
                <ul className="validation-list">
                  {errors.map((error) => <li key={error}>{error}</li>)}
                </ul>
              )}
            </>
          )}
          <div style={{ display: "grid", gap: 9, marginTop: 20 }}>
            <button
              className="button button-secondary"
              onClick={() => save(false)}
            >
              <Save size={15} /> Save draft
            </button>
            <button
              className="button button-primary"
              onClick={() => save(true)}
            >
              <Send size={15} /> Submit for review
            </button>
          </div>
        </section>
      </aside>
    </div>
  );
}
