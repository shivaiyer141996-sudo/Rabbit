package com.rabbit.aip.platform;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RabbitPlatformSettingsRepository
        extends JpaRepository<RabbitPlatformSettings, UUID> {
}
