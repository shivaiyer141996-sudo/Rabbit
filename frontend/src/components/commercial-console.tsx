"use client";

import {
  Building2,
  CalendarDays,
  CreditCard,
  FileText,
  LifeBuoy,
  RefreshCw,
  Users,
} from "lucide-react";
import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { ErrorState, LoadingState } from "@/components/data-state";
import { MetricCard } from "@/components/metric-card";
import { PageHeader } from "@/components/page-header";
import { apiErrorMessage, apiFetch } from "@/lib/api";
import {
  accessLabel,
  formatInrFromPaise,
  planPrice,
  wholeDaysRemaining,
} from "@/lib/commercial-rules";
import type {
  CommercialOverview,
  CommercialPlan,
} from "@/lib/live-types";
import type { UserRole } from "@/lib/types";

const limits = [50, 150, 500] as const;
const plans: CommercialPlan[] = ["BASIC", "PRO", "LEGEND"];

function localDateTime(offsetDays = 0) {
  const date = new Date(Date.now() + offsetDays * 86_400_000);
  date.setSeconds(0, 0);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 16);
}

function localDateKey() {
  const local = localDateTime().slice(0, 10);
  return local.replaceAll("-", "");
}

function asInstant(value: string) {
  return new Date(value).toISOString();
}

function readable(value?: string) {
  return value
    ? new Intl.DateTimeFormat("en-IN", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
}

function eventNote(snapshot: string) {
  const marker = ";note=";
  const start = snapshot.indexOf(marker);
  if (start < 0) return "";
  const note = snapshot.slice(start + marker.length);
  return note === "null" ? "" : note;
}

export function CommercialConsole({ role }: { role: UserRole }) {
  const [data, setData] = useState<CommercialOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [declaredStudents, setDeclaredStudents] = useState(50);
  const [activationUrl, setActivationUrl] = useState("");
  const [invoice, setInvoice] = useState({
    invoiceNumber: "RAB-DRAFT-001",
    plan: "BASIC" as CommercialPlan,
    studentLimit: 50,
    periodStartsAt: "",
    periodEndsAt: "",
    taxRupees: 0,
    issuedAt: "",
    dueAt: "",
    note: "",
  });
  const [payment, setPayment] = useState({
    invoiceId: "",
    paymentReference: "",
    paymentMethod: "BANK_TRANSFER",
    paidAt: "",
    note: "",
  });
  const [invoiceVoid, setInvoiceVoid] = useState({
    invoiceId: "",
    reason: "",
  });
  const [subscriptionReason, setSubscriptionReason] = useState("");
  const [support, setSupport] = useState({
    severity: "S3",
    category: "OTHER",
    summary: "",
    description: "",
  });
  const [supportUpdate, setSupportUpdate] = useState({
    caseId: "",
    status: "IN_PROGRESS",
    assignedTo: "",
    resolution: "",
  });
  const [onboarding, setOnboarding] = useState({
    code: "",
    name: "",
    timezone: "Asia/Kolkata",
    adminEmail: "",
    adminFirstName: "",
    adminLastName: "",
    declaredStudents: 50,
    note: "",
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setData(await apiFetch<CommercialOverview>("/commercial/overview"));
    } catch (requestError) {
      setError(apiErrorMessage(requestError, "Plan and support data could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const initialization = window.setTimeout(() => {
      setInvoice((current) => ({
        ...current,
        invoiceNumber: `RAB-${localDateKey()}-001`,
        periodStartsAt: localDateTime(),
        periodEndsAt: localDateTime(30),
        issuedAt: localDateTime(),
        dueAt: localDateTime(7),
      }));
      setPayment((current) => ({ ...current, paidAt: localDateTime() }));
    }, 0);
    return () => window.clearTimeout(initialization);
  }, []);

  useEffect(() => {
    const initial = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(initial);
  }, [load]);

  const selectedInvoice = useMemo(
    () => data?.invoices.find((item) => item.id === payment.invoiceId),
    [data?.invoices, payment.invoiceId],
  );
  const selectedMonthlyPrice = data
    ? planPrice(data.catalog, invoice.plan, invoice.studentLimit)
    : undefined;
  const trialOrPeriodEnd = data?.subscription?.periodEndsAt
    ?? data?.subscription?.trialEndsAt;

  async function mutate(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(success);
      await load();
      return true;
    } catch (requestError) {
      setError(apiErrorMessage(requestError));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function startTrial() {
    await mutate(
      () => apiFetch("/commercial/trial", {
        method: "POST",
        body: JSON.stringify({ declaredStudents, note: "Configured Rabbit free trial" }),
      }),
      `The ${data?.trialDays ?? 20}-day configured Rabbit trial has started.`,
    );
  }

  async function createInvoice(event: FormEvent) {
    event.preventDefault();
    await mutate(
      () => apiFetch("/commercial/invoices", {
        method: "POST",
        body: JSON.stringify({
          ...invoice,
          taxPaise: Math.round(invoice.taxRupees * 100),
          periodStartsAt: asInstant(invoice.periodStartsAt),
          periodEndsAt: asInstant(invoice.periodEndsAt),
          issuedAt: asInstant(invoice.issuedAt),
          dueAt: asInstant(invoice.dueAt),
        }),
      }),
      "Invoice recorded. Record the matching offline payment only after it is verified.",
    );
  }

  async function recordPayment(event: FormEvent) {
    event.preventDefault();
    if (!selectedInvoice) return;
    await mutate(
      () => apiFetch("/commercial/payments", {
        method: "POST",
        body: JSON.stringify({
          ...payment,
          amountPaise: selectedInvoice.totalPaise,
          paidAt: asInstant(payment.paidAt),
        }),
      }),
      "Payment and receipt recorded; subscription dates were updated transactionally.",
    );
  }

  async function voidInvoice(event: FormEvent) {
    event.preventDefault();
    if (!invoiceVoid.invoiceId) return;
    const succeeded = await mutate(
      () => apiFetch(`/commercial/invoices/${invoiceVoid.invoiceId}/void`, {
        method: "POST",
        body: JSON.stringify({ reason: invoiceVoid.reason }),
      }),
      "The unpaid invoice was voided and retained in the local ledger.",
    );
    if (succeeded) setInvoiceVoid({ invoiceId: "", reason: "" });
  }

  async function changeSubscriptionState(action: "suspend" | "restore") {
    const succeeded = await mutate(
      () => apiFetch(`/commercial/subscription/${action}`, {
        method: "POST",
        body: JSON.stringify({ reason: subscriptionReason }),
      }),
      action === "suspend"
        ? "The subscription was suspended without changing its end date."
        : "The subscription was restored within its original access window.",
    );
    if (succeeded) setSubscriptionReason("");
  }

  async function createSupportCase(event: FormEvent) {
    event.preventDefault();
    const succeeded = await mutate(
      () => apiFetch("/commercial/support-cases", {
        method: "POST",
        body: JSON.stringify(support),
      }),
      "Support case created locally.",
    );
    if (succeeded) {
      setSupport((current) => ({ ...current, summary: "", description: "" }));
    }
  }

  async function updateSupportCase(event: FormEvent) {
    event.preventDefault();
    if (!supportUpdate.caseId) return;
    await mutate(
      () => apiFetch(`/commercial/support-cases/${supportUpdate.caseId}`, {
        method: "PATCH",
        body: JSON.stringify({
          status: supportUpdate.status,
          assignedTo: supportUpdate.assignedTo || null,
          resolution: supportUpdate.resolution || null,
        }),
      }),
      "Support case updated and audited.",
    );
  }

  async function onboardOrganisation(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    setActivationUrl("");
    try {
      const result = await apiFetch<{ activationUrl: string }>("/commercial/onboarding", {
        method: "POST",
        body: JSON.stringify(onboarding),
      });
      setActivationUrl(result.activationUrl);
      setNotice("Organisation, administrator invitation, defaults, and Legend trial created.");
      await load();
    } catch (requestError) {
      setError(apiErrorMessage(requestError));
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <div className="page"><LoadingState label="Loading commercial controls…" /></div>;
  if (!data) return <div className="page"><ErrorState message={error} retry={() => void load()} /></div>;

  const mutationsDisabled = busy || !data.enforcementEnabled;

  return (
    <div className="page commercial-console">
      <PageHeader
        eyebrow="Milestone 6 · Local commercial operations"
        title="Plan, billing & support"
        description="Basic, Pro, and Legend controls run entirely in local PostgreSQL. Payments are verified and recorded manually; no payment gateway is connected."
        actions={
          <button className="button button-secondary" onClick={() => void load()}>
            <RefreshCw size={15} /> Refresh
          </button>
        }
      />

      {!data.enforcementEnabled && (
        <section className="commercial-lock" role="status">
          <div><strong>Commercial activation is locked.</strong>
            <p>M5.6 still needs the real local Go evidence, approved commit on main, and annotated v1.0.0 tag. The catalogue is visible, but trial, onboarding, invoice, payment, and support mutations remain disabled.</p>
          </div>
        </section>
      )}
      {error && <div className="form-error" role="alert">{error}</div>}
      {notice && <div className="success-banner" role="status">{notice}</div>}

      <section className="metrics-grid">
        <MetricCard
          icon={CreditCard}
          value={data.subscription?.plan ?? "No plan"}
          label={accessLabel(data.subscription?.status)}
        />
        <MetricCard
          icon={CalendarDays}
          value={String(wholeDaysRemaining(trialOrPeriodEnd, data.serverNow))}
          label="Access days remaining"
        />
        <MetricCard
          icon={Users}
          value={`${data.activeAndInvitedStudents}/${data.subscription?.studentLimit ?? "—"}`}
          label="Active and invited students"
        />
        <MetricCard
          icon={LifeBuoy}
          value={String(data.supportCases.filter((item) => !["RESOLVED", "CLOSED"].includes(item.status)).length)}
          label="Open support cases"
        />
      </section>

      {!data.subscription && (
        <section className="panel trial-panel">
          <div>
            <h2>Start the configured one-time Rabbit trial</h2>
            <p>Exactly 20 days. Capacity is assigned to the smallest approved band that fits the declared student count.</p>
          </div>
          <div className="trial-action">
            <label htmlFor="trial-students">Expected students</label>
            <input
              id="trial-students"
              max={500}
              min={1}
              onChange={(event) => setDeclaredStudents(Number(event.target.value))}
              type="number"
              value={declaredStudents}
            />
            <button className="button button-primary" disabled={mutationsDisabled} onClick={startTrial}>
              Start {data.trialDays}-day trial
            </button>
          </div>
        </section>
      )}

      <section className="plan-grid" aria-label="Rabbit plan catalogue">
        {data.catalog.map((plan) => (
          <article className={`plan-card ${data.subscription?.plan === plan.code ? "selected" : ""}`} key={plan.code}>
            <div className="plan-card-heading">
              <div><span className="eyebrow">{plan.code}</span><h2>{plan.label}</h2></div>
              {data.subscription?.plan === plan.code && <span className="badge badge-success">Current</span>}
            </div>
            <p>{plan.description}</p>
            <div className="price-list">
              {plan.prices.map((price) => (
                <div key={price.studentLimit}>
                  <strong>{formatInrFromPaise(price.monthlyPricePaise)}</strong>
                  <span>per month · up to {price.studentLimit} students</span>
                </div>
              ))}
            </div>
            <ul>
              {plan.entitlements.map((entitlement) => (
                <li key={entitlement}>{entitlement.replaceAll("_", " ").toLowerCase()}</li>
              ))}
            </ul>
          </article>
        ))}
      </section>

      <div className="commercial-columns">
        <section className="panel">
          <div className="panel-header"><div><h2>Subscription history</h2><p>Immutable events from trial through renewal or downgrade.</p></div></div>
          <div className="timeline-list">
            {data.subscriptionEvents.length === 0 && <p className="empty-copy">No subscription event yet.</p>}
            {data.subscriptionEvents.map((event) => (
              <div className="timeline-row" key={event.id}>
                <span className="timeline-dot" />
                <div><strong>{event.eventType.replaceAll("_", " ")}</strong><span>{readable(event.occurredAt)}</span>{eventNote(event.afterValue) && <small>{eventNote(event.afterValue)}</small>}</div>
              </div>
            ))}
          </div>
        </section>
        <section className="panel">
          <div className="panel-header"><div><h2>Current access</h2><p>Server-derived, not browser-controlled.</p></div></div>
          <dl className="definition-list">
            <div className="definition-row"><dt>Status</dt><dd>{data.subscription?.status ?? "NOT STARTED"}</dd></div>
            <div className="definition-row"><dt>Monthly price</dt><dd>{data.subscription ? formatInrFromPaise(data.subscription.monthlyPricePaise) : "—"}</dd></div>
            <div className="definition-row"><dt>Access ends</dt><dd>{readable(trialOrPeriodEnd)}</dd></div>
            <div className="definition-row"><dt>Available student slots</dt><dd>{data.subscription ? data.availableStudentSlots : "—"}</dd></div>
            <div className="definition-row"><dt>Pending plan</dt><dd>{data.subscription?.pendingPlan ?? "None"}</dd></div>
          </dl>
          {role === "SUPER_ADMIN" && data.subscription
            && ["TRIAL", "ACTIVE", "SUSPENDED", "GRACE_PERIOD"].includes(data.subscription.status) && (
            <div className="subscription-state-controls">
              <div className="field">
                <label htmlFor="subscription-state-reason">Suspension/restoration reason</label>
                <input
                  id="subscription-state-reason"
                  required
                  value={subscriptionReason}
                  onChange={(event) => setSubscriptionReason(event.target.value)}
                />
              </div>
              <button
                className={`button ${data.subscription.status === "SUSPENDED" ? "button-primary" : "button-secondary"}`}
                disabled={mutationsDisabled || !subscriptionReason.trim()}
                onClick={() => void changeSubscriptionState(
                  data.subscription?.status === "SUSPENDED" ? "restore" : "suspend",
                )}
                type="button"
              >
                {data.subscription.status === "SUSPENDED"
                  ? "Restore subscription"
                  : "Suspend subscription"}
              </button>
            </div>
          )}
        </section>
      </div>

      {role === "SUPER_ADMIN" && (
        <section className="commercial-section">
          <div className="section-heading"><FileText size={20} /><div><h2>Manual billing</h2><p>Rabbit records evidence only after an offline payment is independently verified.</p></div></div>
          <div className="commercial-columns">
            <form className="form-section" onSubmit={createInvoice}>
              <h3>Issue monthly invoice</h3>
              <div className="field-row">
                <div className="field"><label htmlFor="invoice-number">Invoice number</label><input id="invoice-number" value={invoice.invoiceNumber} onChange={(event) => setInvoice({ ...invoice, invoiceNumber: event.target.value })} /></div>
                <div className="field"><label htmlFor="invoice-plan">Plan</label><select id="invoice-plan" value={invoice.plan} onChange={(event) => setInvoice({ ...invoice, plan: event.target.value as CommercialPlan })}>{plans.map((plan) => <option key={plan}>{plan}</option>)}</select></div>
              </div>
              <div className="field-row">
                <div className="field"><label htmlFor="invoice-limit">Student limit</label><select id="invoice-limit" value={invoice.studentLimit} onChange={(event) => setInvoice({ ...invoice, studentLimit: Number(event.target.value) })}>{limits.map((limit) => <option key={limit} value={limit}>{limit}</option>)}</select></div>
                <div className="field"><label htmlFor="invoice-tax">Manual tax (₹)</label><input id="invoice-tax" min={0} step="0.01" type="number" value={invoice.taxRupees} onChange={(event) => setInvoice({ ...invoice, taxRupees: Number(event.target.value) })} /></div>
              </div>
              <div className="field-row">
                <div className="field"><label htmlFor="period-start">Period starts</label><input id="period-start" type="datetime-local" value={invoice.periodStartsAt} onChange={(event) => setInvoice({ ...invoice, periodStartsAt: event.target.value })} /></div>
                <div className="field"><label htmlFor="period-end">Period ends</label><input id="period-end" type="datetime-local" value={invoice.periodEndsAt} onChange={(event) => setInvoice({ ...invoice, periodEndsAt: event.target.value })} /></div>
              </div>
              <div className="field-row">
                <div className="field"><label htmlFor="issued-at">Issued at</label><input id="issued-at" type="datetime-local" value={invoice.issuedAt} onChange={(event) => setInvoice({ ...invoice, issuedAt: event.target.value })} /></div>
                <div className="field"><label htmlFor="due-at">Due at</label><input id="due-at" type="datetime-local" value={invoice.dueAt} onChange={(event) => setInvoice({ ...invoice, dueAt: event.target.value })} /></div>
              </div>
              <p className="calculated-price">Approved subtotal: <strong>{selectedMonthlyPrice === undefined ? "—" : formatInrFromPaise(selectedMonthlyPrice)}</strong></p>
              <button className="button button-primary" disabled={mutationsDisabled} type="submit">Issue invoice</button>
            </form>

            <form className="form-section" onSubmit={recordPayment}>
              <h3>Record verified payment</h3>
              <div className="field"><label htmlFor="payment-invoice">Unpaid invoice</label><select id="payment-invoice" required value={payment.invoiceId} onChange={(event) => setPayment({ ...payment, invoiceId: event.target.value })}><option value="">Select invoice</option>{data.invoices.filter((item) => item.status === "ISSUED").map((item) => <option key={item.id} value={item.id}>{item.invoiceNumber} · {formatInrFromPaise(item.totalPaise)}</option>)}</select></div>
              <div className="field"><label htmlFor="payment-reference">Bank/UPI/cheque reference</label><input id="payment-reference" required value={payment.paymentReference} onChange={(event) => setPayment({ ...payment, paymentReference: event.target.value })} /></div>
              <div className="field-row">
                <div className="field"><label htmlFor="payment-method">Method</label><select id="payment-method" value={payment.paymentMethod} onChange={(event) => setPayment({ ...payment, paymentMethod: event.target.value })}>{["BANK_TRANSFER", "UPI", "CHEQUE", "CASH", "OTHER"].map((method) => <option key={method}>{method}</option>)}</select></div>
                <div className="field"><label htmlFor="paid-at">Paid at</label><input id="paid-at" type="datetime-local" value={payment.paidAt} onChange={(event) => setPayment({ ...payment, paidAt: event.target.value })} /></div>
              </div>
              <p className="calculated-price">Exact amount: <strong>{selectedInvoice ? formatInrFromPaise(selectedInvoice.totalPaise) : "Select an invoice"}</strong></p>
              <button className="button button-primary" disabled={mutationsDisabled || !selectedInvoice} type="submit">Record payment & receipt</button>
            </form>
          </div>
          <div className="panel billing-ledger">
            <div className="panel-header"><h3>Invoice and receipt ledger</h3></div>
            <form className="billing-void-form" onSubmit={voidInvoice}>
              <div className="field"><label htmlFor="void-invoice">Unpaid invoice to void</label><select id="void-invoice" required value={invoiceVoid.invoiceId} onChange={(event) => setInvoiceVoid({ ...invoiceVoid, invoiceId: event.target.value })}><option value="">Select invoice</option>{data.invoices.filter((item) => item.status === "ISSUED").map((item) => <option key={item.id} value={item.id}>{item.invoiceNumber}</option>)}</select></div>
              <div className="field"><label htmlFor="void-reason">Reason</label><input id="void-reason" required value={invoiceVoid.reason} onChange={(event) => setInvoiceVoid({ ...invoiceVoid, reason: event.target.value })} /></div>
              <button className="button button-secondary" disabled={mutationsDisabled || !invoiceVoid.invoiceId} type="submit">Void unpaid invoice</button>
            </form>
            <div className="data-table-wrap"><table className="data-table"><thead><tr><th>Invoice</th><th>Plan</th><th>Period</th><th>Total</th><th>Status</th><th>Receipt</th></tr></thead><tbody>{data.invoices.map((item) => { const receipt = data.receipts.find((entry) => entry.invoiceId === item.id); return <tr key={item.id}><td><strong>{item.invoiceNumber}</strong><br /><small>{readable(item.issuedAt)}</small></td><td>{item.plan} · {item.studentLimit}</td><td>{readable(item.periodStartsAt)}<br />to {readable(item.periodEndsAt)}</td><td>{formatInrFromPaise(item.totalPaise)}</td><td><span className={`badge badge-${item.status === "PAID" ? "success" : item.status === "VOID" ? "neutral" : "warning"}`}>{item.status}</span></td><td>{receipt?.receiptNumber ?? "—"}</td></tr>; })}</tbody></table></div>
          </div>
        </section>
      )}

      <section className="commercial-section">
        <div className="section-heading"><LifeBuoy size={20} /><div><h2>Local support register</h2><p>No email, SMS, CRM, or paid help-desk service is connected.</p></div></div>
        <div className="commercial-columns">
          <form className="form-section" onSubmit={createSupportCase}>
            <h3>Create support case</h3>
            <div className="field-row">
              <div className="field"><label htmlFor="support-severity">Severity</label><select id="support-severity" value={support.severity} onChange={(event) => setSupport({ ...support, severity: event.target.value })}>{["S1", "S2", "S3", "S4"].map((value) => <option key={value}>{value}</option>)}</select></div>
              <div className="field"><label htmlFor="support-category">Category</label><select id="support-category" value={support.category} onChange={(event) => setSupport({ ...support, category: event.target.value })}>{["ACCESS", "ASSESSMENT", "REPORTING", "BILLING", "DATA", "OTHER"].map((value) => <option key={value}>{value}</option>)}</select></div>
            </div>
            <div className="field"><label htmlFor="support-summary">Summary</label><input id="support-summary" required value={support.summary} onChange={(event) => setSupport({ ...support, summary: event.target.value })} /></div>
            <div className="field"><label htmlFor="support-description">Description</label><textarea id="support-description" required value={support.description} onChange={(event) => setSupport({ ...support, description: event.target.value })} /></div>
            <button className="button button-primary" disabled={mutationsDisabled} type="submit">Create local case</button>
          </form>
          <form className="form-section" onSubmit={updateSupportCase}>
            <h3>Assign or resolve case</h3>
            <div className="field"><label htmlFor="support-case">Case</label><select id="support-case" required value={supportUpdate.caseId} onChange={(event) => setSupportUpdate({ ...supportUpdate, caseId: event.target.value })}><option value="">Select case</option>{data.supportCases.map((item) => <option key={item.id} value={item.id}>{item.caseNumber} · {item.status}</option>)}</select></div>
            <div className="field-row">
              <div className="field"><label htmlFor="support-status">Status</label><select id="support-status" value={supportUpdate.status} onChange={(event) => setSupportUpdate({ ...supportUpdate, status: event.target.value })}>{["OPEN", "IN_PROGRESS", "WAITING_FOR_INSTITUTION", "RESOLVED", "CLOSED"].map((value) => <option key={value}>{value}</option>)}</select></div>
              <div className="field"><label htmlFor="assigned-to">Assigned to</label><input id="assigned-to" value={supportUpdate.assignedTo} onChange={(event) => setSupportUpdate({ ...supportUpdate, assignedTo: event.target.value })} /></div>
            </div>
            <div className="field"><label htmlFor="resolution">Resolution (required for resolved/closed)</label><textarea id="resolution" value={supportUpdate.resolution} onChange={(event) => setSupportUpdate({ ...supportUpdate, resolution: event.target.value })} /></div>
            <button className="button button-primary" disabled={mutationsDisabled || !supportUpdate.caseId} type="submit">Update case</button>
          </form>
        </div>
        <div className="panel"><div className="data-table-wrap"><table className="data-table"><thead><tr><th>Case</th><th>Severity</th><th>Summary</th><th>Status</th><th>Owner</th><th>Response target</th></tr></thead><tbody>{data.supportCases.length === 0 ? <tr><td colSpan={6}>No support cases.</td></tr> : data.supportCases.map((item) => <tr key={item.id}><td><strong>{item.caseNumber}</strong><br /><small>{item.category}</small></td><td><span className={`badge badge-${item.severity === "S1" || item.severity === "S2" ? "danger" : "neutral"}`}>{item.severity}</span></td><td>{item.summary}</td><td>{item.status.replaceAll("_", " ")}</td><td>{item.assignedTo ?? "Unassigned"}</td><td>{readable(item.responseDueAt)}</td></tr>)}</tbody></table></div></div>
      </section>

      {role === "SUPER_ADMIN" && (
        <section className="commercial-section">
          <div className="section-heading"><Building2 size={20} /><div><h2>Organisation onboarding</h2><p>Creates the tenant, platform-owner membership, invited organisation administrator, default grading, and one-time Legend trial in one transaction.</p></div></div>
          <form className="form-section onboarding-form" onSubmit={onboardOrganisation}>
            <div className="field-row"><div className="field"><label htmlFor="org-code">Organisation code</label><input id="org-code" required value={onboarding.code} onChange={(event) => setOnboarding({ ...onboarding, code: event.target.value.toUpperCase() })} /></div><div className="field"><label htmlFor="org-name">Organisation name</label><input id="org-name" required value={onboarding.name} onChange={(event) => setOnboarding({ ...onboarding, name: event.target.value })} /></div></div>
            <div className="field-row"><div className="field"><label htmlFor="admin-first">Admin first name</label><input id="admin-first" required value={onboarding.adminFirstName} onChange={(event) => setOnboarding({ ...onboarding, adminFirstName: event.target.value })} /></div><div className="field"><label htmlFor="admin-last">Admin last name</label><input id="admin-last" required value={onboarding.adminLastName} onChange={(event) => setOnboarding({ ...onboarding, adminLastName: event.target.value })} /></div></div>
            <div className="field-row"><div className="field"><label htmlFor="admin-email">Admin email</label><input id="admin-email" required type="email" value={onboarding.adminEmail} onChange={(event) => setOnboarding({ ...onboarding, adminEmail: event.target.value })} /></div><div className="field"><label htmlFor="onboard-students">Expected students</label><input id="onboard-students" max={500} min={1} required type="number" value={onboarding.declaredStudents} onChange={(event) => setOnboarding({ ...onboarding, declaredStudents: Number(event.target.value) })} /></div></div>
            <div className="field-row"><div className="field"><label htmlFor="org-timezone">IANA time zone</label><input id="org-timezone" required value={onboarding.timezone} onChange={(event) => setOnboarding({ ...onboarding, timezone: event.target.value })} /></div><div className="field"><label htmlFor="onboarding-note">Onboarding note (optional)</label><input id="onboarding-note" value={onboarding.note} onChange={(event) => setOnboarding({ ...onboarding, note: event.target.value })} /></div></div>
            <button className="button button-primary" disabled={mutationsDisabled} type="submit">Create organisation & trial</button>
            {activationUrl && <div className="activation-output"><strong>Share this activation link through the approved manual channel:</strong><input aria-label="Administrator activation link" readOnly value={activationUrl} /></div>}
          </form>
        </section>
      )}
    </div>
  );
}
