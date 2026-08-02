package com.rabbit.aip.pilot;

public enum PilotCheckKey {
    IDENTITY(
            "Identity and access",
            "Login, lockout, refresh, organisation selection, and logout",
            true
    ),
    TENANT_ISOLATION(
            "Security",
            "Cross-tenant URL and relationship access is rejected",
            true
    ),
    QUESTION_GOVERNANCE(
            "Academic governance",
            "Question authoring, review, return, approval, and versioning",
            true
    ),
    ASSESSMENT_GOVERNANCE(
            "Academic governance",
            "Assessment draft, review, approval, publish, and schedule",
            true
    ),
    DELIVERY_RECOVERY(
            "Assessment delivery",
            "Start, save, refresh, resume, timeout, and submit without data loss",
            true
    ),
    EVALUATION_PUBLICATION(
            "Evaluation",
            "Objective scoring, re-evaluation, and governed result publication",
            true
    ),
    REPORTS_EXPORTS(
            "Reporting",
            "On-screen reports match CSV, PDF, and XLSX exports",
            true
    ),
    OPERATIONS_OBSERVABILITY(
            "Operations",
            "Dependencies, traffic, backlog, capacity, and alerts match evidence",
            true
    ),
    FEATURE_FLAGS(
            "Operations",
            "Tenant rollout changes behaviour and writes an audit event",
            false
    ),
    ACCESSIBILITY(
            "Accessibility",
            "Keyboard, focus order, labels, 200% zoom, and reduced motion pass",
            true
    ),
    MOBILE_WEB(
            "Accessibility",
            "Student journey completes at 360 px without blocked actions",
            true
    ),
    BACKUP_RESTORE(
            "Resilience",
            "Latest backup restores and validates within the recovery objective",
            true
    ),
    PERFORMANCE(
            "Performance",
            "Pilot load meets error-rate, p95, and p99 thresholds",
            true
    ),
    SECURITY_REVIEW(
            "Security",
            "Headers, rate limits, secrets, and dependencies have no critical finding",
            true
    ),
    OPERATING_OWNERSHIP(
            "Operations",
            "Release, support, incident, and rollback owners are named",
            true
    ),
    STAFF_REHEARSAL(
            "Pilot execution",
            "Staff rehearsal completed on the approved local release and cohort",
            true
    ),
    LIVE_ASSESSMENT(
            "Pilot execution",
            "Live institutional assessment completed on the approved local release",
            true
    ),
    PILOT_RECONCILIATION(
            "Pilot execution",
            "Roster, attempts, scoring, publication, exports, and audit reconcile",
            true
    ),
    INCIDENT_CLOSURE(
            "Pilot execution",
            "No Severity 1 or Severity 2 pilot incident remains open",
            true
    );

    private final String category;
    private final String label;
    private final boolean mandatory;

    PilotCheckKey(String category, String label, boolean mandatory) {
        this.category = category;
        this.label = label;
        this.mandatory = mandatory;
    }

    public String category() {
        return category;
    }

    public String label() {
        return label;
    }

    public boolean mandatory() {
        return mandatory;
    }
}
