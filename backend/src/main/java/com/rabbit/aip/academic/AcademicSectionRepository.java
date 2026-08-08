package com.rabbit.aip.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicSectionRepository extends JpaRepository<AcademicSection, UUID> {
    List<AcademicSection> findAllByOrganisationIdOrderByName(UUID organisationId);
    Optional<AcademicSection> findByIdAndOrganisationId(UUID id, UUID organisationId);
    boolean existsByOrganisationIdAndProgrammeIdAndBatchIdAndNameIgnoreCaseAndIdNot(
            UUID organisationId, UUID programmeId, UUID batchId, String name, UUID id
    );
    boolean existsByOrganisationIdAndProgrammeIdAndBatchIdAndNameIgnoreCase(
            UUID organisationId, UUID programmeId, UUID batchId, String name
    );
}
