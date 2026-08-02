package com.rabbit.aip.pilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.pilot.PilotDtos.PilotDecisionRequest;
import com.rabbit.aip.pilot.PilotDtos.UpdatePilotCheckRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class PilotReadinessServiceTest {

    @Test
    void passingCheckRequiresTesterAndEvidence() {
        assertThatThrownBy(() -> PilotReadinessService.validateCheck(
                request(PilotCheckStatus.PASS, "Tester", null, null, null)
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("evidence link");

        assertThatCode(() -> PilotReadinessService.validateCheck(
                request(
                        PilotCheckStatus.PASS,
                        "Institution tester",
                        "https://evidence.example/pilot/identity",
                        null,
                        null
                )
        )).doesNotThrowAnyException();
    }

    @Test
    void failedCheckRequiresDefectOrNotes() {
        assertThatThrownBy(() -> PilotReadinessService.validateCheck(
                request(PilotCheckStatus.FAIL, "Tester", null, null, null)
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("defect ID or notes");

        assertThatCode(() -> PilotReadinessService.validateCheck(
                request(PilotCheckStatus.BLOCKED, "Tester", null, "RAB-42", null)
        )).doesNotThrowAnyException();
    }

    @Test
    void evidenceLinkMustBeAnAbsoluteWebUrl() {
        assertThatThrownBy(() -> PilotReadinessService.validateCheck(
                request(
                        PilotCheckStatus.PASS,
                        "Tester",
                        "javascript:alert(1)",
                        null,
                        null
                )
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("HTTP/HTTPS");
    }

    @Test
    void localEvidenceUrnIsAcceptedWithoutACloudUrl() {
        assertThatCode(() -> PilotReadinessService.validateCheck(
                request(
                        PilotCheckStatus.PASS,
                        "Institution tester",
                        "urn:rabbit-evidence:m5-4:live:20260821T120000Z:abc12345",
                        null,
                        null
                )
        )).doesNotThrowAnyException();

        assertThatThrownBy(() -> PilotReadinessService.validateCheck(
                request(
                        PilotCheckStatus.PASS,
                        "Institution tester",
                        "urn:unapproved-cloud-evidence:abc12345",
                        null,
                        null
                )
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("rabbit-evidence");
    }

    @Test
    void institutionalExecutionChecksAreMandatory() {
        assertThat(PilotCheckKey.STAFF_REHEARSAL.mandatory()).isTrue();
        assertThat(PilotCheckKey.LIVE_ASSESSMENT.mandatory()).isTrue();
        assertThat(PilotCheckKey.PILOT_RECONCILIATION.mandatory()).isTrue();
        assertThat(PilotCheckKey.INCIDENT_CLOSURE.mandatory()).isTrue();
    }

    @Test
    void goRequiresEveryMandatoryCheckAndAllFinalAttestations() {
        Instant now = Instant.parse("2026-08-31T09:00:00Z");
        assertThatThrownBy(() -> PilotReadinessService.validateDecision(
                decision(PilotDecisionOutcome.GO, null, true, true),
                false,
                now
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("mandatory pilot check");

        assertThatThrownBy(() -> PilotReadinessService.validateDecision(
                decision(PilotDecisionOutcome.GO, null, false, true),
                true,
                now
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("operating ownership");

        assertThatCode(() -> PilotReadinessService.validateDecision(
                decision(PilotDecisionOutcome.GO, null, true, true),
                true,
                now
        )).doesNotThrowAnyException();
    }

    @Test
    void conditionalRetestRequiresAFutureDeadline() {
        Instant now = Instant.parse("2026-08-31T09:00:00Z");
        assertThatThrownBy(() -> PilotReadinessService.validateDecision(
                decision(PilotDecisionOutcome.CONDITIONAL_RETEST, null, false, false),
                false,
                now
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("future retest deadline");

        assertThatCode(() -> PilotReadinessService.validateDecision(
                decision(
                        PilotDecisionOutcome.CONDITIONAL_RETEST,
                        now.plus(2, ChronoUnit.DAYS),
                        false,
                        false
                ),
                false,
                now
        )).doesNotThrowAnyException();
    }

    @Test
    void decisionEvidenceMustStayInTheLocalRabbitEvidenceScheme() {
        Instant now = Instant.parse("2026-08-31T09:00:00Z");
        PilotDecisionRequest valid = decision(
                PilotDecisionOutcome.NO_GO,
                null,
                false,
                false
        );
        PilotDecisionRequest webEvidence = withEvidence(
                valid,
                "https://public.example/rabbit-handover"
        );
        assertThatThrownBy(() -> PilotReadinessService.validateDecision(
                webEvidence,
                false,
                now
        ))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("local Rabbit M5.5 evidence");

        assertThatCode(() -> PilotReadinessService.validateDecision(
                valid,
                false,
                now
        )).doesNotThrowAnyException();
    }

    private UpdatePilotCheckRequest request(
            PilotCheckStatus status,
            String tester,
            String evidence,
            String defect,
            String notes
    ) {
        return new UpdatePilotCheckRequest(
                status,
                tester,
                evidence,
                defect,
                notes,
                null
        );
    }

    private PilotDecisionRequest decision(
            PilotDecisionOutcome outcome,
            Instant retestBy,
            boolean ownershipAccepted,
            boolean scopeFreezeAccepted
    ) {
        return new PilotDecisionRequest(
                outcome,
                "1.0.0",
                "5ce4a7a",
                "Rabbit Pilot Institution",
                "Authorised Sponsor",
                "Principal",
                "UAT Lead",
                "Technical Owner",
                "Support Owner / local channel",
                "Monitoring Owner",
                "Backup Restore Owner",
                "Incident Owner",
                "Rollback Owner",
                "Data Privacy Owner",
                "Institution Operations",
                "urn:rabbit-evidence:m5-5:prepare:20260831T090000Z:abc12345",
                "a".repeat(64),
                0,
                null,
                "Institutional release decision.",
                retestBy,
                true,
                true,
                ownershipAccepted,
                scopeFreezeAccepted
        );
    }

    private PilotDecisionRequest withEvidence(
            PilotDecisionRequest request,
            String evidence
    ) {
        return new PilotDecisionRequest(
                request.outcome(),
                request.releaseVersion(),
                request.releaseCommit(),
                request.institutionName(),
                request.authorisedBy(),
                request.authoriserTitle(),
                request.uatLead(),
                request.technicalOwner(),
                request.supportContact(),
                request.monitoringOwner(),
                request.backupRestoreOwner(),
                request.incidentOwner(),
                request.rollbackOwner(),
                request.dataPrivacyOwner(),
                request.handoverRecipient(),
                evidence,
                request.evidenceSha256(),
                request.knownIssueCount(),
                request.knownIssuesReference(),
                request.decisionReason(),
                request.retestBy(),
                request.localDataConfirmed(),
                request.localOnlyConfirmed(),
                request.ownershipAccepted(),
                request.scopeFreezeAccepted()
        );
    }
}
