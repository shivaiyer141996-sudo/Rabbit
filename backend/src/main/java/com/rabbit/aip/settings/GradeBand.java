package com.rabbit.aip.settings;

import com.rabbit.aip.common.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "grade_bands")
public class GradeBand extends TenantEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(name = "min_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercentage;

    @Column(name = "max_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPercentage;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected GradeBand() {
    }

    public GradeBand(
            UUID organisationId,
            String code,
            String label,
            BigDecimal minPercentage,
            BigDecimal maxPercentage,
            int sortOrder
    ) {
        super(organisationId);
        this.code = code;
        this.label = label;
        this.minPercentage = minPercentage;
        this.maxPercentage = maxPercentage;
        this.sortOrder = sortOrder;
    }

    public boolean contains(BigDecimal percentage) {
        return percentage.compareTo(minPercentage) >= 0
                && percentage.compareTo(maxPercentage) <= 0;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
    public BigDecimal getMinPercentage() { return minPercentage; }
    public BigDecimal getMaxPercentage() { return maxPercentage; }
    public int getSortOrder() { return sortOrder; }
}
