package com.rabbit.aip.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerAccountRulesTest {
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333301");

    @Test
    void accountSupportsActivateSuspendAndArchiveWithoutHardDelete() {
        CustomerAccount account = new CustomerAccount("acme", "Acme Learning", ACTOR);
        assertThat(account.getCode()).isEqualTo("ACME");
        account.suspend(ACTOR);
        assertThat(account.getStatus()).isEqualTo(CustomerAccountStatus.SUSPENDED);
        account.activate(ACTOR);
        assertThat(account.getStatus()).isEqualTo(CustomerAccountStatus.ACTIVE);
        account.archive(ACTOR, Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(account.getArchivedAt()).isNotNull();
        assertThatThrownBy(() -> account.activate(ACTOR))
                .isInstanceOf(IllegalStateException.class);
    }
}
