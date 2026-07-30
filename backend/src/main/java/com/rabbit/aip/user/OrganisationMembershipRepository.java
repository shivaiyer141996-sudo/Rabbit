package com.rabbit.aip.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganisationMembershipRepository
        extends JpaRepository<OrganisationMembership, UUID> {

    List<OrganisationMembership> findAllByUserIdAndStatus(
            UUID userId,
            AccountStatus status
    );

    Optional<OrganisationMembership> findByUserIdAndOrganisationIdAndStatus(
            UUID userId,
            UUID organisationId,
            AccountStatus status
    );

    List<OrganisationMembership> findAllByOrganisationIdOrderByCreatedAtDesc(
            UUID organisationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select membership
            from OrganisationMembership membership
            where membership.id = :id
            """)
    Optional<OrganisationMembership> findByIdForUpdate(@Param("id") UUID id);
}
