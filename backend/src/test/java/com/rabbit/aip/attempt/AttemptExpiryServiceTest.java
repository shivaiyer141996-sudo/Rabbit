package com.rabbit.aip.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbit.aip.assessment.Assessment;
import com.rabbit.aip.assessment.AssessmentRepository;
import com.rabbit.aip.assessment.AssessmentType;
import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.notification.NotificationService;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttemptExpiryServiceTest {

    @Test
    void expiredAttemptIsEvaluatedAndAutoSubmittedWithoutAStudentRequest() {
        UUID organisationId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Assessment assessment = new Assessment(
                organisationId,
                "Timed assessment",
                "ASM-TIME",
                AssessmentType.CLASS_TEST,
                List.of(UUID.randomUUID()),
                30,
                true,
                true,
                false,
                1,
                UUID.randomUUID(),
                List.of(),
                BigDecimal.TEN
        );
        AssessmentAttempt attempt = new AssessmentAttempt(
                organisationId,
                assessment.getId(),
                studentId,
                Instant.now().minusSeconds(1)
        );
        AssessmentAttemptRepository attempts = mock(AssessmentAttemptRepository.class);
        AssessmentRepository assessments = mock(AssessmentRepository.class);
        AttemptResponseRepository responses = mock(AttemptResponseRepository.class);
        QuestionRepository questions = mock(QuestionRepository.class);
        EvaluationEngine engine = mock(EvaluationEngine.class);
        SettingsService settings = mock(SettingsService.class);
        AuditService audit = mock(AuditService.class);
        NotificationService notifications = mock(NotificationService.class);
        Instant cutoff = Instant.now();

        when(attempts.findExpiredForUpdate(AttemptStatus.IN_PROGRESS, cutoff))
                .thenReturn(List.of(attempt));
        when(assessments.findByIdAndOrganisationId(
                assessment.getId(), organisationId
        )).thenReturn(Optional.of(assessment));
        when(questions.findAllByIdInAndOrganisationId(any(), eq(organisationId)))
                .thenReturn(List.of());
        when(responses.findAllByAttemptId(attempt.getId())).thenReturn(List.of());
        when(engine.evaluate(eq(assessment), any(), any())).thenReturn(
                new EvaluationEngine.EvaluationOutcome(
                        BigDecimal.valueOf(7),
                        BigDecimal.TEN,
                        BigDecimal.valueOf(70),
                        2,
                        1,
                        0
                )
        );
        when(settings.resolveGrade(organisationId, BigDecimal.valueOf(70)))
                .thenReturn("B");

        AttemptExpiryService service = new AttemptExpiryService(
                attempts,
                assessments,
                responses,
                questions,
                engine,
                settings,
                audit,
                notifications
        );

        assertThat(service.submitExpiredAttempts(cutoff)).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.AUTO_SUBMITTED);
        assertThat(attempt.getScore()).isEqualByComparingTo("7");
        assertThat(attempt.getGrade()).isEqualTo("B");
        assertThat(attempt.getSubmittedAt()).isNotNull();
        verify(audit).recordSystem(
                eq(organisationId),
                eq(studentId),
                eq("DEL"),
                eq("AUTO_SUBMIT_EXPIRED"),
                eq("AssessmentAttempt"),
                eq(attempt.getId()),
                eq("IN_PROGRESS"),
                eq("AUTO_SUBMITTED")
        );
        verify(notifications).notifyRolesForOrganisation(
                eq(organisationId), any(), any(), any(), any(), any(), eq(false)
        );

        when(attempts.findExpiredForUpdate(AttemptStatus.IN_PROGRESS, cutoff))
                .thenReturn(List.of());
        assertThat(service.submitExpiredAttempts(cutoff)).isZero();
        verify(engine, times(1)).evaluate(eq(assessment), any(), any());
    }
}
