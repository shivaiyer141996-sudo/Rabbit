package com.rabbit.aip.organisation;

import com.rabbit.aip.audit.AuditService;
import com.rabbit.aip.common.exception.DomainException;
import com.rabbit.aip.security.CurrentSession;
import com.rabbit.aip.user.UserRole;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OrganisationBrandingService {
    public static final long MAX_LOGO_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> ALLOWED = Set.of(
            "image/png", "image/jpeg", "image/webp"
    );

    private final MinioClient minio;
    private final String bucket;
    private final OrganisationRepository organisations;
    private final CurrentSession session;
    private final AuditService audit;
    private final Clock clock;

    public OrganisationBrandingService(
            MinioClient minio,
            @Value("${rabbit.minio.bucket}") String bucket,
            OrganisationRepository organisations,
            CurrentSession session,
            AuditService audit,
            Clock clock
    ) {
        this.minio = minio;
        this.bucket = bucket;
        this.organisations = organisations;
        this.session = session;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public LogoMetadata upload(UUID organisationId, MultipartFile file) {
        requireManageAccess(organisationId);
        if (file == null || file.isEmpty()) {
            throw DomainException.badRequest("LOGO_REQUIRED", "Choose a logo file to upload.");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw DomainException.badRequest("LOGO_TOO_LARGE", "Organisation logo must not exceed 2 MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Logo could not be read.", exception);
        }
        String detectedType = detectType(bytes);
        String suppliedType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(detectedType)
                || (!suppliedType.isBlank() && !ALLOWED.contains(suppliedType))) {
            throw DomainException.badRequest(
                    "LOGO_TYPE_INVALID", "Use a valid PNG, JPG, or WebP image."
            );
        }
        Organisation organisation = organisation(organisationId);
        String previousKey = organisation.getLogoObjectKey();
        String key = "organisation-branding/" + organisationId + "/"
                + UUID.randomUUID() + extension(detectedType);
        try {
            ensureBucket();
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(detectedType).build());
            organisation.setLogo(
                    key, detectedType, safeName(file.getOriginalFilename()),
                    bytes.length, clock.instant()
            );
            organisations.saveAndFlush(organisation);
            if (previousKey != null) removeObject(previousKey);
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            removeObject(key);
            throw new DomainException(
                    "LOGO_STORAGE_UNAVAILABLE",
                    "Organisation logo could not be stored in local object storage.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        audit.recordForOrganisation(
                organisationId, "BRANDING", previousKey == null ? "LOGO_ADDED" : "LOGO_CHANGED",
                "Organisation", organisationId, previousKey, key
        );
        return metadata(organisation);
    }

    @Transactional
    public void remove(UUID organisationId) {
        requireManageAccess(organisationId);
        Organisation organisation = organisation(organisationId);
        String previousKey = organisation.getLogoObjectKey();
        if (previousKey == null) return;
        removeObject(previousKey);
        organisation.removeLogo();
        audit.recordForOrganisation(
                organisationId, "BRANDING", "LOGO_REMOVED", "Organisation",
                organisationId, previousKey, null
        );
    }

    @Transactional(readOnly = true)
    public StoredLogo download(UUID organisationId) {
        requireReadAccess(organisationId);
        Organisation organisation = organisation(organisationId);
        if (!organisation.hasLogo()) {
            throw DomainException.notFound("ORGANISATION_LOGO_NOT_FOUND", "No Organisation logo is configured.");
        }
        return new StoredLogo(read(organisation), organisation.getLogoContentType(),
                organisation.getLogoFileName());
    }

    @Transactional(readOnly = true)
    public LogoMetadata currentMetadata() {
        return metadata(organisation(session.organisationId()));
    }

    @Transactional(readOnly = true)
    public String inlineLogo(Organisation organisation) {
        if (!organisation.hasLogo()) return null;
        try {
            byte[] bytes = read(organisation);
            return "data:" + organisation.getLogoContentType() + ";base64,"
                    + Base64.getEncoder().encodeToString(bytes);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private byte[] read(Organisation organisation) {
        try (var object = minio.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(organisation.getLogoObjectKey()).build())) {
            return object.readAllBytes();
        } catch (Exception exception) {
            throw new DomainException(
                    "LOGO_STORAGE_UNAVAILABLE",
                    "Organisation logo is temporarily unavailable.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private void ensureBucket() throws Exception {
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private void removeObject(String key) {
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ignored) {
            // Metadata remains authoritative; orphan cleanup can reconcile a failed delete.
        }
    }

    private void requireManageAccess(UUID organisationId) {
        if (session.role() == UserRole.SUPER_ADMIN) return;
        if (session.role() != UserRole.ORG_ADMIN || !session.organisationId().equals(organisationId)) {
            throw DomainException.forbidden("BRANDING_ACCESS_DENIED", "You cannot manage this Organisation logo.");
        }
    }

    private void requireReadAccess(UUID organisationId) {
        if (session.role() != UserRole.SUPER_ADMIN
                && !session.organisationId().equals(organisationId)) {
            throw DomainException.forbidden("BRANDING_ACCESS_DENIED", "You cannot access this Organisation logo.");
        }
    }

    private Organisation organisation(UUID organisationId) {
        return organisations.findById(organisationId).orElseThrow(() -> DomainException.notFound(
                "ORGANISATION_NOT_FOUND", "Organisation was not found."
        ));
    }

    private LogoMetadata metadata(Organisation value) {
        return new LogoMetadata(
                value.hasLogo(), value.getLogoContentType(), value.getLogoFileName(),
                value.getLogoSizeBytes(), value.getLogoUpdatedAt()
        );
    }

    static String detectType(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4E && bytes[3] == 0x47) return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return "application/octet-stream";
    }

    private String extension(String type) {
        return switch (type) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    private String safeName(String name) {
        String value = name == null ? "organisation-logo" : name.trim();
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.isBlank()) value = "organisation-logo";
        return value.substring(0, Math.min(255, value.length()));
    }

    public record LogoMetadata(
            boolean available, String contentType, String fileName,
            Long sizeBytes, java.time.Instant updatedAt
    ) {
    }

    public record StoredLogo(byte[] bytes, String contentType, String fileName) {
    }
}
