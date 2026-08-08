package com.rabbit.aip.platform;

import com.rabbit.aip.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_accounts")
public class CustomerAccount extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerAccountStatus status;
    @Column(name = "archived_at")
    private Instant archivedAt;
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    protected CustomerAccount() {
    }

    public CustomerAccount(String code, String name, UUID actorUserId) {
        this.code = normalizedCode(code);
        this.name = name.trim();
        this.status = CustomerAccountStatus.ACTIVE;
        this.createdByUserId = actorUserId;
        this.updatedByUserId = actorUserId;
    }

    public void update(String code, String name, UUID actorUserId) {
        requireNotArchived();
        this.code = normalizedCode(code);
        this.name = name.trim();
        this.updatedByUserId = actorUserId;
    }

    public void activate(UUID actorUserId) {
        requireNotArchived();
        status = CustomerAccountStatus.ACTIVE;
        updatedByUserId = actorUserId;
    }

    public void suspend(UUID actorUserId) {
        requireNotArchived();
        status = CustomerAccountStatus.SUSPENDED;
        updatedByUserId = actorUserId;
    }

    public void archive(UUID actorUserId, Instant now) {
        status = CustomerAccountStatus.ARCHIVED;
        archivedAt = now;
        updatedByUserId = actorUserId;
    }

    private void requireNotArchived() {
        if (status == CustomerAccountStatus.ARCHIVED) {
            throw new IllegalStateException("Archived Customer Accounts cannot be changed or reactivated.");
        }
    }

    private static String normalizedCode(String value) {
        return value.trim().toUpperCase();
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public CustomerAccountStatus getStatus() { return status; }
    public Instant getArchivedAt() { return archivedAt; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
}
