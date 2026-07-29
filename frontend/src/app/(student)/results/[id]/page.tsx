import Image from "next/image";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, Download } from "lucide-react";

export default function ResultPage() {
  return (
    <div className="result-page">
      <div className="result-wrap">
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 24 }}>
          <Link className="player-brand" href="/dashboard">
            <Image src="/rabbit-mark.svg" width={40} height={40} alt="" />
            <div className="player-title">
              <strong>Rabbit AiP</strong>
              <span>Assessment result</span>
            </div>
          </Link>
          <button className="button button-secondary" disabled title="Milestone 3">
            <Download size={15} /> Export PDF
          </button>
        </div>
        <section className="result-hero">
          <div className="score-ring">
            <div><strong>82%</strong><span>Score</span></div>
          </div>
          <div className="result-copy">
            <span className="badge badge-success">
              <CheckCircle2 size={12} /> Assessment submitted
            </span>
            <h1>Strong work on Kinematics</h1>
            <p>
              You scored 65.5 out of 80 in JEE Physics — Kinematics. Review the
              topic breakdown to focus your next practice session.
            </p>
            <div className="result-stats">
              <div className="result-stat"><strong>65.5 / 80</strong><span>Total score</span></div>
              <div className="result-stat"><strong>16 / 20</strong><span>Correct</span></div>
              <div className="result-stat"><strong>84th</strong><span>Percentile</span></div>
              <div className="result-stat"><strong>38m 42s</strong><span>Time taken</span></div>
            </div>
          </div>
        </section>
        <div className="dashboard-grid" style={{ marginTop: 20 }}>
          <section className="panel">
            <div className="panel-header"><h2>Topic performance</h2></div>
            <div className="task-list">
              {[
                ["Equations of motion", "90%", "9/10 correct"],
                ["Graphs of motion", "80%", "4/5 correct"],
                ["Relative motion", "60%", "3/5 correct"],
              ].map(([topic, score, meta]) => (
                <div className="task-item" key={topic}>
                  <span className="task-count">{score}</span>
                  <div className="task-copy"><strong>{topic}</strong><span>{meta}</span></div>
                </div>
              ))}
            </div>
          </section>
          <aside className="panel">
            <div className="panel-header"><h2>Next step</h2></div>
            <p className="muted" style={{ fontSize: 12, lineHeight: 1.6 }}>
              Revisit relative motion before your next chapter test. Basic result
              guidance uses fixed academic rules only; Release 1.0 contains no AI.
            </p>
            <Link className="button button-primary button-full" href="/dashboard">
              <ArrowLeft size={15} /> Return to dashboard
            </Link>
          </aside>
        </div>
      </div>
    </div>
  );
}
