FROM gradle:8.14.3-jdk21-alpine AS builder

WORKDIR /workspace

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY config ./config
COPY src ./src

RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
