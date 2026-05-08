# ============================================================
# Jenkins 커스텀 이미지
# - 베이스: jenkins/jenkins:lts-jdk21 (JDK 21 내장)
# - 추가: docker CLI + compose plugin + git
# - Jenkins Pipeline이 컨테이너 내부에서 docker 명령 사용 가능
# ============================================================
FROM jenkins/jenkins:lts-jdk21

USER root

# 필수 패키지
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gnupg \
        lsb-release \
    && rm -rf /var/lib/apt/lists/*

# Docker 공식 저장소 추가 및 docker CLI + compose + buildx 플러그인 설치
# buildx는 Jenkinsfile에서 DOCKER_BUILDKIT=1로 호출하는 빌드에 필요
# (cache mount, syntax 디렉티브 등 BuildKit 기능 사용 시 buildx CLI plugin 필수)
RUN install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | \
        gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    chmod a+r /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
        > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        docker-ce-cli \
        docker-compose-plugin \
        docker-buildx-plugin \
    && rm -rf /var/lib/apt/lists/*

# Jenkins 사용자를 docker 그룹에 넣기 위해
# host docker.sock의 GID에 맞춰 docker 그룹 생성
# (실제 GID는 run 시점에 맞추는 게 베스트지만 기본값으로 999 사용)
RUN groupadd -g 999 docker-host 2>/dev/null || true && \
    usermod -aG docker-host jenkins 2>/dev/null || true

# Jenkins 기본 사용자로 복귀는 하지 않음
# docker.sock 접근을 위해 root로 유지 (compose 파일에서 user: root)
