"use client";

import Link from "next/link";
import { Building2, CalendarDays, MapPin, Settings, Users } from "lucide-react";
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
