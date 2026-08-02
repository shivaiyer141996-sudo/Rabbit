package com.rabbit.aip.commercial;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialInvoiceRepository
        extends JpaRepository<CommercialInvoice, UUID> {
    List<CommercialInvoice> findAllByOrganisationIdOrderByIssuedAtDesc(UUID organisationId);
    Optional<CommercialInvoice> findByIdAndOrganisationId(UUID id, UUID organisationId);
    boolean existsByOrganisationIdAndInvoiceNumberIgnoreCase(
            UUID organisationId,
            String invoiceNumber
    );
}
