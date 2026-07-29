package com.rabbit.aip.settings;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicTopicRepository extends JpaRepository<AcademicTopic, UUID> {
    List<AcademicTopic> findAllByOrganisationIdOrderByName(UUID organisationId);
    boolean existsByOrganisationIdAndSubjectIdAndNameIgnoreCase(
            UUID organisationId,
            UUID subjectId,
            String name
    );
}
