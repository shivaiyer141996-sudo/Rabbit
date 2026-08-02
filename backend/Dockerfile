FROM maven:3.9.11-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline
COPY src ./src
RUN mvn --batch-mode package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ARG RABBIT_RELEASE_VERSION=local
ARG RABBIT_RELEASE_COMMIT=unversioned
LABEL org.opencontainers.image.title="Rabbit AiP API" \
      org.opencontainers.image.version="${RABBIT_RELEASE_VERSION}" \
      org.opencontainers.image.revision="${RABBIT_RELEASE_COMMIT}"
RUN addgroup -S rabbit && adduser -S rabbit -G rabbit
COPY --from=builder /workspace/target/rabbit-aip-api.jar app.jar
USER rabbit
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
