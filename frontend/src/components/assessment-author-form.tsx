"use client";

import { useRouter } from "next/navigation";
import { Check, ChevronDown, LoaderCircle, Save, Search, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { StatusBadge } from "@/components/status-badge";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import { subjectName, topicName, type AcademicCatalog, type ApiAssessment, type ApiQuestion } from "@/lib/live-types";
import { addUnique, matchesAssessmentQuestionFilters, questionIdsForRemovedSubject } from "@/lib/enhancement-rules";

type AssessmentType = ApiAssessment["type"];

export function AssessmentAuthorForm() {
  const router = useRouter();
  const [catalog, setCatalog] = useState<AcademicCatalog | null>(null);
  const [questions, setQuestions] = useState<ApiQuestion[]>([]);
  const [title, setTitle] = useState("");
  const [code, setCode] = useState("");
  const [type, setType] = useState<AssessmentType>("CHAPTER_TEST");
  const [subjectIds, setSubjectIds] = useState<string[]>([]);
  const [subjectSearch, setSubjectSearch] = useState("");
  const [subjectsOpen, setSubjectsOpen] = useState(false);
  const [filters, setFilters] = useState({ subjectId: "", topicId: "", difficulty: "", type: "", query: "" });
  const [durationMinutes, setDurationMinutes] = useState(45);
  const [attemptsAllowed, setAttemptsAllowed] = useState(1);
  const [shuffleQuestions, setShuffleQuestions] = useState(true);
  const [shuffleOptions, setShuffleOptions] = useState(false);
  const [partialMarking, setPartialMarking] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    Promise.all([apiFetch<AcademicCatalog>("/academic-catalog"), apiFetch<ApiQuestion[]>("/questions")])
      .then(([nextCatalog, rows]) => {
        if (!active) return;
        setCatalog(nextCatalog);
        setQuestions(rows.filter((question) => ["APPROVED", "PUBLISHED"].includes(question.status)));
      })
      .catch((requestError) => active && setError(apiErrorMessage(requestError, "Assessment masters could not be loaded.")))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, []);

  const dirty = Boolean(title || code || subjectIds.length || selectedIds.length);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty || busy) return;
      event.preventDefault();
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [busy, dirty]);

  const filteredSubjects = useMemo(() => catalog?.subjects.filter((item) => item.active && `${item.code} ${item.name}`.toLowerCase().includes(subjectSearch.toLowerCase())) ?? [], [catalog, subjectSearch]);
  const selected = useMemo(() => questions.filter((question) => selectedIds.includes(question.id)), [questions, selectedIds]);
  const availableQuestions = useMemo(() => questions.filter((question) => matchesAssessmentQuestionFilters(question, subjectIds, filters)), [filters, questions, subjectIds]);
  const totalMarks = selected.reduce((sum, question) => sum + Number(question.marks), 0);
  const subjectSummary = subjectIds.map((subjectId) => {
    const rows = selected.filter((question) => question.subjectId === subjectId);
    return { id: subjectId, name: catalog ? subjectName(catalog, subjectId) : "Subject", questions: rows.length, marks: rows.reduce((sum, question) => sum + Number(question.marks), 0) };
  });
  const validation = [!title.trim() ? "Assessment title is required." : "", !subjectIds.length ? "Choose at least one subject." : "", durationMinutes < 1 ? "Duration must be at least one minute." : "", attemptsAllowed < 1 ? "At least one attempt must be allowed." : "", !selectedIds.length ? "Choose at least one approved question." : ""].filter(Boolean);

  function toggleSubject(subjectId: string, checked: boolean) {
    if (checked) {
      setSubjectIds((current) => addUnique(current, subjectId));
      return;
    }
    const affectedIds = questionIdsForRemovedSubject(questions, selectedIds, subjectId);
    if (affectedIds.length && !window.confirm(`Removing this subject will remove ${affectedIds.length} selected question${affectedIds.length === 1 ? "" : "s"}. Continue?`)) return;
    setSubjectIds((current) => current.filter((id) => id !== subjectId));
    setSelectedIds((current) => current.filter((id) => !affectedIds.includes(id)));
    setFilters((current) => ({ ...current, subjectId: current.subjectId === subjectId ? "" : current.subjectId, topicId: "" }));
  }

  function toggleQuestion(questionId: string, checked: boolean) {
    setSelectedIds((current) => checked ? addUnique(current, questionId) : current.filter((id) => id !== questionId));
  }

  async function save() {
    setError("");
    if (validation.length) { setError(validation.join(" ")); return; }
    setBusy(true);
    try {
      const saved = await apiFetch<ApiAssessment>("/assessments", { method: "POST", body: JSON.stringify({ code: code.trim() || null, title: title.trim(), type, subjectIds, durationMinutes, shuffleQuestions, shuffleOptions, partialMarking, attemptsAllowed, questionIds: selectedIds }) });
      router.push(`/assessments/${saved.id}`);
      router.refresh();
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Assessment draft could not be saved."));
    } finally { setBusy(false); }
  }

  if (loading) return <LoadingState label="Loading approved questions…" />;
  if (!catalog) return <ErrorState message={error || "Academic masters are unavailable."} />;

  return (
    <div className="form-layout">
      <div>
        {error && <div className="form-error" role="alert">{error}</div>}
        <section className="form-section">
          <h2>1. Assessment details</h2>
          <p>Select one or more subjects. Existing question selections are preserved when subjects are added.</p>
          <div className="field-row">
            <div className="field"><label htmlFor="assessment-title">Assessment title</label><input id="assessment-title" onChange={(event) => setTitle(event.target.value)} placeholder="e.g. Science Unit Test" value={title} /></div>
            <div className="field"><label htmlFor="assessment-code">Assessment code</label><input id="assessment-code" onChange={(event) => setCode(event.target.value)} placeholder="Generated automatically when blank" value={code} /></div>
          </div>
          <div className="field-row">
            <div className="field"><label htmlFor="assessment-type">Assessment type</label><select id="assessment-type" onChange={(event) => setType(event.target.value as AssessmentType)} value={type}>{["PRACTICE_ASSESSMENT", "CLASS_TEST", "UNIT_TEST", "CHAPTER_TEST", "MID_TERM_EXAMINATION", "FINAL_EXAMINATION", "MOCK_TEST"].map((value) => <option key={value} value={value}>{value.replaceAll("_", " ")}</option>)}</select></div>
            <div className="field multi-select-field">
              <label id="assessment-subject-label">Subjects</label>
              <button aria-expanded={subjectsOpen} aria-haspopup="listbox" aria-labelledby="assessment-subject-label" className="multi-select-trigger" onClick={() => setSubjectsOpen((current) => !current)} type="button"><span>{subjectIds.length ? `${subjectIds.length} subject${subjectIds.length === 1 ? "" : "s"} selected` : "Select subjects"}</span><ChevronDown size={16} /></button>
              {subjectsOpen && <div className="multi-select-popover" role="listbox" aria-multiselectable="true"><div className="search-wrap"><Search size={15} /><input aria-label="Search subjects" autoFocus onChange={(event) => setSubjectSearch(event.target.value)} placeholder="Search by code or name" value={subjectSearch} /></div>{filteredSubjects.map((subject) => <label className="check-row" key={subject.id}><input checked={subjectIds.includes(subject.id)} onChange={(event) => toggleSubject(subject.id, event.target.checked)} type="checkbox" />{subject.code} · {subject.name}</label>)}</div>}
              <div className="selected-chips">{subjectIds.map((subjectId) => <button className="selection-chip" key={subjectId} onClick={() => toggleSubject(subjectId, false)} type="button">{subjectName(catalog, subjectId)} <X size={12} /></button>)}</div>
            </div>
          </div>
          <div className="field-row"><div className="field"><label htmlFor="duration">Duration (minutes)</label><input id="duration" min="1" onChange={(event) => setDurationMinutes(Number(event.target.value))} type="number" value={durationMinutes} /></div><div className="field"><label htmlFor="attempts">Attempts allowed</label><input id="attempts" min="1" onChange={(event) => setAttemptsAllowed(Number(event.target.value))} type="number" value={attemptsAllowed} /></div></div>
        </section>

        <section className="form-section">
          <h2>2. Choose approved questions</h2>
          <p>Approved questions from every selected subject are available. Duplicate selection is prevented.</p>
          <div className="question-filter-grid">
            <div className="search-wrap"><Search size={15} /><input aria-label="Search approved questions" onChange={(event) => setFilters((current) => ({ ...current, query: event.target.value }))} placeholder="Search code or question" value={filters.query} /></div>
            <select aria-label="Filter by subject" onChange={(event) => setFilters((current) => ({ ...current, subjectId: event.target.value, topicId: "" }))} value={filters.subjectId}><option value="">All selected subjects</option>{subjectIds.map((id) => <option key={id} value={id}>{subjectName(catalog, id)}</option>)}</select>
            <select aria-label="Filter by topic" onChange={(event) => setFilters((current) => ({ ...current, topicId: event.target.value }))} value={filters.topicId}><option value="">All topics</option>{catalog.topics.filter((topic) => subjectIds.includes(topic.subjectId) && (!filters.subjectId || topic.subjectId === filters.subjectId)).map((topic) => <option key={topic.id} value={topic.id}>{topic.name}</option>)}</select>
            <select aria-label="Filter by difficulty" onChange={(event) => setFilters((current) => ({ ...current, difficulty: event.target.value }))} value={filters.difficulty}><option value="">All difficulties</option>{["EASY", "MEDIUM", "HARD"].map((value) => <option key={value}>{value}</option>)}</select>
            <select aria-label="Filter by question type" onChange={(event) => setFilters((current) => ({ ...current, type: event.target.value }))} value={filters.type}><option value="">All question types</option><option value="SINGLE_CORRECT">Single correct</option><option value="MULTIPLE_CORRECT">Multiple correct</option></select>
          </div>
          {!subjectIds.length && <div className="empty-state">Choose at least one subject first.</div>}
          {!!subjectIds.length && !availableQuestions.length && <div className="empty-state">No approved questions match these filters.</div>}
          <div className="option-list">{availableQuestions.map((question, index) => <label className="option-row" key={question.id}><input checked={selectedIds.includes(question.id)} onChange={(event) => toggleQuestion(question.id, event.target.checked)} type="checkbox" /><span className="option-label">{index + 1}</span><div style={{ flex: 1 }}><p style={{ margin: 0 }}>{question.stem}</p><div className="question-meta-row" style={{ margin: "8px 0 0" }}><span className="badge badge-neutral">{question.code}</span><span className="badge badge-neutral">{subjectName(catalog, question.subjectId)}</span><span className="badge badge-neutral">{topicName(catalog, question.topicId)}</span><span className="badge badge-neutral">{question.difficulty}</span><span className="badge badge-neutral">{question.type.replaceAll("_", " ")}</span><StatusBadge status={question.status} /><span className="badge badge-info">{question.marks} marks</span></div></div></label>)}</div>
        </section>

        <section className="form-section"><h2>3. Delivery defaults</h2><p>The final window and eligible sections are set after approval.</p>{[["Shuffle question order", shuffleQuestions, setShuffleQuestions], ["Shuffle option order", shuffleOptions, setShuffleOptions], ["Apply partial marking to Multiple Correct questions", partialMarking, setPartialMarking]].map(([label, checked, setter]) => <label className="check-row" key={String(label)}><input checked={Boolean(checked)} onChange={(event) => (setter as (value: boolean) => void)(event.target.checked)} type="checkbox" />{String(label)}</label>)}</section>
      </div>

      <aside className="sticky-panel"><section className="panel"><div className="panel-header"><h2>Assessment summary</h2></div><dl className="definition-list"><div className="definition-row"><dt>Subjects selected</dt><dd>{subjectIds.length}</dd></div>{subjectSummary.map((subject) => <div className="definition-row summary-subject" key={subject.id}><dt>{subject.name}</dt><dd>{subject.questions} Q · {subject.marks} marks</dd></div>)}<div className="definition-row"><dt>Total questions</dt><dd>{selectedIds.length}</dd></div><div className="definition-row"><dt>Total marks</dt><dd>{totalMarks}</dd></div><div className="definition-row"><dt>Duration</dt><dd>{durationMinutes} minutes</dd></div><div className="definition-row"><dt>Status</dt><dd><StatusBadge status="DRAFT" /></dd></div></dl><div className="explanation"><Check size={14} /> Every selected question is approved and unique.</div><button className="button button-primary button-full" disabled={busy} onClick={() => void save()} style={{ marginTop: 16 }} type="button">{busy ? <LoaderCircle className="spin" size={15} /> : <Save size={15} />}{busy ? "Saving…" : "Save draft"}</button></section></aside>
    </div>
  );
}
