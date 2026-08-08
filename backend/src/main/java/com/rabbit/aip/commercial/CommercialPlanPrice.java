package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import com.rabbit.aip.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "commercial_plan_prices")
public class CommercialPlanPrice extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, length = 20)
    private PlanCode plan;
    @Column(name = "student_limit", nullable = false)
    private int studentLimit;
    @Column(name = "monthly_price_paise", nullable = false)
    private long monthlyPricePaise;
    @Column(nullable = false)
    private boolean active;

    protected CommercialPlanPrice() {
    }

    public PlanCode getPlan() { return plan; }
    public int getStudentLimit() { return studentLimit; }
    public long getMonthlyPricePaise() { return monthlyPricePaise; }
    public boolean isActive() { return active; }
}
