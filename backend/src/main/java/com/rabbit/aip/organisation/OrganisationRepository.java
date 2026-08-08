package com.rabbit.aip.organisation;

import com.rabbit.aip.user.AccountStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {
    Optional<Organisation> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Organisation> findAllByCustomerAccountIdOrderByNameAsc(UUID customerAccountId);
    long countByCustomerAccountId(UUID customerAccountId);
    long countByStatus(AccountStatus status);
}
