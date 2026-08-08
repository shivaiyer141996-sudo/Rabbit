package com.rabbit.aip.academic;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sections")
public class AcademicSection extends TenantEntity {

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SectionStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected AcademicSection() {
    }

    public AcademicSection(
            UUID organisationId,
            String name,
            UUID programmeId,
            UUID academicYearId,
            UUID batchId
    ) {
        super(organisationId);
        this.name = name;
        this.programmeId = programmeId;
        this.academicYearId = academicYearId;
        this.batchId = batchId;
        this.active = true;
        this.status = SectionStatus.ACTIVE;
    }

    public void update(String name, UUID programmeId, UUID academicYearId, UUID batchId) {
        this.name = name;
        this.programmeId = programmeId;
        this.academicYearId = academicYearId;
        this.batchId = batchId;
    }

    public void activate() {
        if (status == SectionStatus.ARCHIVED) return;
        active = true;
        status = SectionStatus.ACTIVE;
    }

    public void deactivate() {
        if (status == SectionStatus.ARCHIVED) return;
        active = false;
        status = SectionStatus.INACTIVE;
    }

    public void archive() {
        if (status == SectionStatus.ARCHIVED) return;
        active = false;
        status = SectionStatus.ARCHIVED;
        archivedAt = Instant.now();
    }

    public String getName() { return name; }
    public UUID getProgrammeId() { return programmeId; }
    public UUID getAcademicYearId() { return academicYearId; }
    public UUID getBatchId() { return batchId; }
    public boolean isActive() { return active; }
    public SectionStatus getStatus() { return status; }
    public Instant getArchivedAt() { return archivedAt; }
}
