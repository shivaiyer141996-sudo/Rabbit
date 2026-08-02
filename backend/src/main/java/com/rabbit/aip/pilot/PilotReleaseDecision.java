package com.rabbit.aip.pilot;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pilot_release_decisions")
public class PilotReleaseDecision extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PilotDecisionOutcome outcome;

    @Column(name = "release_version", nullable = false, length = 50)
    private String releaseVersion;

    @Column(name = "release_commit", nullable = false, length = 40)
    private String releaseCommit;

    @Column(name = "institution_name", nullable = false, length = 200)
    private String institutionName;

    @Column(name = "authorised_by", nullable = false, length = 150)
    private String authorisedBy;

    @Column(name = "authoriser_title", nullable = false, length = 150)
    private String authoriserTitle;

    @Column(name = "uat_lead", nullable = false, length = 150)
    private String uatLead;

    @Column(name = "technical_owner", nullable = false, length = 150)
    private String technicalOwner;

    @Column(name = "support_contact", nullable = false, length = 200)
    private String supportContact;

    @Column(name = "monitoring_owner", nullable = false, length = 150)
    private String monitoringOwner;

    @Column(name = "backup_restore_owner", nullable = false, length = 150)
    private String backupRestoreOwner;

    @Column(name = "incident_owner", nullable = false, length = 150)
    private String incidentOwner;

    @Column(name = "rollback_owner", nullable = false, length = 150)
    private String rollbackOwner;

    @Column(name = "data_privacy_owner", nullable = false, length = 150)
    private String dataPrivacyOwner;

    @Column(name = "handover_recipient", nullable = false, length = 150)
    private String handoverRecipient;

    @Column(name = "evidence_reference", nullable = false, length = 1000)
    private String evidenceReference;

    @Column(name = "evidence_sha256", nullable = false, length = 64)
    private String evidenceSha256;

    @Column(name = "known_issue_count", nullable = false)
    private int knownIssueCount;

    @Column(name = "known_issues_reference", length = 1000)
    private String knownIssuesReference;

    @Column(name = "decision_reason", nullable = false, length = 2000)
    private String decisionReason;

    @Column(name = "retest_by")
    private Instant retestBy;

    @Column(name = "local_data_confirmed", nullable = false)
    private boolean localDataConfirmed;

    @Column(name = "local_only_confirmed", nullable = false)
    private boolean localOnlyConfirmed;

    @Column(name = "ownership_accepted", nullable = false)
    private boolean ownershipAccepted;

    @Column(name = "scope_freeze_accepted", nullable = false)
    private boolean scopeFreezeAccepted;

    @Column(name = "mandatory_checks_passed", nullable = false)
    private boolean mandatoryChecksPassed;

    @Column(name = "passed_checks", nullable = false)
    private int passedChecks;

    @Column(name = "failed_checks", nullable = false)
    private int failedChecks;

    @Column(name = "blocked_checks", nullable = false)
    private int blockedChecks;

    @Column(name = "not_run_checks", nullable = false)
    private int notRunChecks;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "decided_by_user_id", nullable = false)
    private UUID decidedByUserId;

    protected PilotReleaseDecision() {
    }

    public PilotReleaseDecision(
            UUID organisationId,
            PilotDtos.PilotDecisionRequest request,
            boolean mandatoryChecksPassed,
            int passedChecks,
            int failedChecks,
            int blockedChecks,
            int notRunChecks,
            Instant decidedAt,
            UUID decidedByUserId
    ) {
        super(organisationId);
        this.outcome = request.outcome();
        this.releaseVersion = request.releaseVersion().trim();
        this.releaseCommit = request.releaseCommit().trim().toLowerCase();
        this.institutionName = request.institutionName().trim();
        this.authorisedBy = request.authorisedBy().trim();
        this.authoriserTitle = request.authoriserTitle().trim();
        this.uatLead = request.uatLead().trim();
        this.technicalOwner = request.technicalOwner().trim();
        this.supportContact = request.supportContact().trim();
        this.monitoringOwner = request.monitoringOwner().trim();
        this.backupRestoreOwner = request.backupRestoreOwner().trim();
        this.incidentOwner = request.incidentOwner().trim();
        this.rollbackOwner = request.rollbackOwner().trim();
        this.dataPrivacyOwner = request.dataPrivacyOwner().trim();
        this.handoverRecipient = request.handoverRecipient().trim();
        this.evidenceReference = request.evidenceReference().trim();
        this.evidenceSha256 = request.evidenceSha256().trim().toLowerCase();
        this.knownIssueCount = request.knownIssueCount();
        this.knownIssuesReference = normalized(request.knownIssuesReference());
        this.decisionReason = request.decisionReason().trim();
        this.retestBy = request.retestBy();
        this.localDataConfirmed = request.localDataConfirmed();
        this.localOnlyConfirmed = request.localOnlyConfirmed();
        this.ownershipAccepted = request.ownershipAccepted();
        this.scopeFreezeAccepted = request.scopeFreezeAccepted();
        this.mandatoryChecksPassed = mandatoryChecksPassed;
        this.passedChecks = passedChecks;
        this.failedChecks = failedChecks;
        this.blockedChecks = blockedChecks;
        this.notRunChecks = notRunChecks;
        this.decidedAt = decidedAt;
        this.decidedByUserId = decidedByUserId;
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public PilotDecisionOutcome getOutcome() { return outcome; }
    public String getReleaseVersion() { return releaseVersion; }
    public String getReleaseCommit() { return releaseCommit; }
    public String getInstitutionName() { return institutionName; }
    public String getAuthorisedBy() { return authorisedBy; }
    public String getAuthoriserTitle() { return authoriserTitle; }
    public String getUatLead() { return uatLead; }
    public String getTechnicalOwner() { return technicalOwner; }
    public String getSupportContact() { return supportContact; }
    public String getMonitoringOwner() { return monitoringOwner; }
    public String getBackupRestoreOwner() { return backupRestoreOwner; }
    public String getIncidentOwner() { return incidentOwner; }
    public String getRollbackOwner() { return rollbackOwner; }
    public String getDataPrivacyOwner() { return dataPrivacyOwner; }
    public String getHandoverRecipient() { return handoverRecipient; }
    public String getEvidenceReference() { return evidenceReference; }
    public String getEvidenceSha256() { return evidenceSha256; }
    public int getKnownIssueCount() { return knownIssueCount; }
    public String getKnownIssuesReference() { return knownIssuesReference; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getRetestBy() { return retestBy; }
    public boolean isLocalDataConfirmed() { return localDataConfirmed; }
    public boolean isLocalOnlyConfirmed() { return localOnlyConfirmed; }
    public boolean isOwnershipAccepted() { return ownershipAccepted; }
    public boolean isScopeFreezeAccepted() { return scopeFreezeAccepted; }
    public boolean isMandatoryChecksPassed() { return mandatoryChecksPassed; }
    public int getPassedChecks() { return passedChecks; }
    public int getFailedChecks() { return failedChecks; }
    public int getBlockedChecks() { return blockedChecks; }
    public int getNotRunChecks() { return notRunChecks; }
    public Instant getDecidedAt() { return decidedAt; }
    public UUID getDecidedByUserId() { return decidedByUserId; }
}
