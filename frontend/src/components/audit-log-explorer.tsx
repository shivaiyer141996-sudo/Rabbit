"use client";

import {
  Download,
  Filter,
  Search,
  ShieldCheck,
} from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { AuditEvent } from "@/lib/types";

export function AuditLogExplorer() {
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [module, setModule] = useState("");
  const [action, setAction] = useState("");
  const [actor, setActor] = useState("");
  const [live, setLive] = useState(false);
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState("");

  async function search() {
    setLoading(true);
    setError("");
    const query = new URLSearchParams();
    if (module) query.set("module", module);
    if (action) query.set("action", action);
    if (actor) query.set("actor", actor);
    try {
      const rows = await apiFetch<AuditEvent[]>(
        `/audit-events${query.size ? `?${query}` : ""}`,
      );
      setEvents(rows);
      setLive(true);
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Audit events could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let active = true;
    apiFetch<AuditEvent[]>("/audit-events")
      .then((rows) => {
        if (!active) return;
        setEvents(rows);
        setLive(true);
      })
      .catch((requestError) => {
        if (!active) return;
        setLive(false);
        setError(apiErrorMessage(requestError, "Audit events could not be loaded."));
      })
      .finally(() => {
        if (active) setInitialLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  function submit(event: FormEvent) {
    event.preventDefault();
    void search();
  }

  const exportQuery = new URLSearchParams();
  if (module) exportQuery.set("module", module);
  if (action) exportQuery.set("action", action);
  if (actor) exportQuery.set("actor", actor);

  return (
    <div className="page">
      <PageHeader
        eyebrow="Governance & compliance"
        title="Immutable audit explorer"
        description="Search actor, module, action, IP address, trace ID, and before/after values. Audit events cannot be edited or deleted."
        actions={
          <a
            className="button button-secondary"
            href={`/gateway/backend/audit-events/export${exportQuery.size ? `?${exportQuery}` : ""}`}
          >
            <Download size={15} /> Export CSV
          </a>
        }
      />

      {initialLoading && <LoadingState label="Loading immutable audit events…" />}
      {!initialLoading && !live && <ErrorState message={error} />}

      <form className="audit-toolbar" onSubmit={submit}>
        <div className="search-wrap">
          <Search size={17} />
          <input
            className="search-input"
            onChange={(event) => setActor(event.target.value)}
            placeholder="Actor email or user ID"
            value={actor}
          />
        </div>
        <select
          className="filter-select"
          onChange={(event) => setModule(event.target.value)}
          value={module}
        >
          <option value="">All modules</option>
          {["AUTH", "USR", "QB", "QRV", "ASM", "DEL", "EVL", "RPT", "SET"].map(
            (item) => <option key={item}>{item}</option>,
          )}
        </select>
        <input
          className="filter-select"
          onChange={(event) => setAction(event.target.value)}
          placeholder="Action"
          value={action}
        />
        <button className="button button-primary" disabled={loading} type="submit">
          <Filter size={15} /> Apply filters
        </button>
      </form>

      <section className="panel report-table-panel">
        <div className="panel-header">
          <div>
            <h2>Event stream</h2>
            <p>{events.length} event(s) in the current view</p>
          </div>
          <span className="tamper-chip"><ShieldCheck size={14} /> Tamper-evident</span>
        </div>
        <div className="data-table-wrap">
          <table className="data-table audit-table">
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>Actor</th>
                <th>Event</th>
                <th>Entity</th>
                <th>Change</th>
                <th>Trace</th>
              </tr>
            </thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.id}>
                  <td>
                    <strong>{new Date(event.timestamp).toLocaleDateString()}</strong>
                    <span className="table-subtitle">
                      {new Date(event.timestamp).toLocaleTimeString()}
                    </span>
                  </td>
                  <td>
                    <strong>{event.actorEmail ?? event.actorUserId.slice(0, 8)}</strong>
                    <span className="table-subtitle">
                      {event.actorRole?.replaceAll("_", " ") ?? "—"} · {event.ipAddress ?? "—"}
                    </span>
                  </td>
                  <td>
                    <span className="badge badge-neutral">{event.module}</span>
                    <span className="table-subtitle">{event.action}</span>
                  </td>
                  <td>
                    <strong>{event.entityType}</strong>
                    <span className="table-subtitle">{event.entityId?.slice(0, 8) ?? "—"}</span>
                  </td>
                  <td>
                    <span className="audit-before">{event.beforeValue ?? "—"}</span>
                    <span className="audit-arrow">→</span>
                    <span className="audit-after">{event.afterValue ?? "—"}</span>
                  </td>
                  <td><code>{event.traceId?.slice(0, 12) ?? "—"}</code></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!events.length && <div className="empty-state">No audit events match these filters.</div>}
      </section>
    </div>
  );
}
