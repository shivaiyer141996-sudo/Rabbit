import Link from "next/link";
import {
  ArrowRight,
  CalendarClock,
  ClipboardCheck,
  FilePlus2,
  FileQuestion,
  GraduationCap,
  Plus,
  Users,
} from "lucide-react";
import { MetricCard } from "@/components/metric-card";
import { PageHeader } from "@/components/page-header";

const activity = [
  ["Kinematics Chapter Test was scheduled", "Faculty · 12 minutes ago"],
  ["CHE-ORG-014 was submitted for review", "Question Bank · 38 minutes ago"],
  ["32 students completed Cell Structure", "Assessment · 1 hour ago"],
  ["Mathematics batch A was updated", "Organisation · 3 hours ago"],
];

const tasks = [
  ["Questions waiting for review", "Academic governance", "6"],
  ["Draft assessments to complete", "Assessment management", "3"],
  ["Student invitations pending", "User management", "12"],
];

export default function DashboardPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="Foundation dashboard"
        title="Good afternoon, Ananya"
        description="Here is what needs attention across Rabbit Demo Academy today."
        actions={
          <Link className="button button-primary" href="/question-bank/new">
            <Plus size={16} /> Create question
          </Link>
        }
      />

      <section className="metrics-grid" aria-label="Key metrics">
        <MetricCard
          icon={GraduationCap}
          label="Active students"
          value="1,248"
          change="+4.2%"
        />
        <MetricCard
          icon={ClipboardCheck}
          label="Assessments this month"
          value="36"
          change="+8"
        />
        <MetricCard
          icon={FileQuestion}
          label="Approved questions"
          value="4,826"
          change="+124"
        />
        <MetricCard
          icon={CalendarClock}
          label="Scheduled this week"
          value="8"
        />
      </section>

      <section className="dashboard-grid">
        <article className="panel">
          <div className="panel-header">
            <h2>Recent activity</h2>
            <Link href="/question-bank">
              View all <ArrowRight size={12} />
            </Link>
          </div>
          <div className="activity-list">
            {activity.map(([title, meta]) => (
              <div className="activity-item" key={title}>
                <span className="activity-dot" />
                <div className="activity-copy">
                  <strong>{title}</strong>
                  <span>{meta}</span>
                </div>
              </div>
            ))}
          </div>
        </article>

        <article className="panel">
          <div className="panel-header">
            <h2>Pending tasks</h2>
            <Link href="/question-bank">Open queue</Link>
          </div>
          <div className="task-list">
            {tasks.map(([title, meta, count]) => (
              <div className="task-item" key={title}>
                <span className="task-count">{count}</span>
                <div className="task-copy">
                  <strong>{title}</strong>
                  <span>{meta}</span>
                </div>
                <ArrowRight size={15} color="#8d8797" />
              </div>
            ))}
          </div>
        </article>
      </section>

      <section className="quick-grid" aria-label="Quick actions">
        <Link className="quick-action" href="/question-bank/new">
          <FilePlus2 size={20} />
          <strong>Author a question</strong>
          <span>Create a governed Single or Multiple Correct MCQ.</span>
        </Link>
        <Link className="quick-action" href="/assessments/new">
          <ClipboardCheck size={20} />
          <strong>Create an assessment</strong>
          <span>Build from approved questions and configure delivery.</span>
        </Link>
        <Link className="quick-action" href="/users">
          <Users size={20} />
          <strong>Manage users</strong>
          <span>Review roles, status, and section assignments.</span>
        </Link>
      </section>
    </div>
  );
}
