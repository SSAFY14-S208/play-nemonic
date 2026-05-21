# ============================================================
# Spring Boot 런타임 이미지
#
# 컴파일은 CI(Jenkins)에서 한 번만 수행하고, 산출물(boot jar)을
# docker build context로 전달받는다. Dockerfile 안에서 다시
# 컴파일하지 않으므로 Jenkins Test 단계와 중복되지 않는다.
#
# 빌드 컨텍스트에는 Dockerfile + app.jar 두 파일만 포함된다.
# (Jenkins가 mktemp 디렉토리에 staging 후 docker build 호출)
# ============================================================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# AWT PNG 렌더링이 런타임 fontconfig에서도 한글 폰트를 찾을 수 있도록 보강한다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-nanum \
    && rm -rf /var/lib/apt/lists/*

# 비루트 유저
RUN groupadd -r spring && useradd -r -g spring spring

# 빌드 결과 복사 (Jenkins staging 디렉토리에서 전달됨)
COPY app.jar app.jar
RUN chown spring:spring app.jar

USER spring

ENV JAVA_OPTS="-Xms512m -Xmx1024m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
