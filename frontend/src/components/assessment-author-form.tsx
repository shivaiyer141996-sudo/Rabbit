"use client";

import { useRouter } from "next/navigation";
import { Check, LoaderCircle, Save } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { StatusBadge } from "@/components/status-badge";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import {
  type AcademicCatalog,
  type ApiAssessment,
  type ApiQuestion,
} from "@/lib/live-types";

type AssessmentType = ApiAssessment["type"];

export function AssessmentAuthorForm() {
  const router = useRouter();
  const [catalog, setCatalog] = useState<AcademicCatalog | null>(null);
  const [questions, setQuestions] = useState<ApiQuestion[]>([]);
  const [title, setTitle] = useState("");
  const [code, setCode] = useState("");
  const [type, setType] = useState<AssessmentType>("CHAPTER_TEST");
  const [subjectId, setSubjectId] = useState("");
  const [durationMinutes, setDurationMinutes] = useState(45);
  const [attemptsAllowed, setAttemptsAllowed] = useState(1);
  const [shuffleQuestions, setShuffleQuestions] = useState(true);
  const [shuffleOptions, setShuffleOptions] = useState(false);
  const [partialMarking, setPartialMarking] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    Promise.all([
      apiFetch<AcademicCatalog>("/academic-catalog"),
      apiFetch<ApiQuestion[]>("/questions"),
    ])
      .then(([nextCatalog, rows]) => {
        if (!active) return;
        setCatalog(nextCatalog);
        setQuestions(
          rows.filter((question) =>
            ["APPROVED", "PUBLISHED"].includes(question.status),
          ),
        );
      })
      .catch((requestError) => {
        if (active) {
          setError(apiErrorMessage(requestError, "Assessment masters could not be loaded."));
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const availableQuestions = useMemo(
    () => questions.filter((question) => question.subjectId === subjectId),
    [questions, subjectId],
  );
  const selected = useMemo(
    () => questions.filter((question) => selectedIds.includes(question.id)),
    [questions, selectedIds],
  );
  const totalMarks = selected.reduce(
    (sum, question) => sum + Number(question.marks),
    0,
  );
  const validation = [
    !title.trim() ? "Assessment title is required." : "",
    !subjectId ? "Choose a subject." : "",
    durationMinutes < 1 ? "Duration must be at least one minute." : "",
    attemptsAllowed < 1 ? "At least one attempt must be allowed." : "",
    selectedIds.length < 1 ? "Choose at least one approved question." : "",
  ].filter(Boolean);

  async function save() {
    setError("");
    if (validation.length) {
      setError(validation.join(" "));
      return;
    }
    setBusy(true);
    try {
      const saved = await apiFetch<ApiAssessment>("/assessments", {
        method: "POST",
        body: JSON.stringify({
          code: code.trim() || null,
          title: title.trim(),
          type,
          subjectId,
          durationMinutes,
          shuffleQuestions,
          shuffleOptions,
          partialMarking,
          attemptsAllowed,
          questionIds: selectedIds,
        }),
      });
      router.push(`/assessments/${saved.id}`);
      router.refresh();
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Assessment draft could not be saved."));
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <LoadingState label="Loading approved questions…" />;
  if (!catalog) return <ErrorState message={error || "Academic masters are unavailable."} />;

  return (
    <div className="form-layout">
      <div>
        {error && <div className="form-error" role="alert">{error}</div>}
        <section className="form-section">
          <h2>1. Assessment details</h2>
          <p>Define the academic context and delivery behaviour.</p>
          <div className="field-row">
            <div className="field">
              <label htmlFor="assessment-title">Assessment title</label>
              <input
                id="assessment-title"
                onChange={(event) => setTitle(event.target.value)}
                placeholder="e.g. Kinematics Chapter Test"
                value={title}
              />
            </div>
            <div className="field">
              <label htmlFor="assessment-code">Assessment code</label>
              <input
                id="assessment-code"
                onChange={(event) => setCode(event.target.value)}
                placeholder="Generated automatically when blank"
                value={code}
              />
            </div>
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="assessment-type">Assessment type</label>
              <select
                id="assessment-type"
                onChange={(event) => setType(event.target.value as AssessmentType)}
                value={type}
              >
                {[
                  "PRACTICE_ASSESSMENT",
                  "CLASS_TEST",
                  "UNIT_TEST",
                  "CHAPTER_TEST",
                  "MID_TERM_EXAMINATION",
                  "FINAL_EXAMINATION",
                  "MOCK_TEST",
                ].map((value) => (
                  <option key={value} value={value}>{value.replaceAll("_", " ")}</option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="assessment-subject">Subject</label>
              <select
                id="assessment-subject"
                onChange={(event) => {
                  setSubjectId(event.target.value);
                  setSelectedIds([]);
                }}
                value={subjectId}
              >
                <option value="">Select subject</option>
                {catalog.subjects.filter((item) => item.active).map((subject) => (
                  <option key={subject.id} value={subject.id}>
                    {subject.code} · {subject.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="duration">Duration (minutes)</label>
              <input
                id="duration"
                min="1"
                onChange={(event) => setDurationMinutes(Number(event.target.value))}
                type="number"
                value={durationMinutes}
              />
            </div>
            <div className="field">
              <label htmlFor="attempts">Attempts allowed</label>
              <input
                id="attempts"
                min="1"
                onChange={(event) => setAttemptsAllowed(Number(event.target.value))}
                type="number"
                value={attemptsAllowed}
              />
            </div>
          </div>
        </section>

        <section className="form-section">
          <h2>2. Choose approved questions</h2>
          <p>
            The live API excludes draft, review, and retired questions. Changing
            subject clears the current selection.
          </p>
          {!subjectId && <div className="empty-state">Choose a subject first.</div>}
          {subjectId && !availableQuestions.length && (
            <div className="empty-state">
              No approved questions are available for this subject.
            </div>
          )}
          <div className="option-list">
            {availableQuestions.map((question, index) => (
              <label className="option-row" key={question.id}>
                <input
                  checked={selectedIds.includes(question.id)}
                  onChange={(event) =>
                    setSelectedIds((current) =>
                      event.target.checked
                        ? [...current, question.id]
                        : current.filter((id) => id !== question.id),
                    )
                  }
                  type="checkbox"
                />
                <span className="option-label">{index + 1}</span>
                <div style={{ flex: 1 }}>
                  <p style={{ margin: 0 }}>{question.stem}</p>
                  <div className="question-meta-row" style={{ margin: "8px 0 0" }}>
                    <span className="badge badge-neutral">{question.code}</span>
                    <StatusBadge status={question.status} />
                    <span className="badge badge-info">{question.marks} marks</span>
                  </div>
                </div>
              </label>
            ))}
          </div>
        </section>

        <section className="form-section">
          <h2>3. Delivery defaults</h2>
          <p>The final window and eligible sections are set after approval.</p>
          <label className="check-row">
            <input
              checked={shuffleQuestions}
              onChange={(event) => setShuffleQuestions(event.target.checked)}
              type="checkbox"
            />
            Shuffle question order
          </label>
          <br />
          <label className="check-row">
            <input
              checked={shuffleOptions}
              onChange={(event) => setShuffleOptions(event.target.checked)}
              type="checkbox"
            />
            Shuffle option order
          </label>
          <br />
          <label className="check-row">
            <input
              checked={partialMarking}
              onChange={(event) => setPartialMarking(event.target.checked)}
              type="checkbox"
            />
            Apply partial marking to Multiple Correct questions
          </label>
        </section>
      </div>

      <aside className="sticky-panel">
        <section className="panel">
          <div className="panel-header"><h2>Assessment summary</h2></div>
          <dl className="definition-list">
            <div className="definition-row"><dt>Selected questions</dt><dd>{selectedIds.length}</dd></div>
            <div className="definition-row"><dt>Total marks</dt><dd>{totalMarks}</dd></div>
            <div className="definition-row"><dt>Duration</dt><dd>{durationMinutes} minutes</dd></div>
            <div className="definition-row"><dt>Status</dt><dd><StatusBadge status="DRAFT" /></dd></div>
          </dl>
          <div className="explanation">
            <Check size={14} /> Every selected question is live and approved.
          </div>
          <button
            className="button button-primary button-full"
            disabled={busy}
            onClick={() => void save()}
            style={{ marginTop: 16 }}
            type="button"
          >
            {busy ? <LoaderCircle className="spin" size={15} /> : <Save size={15} />}
            {busy ? "Saving…" : "Save draft"}
          </button>
        </section>
      </aside>
    </div>
  );
}
