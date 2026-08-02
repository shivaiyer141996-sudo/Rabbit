package com.rabbit.aip.pilot;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.pilot.PilotDtos.PilotCheckResponse;
import com.rabbit.aip.pilot.PilotDtos.PilotReadinessResponse;
import com.rabbit.aip.pilot.PilotDtos.PilotSignOffRequest;
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

    private final PilotCheckResultRepository checks;
    private final PilotSignOffRepository signOffs;
    private final CurrentSession session;
    private final AuditService audit;

    public PilotReadinessService(
            PilotCheckResultRepository checks,
            PilotSignOffRepository signOffs,
            CurrentSession session,
            AuditService audit
    ) {
        this.checks = checks;
        this.signOffs = signOffs;
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
    public PilotReadinessResponse signOff(PilotSignOffRequest request) {
        ensureDefaults();
        if (signOffs.findByOrganisationId(session.organisationId()).isPresent()) {
            throw DomainException.badRequest(
                    "PILOT_ALREADY_SIGNED_OFF",
                    "This pilot has already been signed off."
            );
        }
        List<PilotCheckResult> current = currentChecks();
        boolean mandatoryPassed = current.stream()
                .filter(item -> item.getKey().mandatory())
                .allMatch(item -> item.getStatus() == PilotCheckStatus.PASS);
        if (!mandatoryPassed) {
            throw DomainException.badRequest(
                    "PILOT_CHECKS_INCOMPLETE",
                    "Every mandatory pilot check must pass before sign-off."
            );
        }
        PilotSignOff saved = signOffs.save(new PilotSignOff(
                session.organisationId(),
                request.releaseVersion(),
                request.authorisedBy(),
                request.authoriserTitle(),
                request.supportContact(),
                request.rollbackOwner(),
                request.notes(),
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
        return response();
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
        return new PilotReadinessResponse(
                current.size(),
                Math.toIntExact(passed),
                Math.toIntExact(failed),
                Math.toIntExact(blocked),
                Math.toIntExact(notRun),
                mandatoryPassed,
                signOff != null,
                current.stream().map(PilotCheckResponse::from).toList(),
                signOff
        );
    }

    private long count(List<PilotCheckResult> values, PilotCheckStatus status) {
        return values.stream().filter(item -> item.getStatus() == status).count();
    }
}
