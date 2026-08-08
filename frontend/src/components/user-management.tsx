"use client";

import {
  Copy,
  Download,
  Plus,
  RefreshCw,
  Search,
  Upload,
  UserRoundCheck,
  X,
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import {
  initials,
  type AcademicCatalog,
  type InvitationIssue,
  type UserSummary,
} from "@/lib/live-types";
import type { UserRole } from "@/lib/types";
import { activeSectionOptions } from "@/lib/enhancement-rules";

function csvCell(value: unknown) {
  return `"${String(value ?? "").replaceAll('"', '""')}"`;
}

export function UserManagement() {
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [catalog, setCatalog] = useState<AcademicCatalog | null>(null);
  const [query, setQuery] = useState("");
  const [role, setRole] = useState("ALL");
  const [status, setStatus] = useState("ALL");
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [issuedInvitation, setIssuedInvitation] =
    useState<InvitationIssue | null>(null);
  const [draft, setDraft] = useState({
    email: "",
    firstName: "",
    lastName: "",
    role: "STUDENT" as UserRole,
    sectionId: "",
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [rows, nextCatalog] = await Promise.all([
        apiFetch<UserSummary[]>("/users"),
        apiFetch<AcademicCatalog>("/academic-catalog"),
      ]);
      setUsers(rows);
      setCatalog(nextCatalog);
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Users could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  const filtered = useMemo(
    () =>
      users.filter((user) => {
        const text = `${user.firstName} ${user.lastName} ${user.email}`.toLowerCase();
        return (
          text.includes(query.toLowerCase()) &&
          (role === "ALL" || user.role === role) &&
          (status === "ALL" || user.status === status)
        );
      }),
    [query, role, status, users],
  );

  async function create(event: FormEvent) {
    event.preventDefault();
    setBusy("create");
    setError("");
    setMessage("");
    try {
      const issued = await apiFetch<InvitationIssue>("/users", {
        method: "POST",
        body: JSON.stringify({
          ...draft,
          sectionId: draft.sectionId || null,
        }),
      });
      setDraft({
        email: "",
        firstName: "",
        lastName: "",
        role: "STUDENT",
        sectionId: "",
      });
      setAdding(false);
      setIssuedInvitation(issued);
      setMessage(
        `Invitation created for ${issued.user.firstName} ${issued.user.lastName}.`,
      );
      await load();
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "User could not be created."));
    } finally {
      setBusy("");
    }
  }

  async function reissueInvitation(user: UserSummary) {
    setBusy(user.membershipId);
    setError("");
    setMessage("");
    try {
      const issued = await apiFetch<InvitationIssue>(
        `/users/${user.membershipId}/invitation`,
        { method: "POST" },
      );
      setIssuedInvitation(issued);
      setMessage(`A new activation link was generated for ${user.firstName}.`);
    } catch (requestError) {
      setError(
        apiErrorMessage(
          requestError,
          "A new invitation link could not be generated.",
        ),
      );
    } finally {
      setBusy("");
    }
  }

  async function copyInvitation() {
    if (!issuedInvitation) return;
    try {
      await navigator.clipboard.writeText(issuedInvitation.activationUrl);
      setMessage(
        "Activation link copied. Share it only through an approved secure channel.",
      );
    } catch {
      setError("Copy is unavailable in this browser. Select the link and copy it manually.");
    }
  }

  async function changeStatus(
    user: UserSummary,
    nextStatus: "ACTIVE" | "SUSPENDED",
  ) {
    setBusy(user.membershipId);
    setError("");
    try {
      const updated = await apiFetch<UserSummary>(
        `/users/${user.membershipId}/status`,
        {
          method: "PATCH",
          body: JSON.stringify({ status: nextStatus }),
        },
      );
      setUsers((current) =>
        current.map((item) =>
          item.membershipId === updated.membershipId ? updated : item,
        ),
      );
      setMessage(
        `${updated.firstName} ${updated.lastName} is now ${updated.status.toLowerCase()}.`,
      );
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Account status could not be changed."));
    } finally {
      setBusy("");
    }
  }

  function exportCsv() {
    const rows = [
      ["Name", "Email", "Role", "Status", "Section"],
      ...filtered.map((user) => [
        `${user.firstName} ${user.lastName}`,
        user.email,
        user.role,
        user.status,
        catalog?.sections.find((section) => section.id === user.sectionId)?.name ?? "",
      ]),
    ];
    const blob = new Blob(
      [rows.map((row) => row.map(csvCell).join(",")).join("\n")],
      { type: "text/csv;charset=utf-8" },
    );
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `rabbit-users-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(link.href);
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="User and role management · Live"
        title="Users"
        description="Manage persisted institutional identities, one primary role, account status, and section assignment."
        actions={
          <>
            <button
              className="button button-secondary"
              disabled={!filtered.length}
              onClick={exportCsv}
              type="button"
            >
              <Download size={15} /> Export
            </button>
            <button
              className="button button-secondary"
              disabled
              title="Bulk import is disabled until the pilot data template is approved."
              type="button"
            >
              <Upload size={15} /> Bulk import
            </button>
            <button
              className="button button-primary"
              onClick={() => setAdding((current) => !current)}
              type="button"
            >
              {adding ? <X size={15} /> : <Plus size={15} />}
              {adding ? "Close form" : "Add user"}
            </button>
          </>
        }
      />
      {message && <div className="success-banner">{message}</div>}
      {error && <div className="form-error" role="alert">{error}</div>}
      {issuedInvitation && (
        <section className="invitation-banner" aria-label="New activation link">
          <div>
            <strong>One-time activation link</strong>
            <span>
              Expires {new Date(issuedInvitation.expiresAt).toLocaleString()}.
              Generating another link invalidates this one.
            </span>
          </div>
          <input
            aria-label="Activation link"
            onFocus={(event) => event.currentTarget.select()}
            readOnly
            value={issuedInvitation.activationUrl}
          />
          <button
            className="button button-secondary"
            onClick={() => void copyInvitation()}
            type="button"
          >
            <Copy size={15} /> Copy link
          </button>
          <button
            aria-label="Dismiss activation link"
            className="icon-button"
            onClick={() => setIssuedInvitation(null)}
            type="button"
          >
            <X size={17} />
          </button>
        </section>
      )}

      {adding && (
        <form className="form-section compact-form" onSubmit={create}>
          <h2>Create invitation record</h2>
          <p>
            Rabbit creates a one-time activation link. Share it through your
            institution&apos;s approved secure channel.
          </p>
          <div className="field-row">
            <div className="field">
              <label htmlFor="new-first-name">First name</label>
              <input
                id="new-first-name"
                onChange={(event) =>
                  setDraft((current) => ({ ...current, firstName: event.target.value }))
                }
                required
                value={draft.firstName}
              />
            </div>
            <div className="field">
              <label htmlFor="new-last-name">Last name</label>
              <input
                id="new-last-name"
                onChange={(event) =>
                  setDraft((current) => ({ ...current, lastName: event.target.value }))
                }
                required
                value={draft.lastName}
              />
            </div>
            <div className="field">
              <label htmlFor="new-email">Email</label>
              <input
                id="new-email"
                onChange={(event) =>
                  setDraft((current) => ({ ...current, email: event.target.value }))
                }
                required
                type="email"
                value={draft.email}
              />
            </div>
          </div>
          <div className="field-row">
            <div className="field">
              <label htmlFor="new-role">Role</label>
              <select
                id="new-role"
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    role: event.target.value as UserRole,
                    sectionId: ["STUDENT", "FACULTY"].includes(event.target.value) ? current.sectionId : "",
                  }))
                }
                value={draft.role}
              >
                {["ORG_ADMIN", "ACADEMIC_HEAD", "FACULTY", "REVIEWER", "STUDENT"].map(
                  (value) => <option key={value}>{value}</option>,
                )}
              </select>
            </div>
            <div className="field">
              <label htmlFor="new-section">Section</label>
              <select
                disabled={!(["STUDENT", "FACULTY"] as UserRole[]).includes(draft.role)}
                id="new-section"
                onChange={(event) =>
                  setDraft((current) => ({ ...current, sectionId: event.target.value }))
                }
                required={(["STUDENT", "FACULTY"] as UserRole[]).includes(draft.role)}
                value={draft.sectionId}
              >
                <option value="">Select section</option>
                {activeSectionOptions(catalog?.sections ?? []).map((section) => (
                  <option key={section.id} value={section.id}>
                    {section.programmeName} · {section.batchName} · {section.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <button className="button button-primary" disabled={busy === "create"} type="submit">
            <Plus size={15} /> {busy === "create" ? "Creating…" : "Create invitation"}
          </button>
        </form>
      )}

      <div className="toolbar">
        <div className="search-wrap">
          <Search size={17} />
          <input
            className="search-input"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search name or email"
            value={query}
          />
        </div>
        <select className="filter-select" onChange={(event) => setRole(event.target.value)} value={role}>
          <option value="ALL">All roles</option>
          {["ORG_ADMIN", "ACADEMIC_HEAD", "FACULTY", "REVIEWER", "STUDENT"].map(
            (value) => <option key={value}>{value}</option>,
          )}
        </select>
        <select className="filter-select" onChange={(event) => setStatus(event.target.value)} value={status}>
          <option value="ALL">All statuses</option>
          {["INVITED", "ACTIVE", "SUSPENDED", "ARCHIVED"].map(
            (value) => <option key={value}>{value}</option>,
          )}
        </select>
      </div>

      {loading && <LoadingState label="Loading live organisation users…" />}
      {!loading && !catalog && <ErrorState message={error} retry={() => void load()} />}
      {!loading && catalog && (
        <section className="user-grid">
          {filtered.map((user) => (
            <article className="user-card" key={user.membershipId}>
              <span className="avatar">{initials(user.firstName, user.lastName)}</span>
              <div className="user-card-copy">
                <strong>{user.firstName} {user.lastName}</strong>
                <span>{user.email}</span>
                <div className="question-meta-row" style={{ margin: "8px 0 0" }}>
                  <span className="badge badge-neutral">{user.role.replaceAll("_", " ")}</span>
                  <StatusBadge status={user.status} />
                </div>
                {user.sectionId && (
                  <small>
                    {catalog.sections.find((section) => section.id === user.sectionId)?.name
                      ?? "Unknown section"}
                  </small>
                )}
              </div>
              {user.status === "ACTIVE" && (
                <button
                  className="icon-button"
                  disabled={busy === user.membershipId}
                  onClick={() => void changeStatus(user, "SUSPENDED")}
                  title="Suspend account"
                  type="button"
                >
                  <UserRoundCheck size={17} />
                </button>
              )}
              {user.status === "SUSPENDED" && (
                <button
                  className="button button-ghost"
                  disabled={busy === user.membershipId}
                  onClick={() => void changeStatus(user, "ACTIVE")}
                  type="button"
                >
                  Activate
                </button>
              )}
              {user.status === "INVITED" && (
                <button
                  className="button button-ghost"
                  disabled={busy === user.membershipId}
                  onClick={() => void reissueInvitation(user)}
                  type="button"
                >
                  <RefreshCw size={15} /> New link
                </button>
              )}
            </article>
          ))}
          {!filtered.length && <div className="empty-state">No users match the filters.</div>}
        </section>
      )}
    </div>
  );
}
