package com.rabbit.aip.pilot;

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
                .hasMessageContaining("HTTP or HTTPS");
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
