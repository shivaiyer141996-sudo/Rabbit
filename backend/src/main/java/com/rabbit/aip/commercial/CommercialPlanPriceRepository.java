package com.rabbit.aip.commercial;

import com.rabbit.aip.commercial.CommercialTypes.PlanCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPlanPriceRepository extends JpaRepository<CommercialPlanPrice, UUID> {
    List<CommercialPlanPrice> findAllByActiveTrueOrderByStudentLimitAsc();
    List<CommercialPlanPrice> findAllByPlanAndActiveTrueOrderByStudentLimitAsc(PlanCode plan);
    Optional<CommercialPlanPrice> findByPlanAndStudentLimitAndActiveTrue(PlanCode plan, int studentLimit);
}
