package com.rabbit.aip.feature;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {
    List<FeatureFlag> findAllByOrganisationIdOrderByKeyAsc(UUID organisationId);
    Optional<FeatureFlag> findByOrganisationIdAndKey(
            UUID organisationId,
            FeatureFlagKey key
    );
}
