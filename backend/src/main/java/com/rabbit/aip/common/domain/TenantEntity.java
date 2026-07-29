package com.rabbit.aip.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @Column(name = "organisation_id", nullable = false, updatable = false)
    private UUID organisationId;

    protected TenantEntity() {
    }

    protected TenantEntity(UUID organisationId) {
        this.organisationId = organisationId;
    }

    public UUID getOrganisationId() {
        return organisationId;
    }
}
