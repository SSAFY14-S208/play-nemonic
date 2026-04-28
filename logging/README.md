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
