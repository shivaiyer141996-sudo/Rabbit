"use client";

import {
  BookOpenCheck,
  Check,
  Palette,
  Plus,
  RefreshCw,
  Save,
  SlidersHorizontal,
} from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch, ApiError } from "@/lib/api";
import { validateGradeBands } from "@/lib/settings-validation";
import type { GradeBand, SettingsBundle } from "@/lib/types";

type SettingsTab = "general" | "grading" | "masters";

const emptySettings: SettingsBundle = {
  general: {
    timezone: "Asia/Kolkata",
    language: "en",
    passPercentage: 40,
    atRiskThreshold: 35,
    defaultDurationMinutes: 45,
    defaultAttemptsAllowed: 1,
    shuffleQuestions: true,
    shuffleOptions: false,
    emailNotificationsEnabled: false,
    smsNotificationsEnabled: false,
    rankingEnabled: false,
    auditRetentionDays: 365,
    displayName: "",
    primaryColour: "#5936C8",
  },
  gradeBands: [],
  subjects: [],
  topics: [],
};

export function SettingsConsole() {
  const [tab, setTab] = useState<SettingsTab>("general");
  const [bundle, setBundle] = useState<SettingsBundle>(emptySettings);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [live, setLive] = useState(false);
  const [subjectCode, setSubjectCode] = useState("");
  const [subjectName, setSubjectName] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  useEffect(() => {
    let active = true;
    apiFetch<SettingsBundle>("/settings")
      .then((value) => {
        if (!active) return;
        setBundle(value);
        setLive(true);
      })
      .catch((requestError) => {
        if (!active) return;
        setLive(false);
        setLoadError(apiErrorMessage(requestError, "Settings could not be loaded."));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  function updateGeneral<K extends keyof SettingsBundle["general"]>(
    key: K,
    value: SettingsBundle["general"][K],
  ) {
    setBundle((current) => ({
      ...current,
      general: { ...current.general, [key]: value },
    }));
  }

  async function saveGeneral() {
    setBusy(true);
    setMessage("");
    try {
      const general = await apiFetch<SettingsBundle["general"]>(
        "/settings/general",
        { method: "PUT", body: JSON.stringify(bundle.general) },
      );
      setBundle((current) => ({ ...current, general }));
      setLive(true);
      setMessage("Organisation settings saved and added to the audit trail.");
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "Settings could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  function updateBand(index: number, patch: Partial<GradeBand>) {
    setBundle((current) => ({
      ...current,
      gradeBands: current.gradeBands.map((band, bandIndex) =>
        bandIndex === index ? { ...band, ...patch } : band,
      ),
    }));
  }

  async function saveGrades() {
    const error = validateGradeBands(bundle.gradeBands);
    if (error) {
      setMessage(error);
      return;
    }
    setBusy(true);
    setMessage("");
    try {
      const gradeBands = await apiFetch<GradeBand[]>("/settings/grade-bands", {
        method: "PUT",
        body: JSON.stringify({ bands: bundle.gradeBands }),
      });
      setBundle((current) => ({ ...current, gradeBands }));
      setLive(true);
      setMessage("Grade bands saved. New evaluations will use this configuration.");
    } catch (apiError) {
      setMessage(
        apiError instanceof ApiError ? apiError.message : "Grade bands could not be saved.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function addSubject(event: FormEvent) {
    event.preventDefault();
    if (!subjectCode.trim() || !subjectName.trim()) return;
    setBusy(true);
    setMessage("");
    try {
      const subject = await apiFetch<SettingsBundle["subjects"][number]>(
        "/settings/subjects",
        {
          method: "POST",
          body: JSON.stringify({ code: subjectCode, name: subjectName }),
        },
      );
      setBundle((current) => ({
        ...current,
        subjects: [...current.subjects, subject],
      }));
      setSubjectCode("");
      setSubjectName("");
      setLive(true);
      setMessage("Subject created.");
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "Subject could not be created.");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <div className="page"><LoadingState label="Loading live organisation settings…" /></div>;
  }
  if (!live) {
    return <div className="page"><ErrorState message={loadError} /></div>;
  }

  return (
    <div className="page">
      <PageHeader
        eyebrow="Organisation configuration · Live"
        title="Settings & academic masters"
        description="Configure grading, assessment defaults, notifications, branding, and master data without code changes."
      />

      {message && <div className="workflow-message">{message}</div>}

      <div className="segmented-control" role="tablist" aria-label="Settings category">
        <button className={tab === "general" ? "active" : ""} onClick={() => setTab("general")}>
          <SlidersHorizontal size={15} /> General
        </button>
        <button className={tab === "grading" ? "active" : ""} onClick={() => setTab("grading")}>
          <Check size={15} /> Grading
        </button>
        <button className={tab === "masters" ? "active" : ""} onClick={() => setTab("masters")}>
          <BookOpenCheck size={15} /> Academic masters
        </button>
      </div>

      {tab === "general" && (
        <div className="settings-grid">
          <section className="form-section">
            <div className="section-heading">
              <div>
                <h2>Organisation & locale</h2>
                <p>These values shape every organisation-scoped experience.</p>
              </div>
              <Palette size={19} />
            </div>
            <div className="field-row">
              <div className="field">
                <label htmlFor="display-name">Display name</label>
                <input
                  id="display-name"
                  onChange={(event) => updateGeneral("displayName", event.target.value)}
                  value={bundle.general.displayName}
                />
              </div>
              <div className="field">
                <label htmlFor="timezone">Time zone</label>
                <select
                  id="timezone"
                  onChange={(event) => updateGeneral("timezone", event.target.value)}
                  value={bundle.general.timezone}
                >
                  <option>Asia/Kolkata</option>
                  <option>Asia/Dubai</option>
                  <option>UTC</option>
                </select>
              </div>
            </div>
            <div className="field-row">
              <div className="field">
                <label htmlFor="primary-colour">Primary colour</label>
                <input
                  id="primary-colour"
                  onChange={(event) => updateGeneral("primaryColour", event.target.value)}
                  pattern="^#[0-9A-Fa-f]{6}$"
                  value={bundle.general.primaryColour}
                />
              </div>
              <div className="field">
                <label htmlFor="language">Language</label>
                <select
                  id="language"
                  onChange={(event) => updateGeneral("language", event.target.value)}
                  value={bundle.general.language}
                >
                  <option value="en">English</option>
                </select>
              </div>
            </div>
          </section>

          <section className="form-section">
            <h2>Assessment defaults</h2>
            <p>Applied to new assessments unless faculty explicitly overrides them.</p>
            <div className="field-row">
              <div className="field">
                <label htmlFor="default-duration">Duration (minutes)</label>
                <input
                  id="default-duration"
                  min={1}
                  onChange={(event) =>
                    updateGeneral("defaultDurationMinutes", Number(event.target.value))
                  }
                  type="number"
                  value={bundle.general.defaultDurationMinutes}
                />
              </div>
              <div className="field">
                <label htmlFor="default-attempts">Attempts allowed</label>
                <input
                  id="default-attempts"
                  min={1}
                  onChange={(event) =>
                    updateGeneral("defaultAttemptsAllowed", Number(event.target.value))
                  }
                  type="number"
                  value={bundle.general.defaultAttemptsAllowed}
                />
              </div>
            </div>
            <label className="switch-row">
              <span><strong>Shuffle questions</strong><small>Randomise question order by default.</small></span>
              <input
                checked={bundle.general.shuffleQuestions}
                onChange={(event) => updateGeneral("shuffleQuestions", event.target.checked)}
                type="checkbox"
              />
            </label>
            <label className="switch-row">
              <span><strong>Shuffle options</strong><small>Randomise answer options by default.</small></span>
              <input
                checked={bundle.general.shuffleOptions}
                onChange={(event) => updateGeneral("shuffleOptions", event.target.checked)}
                type="checkbox"
              />
            </label>
          </section>

          <section className="form-section">
            <h2>Intelligence & retention rules</h2>
            <p>Rule-based thresholds only; Release 1.0 contains no AI.</p>
            <div className="field-row">
              <div className="field">
                <label htmlFor="pass-mark">Pass percentage</label>
                <input
                  id="pass-mark"
                  max={100}
                  min={0}
                  onChange={(event) => updateGeneral("passPercentage", Number(event.target.value))}
                  type="number"
                  value={bundle.general.passPercentage}
                />
              </div>
              <div className="field">
                <label htmlFor="risk-mark">At-risk threshold</label>
                <input
                  id="risk-mark"
                  max={100}
                  min={0}
                  onChange={(event) => updateGeneral("atRiskThreshold", Number(event.target.value))}
                  type="number"
                  value={bundle.general.atRiskThreshold}
                />
              </div>
            </div>
            <div className="field">
              <label htmlFor="retention">Audit retention (days)</label>
              <input
                id="retention"
                min={365}
                onChange={(event) => updateGeneral("auditRetentionDays", Number(event.target.value))}
                type="number"
                value={bundle.general.auditRetentionDays}
              />
            </div>
            <label className="switch-row">
              <span><strong>Student ranking</strong><small>Show rank and topper score only after result publication.</small></span>
              <input
                checked={bundle.general.rankingEnabled}
                onChange={(event) => updateGeneral("rankingEnabled", event.target.checked)}
                type="checkbox"
              />
            </label>
          </section>

          <div className="settings-savebar">
            <button className="button button-primary" disabled={busy} onClick={saveGeneral}>
              {busy ? <RefreshCw className="spin" size={15} /> : <Save size={15} />}
              Save settings
            </button>
          </div>
        </div>
      )}

      {tab === "grading" && (
        <section className="panel grade-editor">
          <div className="panel-header">
            <div>
              <h2>Grade bands</h2>
              <p>Ranges must be contiguous and cover 0–100 without overlaps.</p>
            </div>
            <button
              className="button button-secondary"
              onClick={() =>
                setBundle((current) => ({
                  ...current,
                  gradeBands: [
                    ...current.gradeBands,
                    { code: "", label: "", minPercentage: 0, maxPercentage: 0 },
                  ],
                }))
              }
            >
              <Plus size={15} /> Add band
            </button>
          </div>
          <div className="grade-list">
            {bundle.gradeBands.map((band, index) => (
              <div className="grade-row" key={`${band.id ?? "new"}-${index}`}>
                <input
                  aria-label={`Grade ${index + 1} code`}
                  onChange={(event) => updateBand(index, { code: event.target.value })}
                  value={band.code}
                />
                <input
                  aria-label={`Grade ${index + 1} label`}
                  onChange={(event) => updateBand(index, { label: event.target.value })}
                  value={band.label}
                />
                <label>
                  Min
                  <input
                    max={100}
                    min={0}
                    onChange={(event) =>
                      updateBand(index, { minPercentage: Number(event.target.value) })
                    }
                    type="number"
                    value={band.minPercentage}
                  />
                </label>
                <label>
                  Max
                  <input
                    max={100}
                    min={0}
                    onChange={(event) =>
                      updateBand(index, { maxPercentage: Number(event.target.value) })
                    }
                    type="number"
                    value={band.maxPercentage}
                  />
                </label>
              </div>
            ))}
          </div>
          <button className="button button-primary" disabled={busy} onClick={saveGrades}>
            <Save size={15} /> Save grade bands
          </button>
        </section>
      )}

      {tab === "masters" && (
        <div className="settings-grid">
          <section className="panel">
            <div className="panel-header">
              <div><h2>Subjects</h2><p>Deactivation is blocked when questions exist.</p></div>
            </div>
            <div className="master-list">
              {bundle.subjects.map((subject) => (
                <div className="master-row" key={subject.id}>
                  <span className="badge badge-neutral">{subject.code}</span>
                  <strong>{subject.name}</strong>
                  <span className={`badge ${subject.active ? "badge-success" : "badge-neutral"}`}>
                    {subject.active ? "Active" : "Inactive"}
                  </span>
                </div>
              ))}
            </div>
          </section>
          <form className="form-section" onSubmit={addSubject}>
            <h2>Add a subject</h2>
            <p>Codes are unique within this organisation.</p>
            <div className="field">
              <label htmlFor="subject-code">Code</label>
              <input
                id="subject-code"
                maxLength={30}
                onChange={(event) => setSubjectCode(event.target.value)}
                placeholder="e.g. ENG"
                value={subjectCode}
              />
            </div>
            <div className="field">
              <label htmlFor="subject-name">Subject name</label>
              <input
                id="subject-name"
                maxLength={150}
                onChange={(event) => setSubjectName(event.target.value)}
                placeholder="e.g. English"
                value={subjectName}
              />
            </div>
            <button className="button button-primary" disabled={busy} type="submit">
              <Plus size={15} /> Add subject
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
