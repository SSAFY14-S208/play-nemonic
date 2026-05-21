# nemonic Logging Infrastructure

Stack: Fluent Bit → Kafka → Logstash → OpenSearch → Dashboards

## Data Directory
컨테이너 데이터는 git에서 분리됨. 호스트의 /opt/nemonic/data/ 에 위치.

- /opt/nemonic/data/opensearch/  (1000:1000)
- /opt/nemonic/data/kafka/       (1000:1000)

## Operations

### 기동/정지
cd /opt/nemonic/infra
docker compose -f docker-compose.logging.yml up -d
docker compose -f docker-compose.logging.yml down

### 상태 확인
docker compose -f docker-compose.logging.yml ps
docker compose -f docker-compose.logging.yml logs -f <service>

## Access
OpenSearch Dashboards: https://k14s208.p.ssafy.io/_dashboards (BasicAuth)

## 매핑 충돌 복구 (mapping conflict recovery)

증상: Dashboards에서 `N of M shards failed` + `illegal_argument_exception`.
원인: 인덱스 템플릿이 적용되기 전에 만들어진 옛 일자 인덱스의 동적 매핑이
다른 타입으로 굳어, `<prefix>-*` 패턴 쿼리에서 샤드별로 집계가 깨짐.

복구 절차 (OpenSearch Dashboards Dev Tools 또는 EC2 셸):

1. 충돌 인덱스 식별
   bash logging/opensearch/reindex-fix-mappings.sh --check error-logs '*'

2. 템플릿이 최신인지 확인. 아니면 README 의 _index_template 업로드 절차로 PUT.

3. 옛 인덱스 reindex (예: error-logs 의 04.30~05.10 7개)
   bash logging/opensearch/reindex-fix-mappings.sh error-logs \
       2026.04.30 2026.05.02 2026.05.05 2026.05.07 2026.05.08 2026.05.09 2026.05.10

스크립트는 단계마다 doc count 를 검증하고, 불일치 시 즉시 abort 해서
원본을 보존한다. DRY_RUN=1 로 시뮬레이션 가능.
