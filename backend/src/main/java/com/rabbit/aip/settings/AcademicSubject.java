package com.rabbit.aip.settings;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "subjects")
public class AcademicSubject extends TenantEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected AcademicSubject() {
    }

    public AcademicSubject(UUID organisationId, String code, String name) {
        super(organisationId);
        this.code = code;
        this.name = name;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
