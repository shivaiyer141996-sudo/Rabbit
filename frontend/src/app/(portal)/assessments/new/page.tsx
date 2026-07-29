import Link from "next/link";
import { Check, ChevronRight, Save } from "lucide-react";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { questions } from "@/lib/demo-data";

export default function NewAssessmentPage() {
  const approved = questions.filter((question) =>
    ["APPROVED", "PUBLISHED"].includes(question.status),
  );

  return (
    <div className="page">
      <PageHeader
        eyebrow="Assessment creation"
        title="Create an assessment"
        description="Only approved questions are available for selection. Total marks are calculated from the chosen questions."
        actions={
          <button className="button button-primary">
            <Save size={15} /> Save draft
          </button>
        }
      />

      <div className="form-layout">
        <div>
          <section className="form-section">
            <h2>1. Assessment details</h2>
            <p>Define the academic context and default delivery behaviour.</p>
            <div className="field-row">
              <div className="field">
                <label htmlFor="assessment-title">Assessment title</label>
                <input id="assessment-title" placeholder="e.g. Kinematics Chapter Test" />
              </div>
              <div className="field">
                <label htmlFor="assessment-type">Assessment type</label>
                <select id="assessment-type">
                  <option>Practice Assessment</option>
                  <option>Class Test</option>
                  <option>Unit Test</option>
                  <option>Chapter Test</option>
                  <option>Mid-Term Examination</option>
                  <option>Final Examination</option>
                  <option>Mock Test</option>
                </select>
              </div>
            </div>
            <div className="field-row">
              <div className="field">
                <label htmlFor="assessment-subject">Subject</label>
                <select id="assessment-subject">
                  <option>Physics</option>
                  <option>Chemistry</option>
                  <option>Mathematics</option>
                  <option>Biology</option>
                </select>
              </div>
              <div className="field">
                <label htmlFor="duration">Duration (minutes)</label>
                <input id="duration" type="number" min="1" defaultValue="45" />
              </div>
            </div>
          </section>

          <section className="form-section">
            <h2>2. Choose approved questions</h2>
            <p>
              Questions in draft, review, or retired states are excluded by the
              API and never appear in this selection list.
            </p>
            <div className="option-list">
              {approved.map((question, index) => (
                <label className="option-row" key={question.id}>
                  <input type="checkbox" defaultChecked={index === 0} />
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
            <Link className="button button-ghost" href="/question-bank">
              Browse full question bank <ChevronRight size={15} />
            </Link>
          </section>

          <section className="form-section">
            <h2>3. Delivery settings</h2>
            <p>Scheduling remains editable until the assessment is published.</p>
            <div className="field-row">
              <div className="field">
                <label htmlFor="start-time">Start date and time</label>
                <input id="start-time" type="datetime-local" />
              </div>
              <div className="field">
                <label htmlFor="end-time">End date and time</label>
                <input id="end-time" type="datetime-local" />
              </div>
            </div>
            <div className="field-row">
              <div className="field">
                <label htmlFor="section">Eligible section</label>
                <select id="section">
                  <option>JEE 2027 · Batch A</option>
                  <option>JEE 2027 · Batch B</option>
                  <option>NEET 2027 · Batch A</option>
                </select>
              </div>
              <div className="field">
                <label htmlFor="attempts">Attempts allowed</label>
                <input id="attempts" type="number" min="1" defaultValue="1" />
              </div>
            </div>
            <label className="check-row">
              <input type="checkbox" defaultChecked /> Shuffle question order
            </label>
            <br />
            <label className="check-row">
              <input type="checkbox" /> Shuffle option order
            </label>
          </section>
        </div>

        <aside className="sticky-panel">
          <section className="panel">
            <div className="panel-header"><h2>Assessment summary</h2></div>
            <dl className="definition-list">
              <div className="definition-row">
                <dt>Selected questions</dt><dd>1</dd>
              </div>
              <div className="definition-row">
                <dt>Total marks</dt><dd>4</dd>
              </div>
              <div className="definition-row">
                <dt>Duration</dt><dd>45 minutes</dd>
              </div>
              <div className="definition-row">
                <dt>Status</dt><dd><StatusBadge status="DRAFT" /></dd>
              </div>
            </dl>
            <div className="explanation">
              <Check size={14} /> Every selected question is currently approved.
            </div>
            <button className="button button-primary button-full" style={{ marginTop: 16 }}>
              Save and continue
            </button>
          </section>
        </aside>
      </div>
    </div>
  );
}
