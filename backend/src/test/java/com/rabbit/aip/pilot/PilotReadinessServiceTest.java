package com.rabbit.aip.pilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.pilot.PilotDtos.UpdatePilotCheckRequest;
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
}
