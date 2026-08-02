package com.rabbit.aip.commercial;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialPaymentRepository
        extends JpaRepository<CommercialPayment, UUID> {
    List<CommercialPayment> findAllByOrganisationIdOrderByPaidAtDesc(UUID organisationId);
    boolean existsByOrganisationIdAndPaymentReferenceIgnoreCase(
            UUID organisationId,
            String paymentReference
    );
}
