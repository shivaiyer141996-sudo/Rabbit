package com.rabbit.aip.platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    Optional<CustomerAccount> findByIdAndStatusNot(UUID id, CustomerAccountStatus status);
    List<CustomerAccount> findAllByOrderByNameAsc();
    long countByStatus(CustomerAccountStatus status);
}
