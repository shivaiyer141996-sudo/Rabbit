package com.rabbit.aip.feature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeatureFlagServiceTest {

    @Test
    void rolloutBucketIsStableAndPercentageBoundaryIsExact() {
        UUID organisationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333301");
        int bucket = FeatureFlagService.rolloutBucket(
                organisationId, userId, FeatureFlagKey.PDF_EXPORTS
        );
        FeatureFlag flag = new FeatureFlag(
                organisationId, FeatureFlagKey.PDF_EXPORTS
        );
        FeatureFlagService service = new FeatureFlagService(null, null, null);

        flag.update(true, bucket, userId);
        assertThat(service.activeFor(flag, userId)).isFalse();

        flag.update(true, Math.min(100, bucket + 1), userId);
        assertThat(service.activeFor(flag, userId)).isTrue();
        assertThat(FeatureFlagService.rolloutBucket(
                organisationId, userId, FeatureFlagKey.PDF_EXPORTS
        )).isEqualTo(bucket);
    }
}
