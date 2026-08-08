package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "commercial_plan_entitlements")
@IdClass(CommercialPlanEntitlement.Key.class)
public class CommercialPlanEntitlement {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", length = 20)
    private PlanCode plan;
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "feature_code", length = 60)
    private Entitlement entitlement;

    protected CommercialPlanEntitlement() {
    }

    public PlanCode getPlan() { return plan; }
    public Entitlement getEntitlement() { return entitlement; }

    public record Key(PlanCode plan, Entitlement entitlement) implements Serializable {
    }
}
