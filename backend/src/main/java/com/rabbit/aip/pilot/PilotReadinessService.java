package com.rabbit.aip.pilot;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.pilot.PilotDtos.PilotCheckResponse;
import com.rabbit.aip.pilot.PilotDtos.PilotDecisionRequest;
import com.rabbit.aip.pilot.PilotDtos.PilotDecisionResponse;
import com.rabbit.aip.pilot.PilotDtos.PilotReadinessResponse;
import com.rabbit.aip.pilot.PilotDtos.PilotSignOffResponse;
import com.rabbit.aip.pilot.PilotDtos.UpdatePilotCheckRequest;
import com.rabbit.aip.security.CurrentSession;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PilotReadinessService {

    private static final Pattern LOCAL_EVIDENCE_REFERENCE = Pattern.compile(
            "rabbit-evidence:[A-Za-z0-9][A-Za-z0-9._:-]{7,900}"
    );
    private static final Pattern RELEASE_COMMIT = Pattern.compile(
            "(?i)^[0-9a-f]{7,40}$"
    );
    private static final Pattern SHA256 = Pattern.compile("(?i)^[0-9a-f]{64}$");

    private final PilotCheckResultRepository checks;
    private final PilotSignOffRepository signOffs;
    private final PilotReleaseDecisionRepository decisions;
    private final CurrentSession session;
    private final AuditService audit;

    public PilotReadinessService(
            PilotCheckResultRepository checks,
            PilotSignOffRepository signOffs,
            PilotReleaseDecisionRepository decisions,
            CurrentSession session,
            AuditService audit
    ) {
        this.checks = checks;
        this.signOffs = signOffs;
        this.decisions = decisions;
        this.session = session;
        this.audit = audit;
    }

    @Transactional
    public PilotReadinessResponse readiness() {
        ensureDefaults();
        return response();
    }

    @Transactional
    public PilotReadinessResponse update(
            PilotCheckKey key,
            UpdatePilotCheckRequest request
    ) {
        ensureDefaults();
        if (signOffs.findByOrganisationId(session.organisationId()).isPresent()) {
            throw DomainException.badRequest(
                    "PILOT_ALREADY_SIGNED_OFF",
                    "Pilot evidence is locked after institutional sign-off."
            );
        }
        validateCheck(request);
        PilotCheckResult result = checks.findByOrganisationIdAndKey(
                        session.organisationId(),
                        key
                )
                .orElseThrow();
        PilotCheckStatus before = result.getStatus();
        result.update(
                request.status(),
                request.testerName(),
                request.evidenceUrl(),
                request.defectId(),
                request.notes(),
                request.executedAt() == null ? Instant.now() : request.executedAt(),
                session.userId()
        );
        checks.save(result);
        audit.record(
                "OPS",
                "UPDATE_PILOT_CHECK",
                "PilotCheckResult",
                result.getId(),
                key + ":" + before,
                key + ":" + request.status()
        );
        return response();
    }

    @Transactional
    public PilotReadinessResponse recordDecision(PilotDecisionRequest request) {
        ensureDefaults();
        if (signOffs.findByOrganisationId(session.organisationId()).isPresent()) {
            throw DomainException.badRequest(
                    "PILOT_ALREADY_SIGNED_OFF",
                    "A Go decision has already locked this pilot."
            );
        }
        List<PilotCheckResult> current = currentChecks();
        boolean mandatoryPassed = current.stream()
                .filter(item -> item.getKey().mandatory())
                .allMatch(item -> item.getStatus() == PilotCheckStatus.PASS);
        Instant decidedAt = Instant.now();
        validateDecision(request, mandatoryPassed, decidedAt);
        int passed = Math.toIntExact(count(current, PilotCheckStatus.PASS));
        int failed = Math.toIntExact(count(current, PilotCheckStatus.FAIL));
        int blocked = Math.toIntExact(count(current, PilotCheckStatus.BLOCKED));
        int notRun = Math.toIntExact(count(current, PilotCheckStatus.NOT_RUN));
        PilotReleaseDecision decision = decisions.save(new PilotReleaseDecision(
                session.organisationId(),
                request,
                mandatoryPassed,
                passed,
                failed,
                blocked,
                notRun,
                decidedAt,
                session.userId()
        ));
        audit.record(
                "OPS",
                "PILOT_RELEASE_DECISION",
                "PilotReleaseDecision",
                decision.getId(),
                null,
                decision.getOutcome() + ":" + decision.getReleaseVersion()
        );
        if (request.outcome() == PilotDecisionOutcome.GO) {
            PilotSignOff saved = signOffs.save(new PilotSignOff(
                    session.organisationId(),
                    request.releaseVersion(),
                    request.authorisedBy(),
                    request.authoriserTitle(),
                    request.supportContact(),
                    request.rollbackOwner(),
                    request.decisionReason(),
                    session.userId()
            ));
            audit.record(
                    "OPS",
                    "PILOT_SIGN_OFF",
                    "PilotSignOff",
                    saved.getId(),
                    null,
                    saved.getReleaseVersion() + ":" + saved.getAuthorisedBy()
            );
        }
        return response();
    }

    static void validateDecision(
            PilotDecisionRequest request,
            boolean mandatoryPassed,
            Instant now
    ) {
        if (request.outcome() == null) {
            throw DomainException.badRequest(
                    "PILOT_DECISION_REQUIRED",
                    "Select Go, Conditional Retest, or No-Go."
            );
        }
        if (blank(request.decisionReason())) {
            throw DomainException.badRequest(
                    "PILOT_DECISION_REASON_REQUIRED",
                    "Every final pilot decision requires a reason."
            );
        }
        if (blank(request.releaseCommit())
                || !RELEASE_COMMIT.matcher(request.releaseCommit().trim()).matches()) {
            throw DomainException.badRequest(
                    "PILOT_RELEASE_COMMIT_INVALID",
                    "Record the exact 7-40 character Git release commit."
            );
        }
        if (blank(request.evidenceSha256())
                || !SHA256.matcher(request.evidenceSha256().trim()).matches()) {
            throw DomainException.badRequest(
                    "PILOT_EVIDENCE_SHA_INVALID",
                    "Record the 64-character SHA-256 of the local handover evidence."
            );
        }
        requireLocalEvidenceReference(
                request.evidenceReference(),
                "PILOT_HANDOVER_EVIDENCE_INVALID",
                "The decision must reference a local Rabbit M5.5 evidence bundle."
        );
        if (request.knownIssueCount() > 0 && blank(request.knownIssuesReference())) {
            throw DomainException.badRequest(
                    "PILOT_KNOWN_ISSUES_EVIDENCE_REQUIRED",
                    "Known Severity 3/4 issues require a local evidence reference."
            );
        }
        if (!blank(request.knownIssuesReference())) {
            requireLocalEvidenceReference(
                    request.knownIssuesReference(),
                    "PILOT_KNOWN_ISSUES_EVIDENCE_INVALID",
                    "Known issues must reference local Rabbit evidence."
            );
        }
        if (!request.localDataConfirmed() || !request.localOnlyConfirmed()) {
            throw DomainException.badRequest(
                    "PILOT_LOCAL_DATA_CONFIRMATION_REQUIRED",
                    "Every decision must confirm local-only infrastructure and data media."
            );
        }
        if (request.outcome() == PilotDecisionOutcome.GO) {
            if (!mandatoryPassed) {
                throw DomainException.badRequest(
                        "PILOT_CHECKS_INCOMPLETE",
                        "Every mandatory pilot check must pass before a Go decision."
                );
            }
            if (!request.ownershipAccepted() || !request.scopeFreezeAccepted()) {
                throw DomainException.badRequest(
                        "PILOT_GO_ATTESTATION_REQUIRED",
                        "Go requires accepted operating ownership and Release 1.0 scope freeze."
                );
            }
            if (request.retestBy() != null) {
                throw DomainException.badRequest(
                        "PILOT_RETEST_NOT_APPLICABLE",
                        "A Go decision cannot include a retest deadline."
                );
            }
        }
        if (request.outcome() == PilotDecisionOutcome.CONDITIONAL_RETEST) {
            if (request.retestBy() == null || !request.retestBy().isAfter(now)) {
                throw DomainException.badRequest(
                        "PILOT_RETEST_DEADLINE_REQUIRED",
                        "Conditional Retest requires a future retest deadline."
                );
            }
        }
        if (request.outcome() == PilotDecisionOutcome.NO_GO
                && request.retestBy() != null) {
            throw DomainException.badRequest(
                    "PILOT_RETEST_NOT_APPLICABLE",
                    "No-Go does not carry an automatic retest deadline."
            );
        }
    }

    static void validateCheck(UpdatePilotCheckRequest request) {
        if (request.status() == PilotCheckStatus.NOT_RUN) return;
        if (request.testerName() == null || request.testerName().isBlank()) {
            throw DomainException.badRequest(
                    "PILOT_TESTER_REQUIRED",
                    "Record the tester for every executed pilot check."
            );
        }
        if (request.status() == PilotCheckStatus.PASS
                && (request.evidenceUrl() == null || request.evidenceUrl().isBlank())) {
            throw DomainException.badRequest(
                    "PILOT_EVIDENCE_REQUIRED",
                    "A passing pilot check requires an evidence link."
            );
        }
        if (!blank(request.evidenceUrl()) && !validEvidenceUrl(request.evidenceUrl())) {
            throw DomainException.badRequest(
                    "PILOT_EVIDENCE_URL_INVALID",
                    "Evidence must use an absolute HTTP/HTTPS URL or a local "
                            + "urn:rabbit-evidence reference."
            );
        }
        if ((request.status() == PilotCheckStatus.FAIL
                || request.status() == PilotCheckStatus.BLOCKED)
                && blank(request.defectId())
                && blank(request.notes())) {
            throw DomainException.badRequest(
                    "PILOT_BLOCKER_DETAIL_REQUIRED",
                    "A failed or blocked check requires a defect ID or notes."
            );
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean validEvidenceUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null) {
                return true;
            }
            return "urn".equalsIgnoreCase(uri.getScheme())
                    && uri.getSchemeSpecificPart() != null
                    && LOCAL_EVIDENCE_REFERENCE.matcher(
                            uri.getSchemeSpecificPart()
                    ).matches();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void requireLocalEvidenceReference(
            String value,
            String code,
            String message
    ) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"urn".equalsIgnoreCase(uri.getScheme())
                    || uri.getSchemeSpecificPart() == null
                    || !LOCAL_EVIDENCE_REFERENCE.matcher(
                            uri.getSchemeSpecificPart()
                    ).matches()) {
                throw DomainException.badRequest(code, message);
            }
        } catch (IllegalArgumentException exception) {
            throw DomainException.badRequest(code, message);
        }
    }

    private void ensureDefaults() {
        Arrays.stream(PilotCheckKey.values()).forEach(key ->
                checks.findByOrganisationIdAndKey(session.organisationId(), key)
                        .orElseGet(() -> checks.save(
                                new PilotCheckResult(session.organisationId(), key)
                        ))
        );
    }

    private List<PilotCheckResult> currentChecks() {
        return checks.findAllByOrganisationIdOrderByKeyAsc(session.organisationId());
    }

    private PilotReadinessResponse response() {
        List<PilotCheckResult> current = currentChecks();
        long passed = count(current, PilotCheckStatus.PASS);
        long failed = count(current, PilotCheckStatus.FAIL);
        long blocked = count(current, PilotCheckStatus.BLOCKED);
        long notRun = count(current, PilotCheckStatus.NOT_RUN);
        boolean mandatoryPassed = current.stream()
                .filter(item -> item.getKey().mandatory())
                .allMatch(item -> item.getStatus() == PilotCheckStatus.PASS);
        PilotSignOffResponse signOff = signOffs
                .findByOrganisationId(session.organisationId())
                .map(PilotSignOffResponse::from)
                .orElse(null);
        List<PilotDecisionResponse> decisionHistory = decisions
                .findAllByOrganisationIdOrderByDecidedAtDesc(session.organisationId())
                .stream()
                .map(PilotDecisionResponse::from)
                .toList();
        return new PilotReadinessResponse(
                current.size(),
                Math.toIntExact(passed),
                Math.toIntExact(failed),
                Math.toIntExact(blocked),
                Math.toIntExact(notRun),
                mandatoryPassed,
                signOff != null,
                current.stream().map(PilotCheckResponse::from).toList(),
                signOff,
                decisionHistory.isEmpty() ? null : decisionHistory.get(0),
                decisionHistory
        );
    }

    private long count(List<PilotCheckResult> values, PilotCheckStatus status) {
        return values.stream().filter(item -> item.getStatus() == status).count();
    }
}
