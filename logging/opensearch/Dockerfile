# OpenSearch 2.15.0 + repository-s3 plugin
#
# repository-s3는 OpenSearch core 배포에 포함되지 않은 플러그인이라
# 컨테이너에 미리 굽지 않으면 매번 재설치해야 한다.
# (`docker exec ... opensearch-plugin install` 방식은 컨테이너 재생성 시 날아감)

FROM opensearchproject/opensearch:2.15.0

# repository-s3 플러그인 설치
# --batch: 권한 요구 프롬프트 자동 승인 (S3 접근 권한 등)
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch repository-s3

# 설치 확인 (빌드 시점에 검증)
RUN /usr/share/opensearch/bin/opensearch-plugin list | grep -q repository-s3 \
    && echo "repository-s3 plugin installed successfully"
