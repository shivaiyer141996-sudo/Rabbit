package com.rabbit.aip.settings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicSubjectRepository extends JpaRepository<AcademicSubject, UUID> {
    List<AcademicSubject> findAllByOrganisationIdOrderByName(UUID organisationId);
    Optional<AcademicSubject> findByIdAndOrganisationId(UUID id, UUID organisationId);
    boolean existsByOrganisationIdAndCodeIgnoreCase(UUID organisationId, String code);
}
