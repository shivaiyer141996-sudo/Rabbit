"use client";

import Link from "next/link";
import {
  ArrowRight,
  BarChart3,
  CheckCircle2,
  ClipboardCheck,
  GraduationCap,
  ShieldAlert,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { DashboardResponse } from "@/lib/types";

const icons = [GraduationCap, BarChart3, CheckCircle2, ShieldAlert];

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

  return (
    <div className="page">
      <PageHeader
        eyebrow="Academic intelligence · Live"
        title={dashboard.greeting}
        description={dashboard.description}
        actions={
          <Link className="button button-primary" href="/reports">
            <BarChart3 size={16} /> Explore reports
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
              <h2>Performance trend</h2>
              <p>Published assessment results only</p>
            </div>
            <Link href="/reports">
              Open analytics <ArrowRight size={13} />
            </Link>
          </div>
          {dashboard.trend.length ? (
            <div className="bar-chart" aria-label="Average score trend">
              {dashboard.trend.map((item) => (
                <div className="bar-column" key={item.label}>
                  <span className="bar-value">{item.value}%</span>
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

        <article className="panel">
          <div className="panel-header">
            <div>
              <h2>Needs attention</h2>
              <p>Governance and publication queues</p>
            </div>
            <Link href="/approvals">Open workspace</Link>
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
          </div>
        </article>
      </section>

      <section className="quick-grid" aria-label="Intelligence quick actions">
        <Link className="quick-action" href="/approvals">
          <ClipboardCheck size={20} />
          <strong>Complete approvals</strong>
          <span>Review questions and assessments with full decision history.</span>
        </Link>
        <Link className="quick-action" href="/reports">
          <BarChart3 size={20} />
          <strong>Review performance</strong>
          <span>Compare results, question quality, and faculty contribution.</span>
        </Link>
        <Link className="quick-action" href="/audit-logs">
          <ShieldAlert size={20} />
          <strong>Explore audit events</strong>
          <span>Trace every governed action with actor and before/after values.</span>
        </Link>
      </section>
    </div>
  );
}
