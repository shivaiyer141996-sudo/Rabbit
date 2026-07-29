package com.rabbit.aip.user;

import com.rabbit.aip.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "organisation_memberships")
public class OrganisationMembership extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status;

    @Column(name = "section_id")
    private UUID sectionId;

    protected OrganisationMembership() {
    }

    public OrganisationMembership(
            UUID organisationId,
            UUID userId,
            UserRole role,
            AccountStatus status,
            UUID sectionId
    ) {
        this.organisationId = organisationId;
        this.userId = userId;
        this.role = role;
        this.status = status;
        this.sectionId = sectionId;
    }

    public UUID getOrganisationId() {
        return organisationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UserRole getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}
