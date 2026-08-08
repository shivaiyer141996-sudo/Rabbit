package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPlanEntitlementRepository extends JpaRepository<
        CommercialPlanEntitlement, CommercialPlanEntitlement.Key> {
    List<CommercialPlanEntitlement> findAllByPlan(PlanCode plan);
}
