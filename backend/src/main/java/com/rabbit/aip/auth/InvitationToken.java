package com.rabbit.aip.auth;

import com.rabbit.aip.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invitation_tokens")
public class InvitationToken extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "membership_id", nullable = false, unique = true)
    private UUID membershipId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    protected InvitationToken() {
    }

    public InvitationToken(
            UUID organisationId,
            UUID userId,
            UUID membershipId,
            String tokenHash,
            Instant expiresAt,
            UUID createdByUserId
    ) {
        this.organisationId = organisationId;
        this.userId = userId;
        this.membershipId = membershipId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdByUserId = createdByUserId;
    }

    public UUID getOrganisationId() {
        return organisationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void reissue(
            String nextTokenHash,
            Instant nextExpiry,
            UUID nextCreatedByUserId
    ) {
        tokenHash = nextTokenHash;
        expiresAt = nextExpiry;
        consumedAt = null;
        createdByUserId = nextCreatedByUserId;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}
