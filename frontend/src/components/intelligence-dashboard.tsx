"use client";

import Link from "next/link";
import {
  ArrowRight,
  BarChart3,
  Bell,
  BookOpenCheck,
  CheckCircle2,
  ClipboardCheck,
  ClipboardList,
  FileQuestion,
  GraduationCap,
  ShieldAlert,
  Users,
  type LucideIcon,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { DashboardResponse } from "@/lib/types";

const icons = [GraduationCap, BarChart3, CheckCircle2, ShieldAlert];

interface DashboardAction {
  href: string;
  label: string;
  title: string;
  description: string;
  icon: LucideIcon;
}

function roleContent(role: DashboardResponse["role"]) {
  const content: Record<DashboardResponse["role"], {
    primary: DashboardAction;
    trendTitle: string;
    trendDescription: string;
    trendSuffix: string;
    attentionTitle: string;
    attentionDescription: string;
    quickActions: DashboardAction[];
  }> = {
    SUPER_ADMIN: {
      primary: { href: "/reports", label: "Explore reports", title: "Review performance", description: "Compare institution results and learner risk.", icon: BarChart3 },
      trendTitle: "Institution performance",
      trendDescription: "Published assessment results only",
      trendSuffix: "%",
      attentionTitle: "Needs attention",
      attentionDescription: "Governance and publication queues",
      quickActions: [
        { href: "/users", label: "Manage users", title: "Manage institution users", description: "Invite users and control role and account state.", icon: Users },
        { href: "/approvals", label: "Open approvals", title: "Complete approvals", description: "Resolve governed question and assessment queues.", icon: ClipboardCheck },
        { href: "/audit-logs", label: "Open audit", title: "Explore audit events", description: "Trace governed actions and security-relevant change.", icon: ShieldAlert },
      ],
    },
    ORG_ADMIN: {
      primary: { href: "/reports", label: "Explore reports", title: "Review performance", description: "Compare institution results and learner risk.", icon: BarChart3 },
      trendTitle: "Institution performance",
      trendDescription: "Published assessment results only",
      trendSuffix: "%",
      attentionTitle: "Needs attention",
      attentionDescription: "Governance and publication queues",
      quickActions: [
        { href: "/users", label: "Manage users", title: "Manage institution users", description: "Invite users and control role and account state.", icon: Users },
        { href: "/approvals", label: "Open approvals", title: "Complete approvals", description: "Resolve governed question and assessment queues.", icon: ClipboardCheck },
        { href: "/pilot-readiness", label: "Pilot readiness", title: "Review pilot evidence", description: "Track institutional checks and release ownership.", icon: CheckCircle2 },
      ],
    },
    ACADEMIC_HEAD: {
      primary: { href: "/approvals", label: "Open approvals", title: "Complete approvals", description: "Resolve academic governance queues.", icon: ClipboardCheck },
      trendTitle: "Academic performance",
      trendDescription: "Published results across the institution",
      trendSuffix: "%",
      attentionTitle: "Academic actions",
      attentionDescription: "Quality, publication, and intervention queues",
      quickActions: [
        { href: "/approvals", label: "Open approvals", title: "Govern content", description: "Review questions and assessments independently.", icon: BookOpenCheck },
        { href: "/reports", label: "Open reports", title: "Review learner outcomes", description: "Find at-risk students and compare performance.", icon: BarChart3 },
        { href: "/question-bank", label: "Question bank", title: "Inspect question quality", description: "Review the approved academic inventory.", icon: FileQuestion },
      ],
    },
    FACULTY: {
      primary: { href: "/reports/teacher", label: "Open teacher reports", title: "Review learner performance", description: "Compare batches, students, and weak topics.", icon: BarChart3 },
      trendTitle: "Your assessment performance",
      trendDescription: "Average published score by authored assessment",
      trendSuffix: "%",
      attentionTitle: "Your delivery actions",
      attentionDescription: "Draft, live, and publication work",
      quickActions: [
        { href: "/assessments/new", label: "Create assessment", title: "Create an assessment", description: "Build an MCQ assessment from approved questions.", icon: ClipboardList },
        { href: "/question-bank/new", label: "Author question", title: "Author a question", description: "Add a governed Single or Multiple Correct MCQ.", icon: FileQuestion },
        { href: "/reports/teacher", label: "Open reports", title: "Review learner outcomes", description: "Compare batches, students, and weak topics.", icon: BarChart3 },
      ],
    },
    REVIEWER: {
      primary: { href: "/approvals", label: "Open review queue", title: "Open review queue", description: "Apply the independent academic checklist.", icon: ClipboardCheck },
      trendTitle: "Current review workload",
      trendDescription: "Items waiting for an independent decision",
      trendSuffix: "",
      attentionTitle: "Review priorities",
      attentionDescription: "Governance queues assigned to your role",
      quickActions: [
        { href: "/approvals", label: "Review now", title: "Review pending items", description: "Approve or return questions and assessments.", icon: ClipboardCheck },
        { href: "/question-bank", label: "Question bank", title: "Inspect question context", description: "Open the complete governed question record.", icon: FileQuestion },
        { href: "/notifications", label: "Notifications", title: "Review notifications", description: "Check workflow decisions and urgent requests.", icon: Bell },
      ],
    },
    STUDENT: {
      primary: { href: "/student/assessments", label: "View assessments", title: "Available assessments", description: "Read instructions and start an eligible attempt.", icon: ClipboardList },
      trendTitle: "Your published progress",
      trendDescription: "Score history visible after faculty publication",
      trendSuffix: "%",
      attentionTitle: "Your next actions",
      attentionDescription: "Assessments, pending results, and support",
      quickActions: [
        { href: "/student/assessments", label: "View assessments", title: "Take an assessment", description: "See eligible windows and read instructions first.", icon: ClipboardList },
        { href: "/student/reports", label: "My results", title: "Review performance", description: "See published results, analytics, and question review.", icon: BarChart3 },
        { href: "/notifications", label: "Notifications", title: "Check notifications", description: "See result publication and assessment updates.", icon: Bell },
      ],
    },
  };
  return content[role];
}

export function IntelligenceDashboard() {
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setDashboard(await apiFetch<DashboardResponse>("/dashboard"));
    } catch (requestError) {
      setDashboard(null);
      setError(apiErrorMessage(requestError, "Dashboard could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  if (loading) {
    return <div className="page"><LoadingState label="Loading live academic intelligence…" /></div>;
  }
  if (!dashboard) {
    return <div className="page"><ErrorState message={error} retry={() => void load()} /></div>;
  }

  const maxTrend = Math.max(...dashboard.trend.map((item) => item.value), 1);
  const content = roleContent(dashboard.role);
  const PrimaryIcon = content.primary.icon;

  return (
    <div className="page">
      <PageHeader
        eyebrow={`${dashboard.workspaceTitle} · Live`}
        title={dashboard.greeting}
        description={dashboard.description}
        actions={
          <Link className="button button-primary" href={content.primary.href}>
            <PrimaryIcon size={16} /> {content.primary.label}
          </Link>
        }
      />

      <section className="metrics-grid" aria-label="Academic intelligence metrics">
        {dashboard.metrics.map((metric, index) => {
          const Icon = icons[index % icons.length];
          return (
            <Link className={`metric-card tone-${metric.tone.toLowerCase()}`} href={metric.href} key={metric.label}>
              <span className="metric-icon">
                <Icon size={18} />
              </span>
              <span className="metric-label">{metric.label}</span>
              <strong>{metric.value}</strong>
              <small>{metric.context}</small>
            </Link>
          );
        })}
      </section>

      <section className="intelligence-grid">
        <article className="panel chart-panel">
          <div className="panel-header">
            <div>
              <h2>{content.trendTitle}</h2>
              <p>{content.trendDescription}</p>
            </div>
            <Link href={content.primary.href}>
              {content.primary.label} <ArrowRight size={13} />
            </Link>
          </div>
          {dashboard.trend.length ? (
            <div className="bar-chart" aria-label="Average score trend">
              {dashboard.trend.map((item) => (
                <div className="bar-column" key={item.label}>
                  <span className="bar-value">{item.value}{content.trendSuffix}</span>
                  <div className="bar-track">
                    <div
                      className="bar-fill"
                      style={{ height: `${Math.max(8, (item.value / maxTrend) * 100)}%` }}
                    />
                  </div>
                  <span className="bar-label">{item.label}</span>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty-state">Published results will build this trend.</div>
          )}
        </article>

        {dashboard.role !== "STUDENT" && <article className="panel">
          <div className="panel-header">
            <div>
              <h2>{content.attentionTitle}</h2>
              <p>{content.attentionDescription}</p>
            </div>
            <Link href={content.primary.href}>Open workspace</Link>
          </div>
          <div className="attention-list">
            {dashboard.attention.map((item) => (
              <Link className="attention-item" href={item.href} key={item.title}>
                <span className={`attention-count severity-${item.severity.toLowerCase()}`}>
                  {item.count}
                </span>
                <div>
                  <strong>{item.title}</strong>
                  <span>{item.description}</span>
                </div>
                <ArrowRight size={15} />
              </Link>
            ))}
            {!dashboard.attention.length && (
              <div className="empty-state">There are no actions waiting for you.</div>
            )}
          </div>
        </article>}
      </section>

      <section className="quick-grid" aria-label="Intelligence quick actions">
        {content.quickActions.map((action) => {
          const ActionIcon = action.icon;
          return (
            <Link className="quick-action" href={action.href} key={action.href}>
              <ActionIcon size={20} />
              <strong>{action.title}</strong>
              <span>{action.description}</span>
            </Link>
          );
        })}
      </section>
    </div>
  );
}
