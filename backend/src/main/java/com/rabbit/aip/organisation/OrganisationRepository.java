package com.rabbit.aip.organisation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {
    Optional<Organisation> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
}
