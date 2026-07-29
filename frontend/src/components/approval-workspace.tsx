"use client";

import {
  Check,
  CheckCircle2,
  ClipboardCheck,
  FileQuestion,
  RefreshCw,
  RotateCcw,
  XCircle,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { apiFetch, ApiError } from "@/lib/api";

const checklist = [
  ["CLEAR_STEM", "Question stem is clear and unambiguous"],
  ["PLAUSIBLE_OPTIONS", "All options are plausible and well formed"],
  ["ANSWER_KEY_VALID", "Correct answer(s) are accurately marked"],
  ["METADATA_VALID", "Subject, topic, and difficulty are accurate"],
  ["LANGUAGE_VALID", "Spelling and grammar are acceptable"],
  ["COPYRIGHT_CLEAR", "No copyright concern is present"],
  ["BLOOM_VALID", "Bloom’s taxonomy level is appropriate"],
] as const;

interface ApprovalQuestion {
  id: string;
  code: string;
  stem: string;
  status: string;
  difficulty: string;
  authorUserId: string;
  updatedAt: string;
}

interface ApprovalAssessment {
  id: string;
  code: string;
  title: string;
  status: string;
  questionCount: number;
  totalMarks: number;
  durationMinutes: number;
  createdAt: string;
}

interface ReviewHistory {
  id: string;
  decision: "APPROVE" | "RETURN" | "REJECT";
  reason?: string;
  createdAt: string;
}

const previewQuestions: ApprovalQuestion[] = [
  {
    id: "55555555-5555-5555-5555-555555555503",
    code: "CHE-ORG-014",
    stem: "Which of the following compounds can exhibit geometrical isomerism?",
    status: "UNDER_REVIEW",
    difficulty: "HARD",
    authorUserId: "33333333-3333-3333-3333-333333333302",
    updatedAt: new Date().toISOString(),
  },
];

const previewAssessments: ApprovalAssessment[] = [
  {
    id: "preview-assessment",
    code: "PHY-UNIT-07",
    title: "Physics Unit Test — Motion",
    status: "READY_FOR_REVIEW",
    questionCount: 20,
    totalMarks: 80,
    durationMinutes: 60,
    createdAt: new Date().toISOString(),
  },
];

export function ApprovalWorkspace() {
  const [tab, setTab] = useState<"questions" | "assessments">("questions");
  const [questionQueue, setQuestionQueue] =
    useState<ApprovalQuestion[]>(previewQuestions);
  const [assessmentQueue, setAssessmentQueue] =
    useState<ApprovalAssessment[]>(previewAssessments);
  const [selectedQuestion, setSelectedQuestion] =
    useState<ApprovalQuestion | null>(null);
  const [selectedAssessment, setSelectedAssessment] =
    useState<ApprovalAssessment | null>(null);
  const [checks, setChecks] = useState<string[]>([]);
  const [reason, setReason] = useState("");
  const [history, setHistory] = useState<ReviewHistory[]>([]);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [live, setLive] = useState(false);

  async function loadQueues() {
    const [questions, assessments] = await Promise.all([
      apiFetch<ApprovalQuestion[]>("/questions/review-queue"),
      apiFetch<ApprovalAssessment[]>("/assessments/review-queue"),
    ]);
    setQuestionQueue(questions);
    setAssessmentQueue(assessments);
    setLive(true);
  }

  useEffect(() => {
    let active = true;
    Promise.all([
      apiFetch<ApprovalQuestion[]>("/questions/review-queue"),
      apiFetch<ApprovalAssessment[]>("/assessments/review-queue"),
    ])
      .then(([questions, assessments]) => {
        if (!active) return;
        setQuestionQueue(questions);
        setAssessmentQueue(assessments);
        setLive(true);
      })
      .catch(() => setLive(false));
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    const selected = selectedQuestion ?? selectedAssessment;
    if (!selected || !live) {
      return;
    }
    const path = selectedQuestion
      ? `/questions/${selected.id}/reviews`
      : `/assessments/${selected.id}/reviews`;
    let active = true;
    apiFetch<ReviewHistory[]>(path)
      .then((rows) => {
        if (active) setHistory(rows);
      })
      .catch(() => {
        if (active) setHistory([]);
      });
    return () => {
      active = false;
    };
  }, [selectedQuestion, selectedAssessment, live]);

  const allChecked = useMemo(
    () => checklist.every(([id]) => checks.includes(id)),
    [checks],
  );

  function chooseQuestion(question: ApprovalQuestion) {
    setSelectedQuestion(question);
    setSelectedAssessment(null);
    setChecks([]);
    setReason("");
    setMessage("");
    setHistory([]);
  }

  function chooseAssessment(assessment: ApprovalAssessment) {
    setSelectedAssessment(assessment);
    setSelectedQuestion(null);
    setChecks([]);
    setReason("");
    setMessage("");
    setHistory([]);
  }

  async function reviewQuestion(decision: "APPROVE" | "RETURN" | "REJECT") {
    if (!selectedQuestion) return;
    if (decision === "APPROVE" && !allChecked) {
      setMessage("Complete all seven review checks before approval.");
      return;
    }
    if (decision !== "APPROVE" && reason.trim().length < 10) {
      setMessage("Add a clear reason of at least 10 characters.");
      return;
    }
    setBusy(true);
    setMessage("");
    try {
      await apiFetch(`/questions/${selectedQuestion.id}/review`, {
        method: "POST",
        body: JSON.stringify({
          decision,
          reason: reason.trim() || null,
          checklistItems: checks,
        }),
      });
      setMessage(`Question ${decision.toLowerCase()} decision recorded.`);
      setSelectedQuestion(null);
      await loadQueues();
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "Review could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  async function reviewAssessment(decision: "APPROVE" | "RETURN" | "REJECT") {
    if (!selectedAssessment) return;
    if (decision !== "APPROVE" && reason.trim().length < 10) {
      setMessage("Add a clear reason of at least 10 characters.");
      return;
    }
    setBusy(true);
    setMessage("");
    try {
      await apiFetch(`/assessments/${selectedAssessment.id}/review`, {
        method: "POST",
        body: JSON.stringify({ decision, reason: reason.trim() || null }),
      });
      setMessage(`Assessment ${decision.toLowerCase()} decision recorded.`);
      setSelectedAssessment(null);
      await loadQueues();
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "Review could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Role-based approval workflows"
        title="Academic approval workspace"
        description="Question authors and assessment creators cannot approve their own work. Every decision is retained in audit history."
      />

      {!live && (
        <div className="preview-banner">
          Preview queues are visible until the live API is available.
        </div>
      )}
      {message && <div className="workflow-message">{message}</div>}

      <div className="segmented-control" role="tablist" aria-label="Approval queue">
        <button
          className={tab === "questions" ? "active" : ""}
          onClick={() => setTab("questions")}
          role="tab"
        >
          <FileQuestion size={15} /> Questions
          <span className="tab-count">{questionQueue.length}</span>
        </button>
        <button
          className={tab === "assessments" ? "active" : ""}
          onClick={() => setTab("assessments")}
          role="tab"
        >
          <ClipboardCheck size={15} /> Assessments
          <span className="tab-count">{assessmentQueue.length}</span>
        </button>
      </div>

      <div className="approval-layout">
        <section className="panel approval-queue">
          <div className="panel-header">
            <div>
              <h2>{tab === "questions" ? "Question review queue" : "Assessment review queue"}</h2>
              <p>Oldest submissions should be reviewed first.</p>
            </div>
          </div>
          {tab === "questions" && questionQueue.map((question) => (
            <button
              className={`queue-item ${selectedQuestion?.id === question.id ? "selected" : ""}`}
              key={question.id}
              onClick={() => chooseQuestion(question)}
            >
              <div className="queue-item-top">
                <span className="badge badge-neutral">{question.code}</span>
                <StatusBadge status={question.status} />
              </div>
              <strong>{question.stem}</strong>
              <span>{question.difficulty} · submitted {new Date(question.updatedAt).toLocaleDateString()}</span>
            </button>
          ))}
          {tab === "assessments" && assessmentQueue.map((assessment) => (
            <button
              className={`queue-item ${selectedAssessment?.id === assessment.id ? "selected" : ""}`}
              key={assessment.id}
              onClick={() => chooseAssessment(assessment)}
            >
              <div className="queue-item-top">
                <span className="badge badge-neutral">{assessment.code}</span>
                <StatusBadge status={assessment.status} />
              </div>
              <strong>{assessment.title}</strong>
              <span>{assessment.questionCount} questions · {assessment.totalMarks} marks · {assessment.durationMinutes} min</span>
            </button>
          ))}
          {!questionQueue.length && tab === "questions" && (
            <div className="empty-state">No questions are waiting for review.</div>
          )}
          {!assessmentQueue.length && tab === "assessments" && (
            <div className="empty-state">No assessments are waiting for review.</div>
          )}
        </section>

        <aside className="panel review-panel">
          {!selectedQuestion && !selectedAssessment ? (
            <div className="empty-state review-empty">
              <CheckCircle2 size={30} />
              Select an item to complete its governed review.
            </div>
          ) : (
            <>
              <div className="panel-header">
                <div>
                  <h2>Review decision</h2>
                  <p>{selectedQuestion?.code ?? selectedAssessment?.code}</p>
                </div>
              </div>

              {selectedQuestion && (
                <div className="review-checklist">
                  {checklist.map(([id, label]) => (
                    <label className="review-check" key={id}>
                      <input
                        checked={checks.includes(id)}
                        onChange={(event) =>
                          setChecks((current) =>
                            event.target.checked
                              ? [...current, id]
                              : current.filter((item) => item !== id),
                          )
                        }
                        type="checkbox"
                      />
                      <span><Check size={13} /></span>
                      {label}
                    </label>
                  ))}
                </div>
              )}

              <div className="field">
                <label htmlFor="review-reason">
                  Reviewer comments {reason.length > 0 && `· ${reason.length}/500`}
                </label>
                <textarea
                  id="review-reason"
                  maxLength={500}
                  onChange={(event) => setReason(event.target.value)}
                  placeholder="Mandatory when returning or rejecting"
                  value={reason}
                />
              </div>

              <div className="decision-actions">
                <button
                  className="button button-primary"
                  disabled={busy || (Boolean(selectedQuestion) && !allChecked)}
                  onClick={() =>
                    selectedQuestion
                      ? reviewQuestion("APPROVE")
                      : reviewAssessment("APPROVE")
                  }
                >
                  {busy ? <RefreshCw className="spin" size={15} /> : <CheckCircle2 size={15} />}
                  Approve
                </button>
                <button
                  className="button button-secondary"
                  disabled={busy}
                  onClick={() =>
                    selectedQuestion
                      ? reviewQuestion("RETURN")
                      : reviewAssessment("RETURN")
                  }
                >
                  <RotateCcw size={15} /> Return
                </button>
                <button
                  className="button button-danger"
                  disabled={busy}
                  onClick={() =>
                    selectedQuestion
                      ? reviewQuestion("REJECT")
                      : reviewAssessment("REJECT")
                  }
                >
                  <XCircle size={15} /> Reject
                </button>
              </div>

              <div className="review-history">
                <h3>Decision history</h3>
                {history.length ? history.map((item) => (
                  <div className="history-row" key={item.id}>
                    <span className="activity-dot" />
                    <div>
                      <strong>{item.decision}</strong>
                      <span>{item.reason || "Checklist completed"} · {new Date(item.createdAt).toLocaleString()}</span>
                    </div>
                  </div>
                )) : (
                  <span className="muted">No prior decision on this version.</span>
                )}
              </div>
            </>
          )}
        </aside>
      </div>
    </div>
  );
}
