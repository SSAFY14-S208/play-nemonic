# ============================================================
# Spring Boot 애플리케이션 Dockerfile (캐시 최적화 버전)
# Multi-stage build: 빌드 -> 경량 런타임 이미지
# ============================================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# ------------------------------------------------------------
# Layer 1: Gradle wrapper (거의 안 바뀜)
# ------------------------------------------------------------
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew

# ------------------------------------------------------------
# Layer 2: 의존성 정의 파일 (가끔 바뀜)
# ------------------------------------------------------------
COPY settings.gradle build.gradle ./

# ------------------------------------------------------------
# Layer 3: 의존성 다운로드 (settings/build.gradle 안 바뀌면 캐시 hit)
# || true 제거 - 진짜 실패하면 빌드 중단
# ------------------------------------------------------------
RUN ./gradlew --no-daemon dependencies --refresh-dependencies

# ------------------------------------------------------------
# Layer 4: 설정 파일 (가끔 바뀜, src보다 자주 안 바뀜)
# ------------------------------------------------------------
COPY config ./config

# ------------------------------------------------------------
# Layer 5: 소스 코드 (가장 자주 바뀜)
# ------------------------------------------------------------
COPY src ./src

# ------------------------------------------------------------
# Layer 6: bootJar 빌드 (소스 변경 시에만 실행)
# ------------------------------------------------------------
RUN ./gradlew --no-daemon bootJar -x test && \
    ls -la build/libs/

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# curl 설치 (healthcheck용)
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# 비루트 유저
RUN groupadd -r spring && useradd -r -g spring spring

# 빌드 결과 복사
COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown spring:spring app.jar

USER spring

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
