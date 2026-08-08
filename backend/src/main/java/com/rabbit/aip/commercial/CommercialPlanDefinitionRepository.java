package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPlanDefinitionRepository
        extends JpaRepository<CommercialPlanDefinition, PlanCode> {
    List<CommercialPlanDefinition> findAllByActiveTrueOrderByDisplayOrderAsc();
}
