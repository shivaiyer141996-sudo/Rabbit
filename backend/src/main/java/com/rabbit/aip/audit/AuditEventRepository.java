package com.rabbit.aip.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findAllByOrganisationIdOrderByCreatedAtDesc(UUID organisationId);
    List<AuditEvent> findAllByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID organisationId,
            String entityType,
            UUID entityId
    );
}
