package com.rabbit.aip.feature;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "feature_flags")
public class FeatureFlag extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_key", nullable = false, length = 60)
    private FeatureFlagKey key;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "rollout_percentage", nullable = false)
    private int rolloutPercentage;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected FeatureFlag() {
    }

    public FeatureFlag(UUID organisationId, FeatureFlagKey key) {
        super(organisationId);
        this.key = key;
        this.enabled = key.enabledByDefault();
        this.rolloutPercentage = key.enabledByDefault() ? 100 : 0;
        this.description = key.description();
    }

    public void update(boolean enabled, int rolloutPercentage, UUID updatedBy) {
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.updatedBy = updatedBy;
    }

    public FeatureFlagKey getKey() { return key; }
    public boolean isEnabled() { return enabled; }
    public int getRolloutPercentage() { return rolloutPercentage; }
    public String getDescription() { return description; }
    public UUID getUpdatedBy() { return updatedBy; }
}
