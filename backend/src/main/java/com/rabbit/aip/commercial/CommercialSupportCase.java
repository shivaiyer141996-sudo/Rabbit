package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.SupportCategory;
import com.rabbit.aip.commercial.CommercialTypes.SupportSeverity;
import com.rabbit.aip.commercial.CommercialTypes.SupportStatus;
import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "commercial_support_cases")
public class CommercialSupportCase extends TenantEntity {

    @Column(name = "case_number", nullable = false, length = 80)
    private String caseNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SupportSeverity severity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SupportCategory category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SupportStatus status;
    @Column(nullable = false, length = 200)
    private String summary;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;
    @Column(name = "assigned_to", length = 200)
    private String assignedTo;
    @Column(name = "response_due_at", nullable = false)
    private Instant responseDueAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(columnDefinition = "text")
    private String resolution;
    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;
    @Column(name = "updated_by_user_id", nullable = false)
    private UUID updatedByUserId;

    protected CommercialSupportCase() {
    }

    public CommercialSupportCase(
            UUID organisationId,
            String caseNumber,
            SupportSeverity severity,
            SupportCategory category,
            String summary,
            String description,
            UUID requesterUserId,
            Instant responseDueAt,
            UUID actorUserId
    ) {
        super(organisationId);
        this.caseNumber = caseNumber;
        this.severity = severity;
        this.category = category;
        this.status = SupportStatus.OPEN;
        this.summary = summary.trim();
        this.description = description.trim();
        this.requesterUserId = requesterUserId;
        this.responseDueAt = responseDueAt;
        this.createdByUserId = actorUserId;
        this.updatedByUserId = actorUserId;
    }

    public void update(
            SupportStatus nextStatus,
            String nextAssignedTo,
            String nextResolution,
            UUID actorUserId,
            Instant now
    ) {
        assignedTo = nextAssignedTo == null || nextAssignedTo.isBlank()
                ? null
                : nextAssignedTo.trim();
        status = nextStatus;
        updatedByUserId = actorUserId;
        if (nextStatus == SupportStatus.RESOLVED || nextStatus == SupportStatus.CLOSED) {
            if (nextResolution == null || nextResolution.isBlank()) {
                throw new IllegalArgumentException("Resolved support cases require a resolution.");
            }
            resolution = nextResolution.trim();
            resolvedAt = now;
        } else {
            resolution = null;
            resolvedAt = null;
        }
    }

    public String getCaseNumber() { return caseNumber; }
    public SupportSeverity getSeverity() { return severity; }
    public SupportCategory getCategory() { return category; }
    public SupportStatus getStatus() { return status; }
    public String getSummary() { return summary; }
    public String getDescription() { return description; }
    public UUID getRequesterUserId() { return requesterUserId; }
    public String getAssignedTo() { return assignedTo; }
    public Instant getResponseDueAt() { return responseDueAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolution() { return resolution; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
}
