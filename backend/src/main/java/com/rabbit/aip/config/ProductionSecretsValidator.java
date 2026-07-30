package com.rabbit.aip.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class ProductionSecretsValidator implements ApplicationRunner {

    private final String jwtSecret;
    private final String databasePassword;
    private final String minioSecret;
    private final String rabbitPassword;
    private final String allowedOrigins;
    private final String environment;

    public ProductionSecretsValidator(
            @Value("${rabbit.jwt.secret}") String jwtSecret,
            @Value("${spring.datasource.password}") String databasePassword,
            @Value("${rabbit.minio.secret-key}") String minioSecret,
            @Value("${spring.rabbitmq.password}") String rabbitPassword,
            @Value("${rabbit.cors.allowed-origins}") String allowedOrigins,
            @Value("${rabbit.release.environment}") String environment
    ) {
        this.jwtSecret = jwtSecret;
        this.databasePassword = databasePassword;
        this.minioSecret = minioSecret;
        this.rabbitPassword = rabbitPassword;
        this.allowedOrigins = allowedOrigins;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireStrong("JWT_SECRET", jwtSecret, 48);
        requireStrong("DATABASE_PASSWORD", databasePassword, 16);
        requireStrong("MINIO_SECRET_KEY", minioSecret, 16);
        requireStrong("RABBITMQ_PASSWORD", rabbitPassword, 16);
        if (allowedOrigins.contains("localhost") || allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "Production CORS origins must be explicit non-local HTTPS origins."
            );
        }
        if (environment.equalsIgnoreCase("local")) {
            throw new IllegalStateException(
                    "RABBIT_ENVIRONMENT must identify the production environment."
            );
        }
    }

    private void requireStrong(String name, String value, int minimumLength) {
        List<String> forbidden = List.of("rabbit_local", "change-me", "password");
        if (value == null
                || value.length() < minimumLength
                || forbidden.stream().anyMatch(
                        item -> value.toLowerCase().contains(item)
                )) {
            throw new IllegalStateException(name + " is not production-safe.");
        }
    }
}
