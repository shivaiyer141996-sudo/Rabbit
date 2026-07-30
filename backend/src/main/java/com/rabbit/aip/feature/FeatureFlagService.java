package com.rabbit.aip.feature;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.feature.FeatureFlagDtos.FeatureFlagResponse;
import com.rabbit.aip.security.CurrentSession;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {

    private final FeatureFlagRepository flags;
    private final CurrentSession session;
    private final AuditService audit;

    public FeatureFlagService(
            FeatureFlagRepository flags,
            CurrentSession session,
            AuditService audit
    ) {
        this.flags = flags;
        this.session = session;
        this.audit = audit;
    }

    @Transactional
    public List<FeatureFlagResponse> list() {
        ensureDefaults();
        return flags.findAllByOrganisationIdOrderByKeyAsc(session.organisationId())
                .stream()
                .map(flag -> FeatureFlagResponse.from(
                        flag,
                        activeFor(flag, session.userId())
                ))
                .toList();
    }

    @Transactional
    public FeatureFlagResponse update(
            FeatureFlagKey key,
            boolean enabled,
            int rolloutPercentage
    ) {
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw DomainException.badRequest(
                    "FEATURE_ROLLOUT_INVALID",
                    "Rollout percentage must be between 0 and 100."
            );
        }
        FeatureFlag flag = findOrCreate(key);
        String before = flag.isEnabled() + "@" + flag.getRolloutPercentage();
        flag.update(enabled, rolloutPercentage, session.userId());
        flags.save(flag);
        audit.record(
                "OPS",
                "UPDATE_FEATURE_FLAG",
                "FeatureFlag",
                flag.getId(),
                before,
                enabled + "@" + rolloutPercentage
        );
        return FeatureFlagResponse.from(flag, activeFor(flag, session.userId()));
    }

    @Transactional
    public void require(FeatureFlagKey key) {
        FeatureFlag flag = findOrCreate(key);
        if (!activeFor(flag, session.userId())) {
            throw DomainException.forbidden(
                    "FEATURE_DISABLED",
                    key.label() + " is not enabled for this organisation."
            );
        }
    }

    @Transactional(readOnly = true)
    public boolean active(FeatureFlagKey key) {
        return flags.findByOrganisationIdAndKey(session.organisationId(), key)
                .map(flag -> activeFor(flag, session.userId()))
                .orElse(key.enabledByDefault());
    }

    boolean activeFor(FeatureFlag flag, UUID userId) {
        if (!flag.isEnabled() || flag.getRolloutPercentage() == 0) return false;
        if (flag.getRolloutPercentage() == 100) return true;
        int bucket = rolloutBucket(
                flag.getOrganisationId(), userId, flag.getKey()
        );
        return bucket < flag.getRolloutPercentage();
    }

    static int rolloutBucket(
            UUID organisationId,
            UUID userId,
            FeatureFlagKey key
    ) {
        String value = organisationId + ":" + userId + ":" + key.name();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            int hash = ByteBuffer.wrap(Arrays.copyOf(digest, 4)).getInt();
            return Math.floorMod(hash, 100);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void ensureDefaults() {
        for (FeatureFlagKey key : FeatureFlagKey.values()) {
            findOrCreate(key);
        }
    }

    private FeatureFlag findOrCreate(FeatureFlagKey key) {
        return flags.findByOrganisationIdAndKey(session.organisationId(), key)
                .orElseGet(() -> flags.save(
                        new FeatureFlag(session.organisationId(), key)
                ));
    }
}
