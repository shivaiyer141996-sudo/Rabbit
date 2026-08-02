package com.rabbit.aip.commercial;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialReceiptRepository
        extends JpaRepository<CommercialReceipt, UUID> {
    List<CommercialReceipt> findAllByOrganisationIdOrderByIssuedAtDesc(UUID organisationId);
}
