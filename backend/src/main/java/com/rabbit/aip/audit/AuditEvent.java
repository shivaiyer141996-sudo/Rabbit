package com.rabbit.aip.audit;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent extends TenantEntity {

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 50)
    private String module;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "before_value", columnDefinition = "text")
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "text")
    private String afterValue;

    @Column(name = "actor_email", length = 320)
    private String actorEmail;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    protected AuditEvent() {
    }

    public AuditEvent(
            UUID organisationId,
            UUID actorUserId,
            String module,
            String action,
            String entityType,
            UUID entityId,
            String beforeValue,
            String afterValue,
            String actorEmail,
            String actorRole,
            String ipAddress,
            String traceId
    ) {
        super(organisationId);
        this.actorUserId = actorUserId;
        this.module = module;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.status = "SUCCESS";
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.ipAddress = ipAddress;
        this.traceId = traceId;
    }

    public UUID getActorUserId() { return actorUserId; }
    public String getModule() { return module; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getStatus() { return status; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public String getActorEmail() { return actorEmail; }
    public String getActorRole() { return actorRole; }
    public String getIpAddress() { return ipAddress; }
    public String getTraceId() { return traceId; }
}
