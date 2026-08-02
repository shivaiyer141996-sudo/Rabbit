# Milestone 6 — Commercial readiness

## Status

**Repository implementation is complete; commercial activation remains blocked by
the real Milestone 5.6 release-closure gate.**

Milestone 6 is the final authorised Rabbit milestone. It adds commercial controls
around the approved Release 1.0 feature set and does not add a new assessment type,
AI, a payment gateway, cloud hosting, email/SMS, or any other external service.

The application defaults to:

```text
COMMERCIAL_CONTROLS_ENABLED=false
```

Existing Milestone 5 journeys therefore continue unchanged. Setting the value to
`true` is rejected at application startup unless both of these are present:

- the exact 7–40 character `RABBIT_RELEASE_COMMIT`; and
- a final checksummed `urn:rabbit-evidence:m5-6:final:...` reference.

The activation inputs do not replace the human M5.6 process. They bind M6 to the
release that has already received the real Go decision, local image/source export,
`main` fast-forward, and annotated `v1.0.0` tag.

## Approved monthly plans

All amounts are stored in paise and locked independently in Java and PostgreSQL.
They cannot be overridden by the browser or an invoice request.

| Plan | Up to 50 students | Up to 150 students | Up to 500 students |
| --- | ---: | ---: | ---: |
| Basic | ₹599 | ₹999 | ₹1,499 |
| Pro | ₹899 | ₹1,399 | ₹1,899 |
| Legend | ₹1,499 | ₹1,999 | ₹2,499 |

Plan entitlements:

| Capability | Basic | Pro | Legend |
| --- | :---: | :---: | :---: |
| Assessment authoring, governance, delivery and scoring | Yes | Yes | Yes |
| Detailed student evaluation and progress report | No | Yes | Yes |
| Institution and question analytics | No | No | Yes |
| Teacher/batch analytics | No | No | Yes |
| Governed CSV/PDF/Excel exports | No | No | Yes |

All organisations retain access to plan/billing information and the local support
register, including after expiry. Core questions, assessments, attempts, results,
and audit records remain readable after expiry; new paid actions and plan-specific
analytics are paused. A student already inside an attempt may still save and submit,
so subscription expiry cannot strand an assessment.

## Twenty-day Legend trial

- Each organisation can start the trial only once.
- The trial always uses Legend entitlements.
- Its capacity is the smallest approved band that fits the declared 1–500 students.
- `trial_ends_at` is database-constrained to exactly 20 days after `trial_starts_at`.
- Expiry is evaluated on the server; browser time cannot extend access.
- Trial conversion requires a fully paid manual invoice covering the current time.
- There is no automatic renewal, stored card, or external payment request.

## Paid subscription rules

1. A Super Admin records one monthly invoice using an approved plan/capacity pair.
2. Rabbit derives the subtotal from the locked catalogue. Tax is a manual field and
   must be reviewed by the operator; Rabbit makes no tax-compliance determination.
3. The offline bank/UPI/cheque/cash reference is independently verified.
4. Rabbit accepts only an exact payment matching the invoice total.
5. The payment, paid invoice, receipt, subscription transition, immutable commercial
   event, and audit event commit in one database transaction.
6. An upgrade may start immediately. A renewal, lower plan, or lower capacity starts
   only on or after the current paid period ends and is shown as pending until then.
7. An invoice may be voided only before payment. Recorded payments are never silently
   edited or deleted. PostgreSQL permits only `ISSUED` → `PAID` or `ISSUED` →
   `VOID` invoice transitions and makes payment and receipt rows immutable.
8. A Super Admin may suspend or restore access only with a recorded reason. This
   never moves the original trial/paid end date, and an ended window cannot be revived.

Student admissions use both an application check and a PostgreSQL subscription-row
lock. Concurrent invitations therefore cannot overrun a 50, 150, or 500 Student
capacity, and a scheduled lower capacity reserves its smaller limit immediately.

## Organisation onboarding

The Super Admin onboarding transaction creates:

- the organisation and local tenant boundary;
- an active Super Admin membership for continued tenant administration;
- default organisation settings and grade bands;
- a hashed, expiring, one-time Organisation Admin invitation;
- the correct 20-day Legend trial and immutable subscription event; and
- tenant audit records.

The activation URL is displayed once for sharing through the approved manual
channel. No email/SMS provider is contacted.

## Local support administration

Rabbit stores support cases in PostgreSQL with S1–S4 severity, category, status,
requester, owner, first-response target, resolution, and audit history. The targets
are operational reminders, not a contractual SLA. Resolved/closed cases require
resolution text. No external CRM or help-desk product is connected.

## Interface and API

- Admin route: `/commercial`
- Role-safe entitlement route: `GET /api/v1/commercial-access`
- Admin overview and catalogue: `GET /api/v1/commercial/overview` and `/catalog`
- Trial and onboarding: `POST /api/v1/commercial/trial` and `/onboarding`
- Manual billing: `/commercial/invoices`, `/commercial/payments`
- Local support: `/commercial/support-cases`

The portal hides non-entitled report navigation. The API remains authoritative and
returns `402 Payment Required` when a current plan does not permit a paid action.

## Local data and migration

Flyway `V9__commercial_readiness.sql` adds tenant-scoped subscriptions, immutable
subscription events, invoices, payments, receipts, and support cases. PostgreSQL
validates the approved price matrix, trial length, invoice totals, payment/invoice
ownership, receipt/payment ownership, accounting immutability, concurrent Student
capacity, and immutable event history.

All data remains in the local `postgres-data` Docker volume and the existing backup,
restore, and PostgreSQL-portability process. Redis, RabbitMQ, MinIO, and Nginx remain
unchanged. No new container or externally reachable port is added.

## Verification and activation

Repository contract:

```bash
make m6-contract
```

Protected-host activation check, only after M5.6 passes:

```bash
make m6-activation-check M6_ENV=/absolute/path/to/protected.env
```

M6 is accepted only after all of the following are true:

- real M5.6 final evidence, `main`, release commit, and `v1.0.0` agree;
- V1–V9 migrate successfully on a copied/restored local PostgreSQL database;
- Basic, Pro, Legend and expired-plan API denials are exercised;
- one trial start/expiry, immediate upgrade, scheduled downgrade, invoice, payment,
  receipt, and support-case journey pass on the designated Docker computer;
- student-capacity enforcement is tested at 50, 150, and 500 boundaries;
- desktop and representative physical Android review is signed; and
- backup/isolated restore includes all M6 tables and audit records.

Until those items pass, the correct state is **implemented but not commercially
activated**. No Milestone 7 work is authorised.
