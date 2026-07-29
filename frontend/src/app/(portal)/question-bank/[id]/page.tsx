import Link from "next/link";
import { ArrowLeft, Check, Copy, Pencil, Send } from "lucide-react";
import { notFound } from "next/navigation";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { questions } from "@/lib/demo-data";

export default async function QuestionDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const question = questions.find((item) => item.id === id);
  if (!question) notFound();

  return (
    <div className="page">
      <Link className="button button-ghost" href="/question-bank">
        <ArrowLeft size={15} /> Back to Question Bank
      </Link>
      <PageHeader
        eyebrow={question.code}
        title="Question details"
        description={`Version ${question.version} · Authored by ${question.author}`}
        actions={
          <>
            <button className="button button-secondary">
              <Copy size={15} /> Duplicate
            </button>
            <button className="button button-secondary">
              <Pencil size={15} /> Edit
            </button>
            {question.status === "DRAFT" && (
              <button className="button button-primary">
                <Send size={15} /> Submit for review
              </button>
            )}
          </>
        }
      />

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
                  <strong>Version {question.version} created</strong>
                  <span>{question.author} · {question.updatedAt}</span>
                </div>
              </div>
              {question.status !== "DRAFT" && (
                <div className="activity-item">
                  <span className="activity-dot" />
                  <div className="activity-copy">
                    <strong>Submitted for academic review</strong>
                    <span>Workflow event recorded</span>
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
