package com.rabbit.aip.auth;

import com.rabbit.aip.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {
    }

    public RefreshToken(
            String tokenHash,
            UUID userId,
            UUID organisationId,
            UUID membershipId,
            Instant expiresAt
    ) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.organisationId = organisationId;
        this.membershipId = membershipId;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrganisationId() {
        return organisationId;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        revokedAt = Instant.now();
    }
}
