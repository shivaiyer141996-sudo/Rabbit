package com.rabbit.aip.organisation;

import com.rabbit.aip.common.domain.BaseEntity;
import com.rabbit.aip.user.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organisations")
public class Organisation extends BaseEntity {

    @Column(name = "customer_account_id", nullable = false)
    private UUID customerAccountId;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 80)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status;

    @Column(name = "logo_object_key", length = 500)
    private String logoObjectKey;
    @Column(name = "logo_content_type", length = 50)
    private String logoContentType;
    @Column(name = "logo_file_name", length = 255)
    private String logoFileName;
    @Column(name = "logo_size_bytes")
    private Long logoSizeBytes;
    @Column(name = "logo_updated_at")
    private Instant logoUpdatedAt;

    protected Organisation() {
    }

    public Organisation(UUID customerAccountId, String code, String name, String timezone) {
        this.customerAccountId = customerAccountId;
        this.code = code.toUpperCase();
        this.name = name;
        this.timezone = timezone;
        this.status = AccountStatus.ACTIVE;
    }

    public Organisation(String code, String name, String timezone) {
        this(null, code, name, timezone);
    }

    public void update(String name, String timezone) {
        this.name = name.trim();
        this.timezone = timezone.trim();
    }

    public void assignCustomerAccount(UUID nextCustomerAccountId) {
        this.customerAccountId = nextCustomerAccountId;
    }

    public void activate() { status = AccountStatus.ACTIVE; }
    public void suspend() { status = AccountStatus.SUSPENDED; }
    public void archive() { status = AccountStatus.ARCHIVED; }

    public void setLogo(String objectKey, String contentType, String fileName,
                        long sizeBytes, Instant updatedAt) {
        logoObjectKey = objectKey;
        logoContentType = contentType;
        logoFileName = fileName;
        logoSizeBytes = sizeBytes;
        logoUpdatedAt = updatedAt;
    }

    public void removeLogo() {
        logoObjectKey = null;
        logoContentType = null;
        logoFileName = null;
        logoSizeBytes = null;
        logoUpdatedAt = null;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public UUID getCustomerAccountId() { return customerAccountId; }
    public String getLogoObjectKey() { return logoObjectKey; }
    public String getLogoContentType() { return logoContentType; }
    public String getLogoFileName() { return logoFileName; }
    public Long getLogoSizeBytes() { return logoSizeBytes; }
    public Instant getLogoUpdatedAt() { return logoUpdatedAt; }
    public boolean hasLogo() { return logoObjectKey != null; }
}
