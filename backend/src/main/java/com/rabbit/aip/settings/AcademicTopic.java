package com.rabbit.aip.settings;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "topics")
public class AcademicTopic extends TenantEntity {

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected AcademicTopic() {
    }

    public AcademicTopic(UUID organisationId, UUID subjectId, String name) {
        super(organisationId);
        this.subjectId = subjectId;
        this.name = name;
        this.active = true;
    }

    public UUID getSubjectId() { return subjectId; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
