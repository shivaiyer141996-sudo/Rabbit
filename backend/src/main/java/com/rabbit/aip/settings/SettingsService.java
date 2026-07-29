package com.rabbit.aip.settings;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.question.QuestionRepository;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.settings.SettingsDtos.GeneralSettingsRequest;
import com.rabbit.aip.settings.SettingsDtos.GeneralSettingsResponse;
import com.rabbit.aip.settings.SettingsDtos.GradeBandRequest;
import com.rabbit.aip.settings.SettingsDtos.GradeBandResponse;
import com.rabbit.aip.settings.SettingsDtos.SettingsBundle;
import com.rabbit.aip.settings.SettingsDtos.SubjectResponse;
import com.rabbit.aip.settings.SettingsDtos.TopicResponse;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final OrganisationSettingsRepository settings;
    private final GradeBandRepository gradeBands;
    private final AcademicSubjectRepository subjects;
    private final AcademicTopicRepository topics;
    private final QuestionRepository questions;
    private final CurrentSession session;
    private final AuditService audit;

    public SettingsService(
            OrganisationSettingsRepository settings,
            GradeBandRepository gradeBands,
            AcademicSubjectRepository subjects,
            AcademicTopicRepository topics,
            QuestionRepository questions,
            CurrentSession session,
            AuditService audit
    ) {
        this.settings = settings;
        this.gradeBands = gradeBands;
        this.subjects = subjects;
        this.topics = topics;
        this.questions = questions;
        this.session = session;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SettingsBundle get() {
        UUID organisationId = session.organisationId();
        return new SettingsBundle(
                GeneralSettingsResponse.from(findSettings(organisationId)),
                gradeBands.findAllByOrganisationIdOrderBySortOrder(organisationId)
                        .stream().map(GradeBandResponse::from).toList(),
                subjects.findAllByOrganisationIdOrderByName(organisationId)
                        .stream().map(SubjectResponse::from).toList(),
                topics.findAllByOrganisationIdOrderByName(organisationId)
                        .stream().map(TopicResponse::from).toList()
        );
    }

    @Transactional
    public GeneralSettingsResponse updateGeneral(GeneralSettingsRequest request) {
        OrganisationSettings current = findSettings(session.organisationId());
        String before = GeneralSettingsResponse.from(current).toString();
        current.update(
                request.timezone().trim(),
                request.language().trim().toLowerCase(Locale.ROOT),
                request.passPercentage(),
                request.atRiskThreshold(),
                request.defaultDurationMinutes(),
                request.defaultAttemptsAllowed(),
                request.shuffleQuestions(),
                request.shuffleOptions(),
                request.emailNotificationsEnabled(),
                request.smsNotificationsEnabled(),
                request.auditRetentionDays(),
                request.displayName().trim(),
                request.primaryColour()
        );
        GeneralSettingsResponse response = GeneralSettingsResponse.from(current);
        audit.record(
                "SET", "UPDATE_GENERAL", "OrganisationSettings", current.getId(),
                before, response.toString()
        );
        return response;
    }

    @Transactional
    public List<GradeBandResponse> updateGradeBands(List<GradeBandRequest> requests) {
        GradeBandValidator.validate(requests);
        UUID organisationId = session.organisationId();
        String before = gradeBands.findAllByOrganisationIdOrderBySortOrder(organisationId)
                .stream().map(GradeBandResponse::from).toList().toString();
        gradeBands.deleteAllByOrganisationId(organisationId);
        gradeBands.flush();
        List<GradeBandRequest> ordered = requests.stream()
                .sorted(Comparator.comparing(
                        GradeBandRequest::maxPercentage,
                        Comparator.reverseOrder()
                ))
                .toList();
        List<GradeBand> saved = java.util.stream.IntStream.range(0, ordered.size())
                .mapToObj(index -> {
                    GradeBandRequest request = ordered.get(index);
                    return new GradeBand(
                            organisationId,
                            request.code().trim().toUpperCase(Locale.ROOT),
                            request.label().trim(),
                            request.minPercentage(),
                            request.maxPercentage(),
                            index + 1
                    );
                })
                .map(gradeBands::save)
                .toList();
        List<GradeBandResponse> response = saved.stream()
                .map(GradeBandResponse::from)
                .toList();
        audit.record(
                "SET", "UPDATE_GRADES", "OrganisationSettings",
                findSettings(organisationId).getId(), before, response.toString()
        );
        return response;
    }

    @Transactional
    public SubjectResponse createSubject(String code, String name) {
        UUID organisationId = session.organisationId();
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (subjects.existsByOrganisationIdAndCodeIgnoreCase(
                organisationId, normalizedCode
        )) {
            throw DomainException.badRequest(
                    "SUBJECT_CODE_EXISTS",
                    "Subject code already exists in this organisation."
            );
        }
        AcademicSubject saved = subjects.save(new AcademicSubject(
                organisationId, normalizedCode, name.trim()
        ));
        audit.record("SET", "CREATE_SUBJECT", "Subject", saved.getId(), null, saved.getCode());
        return SubjectResponse.from(saved);
    }

    @Transactional
    public SubjectResponse deactivateSubject(UUID id) {
        AcademicSubject subject = subjects.findByIdAndOrganisationId(
                        id, session.organisationId()
                )
                .orElseThrow(() -> DomainException.notFound(
                        "SUBJECT_NOT_FOUND", "Subject was not found."
                ));
        if (questions.existsByOrganisationIdAndSubjectId(
                session.organisationId(), id
        )) {
            throw DomainException.badRequest(
                    "SUBJECT_IN_USE",
                    "A subject with questions cannot be deactivated."
            );
        }
        subject.deactivate();
        audit.record("SET", "DEACTIVATE_SUBJECT", "Subject", id, "ACTIVE", "INACTIVE");
        return SubjectResponse.from(subject);
    }

    @Transactional
    public TopicResponse createTopic(UUID subjectId, String name) {
        UUID organisationId = session.organisationId();
        subjects.findByIdAndOrganisationId(subjectId, organisationId)
                .orElseThrow(() -> DomainException.notFound(
                        "SUBJECT_NOT_FOUND", "Subject was not found."
                ));
        if (topics.existsByOrganisationIdAndSubjectIdAndNameIgnoreCase(
                organisationId, subjectId, name.trim()
        )) {
            throw DomainException.badRequest(
                    "TOPIC_EXISTS", "Topic already exists for this subject."
            );
        }
        AcademicTopic saved = topics.save(new AcademicTopic(
                organisationId, subjectId, name.trim()
        ));
        audit.record("SET", "CREATE_TOPIC", "Topic", saved.getId(), null, saved.getName());
        return TopicResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public String resolveGrade(BigDecimal percentage) {
        return gradeBands.findAllByOrganisationIdOrderBySortOrder(
                        session.organisationId()
                ).stream()
                .filter(band -> band.contains(percentage))
                .findFirst()
                .map(GradeBand::getCode)
                .orElse("—");
    }

    @Transactional(readOnly = true)
    public BigDecimal passPercentage() {
        return findSettings(session.organisationId()).getPassPercentage();
    }

    private OrganisationSettings findSettings(UUID organisationId) {
        return settings.findByOrganisationId(organisationId)
                .orElseGet(() -> settings.save(new OrganisationSettings(organisationId)));
    }
}
