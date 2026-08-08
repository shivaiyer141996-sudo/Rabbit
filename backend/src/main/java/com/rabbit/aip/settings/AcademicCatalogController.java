package com.rabbit.aip.settings;

import com.rabbit.aip.security.CurrentSession;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/academic-catalog")
@PreAuthorize(
        "hasAnyRole('SUPER_ADMIN','ORG_ADMIN','ACADEMIC_HEAD','FACULTY','REVIEWER')"
)
public class AcademicCatalogController {

    private final AcademicSubjectRepository subjects;
    private final AcademicTopicRepository topics;
    private final JdbcTemplate jdbc;
    private final CurrentSession session;

    public AcademicCatalogController(
            AcademicSubjectRepository subjects,
            AcademicTopicRepository topics,
            JdbcTemplate jdbc,
            CurrentSession session
    ) {
        this.subjects = subjects;
        this.topics = topics;
        this.jdbc = jdbc;
        this.session = session;
    }

    @GetMapping
    CatalogResponse catalog() {
        UUID organisationId = session.organisationId();
        List<AcademicYearResponse> academicYears = jdbc.query(
                """
                SELECT id, name, start_date, end_date, active
                FROM academic_years
                WHERE organisation_id = ?
                ORDER BY start_date DESC
                """,
                (result, row) -> new AcademicYearResponse(
                        result.getObject("id", UUID.class),
                        result.getString("name"),
                        result.getObject("start_date", LocalDate.class),
                        result.getObject("end_date", LocalDate.class),
                        result.getBoolean("active")
                ),
                organisationId
        );
        List<DepartmentResponse> departments = jdbc.query(
                """
                SELECT d.id, d.name, d.active, count(s.id) AS section_count
                FROM departments d
                LEFT JOIN sections s
                  ON s.department_id = d.id AND s.organisation_id = d.organisation_id
                WHERE d.organisation_id = ?
                GROUP BY d.id, d.name, d.active
                ORDER BY d.name
                """,
                (result, row) -> new DepartmentResponse(
                        result.getObject("id", UUID.class),
                        result.getString("name"),
                        result.getBoolean("active"),
                        result.getInt("section_count")
                ),
                organisationId
        );
        List<SectionResponse> sections = jdbc.query(
                """
                SELECT s.id, s.department_id, s.name, s.active, s.programme_id,
                       s.academic_year_id, s.batch_id, s.status,
                       coalesce(d.name, p.name) AS department_name,
                       p.name AS programme_name, y.name AS academic_year_name,
                       b.name AS batch_name
                FROM sections s
                LEFT JOIN departments d
                  ON d.id = s.department_id AND d.organisation_id = s.organisation_id
                JOIN academic_programmes p ON p.id = s.programme_id
                JOIN academic_years y ON y.id = s.academic_year_id
                JOIN academic_batches b ON b.id = s.batch_id
                WHERE s.organisation_id = ?
                ORDER BY p.name, b.name, s.name
                """,
                (result, row) -> new SectionResponse(
                        result.getObject("id", UUID.class),
                        result.getObject("department_id", UUID.class),
                        result.getString("department_name"),
                        result.getString("name"),
                        result.getBoolean("active"),
                        result.getObject("programme_id", UUID.class),
                        result.getString("programme_name"),
                        result.getObject("academic_year_id", UUID.class),
                        result.getString("academic_year_name"),
                        result.getObject("batch_id", UUID.class),
                        result.getString("batch_name"),
                        result.getString("status")
                ),
                organisationId
        );
        List<ProgrammeResponse> programmes = jdbc.query(
                """
                SELECT id, code, name, active FROM academic_programmes
                WHERE organisation_id = ? ORDER BY name
                """,
                (result, row) -> new ProgrammeResponse(
                        result.getObject("id", UUID.class), result.getString("code"),
                        result.getString("name"), result.getBoolean("active")
                ), organisationId
        );
        List<BatchResponse> batches = jdbc.query(
                """
                SELECT id, programme_id, academic_year_id, name, active
                FROM academic_batches WHERE organisation_id = ? ORDER BY name
                """,
                (result, row) -> new BatchResponse(
                        result.getObject("id", UUID.class),
                        result.getObject("programme_id", UUID.class),
                        result.getObject("academic_year_id", UUID.class),
                        result.getString("name"), result.getBoolean("active")
                ), organisationId
        );
        return new CatalogResponse(
                academicYears,
                departments,
                programmes,
                batches,
                sections,
                subjects.findAllByOrganisationIdOrderByName(organisationId).stream()
                        .map(item -> new SubjectResponse(
                                item.getId(),
                                item.getCode(),
                                item.getName(),
                                item.isActive()
                        ))
                        .toList(),
                topics.findAllByOrganisationIdOrderByName(organisationId).stream()
                        .map(item -> new TopicResponse(
                                item.getId(),
                                item.getSubjectId(),
                                item.getName(),
                                item.isActive()
                        ))
                        .toList()
        );
    }

    record AcademicYearResponse(
            UUID id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean active
    ) {
    }

    record DepartmentResponse(
            UUID id,
            String name,
            boolean active,
            int sectionCount
    ) {
    }

    record SectionResponse(
            UUID id,
            UUID departmentId,
            String departmentName,
            String name,
            boolean active,
            UUID programmeId,
            String programmeName,
            UUID academicYearId,
            String academicYearName,
            UUID batchId,
            String batchName,
            String status
    ) {
    }

    record ProgrammeResponse(UUID id, String code, String name, boolean active) {
    }

    record BatchResponse(
            UUID id, UUID programmeId, UUID academicYearId, String name, boolean active
    ) {
    }

    record SubjectResponse(UUID id, String code, String name, boolean active) {
    }

    record TopicResponse(
            UUID id,
            UUID subjectId,
            String name,
            boolean active
    ) {
    }

    record CatalogResponse(
            List<AcademicYearResponse> academicYears,
            List<DepartmentResponse> departments,
            List<ProgrammeResponse> programmes,
            List<BatchResponse> batches,
            List<SectionResponse> sections,
            List<SubjectResponse> subjects,
            List<TopicResponse> topics
    ) {
    }
}
