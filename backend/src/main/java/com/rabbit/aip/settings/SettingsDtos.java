package com.rabbit.aip.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record GeneralSettingsRequest(
            @NotBlank String timezone,
            @NotBlank @Size(max = 20) String language,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal passPercentage,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal atRiskThreshold,
            @Min(1) @Max(480) int defaultDurationMinutes,
            @Min(1) @Max(10) int defaultAttemptsAllowed,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            boolean emailNotificationsEnabled,
            boolean smsNotificationsEnabled,
            boolean rankingEnabled,
            @Min(365) @Max(3650) int auditRetentionDays,
            @NotBlank @Size(max = 200) String displayName,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String primaryColour
    ) {
    }

    public record GeneralSettingsResponse(
            String timezone,
            String language,
            BigDecimal passPercentage,
            BigDecimal atRiskThreshold,
            int defaultDurationMinutes,
            int defaultAttemptsAllowed,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            boolean emailNotificationsEnabled,
            boolean smsNotificationsEnabled,
            boolean rankingEnabled,
            int auditRetentionDays,
            String displayName,
            String primaryColour
    ) {
        static GeneralSettingsResponse from(OrganisationSettings settings) {
            return new GeneralSettingsResponse(
                    settings.getTimezone(),
                    settings.getLanguage(),
                    settings.getPassPercentage(),
                    settings.getAtRiskThreshold(),
                    settings.getDefaultDurationMinutes(),
                    settings.getDefaultAttemptsAllowed(),
                    settings.isShuffleQuestions(),
                    settings.isShuffleOptions(),
                    settings.isEmailNotificationsEnabled(),
                    settings.isSmsNotificationsEnabled(),
                    settings.isRankingEnabled(),
                    settings.getAuditRetentionDays(),
                    settings.getDisplayName(),
                    settings.getPrimaryColour()
            );
        }
    }

    public record GradeBandRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 80) String label,
            @NotNull BigDecimal minPercentage,
            @NotNull BigDecimal maxPercentage
    ) {
    }

    public record GradeBandResponse(
            UUID id,
            String code,
            String label,
            BigDecimal minPercentage,
            BigDecimal maxPercentage,
            int sortOrder
    ) {
        static GradeBandResponse from(GradeBand band) {
            return new GradeBandResponse(
                    band.getId(),
                    band.getCode(),
                    band.getLabel(),
                    band.getMinPercentage(),
                    band.getMaxPercentage(),
                    band.getSortOrder()
            );
        }
    }

    public record GradeBandsRequest(
            @NotEmpty List<@Valid GradeBandRequest> bands
    ) {
    }

    public record SubjectRequest(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 150) String name
    ) {
    }

    public record TopicRequest(
            @NotNull UUID subjectId,
            @NotBlank @Size(max = 200) String name
    ) {
    }

    public record SubjectResponse(UUID id, String code, String name, boolean active) {
        static SubjectResponse from(AcademicSubject subject) {
            return new SubjectResponse(
                    subject.getId(),
                    subject.getCode(),
                    subject.getName(),
                    subject.isActive()
            );
        }
    }

    public record TopicResponse(
            UUID id,
            UUID subjectId,
            String name,
            boolean active
    ) {
        static TopicResponse from(AcademicTopic topic) {
            return new TopicResponse(
                    topic.getId(),
                    topic.getSubjectId(),
                    topic.getName(),
                    topic.isActive()
            );
        }
    }

    public record SettingsBundle(
            GeneralSettingsResponse general,
            List<GradeBandResponse> gradeBands,
            List<SubjectResponse> subjects,
            List<TopicResponse> topics
    ) {
    }
}
