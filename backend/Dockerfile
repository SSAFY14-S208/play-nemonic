# ============================================================
# Spring Boot 애플리케이션 Dockerfile
# Multi-stage build: 빌드 -> 경량 런타임 이미지
# ============================================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Gradle 캐시 활용을 위해 의존성 파일만 먼저 복사
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle* build.gradle* ./

RUN chmod +x gradlew && \
    ./gradlew --no-daemon dependencies || true

# 전체 소스 복사
COPY . .

# 테스트 제외 빌드 (테스트는 Jenkins에서 따로)
RUN ./gradlew --no-daemon bootJar -x test && \
    ls -la build/libs/

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# curl 설치 (healthcheck용)
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# 비루트 유저
RUN groupadd -r spring && useradd -r -g spring spring

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown spring:spring app.jar
USER spring

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
