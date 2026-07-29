"use client";

import Link from "next/link";
import { Bell, CheckCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { demoNotifications } from "@/lib/intelligence-demo";
import type { NotificationInbox } from "@/lib/types";

export function NotificationPopover() {
  const [inbox, setInbox] = useState<NotificationInbox>(demoNotifications);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    let active = true;
    apiFetch<NotificationInbox>("/notifications")
      .then((value) => {
        if (active) setInbox(value);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, []);

  async function markAllRead() {
    await apiFetch("/notifications/read-all", { method: "PATCH" }).catch(
      () => undefined,
    );
    setInbox((current) => ({
      unreadCount: 0,
      items: current.items.map((item) => ({ ...item, read: true })),
    }));
  }

  return (
    <div className="notification-wrap">
      <button
        aria-expanded={open}
        aria-label={`Notifications, ${inbox.unreadCount} unread`}
        className="icon-button notification-trigger"
        onClick={() => setOpen((value) => !value)}
      >
        <Bell size={18} />
        {inbox.unreadCount > 0 && (
          <span className="notification-dot">{Math.min(9, inbox.unreadCount)}</span>
        )}
      </button>
      {open && (
        <div className="notification-popover">
          <div className="popover-heading">
            <div><strong>Notifications</strong><span>{inbox.unreadCount} unread</span></div>
            <button className="icon-button" onClick={markAllRead} aria-label="Mark all read">
              <CheckCheck size={16} />
            </button>
          </div>
          <div className="popover-list">
            {inbox.items.slice(0, 4).map((item) => (
              <Link
                className={`popover-item ${item.read ? "" : "unread"}`}
                href={item.actionUrl ?? "/notifications"}
                key={item.id}
                onClick={() => setOpen(false)}
              >
                <span className={`notification-type type-${item.type.toLowerCase()}`} />
                <div><strong>{item.title}</strong><span>{item.message}</span></div>
              </Link>
            ))}
          </div>
          <Link className="popover-footer" href="/notifications" onClick={() => setOpen(false)}>
            Open notification centre
          </Link>
        </div>
      )}
    </div>
  );
}
