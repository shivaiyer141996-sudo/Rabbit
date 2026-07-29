package com.rabbit.aip.config;

import io.minio.MinioClient;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfrastructureConfig {

    @Bean
    MinioClient minioClient(
            @Value("${rabbit.minio.endpoint}") String endpoint,
            @Value("${rabbit.minio.access-key}") String accessKey,
            @Value("${rabbit.minio.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    Queue auditEventQueue() {
        return new Queue("rabbit.audit.events", true);
    }

    @Bean
    Queue notificationEventQueue() {
        return new Queue("rabbit.notification.events", true);
    }
}
