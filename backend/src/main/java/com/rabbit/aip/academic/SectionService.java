package com.rabbit.aip.academic;

import com.rabbit.aip.academic.SectionDtos.SectionRequest;
import com.rabbit.aip.academic.SectionDtos.SectionResponse;
import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.security.CurrentSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionService {
    private final AcademicSectionRepository sections;
    private final JdbcTemplate jdbc;
    private final CurrentSession session;
    private final AuditService audit;

    public SectionService(
            AcademicSectionRepository sections,
            JdbcTemplate jdbc,
            CurrentSession session,
            AuditService audit
    ) {
        this.sections = sections;
        this.jdbc = jdbc;
        this.session = session;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SectionResponse> list() {
        return jdbc.query("""
                SELECT s.id, s.name, s.programme_id, p.name AS programme_name,
                       s.academic_year_id, y.name AS academic_year_name,
                       s.batch_id, b.name AS batch_name, s.status, s.archived_at,
                       count(DISTINCT m.user_id) FILTER (WHERE m.role = 'STUDENT'
                           AND m.status IN ('ACTIVE','INVITED')) AS student_count,
                       count(DISTINCT m.user_id) FILTER (WHERE m.role = 'FACULTY'
                           AND m.status IN ('ACTIVE','INVITED')) AS teacher_count,
                       count(DISTINCT aes.assessment_id) AS assessment_count
                FROM sections s
                JOIN academic_programmes p ON p.id = s.programme_id
                JOIN academic_years y ON y.id = s.academic_year_id
                JOIN academic_batches b ON b.id = s.batch_id
                LEFT JOIN organisation_memberships m
                  ON m.organisation_id = s.organisation_id AND m.section_id = s.id
                LEFT JOIN assessment_eligible_sections aes ON aes.section_id = s.id
                WHERE s.organisation_id = ?
                GROUP BY s.id, p.name, y.name, b.name
                ORDER BY CASE s.status WHEN 'ACTIVE' THEN 0 WHEN 'INACTIVE' THEN 1 ELSE 2 END,
                         p.name, b.name, s.name
                """, (result, row) -> response(result), session.organisationId());
    }

    @Transactional
    public SectionResponse create(SectionRequest request) {
        validateMasters(request);
        ensureUnique(null, request);
        try {
            AcademicSection saved = sections.saveAndFlush(new AcademicSection(
                    session.organisationId(), request.name().trim(), request.programmeId(),
                    request.academicYearId(), request.batchId()
            ));
            audit.record("ORG", "CREATE_SECTION", "Section", saved.getId(), null, saved.getName());
            return get(saved.getId());
        } catch (DataIntegrityViolationException conflict) {
            throw duplicate();
        }
    }

    @Transactional
    public SectionResponse update(UUID id, SectionRequest request) {
        AcademicSection section = find(id);
        if (section.getStatus() == SectionStatus.ARCHIVED) {
            throw DomainException.badRequest("SECTION_ARCHIVED", "Archived sections cannot be edited.");
        }
        validateMasters(request);
        ensureUnique(id, request);
        String before = section.getName();
        section.update(request.name().trim(), request.programmeId(), request.academicYearId(), request.batchId());
        try {
            sections.flush();
        } catch (DataIntegrityViolationException conflict) {
            throw duplicate();
        }
        audit.record("ORG", "UPDATE_SECTION", "Section", id, before, section.getName());
        return get(id);
    }

    @Transactional
    public SectionResponse activate(UUID id) {
        AcademicSection section = find(id);
        if (section.getStatus() == SectionStatus.ARCHIVED) {
            throw DomainException.badRequest("SECTION_ARCHIVED", "Archived sections cannot be activated.");
        }
        section.activate();
        sections.flush();
        audit.record("ORG", "ACTIVATE_SECTION", "Section", id, null, "ACTIVE");
        return get(id);
    }

    @Transactional
    public SectionResponse deactivate(UUID id) {
        AcademicSection section = find(id);
        if (section.getStatus() == SectionStatus.ARCHIVED) {
            throw DomainException.badRequest("SECTION_ARCHIVED", "The section is already archived.");
        }
        section.deactivate();
        sections.flush();
        audit.record("ORG", "DEACTIVATE_SECTION", "Section", id, null, "INACTIVE");
        return get(id);
    }

    @Transactional
    public SectionResponse archive(UUID id) {
        AcademicSection section = find(id);
        section.archive();
        sections.flush();
        audit.record("ORG", "ARCHIVE_SECTION", "Section", id, null, "ARCHIVED");
        return get(id);
    }

    @Transactional(readOnly = true)
    public SectionResponse get(UUID id) {
        return list().stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> DomainException.notFound("SECTION_NOT_FOUND", "Section was not found."));
    }

    private AcademicSection find(UUID id) {
        return sections.findByIdAndOrganisationId(id, session.organisationId())
                .orElseThrow(() -> DomainException.notFound("SECTION_NOT_FOUND", "Section was not found."));
    }

    private void validateMasters(SectionRequest request) {
        Integer valid = jdbc.queryForObject("""
                SELECT count(*) FROM academic_programmes p
                JOIN academic_batches b ON b.programme_id = p.id
                JOIN academic_years y ON y.id = b.academic_year_id
                WHERE p.organisation_id = ? AND p.id = ? AND b.id = ?
                  AND y.organisation_id = p.organisation_id AND y.id = ?
                """, Integer.class, session.organisationId(), request.programmeId(),
                request.batchId(), request.academicYearId());
        if (valid == null || valid != 1) {
            throw DomainException.badRequest(
                    "SECTION_MASTER_INVALID",
                    "Programme, academic year, and batch must belong to this organisation and match."
            );
        }
    }

    private void ensureUnique(UUID id, SectionRequest request) {
        boolean exists = id == null
                ? sections.existsByOrganisationIdAndProgrammeIdAndBatchIdAndNameIgnoreCase(
                        session.organisationId(), request.programmeId(), request.batchId(), request.name().trim())
                : sections.existsByOrganisationIdAndProgrammeIdAndBatchIdAndNameIgnoreCaseAndIdNot(
                        session.organisationId(), request.programmeId(), request.batchId(), request.name().trim(), id);
        if (exists) throw duplicate();
    }

    private DomainException duplicate() {
        return DomainException.badRequest(
                "SECTION_NAME_EXISTS",
                "A section with this name already exists in the selected programme and batch."
        );
    }

    private SectionResponse response(ResultSet result) throws SQLException {
        return new SectionResponse(
                result.getObject("id", UUID.class), result.getString("name"),
                result.getObject("programme_id", UUID.class), result.getString("programme_name"),
                result.getObject("academic_year_id", UUID.class), result.getString("academic_year_name"),
                result.getObject("batch_id", UUID.class), result.getString("batch_name"),
                result.getLong("student_count"), result.getLong("teacher_count"),
                result.getLong("assessment_count"), SectionStatus.valueOf(result.getString("status")),
                result.getTimestamp("archived_at") == null ? null : result.getTimestamp("archived_at").toInstant()
        );
    }
}
