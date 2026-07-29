import { Building2, CalendarDays, MapPin, Users } from "lucide-react";
import { MetricCard } from "@/components/metric-card";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";

export default function OrganisationPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="Tenant configuration"
        title="Rabbit Demo Academy"
        description="Organisation code DEMO · Asia/Kolkata · Tenant data is isolated by the signed session context."
        actions={<button className="button button-primary">Edit organisation</button>}
      />
      <section className="metrics-grid">
        <MetricCard icon={Users} value="1,312" label="Active users" />
        <MetricCard icon={Building2} value="4" label="Departments" />
        <MetricCard icon={CalendarDays} value="2026–27" label="Active academic year" />
        <MetricCard icon={MapPin} value="8" label="Sections and batches" />
      </section>
      <div className="content-grid">
        <section className="panel">
          <div className="panel-header"><h2>Academic structure</h2></div>
          <table className="data-table">
            <thead>
              <tr><th>Department</th><th>Programme / class</th><th>Sections</th><th>Status</th></tr>
            </thead>
            <tbody>
              <tr><td>Science</td><td>JEE 2027</td><td>Batch A, Batch B</td><td><StatusBadge status="ACTIVE" /></td></tr>
              <tr><td>Medical</td><td>NEET 2027</td><td>Batch A, Batch B</td><td><StatusBadge status="ACTIVE" /></td></tr>
              <tr><td>Management</td><td>CAT 2026</td><td>Weekend</td><td><StatusBadge status="ACTIVE" /></td></tr>
            </tbody>
          </table>
        </section>
        <aside className="panel">
          <div className="panel-header"><h2>Organisation details</h2></div>
          <dl className="definition-list">
            {[
              ["Organisation code", "DEMO"],
              ["Time zone", "Asia/Kolkata"],
              ["Active year", "2026–27"],
              ["Default language", "English"],
              ["Status", "Active"],
              ["Created", "26 July 2026"],
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
