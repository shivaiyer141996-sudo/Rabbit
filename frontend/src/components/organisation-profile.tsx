"use client";

import Link from "next/link";
import Image from "next/image";
import { Building2, CalendarDays, ImagePlus, MapPin, Settings, Trash2, Users } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { MetricCard } from "@/components/metric-card";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import {
  type AcademicCatalog,
  type OrganisationSummary,
  type UserSummary,
} from "@/lib/live-types";

export function OrganisationProfile() {
  const [organisation, setOrganisation] = useState<OrganisationSummary | null>(null);
  const [catalog, setCatalog] = useState<AcademicCatalog | null>(null);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [logoBusy, setLogoBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [current, nextCatalog, nextUsers] = await Promise.all([
        apiFetch<OrganisationSummary>("/organisations/current"),
        apiFetch<AcademicCatalog>("/academic-catalog"),
        apiFetch<UserSummary[]>("/users"),
      ]);
      setOrganisation(current);
      setCatalog(nextCatalog);
      setUsers(nextUsers);
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Organisation profile could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  const activeYear = catalog?.academicYears.find((year) => year.active);
  const activeUsers = users.filter((user) => user.status === "ACTIVE").length;
  const sectionsByDepartment = useMemo(() => {
    if (!catalog) return new Map<string, string[]>();
    return new Map(
      catalog.departments.map((department) => [
        department.id,
        catalog.sections
          .filter((section) => section.departmentId === department.id)
          .map((section) => section.name),
      ]),
    );
  }, [catalog]);

  if (loading) return <div className="page"><LoadingState label="Loading tenant profile…" /></div>;
  if (!organisation || !catalog) {
    return <div className="page"><ErrorState message={error} retry={() => void load()} /></div>;
  }

  async function uploadLogo(file?: File) {
    if (!file) return;
    setLogoBusy(true); setError(""); setMessage("");
    const form = new FormData(); form.append("file", file);
    try {
      const response = await fetch("/gateway/backend/organisation-branding/current/logo", {
        method: "PUT", body: form,
      });
      const body = await response.json().catch(() => null);
      if (!response.ok) throw new Error(body?.message ?? "Logo upload failed.");
      setMessage("Organisation logo updated."); await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Logo upload failed.");
    } finally { setLogoBusy(false); }
  }

  async function removeLogo() {
    if (!window.confirm("Remove the Organisation logo? Rabbit branding and initials will remain.")) return;
    setLogoBusy(true); setError(""); setMessage("");
    try {
      const response = await fetch("/gateway/backend/organisation-branding/current/logo", { method: "DELETE" });
      if (!response.ok) throw new Error("Logo could not be removed.");
      setMessage("Organisation logo removed."); await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Logo removal failed.");
    } finally { setLogoBusy(false); }
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Tenant configuration · Live"
        title={organisation.name}
        description={`Organisation code ${organisation.code} · ${organisation.timezone} · Tenant scope is derived from the signed session.`}
        actions={
          <Link className="button button-primary" href="/settings">
            <Settings size={15} /> Open settings
          </Link>
        }
      />
      {message && <div className="success-banner" role="status">{message}</div>}
      {error && <div className="form-error" role="alert">{error}</div>}
      <section className="panel organisation-branding-panel">
        <div className="organisation-brand-preview">
          {organisation.logoAvailable ? <Image alt={`${organisation.name} logo`} className="organisation-logo" height={72} src={`/gateway/backend/organisation-branding/organisations/${organisation.id}/logo?v=${organisation.logoUpdatedAt ?? "1"}`} unoptimized width={72} /> : <span className="org-avatar organisation-logo-fallback">{organisation.code.slice(0, 2)}</span>}
          <div><h2>Organisation branding</h2><p>Displayed beside Rabbit on this dashboard, Organisation selection, assessments and reports.</p></div>
        </div>
        <div className="table-actions"><label className="button button-secondary"><ImagePlus size={15} /> {organisation.logoAvailable ? "Change logo" : "Add logo"}<input accept="image/png,image/jpeg,image/webp" className="visually-hidden" disabled={logoBusy} onChange={(event) => void uploadLogo(event.target.files?.[0])} type="file" /></label>{organisation.logoAvailable && <button className="button button-ghost" disabled={logoBusy} onClick={() => void removeLogo()} type="button"><Trash2 size={15} /> Remove</button>}</div>
      </section>
      <section className="metrics-grid">
        <MetricCard icon={Users} value={String(activeUsers)} label="Active users" />
        <MetricCard icon={Building2} value={String(catalog.departments.length)} label="Departments" />
        <MetricCard icon={CalendarDays} value={activeYear?.name ?? "—"} label="Active academic year" />
        <MetricCard icon={MapPin} value={String(catalog.sections.length)} label="Sections and batches" />
      </section>
      <div className="content-grid">
        <section className="panel">
          <div className="panel-header"><h2>Academic structure</h2></div>
          <div className="data-table-wrap">
            <table className="data-table">
              <thead>
                <tr><th>Department</th><th>Sections</th><th>Count</th><th>Status</th></tr>
              </thead>
              <tbody>
                {catalog.departments.map((department) => (
                  <tr key={department.id}>
                    <td><strong>{department.name}</strong></td>
                    <td>{sectionsByDepartment.get(department.id)?.join(", ") || "—"}</td>
                    <td>{department.sectionCount}</td>
                    <td><StatusBadge status={department.active ? "ACTIVE" : "ARCHIVED"} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
        <aside className="panel">
          <div className="panel-header"><h2>Organisation details</h2></div>
          <dl className="definition-list">
            {[
              ["Organisation code", organisation.code],
              ["Time zone", organisation.timezone],
              ["Active year", activeYear?.name ?? "—"],
              ["Subjects", String(catalog.subjects.filter((subject) => subject.active).length)],
              ["Status", organisation.status],
              ["Tenant ID", organisation.id.slice(0, 8)],
            ].map(([label, value]) => (
              <div className="definition-row" key={label}>
                <dt>{label}</dt><dd>{value}</dd>
              </div>
            ))}
          </dl>
        </aside>
      </div>
    </div>
  );
}
