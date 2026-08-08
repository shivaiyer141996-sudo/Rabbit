package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialDtos.PlanCatalogResponse;
import com.rabbit.aip.commercial.CommercialDtos.PricePoint;
import com.rabbit.aip.commercial.CommercialTypes.Entitlement;
import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import com.rabbit.aip.common.exception.DomainException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialCatalogService {
    private final CommercialPlanDefinitionRepository definitions;
    private final CommercialPlanPriceRepository prices;
    private final CommercialPlanEntitlementRepository entitlements;

    public CommercialCatalogService(
            CommercialPlanDefinitionRepository definitions,
            CommercialPlanPriceRepository prices,
            CommercialPlanEntitlementRepository entitlements
    ) {
        this.definitions = definitions;
        this.prices = prices;
        this.entitlements = entitlements;
    }

    @Transactional(readOnly = true)
    public List<PlanCatalogResponse> catalog() {
        return definitions.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(plan -> new PlanCatalogResponse(
                        plan.getCode(), plan.getLabel(), plan.getDescription(),
                        prices.findAllByPlanAndActiveTrueOrderByStudentLimitAsc(plan.getCode())
                                .stream().map(price -> new PricePoint(
                                        price.getStudentLimit(), price.getMonthlyPricePaise()
                                )).toList(),
                        entitlements(plan.getCode())
                )).toList();
    }

    @Transactional(readOnly = true)
    public Set<Entitlement> entitlements(PlanCode plan) {
        EnumSet<Entitlement> result = EnumSet.noneOf(Entitlement.class);
        entitlements.findAllByPlan(plan)
                .forEach(value -> result.add(value.getEntitlement()));
        return result;
    }

    @Transactional(readOnly = true)
    public CommercialPlanPrice requirePrice(PlanCode plan, int studentLimit) {
        return prices.findByPlanAndStudentLimitAndActiveTrue(plan, studentLimit)
                .orElseThrow(() -> DomainException.badRequest(
                        "PLAN_CAPACITY_INVALID",
                        "The selected plan and Student-capacity slab is not active."
                ));
    }

    @Transactional(readOnly = true)
    public int capacityFor(PlanCode plan, int declaredStudents) {
        return prices.findAllByPlanAndActiveTrueOrderByStudentLimitAsc(plan).stream()
                .map(CommercialPlanPrice::getStudentLimit)
                .filter(limit -> limit >= declaredStudents)
                .findFirst()
                .orElseThrow(() -> DomainException.badRequest(
                        "STUDENT_COUNT_INVALID",
                        "No active capacity slab covers the declared Student count."
                ));
    }
}
