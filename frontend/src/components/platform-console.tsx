"use client";

import {
  Building2,
  CalendarClock,
  CreditCard,
  RefreshCw,
  ShieldCheck,
  Users,
} from "lucide-react";
import Image from "next/image";
import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { MetricCard } from "@/components/metric-card";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import { formatInrFromPaise } from "@/lib/commercial-rules";
import type { CommercialEntitlement, CommercialPlan, SubscriptionStatus } from "@/lib/live-types";

type CustomerAccountStatus = "ACTIVE" | "SUSPENDED" | "ARCHIVED";
type PaymentStatus = "PENDING" | "PAID" | "WAIVED";

interface PlatformOverview {
  dashboard: {
    totalCustomerAccounts: number;
    totalOrganisations: number;
    organisationsOnTrial: number;
    trialsExpiringSoon: number;
    activeSubscriptions: number;
    expiredSubscriptions: number;
    basicOrganisations: number;
    proOrganisations: number;
    legendOrganisations: number;
    studentCapacity: number;
    actualStudentUsage: number;
    serverNow: string;
  };
  settings: {
    defaultTrialDays: number;
    defaultTrialPlan: CommercialPlan;
    reminderDays: number[];
  };
  plans: Array<{
    code: CommercialPlan;
    label: string;
    description: string;
    prices: Array<{ studentLimit: number; monthlyPricePaise: number }>;
    entitlements: CommercialEntitlement[];
  }>;
  customerAccounts: Array<{
    id: string;
    code: string;
    name: string;
    status: CustomerAccountStatus;
    organisationCount: number;
    studentUsage: number;
    studentCapacity: number;
    trials: number;
    activeSubscriptions: number;
  }>;
  organisations: Array<{
    id: string;
    customerAccountId: string;
    code: string;
    name: string;
    timezone: string;
    status: string;
    logoAvailable: boolean;
    logoUpdatedAt?: string;
    effectivePlan?: CommercialPlan;
    selectedPlan?: CommercialPlan;
    subscriptionStatus?: SubscriptionStatus;
    studentCapacity: number;
    studentUsage: number;
    accessEndsAt?: string;
  }>;
}

function instant(value: string) {
  return new Date(value).toISOString();
}

function localDateTime(offsetDays = 0) {
  const date = new Date(Date.now() + offsetDays * 86_400_000);
  date.setSeconds(0, 0);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
    .toISOString().slice(0, 16);
}

export function PlatformConsole() {
  const [data, setData] = useState<PlatformOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [account, setAccount] = useState({ code: "", name: "" });
  const [onboardingLogo, setOnboardingLogo] = useState<File | null>(null);
  const [onboarding, setOnboarding] = useState({
    customerAccountId: "", code: "", name: "", timezone: "Asia/Kolkata",
    adminEmail: "", adminFirstName: "", adminLastName: "",
    selectedPlan: "BASIC" as CommercialPlan, studentCapacity: 50,
    trialEnabled: true, trialDurationDays: 20,
    trialPlan: "LEGEND" as CommercialPlan, activationDate: "", note: "",
  });
  const [subscription, setSubscription] = useState({
    organisationId: "", action: "ACTIVATE", plan: "BASIC" as CommercialPlan,
    studentCapacity: 50, startsAt: "", endsAt: "", extensionDays: 7,
    paymentStatus: "PENDING" as PaymentStatus, amountRupees: 0,
    paymentReference: "", remarks: "", reason: "",
  });
  const [settings, setSettings] = useState({
    defaultTrialDays: 20, defaultTrialPlan: "LEGEND" as CommercialPlan,
    reminderDays: "7,3,1",
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const next = await apiFetch<PlatformOverview>("/platform/overview");
      setData(next);
      setSettings({
        defaultTrialDays: next.settings.defaultTrialDays,
        defaultTrialPlan: next.settings.defaultTrialPlan,
        reminderDays: next.settings.reminderDays.join(","),
      });
      setOnboarding((current) => ({
        ...current,
        customerAccountId: current.customerAccountId || next.customerAccounts.find((item) => item.status === "ACTIVE")?.id || "",
        trialDurationDays: next.settings.defaultTrialDays,
        trialPlan: next.settings.defaultTrialPlan,
        activationDate: current.activationDate || localDateTime(),
      }));
      setSubscription((current) => ({
        ...current,
        organisationId: current.organisationId || next.organisations[0]?.id || "",
        startsAt: current.startsAt || localDateTime(),
        endsAt: current.endsAt || localDateTime(30),
      }));
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Rabbit Platform controls could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  async function mutate(action: () => Promise<unknown>, success: string) {
    setBusy(true); setError(""); setNotice("");
    try {
      await action(); setNotice(success); await load(); return true;
    } catch (requestError) {
      setError(apiErrorMessage(requestError)); return false;
    } finally { setBusy(false); }
  }

  const selectedPlan = useMemo(
    () => data?.plans.find((item) => item.code === onboarding.selectedPlan),
    [data?.plans, onboarding.selectedPlan],
  );
  const selectedActionPlan = useMemo(
    () => data?.plans.find((item) => item.code === subscription.plan),
    [data?.plans, subscription.plan],
  );

  async function createAccount(event: FormEvent) {
    event.preventDefault();
    const ok = await mutate(() => apiFetch("/platform/customer-accounts", {
      method: "POST", body: JSON.stringify(account),
    }), "Customer Account created and audited.");
    if (ok) setAccount({ code: "", name: "" });
  }

  async function changeAccountStatus(id: string, status: CustomerAccountStatus) {
    if (!window.confirm(`Change this Customer Account to ${status}?`)) return;
    await mutate(() => apiFetch(`/platform/customer-accounts/${id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status, reason: `Manual ${status.toLowerCase()} by Platform Super Admin` }),
    }), `Customer Account changed to ${status}.`);
  }

  async function editAccount(item: PlatformOverview["customerAccounts"][number]) {
    const name = window.prompt("Customer Account name", item.name)?.trim();
    if (!name) return;
    const code = window.prompt("Customer Account code", item.code)?.trim();
    if (!code) return;
    await mutate(() => apiFetch(`/platform/customer-accounts/${item.id}`, {
      method: "PUT", body: JSON.stringify({ name, code }),
    }), "Customer Account updated and audited.");
  }

  async function assignOrganisation(organisationId: string, customerAccountId: string) {
    const reason = window.prompt("Reason for moving this Organisation to another Customer Account")?.trim();
    if (!reason) return;
    await mutate(() => apiFetch(`/platform/organisations/${organisationId}/customer-account`, {
      method: "PATCH", body: JSON.stringify({ customerAccountId, reason }),
    }), "Organisation reassigned through the controlled audited flow.");
  }

  async function editOrganisation(item: PlatformOverview["organisations"][number]) {
    const name = window.prompt("Organisation name", item.name)?.trim();
    if (!name) return;
    const timezone = window.prompt("IANA time zone", item.timezone)?.trim();
    if (!timezone) return;
    await mutate(() => apiFetch(`/platform/organisations/${item.id}`, {
      method: "PUT", body: JSON.stringify({ name, timezone }),
    }), "Organisation details updated and audited.");
  }

  async function onboard(event: FormEvent) {
    event.preventDefault();
    await mutate(async () => {
      const created = await apiFetch<{ organisation: { id: string } }>("/platform/organisations", {
        method: "POST",
        body: JSON.stringify({
          ...onboarding,
          trialDurationDays: onboarding.trialEnabled ? onboarding.trialDurationDays : null,
          trialPlan: onboarding.trialEnabled ? onboarding.trialPlan : null,
          activationDate: instant(onboarding.activationDate),
        }),
      });
      if (onboardingLogo) {
        const form = new FormData(); form.append("file", onboardingLogo);
        const response = await fetch(
          `/gateway/backend/organisation-branding/organisations/${created.organisation.id}/logo`,
          { method: "PUT", body: form },
        );
        if (!response.ok) throw new Error("Organisation was created, but its logo upload failed. Upload it from the Organisation table.");
      }
    }, "Organisation, logo, administrator invitation and commercial setup created.");
  }

  async function runSubscriptionAction(event: FormEvent) {
    event.preventDefault();
    await mutate(() => apiFetch(
      `/platform/organisations/${subscription.organisationId}/subscription-actions`,
      {
        method: "POST",
        body: JSON.stringify({
          ...subscription,
          organisationId: undefined,
          startsAt: subscription.startsAt ? instant(subscription.startsAt) : null,
          endsAt: subscription.endsAt ? instant(subscription.endsAt) : null,
          amountPaise: Math.round(subscription.amountRupees * 100),
        }),
      },
    ), "Subscription action completed and added to immutable history.");
  }

  async function updateSettings(event: FormEvent) {
    event.preventDefault();
    const reminderDays = settings.reminderDays.split(",")
      .map((value) => Number(value.trim())).filter((value) => Number.isInteger(value) && value > 0);
    await mutate(() => apiFetch("/platform/settings", {
      method: "PUT", body: JSON.stringify({ ...settings, reminderDays }),
    }), "Platform trial defaults and reminder schedule updated.");
  }

  async function uploadLogo(organisationId: string, file?: File) {
    if (!file) return;
    const form = new FormData(); form.append("file", file);
    await mutate(async () => {
      const response = await fetch(
        `/gateway/backend/organisation-branding/organisations/${organisationId}/logo`,
        { method: "PUT", body: form },
      );
      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.message ?? "Logo upload failed.");
      }
    }, "Organisation logo stored in local MinIO.");
  }

  async function removeLogo(organisationId: string) {
    if (!window.confirm("Remove this Organisation logo? Rabbit and initials branding will remain.")) return;
    await mutate(() => apiFetch(
      `/organisation-branding/organisations/${organisationId}/logo`,
      { method: "DELETE" },
    ), "Organisation logo removed; initials fallback is active.");
  }

  if (loading) return <div className="page"><LoadingState label="Loading Rabbit Platform…" /></div>;
  if (!data) return <div className="page"><ErrorState message={error} retry={() => void load()} /></div>;

  const capacities = selectedPlan?.prices ?? [];
  const actionCapacities = selectedActionPlan?.prices ?? [];
  return (
    <div className="page platform-console">
      <PageHeader eyebrow="Rabbit Platform · Super Admin only" title="Customer & Commercial Control"
        description="Govern Customer Accounts, Organisations, branding, trials, plan entitlements, capacity and manual payments without a payment gateway."
        actions={<button className="button button-secondary" onClick={() => void load()}><RefreshCw size={15} /> Refresh</button>} />
      {error && <div className="form-error" role="alert">{error}</div>}
      {notice && <div className="success-banner" role="status">{notice}</div>}

      <section className="metrics-grid">
        <MetricCard icon={Users} value={String(data.dashboard.totalCustomerAccounts)} label="Customer Accounts" />
        <MetricCard icon={Building2} value={String(data.dashboard.totalOrganisations)} label="Organisations" />
        <MetricCard icon={CalendarClock} value={`${data.dashboard.organisationsOnTrial} / ${data.dashboard.trialsExpiringSoon}`} label="Trials / expiring" />
        <MetricCard icon={CreditCard} value={`${data.dashboard.activeSubscriptions} / ${data.dashboard.expiredSubscriptions}`} label="Active / expired" />
        <MetricCard icon={ShieldCheck} value={`${data.dashboard.actualStudentUsage}/${data.dashboard.studentCapacity}`} label="Student usage" />
      </section>

      <section className="content-grid platform-grid">
        <form className="panel form-section" onSubmit={createAccount}>
          <div className="panel-header"><div><h2>Create Customer Account</h2><p>Commercial owner above one or more Organisations.</p></div></div>
          <div className="field"><label htmlFor="account-code">Account code</label><input id="account-code" required value={account.code} onChange={(event) => setAccount({ ...account, code: event.target.value.toUpperCase() })} /></div>
          <div className="field"><label htmlFor="account-name">Account name</label><input id="account-name" required value={account.name} onChange={(event) => setAccount({ ...account, name: event.target.value })} /></div>
          <button className="button button-primary" disabled={busy} type="submit">Create Account</button>
        </form>
        <form className="panel form-section" onSubmit={updateSettings}>
          <div className="panel-header"><div><h2>Trial defaults</h2><p>Applied to new Organisations unless overridden.</p></div></div>
          <div className="field-row"><div className="field"><label htmlFor="trial-days">Default days</label><input id="trial-days" min={1} max={365} type="number" value={settings.defaultTrialDays} onChange={(event) => setSettings({ ...settings, defaultTrialDays: Number(event.target.value) })} /></div><div className="field"><label htmlFor="trial-plan">Default plan</label><select id="trial-plan" value={settings.defaultTrialPlan} onChange={(event) => setSettings({ ...settings, defaultTrialPlan: event.target.value as CommercialPlan })}>{data.plans.map((plan) => <option key={plan.code} value={plan.code}>{plan.label}</option>)}</select></div></div>
          <div className="field"><label htmlFor="reminder-days">Reminder days (comma separated)</label><input id="reminder-days" value={settings.reminderDays} onChange={(event) => setSettings({ ...settings, reminderDays: event.target.value })} /></div>
          <button className="button button-primary" disabled={busy} type="submit">Save defaults</button>
        </form>
      </section>

      <section className="panel">
        <div className="panel-header"><div><h2>Customer Accounts</h2><p>Suspended accounts lose feature access; archival requires all Organisations to be archived.</p></div></div>
        <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Account</th><th>Organisations</th><th>Usage</th><th>Subscriptions</th><th>Status</th><th>Actions</th></tr></thead><tbody>
          {data.customerAccounts.map((item) => <tr key={item.id}><td><strong>{item.name}</strong><br /><small>{item.code}</small></td><td>{item.organisationCount}</td><td>{item.studentUsage}/{item.studentCapacity}</td><td>{item.trials} trial · {item.activeSubscriptions} active</td><td><span className="badge badge-neutral">{item.status}</span></td><td><div className="table-actions"><button className="button button-ghost button-small" disabled={item.status === "ARCHIVED"} onClick={() => void editAccount(item)} type="button">Edit</button>{item.status === "ACTIVE" ? <button className="button button-secondary button-small" onClick={() => void changeAccountStatus(item.id, "SUSPENDED")} type="button">Suspend</button> : item.status === "SUSPENDED" ? <button className="button button-secondary button-small" onClick={() => void changeAccountStatus(item.id, "ACTIVE")} type="button">Activate</button> : null}<button className="button button-ghost button-small" disabled={item.status === "ARCHIVED"} onClick={() => void changeAccountStatus(item.id, "ARCHIVED")} type="button">Archive</button></div></td></tr>)}
        </tbody></table></div>
      </section>

      <section className="commercial-section">
        <div className="section-heading"><Building2 size={20} /><div><h2>Create Organisation</h2><p>Customer ownership, Organisation, administrator, plan, capacity and trial are committed atomically.</p></div></div>
        <form className="panel form-section" onSubmit={onboard}>
          <div className="field-row"><div className="field"><label htmlFor="onboard-account">Customer Account</label><select id="onboard-account" required value={onboarding.customerAccountId} onChange={(event) => setOnboarding({ ...onboarding, customerAccountId: event.target.value })}><option value="">Select Account</option>{data.customerAccounts.filter((item) => item.status === "ACTIVE").map((item) => <option key={item.id} value={item.id}>{item.name} · {item.code}</option>)}</select></div><div className="field"><label htmlFor="onboard-code">Organisation code</label><input id="onboard-code" required value={onboarding.code} onChange={(event) => setOnboarding({ ...onboarding, code: event.target.value.toUpperCase() })} /></div></div>
          <div className="field-row"><div className="field"><label htmlFor="onboard-name">Organisation name</label><input id="onboard-name" required value={onboarding.name} onChange={(event) => setOnboarding({ ...onboarding, name: event.target.value })} /></div><div className="field"><label htmlFor="onboard-zone">Time zone</label><input id="onboard-zone" required value={onboarding.timezone} onChange={(event) => setOnboarding({ ...onboarding, timezone: event.target.value })} /></div></div>
          <div className="field-row"><div className="field"><label htmlFor="admin-first">Admin first name</label><input id="admin-first" required value={onboarding.adminFirstName} onChange={(event) => setOnboarding({ ...onboarding, adminFirstName: event.target.value })} /></div><div className="field"><label htmlFor="admin-last">Admin last name</label><input id="admin-last" required value={onboarding.adminLastName} onChange={(event) => setOnboarding({ ...onboarding, adminLastName: event.target.value })} /></div><div className="field"><label htmlFor="admin-email">Admin email</label><input id="admin-email" required type="email" value={onboarding.adminEmail} onChange={(event) => setOnboarding({ ...onboarding, adminEmail: event.target.value })} /></div></div>
          <div className="field-row"><div className="field"><label htmlFor="selected-plan">Paid plan</label><select id="selected-plan" value={onboarding.selectedPlan} onChange={(event) => { const plan = event.target.value as CommercialPlan; const first = data.plans.find((item) => item.code === plan)?.prices[0]?.studentLimit ?? 50; setOnboarding({ ...onboarding, selectedPlan: plan, studentCapacity: first }); }}>{data.plans.map((plan) => <option key={plan.code} value={plan.code}>{plan.label}</option>)}</select></div><div className="field"><label htmlFor="selected-capacity">Student capacity</label><select id="selected-capacity" value={onboarding.studentCapacity} onChange={(event) => setOnboarding({ ...onboarding, studentCapacity: Number(event.target.value) })}>{capacities.map((price) => <option key={price.studentLimit} value={price.studentLimit}>{price.studentLimit} · {formatInrFromPaise(price.monthlyPricePaise)}</option>)}</select></div><div className="field"><label htmlFor="activation-date">Activation date</label><input id="activation-date" type="datetime-local" required value={onboarding.activationDate} onChange={(event) => setOnboarding({ ...onboarding, activationDate: event.target.value })} /></div></div>
          <label className="check-row"><input checked={onboarding.trialEnabled} onChange={(event) => setOnboarding({ ...onboarding, trialEnabled: event.target.checked })} type="checkbox" />Enable free trial</label>
          {onboarding.trialEnabled && <div className="field-row"><div className="field"><label htmlFor="custom-trial-days">Trial days</label><input id="custom-trial-days" min={1} max={365} type="number" value={onboarding.trialDurationDays} onChange={(event) => setOnboarding({ ...onboarding, trialDurationDays: Number(event.target.value) })} /></div><div className="field"><label htmlFor="custom-trial-plan">Trial plan</label><select id="custom-trial-plan" value={onboarding.trialPlan} onChange={(event) => setOnboarding({ ...onboarding, trialPlan: event.target.value as CommercialPlan })}>{data.plans.map((plan) => <option key={plan.code} value={plan.code}>{plan.label}</option>)}</select></div></div>}
          <div className="field"><label htmlFor="onboard-logo">Organisation logo (PNG/JPG/WebP, max 2 MB)</label><input accept="image/png,image/jpeg,image/webp" id="onboard-logo" onChange={(event) => setOnboardingLogo(event.target.files?.[0] ?? null)} type="file" /></div>
          <div className="field"><label htmlFor="onboard-note">Reason / note</label><textarea id="onboard-note" value={onboarding.note} onChange={(event) => setOnboarding({ ...onboarding, note: event.target.value })} /></div>
          <button className="button button-primary" disabled={busy} type="submit">Create Organisation</button>
        </form>
      </section>

      <section className="panel">
        <div className="panel-header"><div><h2>Organisations & branding</h2><p>Rabbit remains the platform brand; each Organisation can add its own local MinIO logo.</p></div></div>
          <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Organisation</th><th>Customer Account</th><th>Plan</th><th>Trial/subscription</th><th>Capacity</th><th>Branding</th></tr></thead><tbody>{data.organisations.map((item) => <tr key={item.id}><td><strong>{item.name}</strong><br /><small>{item.code}</small><br /><button className="button button-ghost button-small" onClick={() => void editOrganisation(item)} type="button">Edit</button></td><td><select aria-label={`Customer Account for ${item.name}`} disabled={busy} onChange={(event) => void assignOrganisation(item.id, event.target.value)} value={item.customerAccountId}>{data.customerAccounts.filter((accountItem) => accountItem.status === "ACTIVE").map((accountItem) => <option key={accountItem.id} value={accountItem.id}>{accountItem.name}</option>)}</select></td><td>{item.effectivePlan ?? "—"}<br /><small>Selected: {item.selectedPlan ?? "—"}</small></td><td>{item.subscriptionStatus?.replaceAll("_", " ") ?? "Not configured"}<br /><small>{item.accessEndsAt ? new Date(item.accessEndsAt).toLocaleDateString("en-IN") : "—"}</small></td><td>{item.studentUsage}/{item.studentCapacity}</td><td><div className="table-actions">{item.logoAvailable ? <Image alt={`${item.name} logo`} className="organisation-logo organisation-logo-small" height={40} src={`/gateway/backend/organisation-branding/organisations/${item.id}/logo?v=${item.logoUpdatedAt ?? "1"}`} unoptimized width={40} /> : <span className="org-avatar organisation-logo-small">{item.code.slice(0, 2)}</span>}<label className="button button-secondary button-small">{item.logoAvailable ? "Change" : "Upload"}<input accept="image/png,image/jpeg,image/webp" className="visually-hidden" onChange={(event) => void uploadLogo(item.id, event.target.files?.[0])} type="file" /></label>{item.logoAvailable && <button className="button button-ghost button-small" onClick={() => void removeLogo(item.id)} type="button">Remove</button>}</div></td></tr>)}</tbody></table></div>
      </section>

      <section className="commercial-section">
        <div className="section-heading"><CreditCard size={20} /><div><h2>Manual subscription control</h2><p>Upgrade, downgrade, renew, extend trial, suspend, reactivate, grant grace, cancel or update manual payment status.</p></div></div>
        <form className="panel form-section" onSubmit={runSubscriptionAction}>
          <div className="field-row"><div className="field"><label htmlFor="action-org">Organisation</label><select id="action-org" required value={subscription.organisationId} onChange={(event) => setSubscription({ ...subscription, organisationId: event.target.value })}>{data.organisations.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.code}</option>)}</select></div><div className="field"><label htmlFor="subscription-action">Action</label><select id="subscription-action" value={subscription.action} onChange={(event) => setSubscription({ ...subscription, action: event.target.value })}>{["ACTIVATE", "UPGRADE", "DOWNGRADE", "RENEW", "EXTEND_TRIAL", "SUSPEND", "REACTIVATE", "GRACE_PERIOD", "CANCEL", "PAYMENT_STATUS"].map((value) => <option key={value}>{value}</option>)}</select></div></div>
          {["ACTIVATE", "UPGRADE", "DOWNGRADE", "RENEW"].includes(subscription.action) && <><div className="field-row"><div className="field"><label htmlFor="action-plan">Plan</label><select id="action-plan" value={subscription.plan} onChange={(event) => setSubscription({ ...subscription, plan: event.target.value as CommercialPlan })}>{data.plans.map((plan) => <option key={plan.code} value={plan.code}>{plan.label}</option>)}</select></div><div className="field"><label htmlFor="action-capacity">Capacity</label><select id="action-capacity" value={subscription.studentCapacity} onChange={(event) => setSubscription({ ...subscription, studentCapacity: Number(event.target.value) })}>{actionCapacities.map((price) => <option key={price.studentLimit} value={price.studentLimit}>{price.studentLimit}</option>)}</select></div><div className="field"><label htmlFor="action-payment">Payment status</label><select id="action-payment" value={subscription.paymentStatus} onChange={(event) => setSubscription({ ...subscription, paymentStatus: event.target.value as PaymentStatus })}>{["PENDING", "PAID", "WAIVED"].map((value) => <option key={value}>{value}</option>)}</select></div></div><div className="field-row"><div className="field"><label htmlFor="action-start">Start</label><input id="action-start" required type="datetime-local" value={subscription.startsAt} onChange={(event) => setSubscription({ ...subscription, startsAt: event.target.value })} /></div><div className="field"><label htmlFor="action-end">End</label><input id="action-end" required type="datetime-local" value={subscription.endsAt} onChange={(event) => setSubscription({ ...subscription, endsAt: event.target.value })} /></div><div className="field"><label htmlFor="action-amount">Amount (₹)</label><input id="action-amount" min={0} type="number" value={subscription.amountRupees} onChange={(event) => setSubscription({ ...subscription, amountRupees: Number(event.target.value) })} /></div></div></>}
          {subscription.action === "EXTEND_TRIAL" && <div className="field"><label htmlFor="extension-days">Additional days</label><input id="extension-days" min={1} max={365} type="number" value={subscription.extensionDays} onChange={(event) => setSubscription({ ...subscription, extensionDays: Number(event.target.value) })} /></div>}
          <div className="field-row"><div className="field"><label htmlFor="payment-reference">Payment reference</label><input id="payment-reference" value={subscription.paymentReference} onChange={(event) => setSubscription({ ...subscription, paymentReference: event.target.value })} /></div><div className="field"><label htmlFor="payment-remarks">Remarks</label><input id="payment-remarks" value={subscription.remarks} onChange={(event) => setSubscription({ ...subscription, remarks: event.target.value })} /></div></div>
          <div className="field"><label htmlFor="action-reason">Reason (mandatory)</label><textarea id="action-reason" required value={subscription.reason} onChange={(event) => setSubscription({ ...subscription, reason: event.target.value })} /></div>
          <button className="button button-primary" disabled={busy} type="submit">Apply authorised action</button>
        </form>
      </section>

      <section className="plan-grid" aria-label="Final entitlement matrix">{data.plans.map((plan) => <article className="panel plan-card" key={plan.code}><span className="badge badge-neutral">{plan.label}</span><h3>{plan.description}</h3><ul>{plan.entitlements.map((item) => <li key={item}>{item.replaceAll("_", " ")}</li>)}</ul></article>)}</section>
    </div>
  );
}
