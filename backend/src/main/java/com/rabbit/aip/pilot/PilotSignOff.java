package com.rabbit.aip.pilot;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pilot_sign_offs")
public class PilotSignOff extends TenantEntity {

    @Column(name = "release_version", nullable = false, length = 50)
    private String releaseVersion;

    @Column(name = "authorised_by", nullable = false, length = 150)
    private String authorisedBy;

    @Column(name = "authoriser_title", nullable = false, length = 150)
    private String authoriserTitle;

    @Column(name = "support_contact", nullable = false, length = 200)
    private String supportContact;

    @Column(name = "rollback_owner", nullable = false, length = 150)
    private String rollbackOwner;

    @Column(length = 2000)
    private String notes;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column(name = "signed_by_user_id", nullable = false)
    private UUID signedByUserId;

    protected PilotSignOff() {
    }

    public PilotSignOff(
            UUID organisationId,
            String releaseVersion,
            String authorisedBy,
            String authoriserTitle,
            String supportContact,
            String rollbackOwner,
            String notes,
            UUID signedByUserId
    ) {
        super(organisationId);
        this.releaseVersion = releaseVersion.trim();
        this.authorisedBy = authorisedBy.trim();
        this.authoriserTitle = authoriserTitle.trim();
        this.supportContact = supportContact.trim();
        this.rollbackOwner = rollbackOwner.trim();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.signedAt = Instant.now();
        this.signedByUserId = signedByUserId;
    }

    public String getReleaseVersion() { return releaseVersion; }
    public String getAuthorisedBy() { return authorisedBy; }
    public String getAuthoriserTitle() { return authoriserTitle; }
    public String getSupportContact() { return supportContact; }
    public String getRollbackOwner() { return rollbackOwner; }
    public String getNotes() { return notes; }
    public Instant getSignedAt() { return signedAt; }
    public UUID getSignedByUserId() { return signedByUserId; }
}
