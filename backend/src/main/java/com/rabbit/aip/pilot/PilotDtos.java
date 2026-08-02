package com.rabbit.aip.pilot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PilotDtos {

    private PilotDtos() {
    }

    public record UpdatePilotCheckRequest(
            @NotNull PilotCheckStatus status,
            @Size(max = 150) String testerName,
            @Size(max = 1000) String evidenceUrl,
            @Size(max = 100) String defectId,
            @Size(max = 2000) String notes,
            Instant executedAt
    ) {
    }

    public record PilotCheckResponse(
            UUID id,
            PilotCheckKey key,
            String category,
            String label,
            boolean mandatory,
            PilotCheckStatus status,
            String testerName,
            String evidenceUrl,
            String defectId,
            String notes,
            Instant executedAt,
            UUID updatedBy
    ) {
        static PilotCheckResponse from(PilotCheckResult result) {
            return new PilotCheckResponse(
                    result.getId(),
                    result.getKey(),
                    result.getKey().category(),
                    result.getKey().label(),
                    result.getKey().mandatory(),
                    result.getStatus(),
                    result.getTesterName(),
                    result.getEvidenceUrl(),
                    result.getDefectId(),
                    result.getNotes(),
                    result.getExecutedAt(),
                    result.getUpdatedBy()
            );
        }
    }

    public record PilotDecisionRequest(
            @NotNull PilotDecisionOutcome outcome,
            @NotBlank @Size(max = 50) String releaseVersion,
            @NotBlank
            @Pattern(regexp = "(?i)^[0-9a-f]{7,40}$")
            String releaseCommit,
            @NotBlank @Size(max = 200) String institutionName,
            @NotBlank @Size(max = 150) String authorisedBy,
            @NotBlank @Size(max = 150) String authoriserTitle,
            @NotBlank @Size(max = 150) String uatLead,
            @NotBlank @Size(max = 150) String technicalOwner,
            @NotBlank @Size(max = 200) String supportContact,
            @NotBlank @Size(max = 150) String monitoringOwner,
            @NotBlank @Size(max = 150) String backupRestoreOwner,
            @NotBlank @Size(max = 150) String incidentOwner,
            @NotBlank @Size(max = 150) String rollbackOwner,
            @NotBlank @Size(max = 150) String dataPrivacyOwner,
            @NotBlank @Size(max = 150) String handoverRecipient,
            @NotBlank @Size(max = 1000) String evidenceReference,
            @NotBlank
            @Pattern(regexp = "(?i)^[0-9a-f]{64}$")
            String evidenceSha256,
            @Min(0) @Max(10000) int knownIssueCount,
            @Size(max = 1000) String knownIssuesReference,
            @NotBlank @Size(max = 2000) String decisionReason,
            Instant retestBy,
            boolean localDataConfirmed,
            boolean localOnlyConfirmed,
            boolean ownershipAccepted,
            boolean scopeFreezeAccepted
    ) {
    }

    public record PilotDecisionResponse(
            UUID id,
            PilotDecisionOutcome outcome,
            String releaseVersion,
            String releaseCommit,
            String institutionName,
            String authorisedBy,
            String authoriserTitle,
            String uatLead,
            String technicalOwner,
            String supportContact,
            String monitoringOwner,
            String backupRestoreOwner,
            String incidentOwner,
            String rollbackOwner,
            String dataPrivacyOwner,
            String handoverRecipient,
            String evidenceReference,
            String evidenceSha256,
            int knownIssueCount,
            String knownIssuesReference,
            String decisionReason,
            Instant retestBy,
            boolean localDataConfirmed,
            boolean localOnlyConfirmed,
            boolean ownershipAccepted,
            boolean scopeFreezeAccepted,
            boolean mandatoryChecksPassed,
            int passedChecks,
            int failedChecks,
            int blockedChecks,
            int notRunChecks,
            Instant decidedAt,
            UUID decidedByUserId
    ) {
        static PilotDecisionResponse from(PilotReleaseDecision decision) {
            return new PilotDecisionResponse(
                    decision.getId(),
                    decision.getOutcome(),
                    decision.getReleaseVersion(),
                    decision.getReleaseCommit(),
                    decision.getInstitutionName(),
                    decision.getAuthorisedBy(),
                    decision.getAuthoriserTitle(),
                    decision.getUatLead(),
                    decision.getTechnicalOwner(),
                    decision.getSupportContact(),
                    decision.getMonitoringOwner(),
                    decision.getBackupRestoreOwner(),
                    decision.getIncidentOwner(),
                    decision.getRollbackOwner(),
                    decision.getDataPrivacyOwner(),
                    decision.getHandoverRecipient(),
                    decision.getEvidenceReference(),
                    decision.getEvidenceSha256(),
                    decision.getKnownIssueCount(),
                    decision.getKnownIssuesReference(),
                    decision.getDecisionReason(),
                    decision.getRetestBy(),
                    decision.isLocalDataConfirmed(),
                    decision.isLocalOnlyConfirmed(),
                    decision.isOwnershipAccepted(),
                    decision.isScopeFreezeAccepted(),
                    decision.isMandatoryChecksPassed(),
                    decision.getPassedChecks(),
                    decision.getFailedChecks(),
                    decision.getBlockedChecks(),
                    decision.getNotRunChecks(),
                    decision.getDecidedAt(),
                    decision.getDecidedByUserId()
            );
        }
    }

    public record PilotSignOffResponse(
            UUID id,
            String releaseVersion,
            String authorisedBy,
            String authoriserTitle,
            String supportContact,
            String rollbackOwner,
            String notes,
            Instant signedAt,
            UUID signedByUserId
    ) {
        static PilotSignOffResponse from(PilotSignOff signOff) {
            return new PilotSignOffResponse(
                    signOff.getId(),
                    signOff.getReleaseVersion(),
                    signOff.getAuthorisedBy(),
                    signOff.getAuthoriserTitle(),
                    signOff.getSupportContact(),
                    signOff.getRollbackOwner(),
                    signOff.getNotes(),
                    signOff.getSignedAt(),
                    signOff.getSignedByUserId()
            );
        }
    }

    public record PilotReadinessResponse(
            int totalChecks,
            int passedChecks,
            int failedChecks,
            int blockedChecks,
            int notRunChecks,
            boolean mandatoryChecksPassed,
            boolean signedOff,
            List<PilotCheckResponse> checks,
            PilotSignOffResponse signOff,
            PilotDecisionResponse latestDecision,
            List<PilotDecisionResponse> decisions
    ) {
    }
}
