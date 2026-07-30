package com.rabbit.aip.pilot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    public record PilotSignOffRequest(
            @NotBlank @Size(max = 50) String releaseVersion,
            @NotBlank @Size(max = 150) String authorisedBy,
            @NotBlank @Size(max = 150) String authoriserTitle,
            @NotBlank @Size(max = 200) String supportContact,
            @NotBlank @Size(max = 150) String rollbackOwner,
            @Size(max = 2000) String notes
    ) {
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
            PilotSignOffResponse signOff
    ) {
    }
}
