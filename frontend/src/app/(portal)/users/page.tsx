import { Download, Plus, Search, Upload, UserRoundCheck } from "lucide-react";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";

const users = [
  ["AR", "Ananya Rao", "ananya@demo.rabbit.local", "ORG ADMIN", "ACTIVE"],
  ["SM", "Sanjay Mehta", "sanjay@demo.rabbit.local", "FACULTY", "ACTIVE"],
  ["PM", "Priya Menon", "priya@demo.rabbit.local", "REVIEWER", "ACTIVE"],
  ["RI", "Rohan Iyer", "rohan@demo.rabbit.local", "STUDENT", "ACTIVE"],
  ["NK", "Nisha Kumar", "nisha@demo.rabbit.local", "FACULTY", "SUSPENDED"],
  ["VS", "Varun Shah", "varun@demo.rabbit.local", "ACADEMIC HEAD", "ACTIVE"],
];

export default function UsersPage() {
  return (
    <div className="page">
      <PageHeader
        eyebrow="User and role management"
        title="Users"
        description="Manage institutional identities, one primary role per user, account status, and section assignment."
        actions={
          <>
            <button className="button button-secondary">
              <Download size={15} /> Export
            </button>
            <button className="button button-secondary" disabled>
              <Upload size={15} /> Bulk import
            </button>
            <button className="button button-primary">
              <Plus size={15} /> Add user
            </button>
          </>
        }
      />
      <div className="toolbar">
        <div className="search-wrap">
          <Search size={17} />
          <input className="search-input" placeholder="Search name or email" />
        </div>
        <select className="filter-select"><option>All roles</option></select>
        <select className="filter-select"><option>All statuses</option></select>
      </div>
      <section className="user-grid">
        {users.map(([initials, name, email, role, status]) => (
          <article className="user-card" key={email}>
            <span className="avatar">{initials}</span>
            <div className="user-card-copy">
              <strong>{name}</strong>
              <span>{email}</span>
              <div className="question-meta-row" style={{ margin: "8px 0 0" }}>
                <span className="badge badge-neutral">{role}</span>
                <StatusBadge status={status} />
              </div>
            </div>
            <UserRoundCheck size={17} color="#827b90" />
          </article>
        ))}
      </section>
    </div>
  );
}
