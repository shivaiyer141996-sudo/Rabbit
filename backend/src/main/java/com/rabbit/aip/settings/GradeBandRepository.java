package com.rabbit.aip.settings;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeBandRepository extends JpaRepository<GradeBand, UUID> {
    List<GradeBand> findAllByOrganisationIdOrderBySortOrder(UUID organisationId);
    void deleteAllByOrganisationId(UUID organisationId);
}
