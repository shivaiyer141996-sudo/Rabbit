package com.rabbit.aip.pilot;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pilot_check_results")
public class PilotCheckResult extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "check_key", nullable = false, length = 80)
    private PilotCheckKey key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PilotCheckStatus status;

    @Column(name = "tester_name", length = 150)
    private String testerName;

    @Column(name = "evidence_url", length = 1000)
    private String evidenceUrl;

    @Column(name = "defect_id", length = 100)
    private String defectId;

    @Column(length = 2000)
    private String notes;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected PilotCheckResult() {
    }

    public PilotCheckResult(UUID organisationId, PilotCheckKey key) {
        super(organisationId);
        this.key = key;
        this.status = PilotCheckStatus.NOT_RUN;
    }

    public void update(
            PilotCheckStatus status,
            String testerName,
            String evidenceUrl,
            String defectId,
            String notes,
            Instant executedAt,
            UUID updatedBy
    ) {
        this.status = status;
        if (status == PilotCheckStatus.NOT_RUN) {
            this.testerName = null;
            this.evidenceUrl = null;
            this.defectId = null;
            this.notes = null;
            this.executedAt = null;
            this.updatedBy = updatedBy;
            return;
        }
        this.testerName = normalized(testerName);
        this.evidenceUrl = normalized(evidenceUrl);
        this.defectId = normalized(defectId);
        this.notes = normalized(notes);
        this.executedAt = executedAt;
        this.updatedBy = updatedBy;
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public PilotCheckKey getKey() { return key; }
    public PilotCheckStatus getStatus() { return status; }
    public String getTesterName() { return testerName; }
    public String getEvidenceUrl() { return evidenceUrl; }
    public String getDefectId() { return defectId; }
    public String getNotes() { return notes; }
    public Instant getExecutedAt() { return executedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
}
