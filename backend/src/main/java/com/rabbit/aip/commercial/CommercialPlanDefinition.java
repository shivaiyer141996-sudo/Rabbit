package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "commercial_plan_definitions")
public class CommercialPlanDefinition {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PlanCode code;
    @Column(nullable = false, length = 80)
    private String label;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(nullable = false)
    private boolean active;

    protected CommercialPlanDefinition() {
    }

    public PlanCode getCode() { return code; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}
