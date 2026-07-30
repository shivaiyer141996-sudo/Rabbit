package com.rabbit.aip.feature;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class FeatureFlagDtos {

    private FeatureFlagDtos() {
    }

    public record FeatureFlagResponse(
            FeatureFlagKey key,
            String label,
            String description,
            boolean enabled,
            int rolloutPercentage,
            boolean activeForCurrentUser
    ) {
        static FeatureFlagResponse from(
                FeatureFlag flag,
                boolean activeForCurrentUser
        ) {
            return new FeatureFlagResponse(
                    flag.getKey(),
                    flag.getKey().label(),
                    flag.getDescription(),
                    flag.isEnabled(),
                    flag.getRolloutPercentage(),
                    activeForCurrentUser
            );
        }
    }

    public record UpdateFeatureFlagRequest(
            boolean enabled,
            @Min(0) @Max(100) int rolloutPercentage
    ) {
    }
}
