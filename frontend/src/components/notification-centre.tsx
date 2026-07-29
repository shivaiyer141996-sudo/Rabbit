"use client";

import Link from "next/link";
import {
  Bell,
  CheckCheck,
  ChevronRight,
  Mail,
  MessageSquareText,
  RefreshCw,
  Save,
} from "lucide-react";
import { useEffect, useState } from "react";
import { PageHeader } from "@/components/page-header";
import { apiFetch } from "@/lib/api";
import { demoNotifications } from "@/lib/intelligence-demo";
import type { NotificationInbox } from "@/lib/types";

interface Preferences {
  inAppEnabled: boolean;
  emailEnabled: boolean;
  smsEnabled: boolean;
  assessmentReminders: boolean;
  workflowUpdates: boolean;
  resultUpdates: boolean;
}

const defaultPreferences: Preferences = {
  inAppEnabled: true,
  emailEnabled: true,
  smsEnabled: false,
  assessmentReminders: true,
  workflowUpdates: true,
  resultUpdates: true,
};

export function NotificationCentre() {
  const [inbox, setInbox] = useState<NotificationInbox>(demoNotifications);
  const [preferences, setPreferences] =
    useState<Preferences>(defaultPreferences);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [live, setLive] = useState(false);

  useEffect(() => {
    let active = true;
    Promise.all([
      apiFetch<NotificationInbox>("/notifications"),
      apiFetch<Preferences>("/notifications/preferences"),
    ])
      .then(([nextInbox, nextPreferences]) => {
        if (!active) return;
        setInbox(nextInbox);
        setPreferences(nextPreferences);
        setLive(true);
      })
      .catch(() => setLive(false));
    return () => {
      active = false;
    };
  }, []);

  async function markRead(id: string) {
    await apiFetch(`/notifications/${id}/read`, { method: "PATCH" }).catch(
      () => undefined,
    );
    setInbox((current) => {
      const target = current.items.find((item) => item.id === id);
      return {
        unreadCount: Math.max(0, current.unreadCount - (target?.read ? 0 : 1)),
        items: current.items.map((item) =>
          item.id === id ? { ...item, read: true } : item,
        ),
      };
    });
  }

  async function markAllRead() {
    await apiFetch("/notifications/read-all", { method: "PATCH" }).catch(
      () => undefined,
    );
    setInbox((current) => ({
      unreadCount: 0,
      items: current.items.map((item) => ({ ...item, read: true })),
    }));
  }

  async function savePreferences() {
    setBusy(true);
    setMessage("");
    try {
      const saved = await apiFetch<Preferences>("/notifications/preferences", {
        method: "PUT",
        body: JSON.stringify(preferences),
      });
      setPreferences(saved);
      setLive(true);
      setMessage("Notification preferences saved.");
    } finally {
      setBusy(false);
    }
  }

  function preferenceRow(
    key: keyof Preferences,
    title: string,
    description: string,
    icon: typeof Bell,
  ) {
    const Icon = icon;
    return (
      <label className="preference-row">
        <span className="preference-icon"><Icon size={17} /></span>
        <span><strong>{title}</strong><small>{description}</small></span>
        <input
          checked={preferences[key]}
          onChange={(event) =>
            setPreferences((current) => ({
              ...current,
              [key]: event.target.checked,
            }))
          }
          type="checkbox"
        />
      </label>
    );
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Notification & communication framework"
        title="Notification centre"
        description="Workflow decisions, assessment reminders, result publication, and critical academic alerts."
        actions={
          <button className="button button-secondary" onClick={markAllRead}>
            <CheckCheck size={15} /> Mark all read
          </button>
        }
      />

      {!live && <div className="preview-banner">Preview notifications are currently shown.</div>}
      {message && <div className="success-banner">{message}</div>}

      <div className="notification-page-grid">
        <section className="panel inbox-panel">
          <div className="panel-header">
            <div><h2>Inbox</h2><p>{inbox.unreadCount} unread notification(s)</p></div>
          </div>
          <div className="notification-list">
            {inbox.items.map((item) => (
              <article className={`notification-card ${item.read ? "" : "unread"}`} key={item.id}>
                <span className={`notification-type type-${item.type.toLowerCase()}`} />
                <div className="notification-copy">
                  <div>
                    <span className="badge badge-neutral">{item.type.replaceAll("_", " ")}</span>
                    {item.critical && <span className="badge badge-danger">Critical</span>}
                  </div>
                  <strong>{item.title}</strong>
                  <p>{item.message}</p>
                  <span>{new Date(item.createdAt).toLocaleString()}</span>
                </div>
                <div className="notification-actions">
                  {!item.read && (
                    <button className="button button-ghost" onClick={() => markRead(item.id)}>
                      Mark read
                    </button>
                  )}
                  {item.actionUrl && (
                    <Link className="icon-button" href={item.actionUrl}>
                      <ChevronRight size={17} />
                    </Link>
                  )}
                </div>
              </article>
            ))}
          </div>
        </section>

        <aside className="panel preferences-panel">
          <div className="panel-header">
            <div><h2>Preferences</h2><p>Critical alerts cannot be disabled.</p></div>
          </div>
          {preferenceRow("inAppEnabled", "In-app centre", "Show non-critical events inside Rabbit.", Bell)}
          {preferenceRow("emailEnabled", "Email", "Prepare email delivery for enabled event types.", Mail)}
          {preferenceRow("smsEnabled", "SMS", "Organisation-level SMS must also be enabled.", MessageSquareText)}
          <div className="preference-divider">Event types</div>
          {preferenceRow("assessmentReminders", "Assessment reminders", "Scheduling and window-opening notices.", Bell)}
          {preferenceRow("workflowUpdates", "Workflow decisions", "Question and assessment review updates.", CheckCheck)}
          {preferenceRow("resultUpdates", "Result updates", "Result publication and re-evaluation events.", RefreshCw)}
          <button className="button button-primary button-full" disabled={busy} onClick={savePreferences}>
            <Save size={15} /> Save preferences
          </button>
        </aside>
      </div>
    </div>
  );
}
