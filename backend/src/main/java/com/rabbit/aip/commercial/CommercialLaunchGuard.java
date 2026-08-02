package com.rabbit.aip.commercial;

import com.rabbit.aip.common.exception.DomainException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CommercialLaunchGuard {

    private static final Pattern COMMIT = Pattern.compile("(?i)^[0-9a-f]{7,40}$");
    private static final Pattern FINAL_EVIDENCE = Pattern.compile(
            "^urn:rabbit-evidence:m5-6:final:[A-Za-z0-9][A-Za-z0-9._:-]{20,900}$"
    );

    private final boolean enabled;
    private final String releaseCommit;
    private final String finalEvidenceReference;

    public CommercialLaunchGuard(
            @Value("${rabbit.commercial.enabled:false}") boolean enabled,
            @Value("${rabbit.release.commit:unversioned}") String releaseCommit,
            @Value("${rabbit.commercial.m5-6-evidence-reference:}")
            String finalEvidenceReference
    ) {
        this.enabled = enabled;
        this.releaseCommit = releaseCommit == null ? "" : releaseCommit.trim();
        this.finalEvidenceReference = finalEvidenceReference == null
                ? ""
                : finalEvidenceReference.trim();
        if (enabled) validateActivation(this.releaseCommit, this.finalEvidenceReference);
    }

    public boolean enabled() {
        return enabled;
    }

    public String finalEvidenceReference() {
        return finalEvidenceReference;
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new DomainException(
                    "COMMERCIAL_CONTROLS_NOT_ACTIVATED",
                    "Commercial controls remain disabled until Milestone 5.6 has a verified local Go bundle and release tag.",
                    HttpStatus.LOCKED
            );
        }
    }

    static void validateActivation(String releaseCommit, String evidenceReference) {
        if (!COMMIT.matcher(releaseCommit).matches()) {
            throw new IllegalStateException(
                    "COMMERCIAL_CONTROLS_ENABLED requires an exact 7-40 character RABBIT_RELEASE_COMMIT."
            );
        }
        if (!FINAL_EVIDENCE.matcher(evidenceReference).matches()) {
            throw new IllegalStateException(
                    "COMMERCIAL_CONTROLS_ENABLED requires the final local M5.6 evidence reference."
            );
        }
    }
}
