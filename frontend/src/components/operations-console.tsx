"use client";

import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  CircleGauge,
  Database,
  Flag,
  RefreshCw,
  Server,
  ShieldCheck,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { PageHeader } from "@/components/page-header";
import { apiFetch, ApiError } from "@/lib/api";
import { demoFeatureFlags, demoOperationalSnapshot } from "@/lib/ga-demo";
import type {
  FeatureFlag,
  FeatureFlagKey,
  OperationalSnapshot,
} from "@/lib/types";

function duration(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return hours ? `${hours}h ${minutes}m` : `${minutes}m`;
}

export function OperationsConsole() {
  const [snapshot, setSnapshot] =
    useState<OperationalSnapshot>(demoOperationalSnapshot);
  const [flags, setFlags] = useState<FeatureFlag[]>(demoFeatureFlags);
  const [live, setLive] = useState(false);
  const [busy, setBusy] = useState<FeatureFlagKey | "refresh" | null>(null);
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    setBusy("refresh");
    try {
      const [nextSnapshot, nextFlags] = await Promise.all([
        apiFetch<OperationalSnapshot>("/operations/readiness"),
        apiFetch<FeatureFlag[]>("/feature-flags"),
      ]);
      setSnapshot(nextSnapshot);
      setFlags(nextFlags);
      setLive(true);
      setMessage("");
    } catch {
      setLive(false);
    } finally {
      setBusy(null);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    const timer = window.setInterval(load, 30_000);
    return () => {
      window.clearTimeout(initial);
      window.clearInterval(timer);
    };
  }, [load]);

  async function updateFlag(
    key: FeatureFlagKey,
    enabled: boolean,
    rolloutPercentage: number,
  ) {
    setBusy(key);
    setMessage("");
    try {
      const updated = await apiFetch<FeatureFlag>(`/feature-flags/${key}`, {
        method: "PATCH",
        body: JSON.stringify({ enabled, rolloutPercentage }),
      });
      setFlags((current) =>
        current.map((flag) => (flag.key === key ? updated : flag)),
      );
      setLive(true);
      setMessage(`${updated.label} updated and recorded in the audit log.`);
    } catch (error) {
      setMessage(
        error instanceof ApiError
          ? error.message
          : "The feature flag could not be updated.",
      );
    } finally {
      setBusy(null);
    }
  }

  const databaseUtilisation = useMemo(() => {
    const maximum = snapshot.capacity.databaseMaximumConnections;
    if (!maximum) return 0;
    return Math.round(
      (snapshot.capacity.databaseActiveConnections / maximum) * 100,
    );
  }, [snapshot.capacity]);

  const readinessTone =
    snapshot.overallStatus === "READY"
      ? "success"
      : snapshot.overallStatus === "NOT_READY"
        ? "danger"
        : "warning";

  return (
    <div className="page">
      <PageHeader
        eyebrow="Release 1.0 operations"
        title="Pilot readiness & controls"
        description="Live service health, workflow backlogs, capacity, release gates, and tenant-scoped feature rollout."
        actions={
          <button
            className="button button-secondary"
            disabled={busy === "refresh"}
            onClick={load}
          >
            <RefreshCw
              className={busy === "refresh" ? "spin" : ""}
              size={15}
            />
            Refresh
          </button>
        }
      />

      {!live && (
        <div className="preview-banner" role="status">
          Preview readiness data is visible while the live operations API is unavailable.
        </div>
      )}
      {message && <div className="workflow-message" role="status">{message}</div>}

      <section className={`readiness-hero readiness-${readinessTone}`}>
        <div className="readiness-mark">
          {snapshot.overallStatus === "READY" ? (
            <CheckCircle2 size={26} />
          ) : snapshot.overallStatus === "NOT_READY" ? (
            <XCircle size={26} />
          ) : (
            <AlertTriangle size={26} />
          )}
        </div>
        <div>
          <span>Current release state</span>
          <h2>{snapshot.overallStatus.replaceAll("_", " ")}</h2>
          <p>
            Release {snapshot.releaseVersion} · {snapshot.environment} · uptime{" "}
            {duration(snapshot.uptimeSeconds)}
          </p>
        </div>
        <time dateTime={snapshot.generatedAt}>
          Updated {new Date(snapshot.generatedAt).toLocaleTimeString()}
        </time>
      </section>

      <section className="operations-grid">
        <article className="panel">
          <div className="panel-header">
            <div><h2>Dependencies</h2><p>Direct live probes</p></div>
            <Server size={19} />
          </div>
          <div className="dependency-list">
            {snapshot.dependencies.map((dependency) => (
              <div className="dependency-row" key={dependency.name}>
                <span
                  className={`dependency-dot ${dependency.status.toLowerCase()}`}
                  aria-hidden="true"
                />
                <div>
                  <strong>{dependency.name}</strong>
                  <small>{dependency.detail}</small>
                </div>
                <span className={`badge ${
                  dependency.status === "UP" ? "badge-success" : "badge-danger"
                }`}>
                  {dependency.status} · {dependency.latencyMs} ms
                </span>
              </div>
            ))}
          </div>
        </article>

        <article className="panel">
          <div className="panel-header">
            <div><h2>Traffic & capacity</h2><p>Since the current API process started</p></div>
            <CircleGauge size={19} />
          </div>
          <dl className="definition-list">
            <div className="definition-row">
              <dt>Requests</dt><dd>{snapshot.traffic.requests.toLocaleString()}</dd>
            </div>
            <div className="definition-row">
              <dt>Average latency</dt><dd>{snapshot.traffic.averageLatencyMs} ms</dd>
            </div>
            <div className="definition-row">
              <dt>Server error rate</dt><dd>{snapshot.traffic.errorRate}%</dd>
            </div>
            <div className="definition-row">
              <dt>Rate limited</dt><dd>{snapshot.traffic.rateLimitedRequests}</dd>
            </div>
            <div className="definition-row">
              <dt>Database pool</dt>
              <dd>{snapshot.capacity.databaseActiveConnections} / {snapshot.capacity.databaseMaximumConnections} ({databaseUtilisation}%)</dd>
            </div>
            <div className="definition-row">
              <dt>JVM memory</dt>
              <dd>{snapshot.capacity.jvmUsedMemoryMb} / {snapshot.capacity.jvmMaximumMemoryMb} MB</dd>
            </div>
          </dl>
        </article>
      </section>

      <section className="metrics-grid operations-metrics">
        {[
          [Activity, "Live attempts", snapshot.workflows.activeAssessmentAttempts, "Students currently in progress"],
          [ShieldCheck, "Pending reviews", snapshot.workflows.pendingQuestionReviews + snapshot.workflows.pendingAssessmentReviews, "Question and assessment governance"],
          [Database, "Results to publish", snapshot.workflows.pendingResultPublications, "Explicit publication required"],
          [AlertTriangle, "Failed deliveries", snapshot.workflows.failedNotifications, "Provider or retry attention"],
        ].map(([Icon, label, value, context]) => {
          const ItemIcon = Icon as typeof Activity;
          return (
            <article className="metric-card" key={String(label)}>
              <span className="metric-icon"><ItemIcon size={18} /></span>
              <span className="metric-label">{String(label)}</span>
              <strong>{String(value)}</strong>
              <small>{String(context)}</small>
            </article>
          );
        })}
      </section>

      <section className="operations-grid">
        <article className="panel">
          <div className="panel-header">
            <div><h2>Release gates</h2><p>Evidence-backed pilot checks</p></div>
            <ShieldCheck size={19} />
          </div>
          <div className="readiness-list">
            {snapshot.readiness.map((check) => (
              <div className="readiness-row" key={check.key}>
                {check.status === "PASS" ? (
                  <CheckCircle2 className="text-success" size={19} />
                ) : check.status === "FAIL" ? (
                  <XCircle className="text-danger" size={19} />
                ) : (
                  <AlertTriangle className="text-warning" size={19} />
                )}
                <div>
                  <strong>{check.label}</strong>
                  <small>{check.detail}</small>
                </div>
                <span className={`badge badge-${check.status === "PASS" ? "success" : check.status === "FAIL" ? "danger" : "warning"}`}>
                  {check.status}
                </span>
              </div>
            ))}
          </div>
        </article>

        <article className="panel">
          <div className="panel-header">
            <div><h2>Feature rollout</h2><p>Every change is tenant scoped and audited</p></div>
            <Flag size={19} />
          </div>
          <div className="feature-flag-list">
            {flags.map((flag) => (
              <div className="feature-flag-row" key={flag.key}>
                <div>
                  <strong>{flag.label}</strong>
                  <small>{flag.description}</small>
                  <label className="rollout-control">
                    <span>Rollout</span>
                    <select
                      aria-label={`${flag.label} rollout percentage`}
                      disabled={busy === flag.key}
                      onChange={(event) =>
                        updateFlag(
                          flag.key,
                          flag.enabled,
                          Number(event.target.value),
                        )
                      }
                      value={flag.rolloutPercentage}
                    >
                      {[0, 10, 25, 50, 75, 100].map((percentage) => (
                        <option key={percentage} value={percentage}>
                          {percentage}%
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                <label className="flag-switch">
                  <span className="sr-only">
                    {flag.enabled ? "Disable" : "Enable"} {flag.label}
                  </span>
                  <input
                    checked={flag.enabled}
                    disabled={busy === flag.key}
                    onChange={(event) =>
                      updateFlag(
                        flag.key,
                        event.target.checked,
                        event.target.checked
                          ? flag.rolloutPercentage || 100
                          : flag.rolloutPercentage,
                      )
                    }
                    type="checkbox"
                  />
                </label>
              </div>
            ))}
          </div>
        </article>
      </section>
    </div>
  );
}
