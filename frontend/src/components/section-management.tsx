"use client";

import { Archive, CheckCircle2, Edit3, Plus, Search, XCircle } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import type { AcademicCatalog, AcademicSectionSummary } from "@/lib/live-types";

const PAGE_SIZE = 10;
const emptyDraft = { name: "", programmeId: "", academicYearId: "", batchId: "" };

export function SectionManagement() {
  const [sections, setSections] = useState<AcademicSectionSummary[]>([]);
  const [catalog, setCatalog] = useState<AcademicCatalog | null>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [page, setPage] = useState(1);
  const [editing, setEditing] = useState<AcademicSectionSummary | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [draft, setDraft] = useState(emptyDraft);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [rows, masters] = await Promise.all([apiFetch<AcademicSectionSummary[]>("/academic-masters/sections"), apiFetch<AcademicCatalog>("/academic-catalog")]);
      setSections(rows); setCatalog(masters);
    } catch (requestError) { setError(apiErrorMessage(requestError, "Sections could not be loaded.")); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { const initial = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(initial); }, [load]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (!formOpen || !Object.values(draft).some(Boolean)) return; event.preventDefault(); };
    window.addEventListener("beforeunload", warn); return () => window.removeEventListener("beforeunload", warn);
  }, [draft, formOpen]);

  const batches = useMemo(() => catalog?.batches.filter((batch) => (!draft.programmeId || batch.programmeId === draft.programmeId) && (!draft.academicYearId || batch.academicYearId === draft.academicYearId) && batch.active) ?? [], [catalog, draft.academicYearId, draft.programmeId]);
  const filtered = useMemo(() => sections.filter((section) => `${section.name} ${section.programmeName} ${section.batchName} ${section.academicYearName}`.toLowerCase().includes(query.toLowerCase()) && (status === "ALL" || section.status === status)), [query, sections, status]);
  const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, pages);
  const visible = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

  function openCreate() { setEditing(null); setDraft(emptyDraft); setFormOpen(true); setError(""); }
  function openEdit(section: AcademicSectionSummary) { setEditing(section); setDraft({ name: section.name, programmeId: section.programmeId, academicYearId: section.academicYearId, batchId: section.batchId }); setFormOpen(true); setError(""); }

  async function save(event: FormEvent) {
    event.preventDefault(); setBusy("save"); setError(""); setMessage("");
    try {
      const saved = await apiFetch<AcademicSectionSummary>(editing ? `/academic-masters/sections/${editing.id}` : "/academic-masters/sections", { method: editing ? "PUT" : "POST", body: JSON.stringify(draft) });
      setSections((current) => editing ? current.map((item) => item.id === saved.id ? saved : item) : [saved, ...current]);
      setFormOpen(false); setDraft(emptyDraft); setEditing(null); setMessage(`Section ${editing ? "updated" : "created"} successfully. It is now available in active section dropdowns.`);
      const refreshed = await apiFetch<AcademicCatalog>("/academic-catalog"); setCatalog(refreshed);
    } catch (requestError) { setError(apiErrorMessage(requestError, "Section could not be saved.")); }
    finally { setBusy(""); }
  }

  async function transition(section: AcademicSectionSummary, action: "activate" | "deactivate" | "archive") {
    const prompt = action === "archive" ? `Archive ${section.name}? Historical assignments and reports will be retained.` : `${action === "activate" ? "Activate" : "Deactivate"} ${section.name}?`;
    if (!window.confirm(prompt)) return;
    setBusy(section.id); setError(""); setMessage("");
    try {
      const updated = await apiFetch<AcademicSectionSummary>(`/academic-masters/sections/${section.id}/${action}`, { method: "PATCH" });
      setSections((current) => current.map((item) => item.id === updated.id ? updated : item));
      setMessage(`${section.name} is now ${updated.status.toLowerCase()}.`);
      setCatalog(await apiFetch<AcademicCatalog>("/academic-catalog"));
    } catch (requestError) { setError(apiErrorMessage(requestError, "Section status could not be changed.")); }
    finally { setBusy(""); }
  }

  if (loading) return <div className="page"><LoadingState label="Loading academic sections…" /></div>;
  if (!catalog) return <div className="page"><ErrorState message={error || "Academic masters are unavailable."} retry={() => void load()} /></div>;

  return <div className="page"><PageHeader eyebrow="Organisation → Academic Masters → Sections" title="Section Management" description="Create and govern programme, academic-year, and batch-specific sections without deleting history." actions={<button className="button button-primary" onClick={openCreate} type="button"><Plus size={15} /> Create Section</button>} />{message && <div className="success-banner" role="status">{message}</div>}{error && <div className="form-error" role="alert">{error}</div>}
    {formOpen && <form className="form-section" onSubmit={save}><div className="panel-header"><div><h2>{editing ? "Edit Section" : "Create Section"}</h2><p>Names must be unique within the selected programme and batch.</p></div><button aria-label="Close section form" className="icon-button" onClick={() => setFormOpen(false)} type="button"><XCircle size={18} /></button></div><div className="field-row"><div className="field"><label htmlFor="section-name">Section name</label><input id="section-name" maxLength={150} onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))} required value={draft.name} /></div><div className="field"><label htmlFor="section-programme">Programme / Course</label><select id="section-programme" onChange={(event) => setDraft((current) => ({ ...current, programmeId: event.target.value, batchId: "" }))} required value={draft.programmeId}><option value="">Select programme</option>{catalog.programmes.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{item.code} · {item.name}</option>)}</select></div></div><div className="field-row"><div className="field"><label htmlFor="section-year">Academic year</label><select id="section-year" onChange={(event) => setDraft((current) => ({ ...current, academicYearId: event.target.value, batchId: "" }))} required value={draft.academicYearId}><option value="">Select academic year</option>{catalog.academicYears.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></div><div className="field"><label htmlFor="section-batch">Batch</label><select id="section-batch" onChange={(event) => setDraft((current) => ({ ...current, batchId: event.target.value }))} required value={draft.batchId}><option value="">Select batch</option>{batches.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></div></div><button className="button button-primary" disabled={busy === "save"} type="submit">{busy === "save" ? "Saving…" : editing ? "Save changes" : "Create section"}</button></form>}
    <section className="panel report-table-panel"><div className="panel-header"><div><h2>Sections</h2><p>{filtered.length} matching records</p></div><div className="table-filters"><div className="search-wrap"><Search size={15} /><input aria-label="Search sections" onChange={(event) => setQuery(event.target.value)} placeholder="Search sections" value={query} /></div><select aria-label="Filter section status" onChange={(event) => setStatus(event.target.value)} value={status}><option value="ALL">All statuses</option><option>ACTIVE</option><option>INACTIVE</option><option>ARCHIVED</option></select></div></div>{!visible.length ? <div className="empty-state">No sections match the selected filters.</div> : <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Section Name</th><th>Programme</th><th>Batch</th><th>Academic Year</th><th>Students</th><th>Teachers</th><th>Status</th><th>Actions</th></tr></thead><tbody>{visible.map((section) => <tr key={section.id}><td><strong>{section.name}</strong><span className="table-subtitle">{section.assessmentCount} assessments</span></td><td>{section.programmeName}</td><td>{section.batchName}</td><td>{section.academicYearName}</td><td>{section.studentCount}</td><td>{section.teacherCount}</td><td><span className={`badge ${section.status === "ACTIVE" ? "badge-success" : "badge-neutral"}`}>{section.status}</span></td><td><div className="table-actions">{section.status !== "ARCHIVED" && <button aria-label={`Edit ${section.name}`} className="icon-button" disabled={busy === section.id} onClick={() => openEdit(section)} type="button"><Edit3 size={15} /></button>}{section.status === "ACTIVE" && <button aria-label={`Deactivate ${section.name}`} className="icon-button" disabled={busy === section.id} onClick={() => void transition(section, "deactivate")} type="button"><XCircle size={15} /></button>}{section.status === "INACTIVE" && <button aria-label={`Activate ${section.name}`} className="icon-button" disabled={busy === section.id} onClick={() => void transition(section, "activate")} type="button"><CheckCircle2 size={15} /></button>}{section.status !== "ARCHIVED" && <button aria-label={`Archive ${section.name}`} className="icon-button" disabled={busy === section.id} onClick={() => void transition(section, "archive")} type="button"><Archive size={15} /></button>}</div></td></tr>)}</tbody></table></div>}<div className="pagination"><button className="button button-secondary" disabled={page === 1} onClick={() => setPage((current) => current - 1)} type="button">Previous</button><span>Page {page} of {pages}</span><button className="button button-secondary" disabled={page >= pages} onClick={() => setPage((current) => current + 1)} type="button">Next</button></div></section>
  </div>;
}
