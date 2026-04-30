# nemonic Logging Infrastructure

EC2 단일 호스트의 모든 Docker 컨테이너 로그를 수집·파싱·인덱싱하여 OpenSearch
Dashboards에서 시각화하는 종합 로그 분석 플랫폼.

---

## 1. Stack

```
[All containers stdout]
        │
        ▼
   Fluent Bit  (호스트 모든 컨테이너 자동 수집, Lua로 container_id 추출)
        │
        ▼
     Kafka     (버퍼링 + 백프레셔, 단일 노드 KRaft)
        │
        ▼
   Logstash    (grok 파싱: Spring Boot 포맷, 자기 로그 drop)
        │
        ▼
  OpenSearch   (인덱싱: nemonic-app-logs-YYYY.MM.dd)
        │
        ▼
  Dashboards   (https://k14s208.p.ssafy.io/_dashboards)
```

### 1.1 검증된 버전 조합

| 컴포넌트 | 이미지 | 버전 |
| --- | --- | --- |
| Fluent Bit | `fluent/fluent-bit` | 3.1 |
| Kafka | `confluentinc/cp-kafka` | 7.5.4 (= Kafka 3.5) |
| OpenSearch | `nemonic/opensearch` (= 공식 + repository-s3) | 2.15.0-s3 |
| Dashboards | `opensearchproject/opensearch-dashboards` | 2.15.0 |
| Logstash | `opensearchproject/logstash-oss-with-opensearch-output-plugin` | 8.9.0 |

### 1.2 버전 선택 이유

- **Kafka는 Confluent 7.5.4 (= 3.5)**. Logstash 8.9의 kafka-client 3.3.x가
  Apache Kafka 3.7+ 와 호환되지 않음. consumer가 subscribe는 성공하지만
  fetch에서 0건. Kafka 3.5는 호환 OK.
- **`apache/kafka` 이미지는 3.7.0+ 만 제공** (3.5 태그 없음). 따라서
  Confluent 사용. Confluent Platform의 cp-kafka는 Apache 2.0 라이선스로
  무료 사용 가능.
- **Bitnami Kafka 사용 금지**. 2025년 정책 변경으로 마이너 버전 태그가
  Docker Hub에서 제거됨 (`bitnami/kafka:3.7` not found).
- **Logstash 8.9.0이 사실상 최신**. opensearchproject 측에서 8.10+ 이미지
  배포 안 함. `latest` 태그 의존은 재현성 깨짐.
- **OpenSearch는 custom 이미지로 굽는다**. `repository-s3` 플러그인이 core
  배포에 포함 안 됨. runtime 설치는 컨테이너 재생성 시 날아가서
  `logging/opensearch/Dockerfile`에 굽는다. 빌드 시점에 설치 검증까지
  수행하므로 런타임 디버깅이 필요 없음.

---

## 2. File Structure

레포 루트(`/opt/nemonic/infra/` = `infra/dev` 브랜치) 기준:

```
infra/
├── docker-compose.logging.yml       # 5-서비스 정의
└── logging/
    ├── README.md                    # 이 문서
    ├── TROUBLESHOOTING.md           # 디버깅 사례 모음
    ├── .gitignore                   # data/, *.log 제외
    ├── fluent-bit/
    │   ├── fluent-bit.conf          # 수집 설정
    │   ├── parsers.conf             # Docker JSON 파서
    │   └── extract_container_id.lua # 메타데이터 추출 스크립트
    └── logstash/
        ├── pipeline/main.conf       # Kafka → grok → OpenSearch
        └── config/logstash.yml      # Logstash 시스템 설정
```

### 2.1 Data Directory (git 관리 X)

호스트 전용 영역:

```
/opt/nemonic/data/
├── opensearch/    # 인덱스 데이터, UID 1000 소유
├── kafka/         # 토픽 로그, UID 1000 소유
└── fluent-bit/    # tail offset DB (sqlite), UID 0 소유
```

**반드시 git에서 분리**해야 함. 인덱스는 GB 단위로 커지고, 컨테이너가 직접
쓰는 영역이라 권한 문제도 생김. `.gitignore`로 `data/` 패턴 차단.

---

## 3. Operations

### 3.1 기동

```bash
cd /opt/nemonic/infra
docker compose -f docker-compose.logging.yml up -d
```

자동 기동 순서 (depends_on + healthcheck 기반):

1. Kafka, OpenSearch (병렬)
2. Dashboards (OpenSearch healthy 후)
3. Logstash (Kafka + OpenSearch healthy 후)
4. Fluent Bit (Kafka healthy 후)

총 부팅 시간 약 90초 (OpenSearch가 가장 오래 걸림).

### 3.2 정지

```bash
docker compose -f docker-compose.logging.yml down
```

데이터는 호스트(`/opt/nemonic/data/`)에 보존됨. **`down -v` 사용 금지** (볼륨
삭제로 인덱스 날아감).

### 3.3 상태 확인

```bash
# 컨테이너 상태 + healthcheck
docker compose -f docker-compose.logging.yml ps

# 개별 로그 (실시간)
docker logs -f nemonic-logging-<service>
# service: kafka, opensearch, dashboards, logstash, fluent-bit
```

### 3.4 사전 요구사항 (1회 셋업)

처음 EC2에 배포할 때:

```bash
# 1. 데이터 디렉토리 생성 + 권한
sudo mkdir -p /opt/nemonic/data/{opensearch,kafka,fluent-bit}
sudo chown -R 1000:1000 /opt/nemonic/data/opensearch
sudo chown -R 1000:1000 /opt/nemonic/data/kafka
# fluent-bit은 root(0:0)로 둠 - Fluent Bit 컨테이너가 root로 실행

# 2. OpenSearch 커널 파라미터
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf

# 3. OpenSearch config 디렉토리 영속화 (snapshot S3 키 저장용 keystore)
#    - 파일 단위 마운트는 keystore의 atomic rename(mv)을 막는다 (TROUBLESHOOTING #14).
#    - 디렉토리 단위로 마운트하되, 이미지 default를 잃지 않도록 사전 추출 필수.
sudo mkdir -p /opt/nemonic/data/opensearch-config
TEMP_ID=$(docker create nemonic/opensearch:2.15.0-s3)
sudo docker cp ${TEMP_ID}:/usr/share/opensearch/config/. \
  /opt/nemonic/data/opensearch-config/
docker rm ${TEMP_ID}
sudo chown -R 1000:1000 /opt/nemonic/data/opensearch-config
```

---

## 4. Healthchecks

모든 healthcheck는 **`127.0.0.1`** 사용. `localhost`는 alpine musl libc가
IPv6 (`::1`)로 우선 해석하는데, IPv4-only 서비스에서 connection refused 발생.

| 서비스 | 검증 명령 |
| --- | --- |
| kafka | `kafka-broker-api-versions --bootstrap-server 127.0.0.1:9092` |
| opensearch | `curl http://127.0.0.1:9200/_cluster/health` (green/yellow) |
| dashboards | `curl http://127.0.0.1:5601/_dashboards/api/status` |
| logstash | `curl http://127.0.0.1:9600/_node/stats/pipelines/main` |
| fluent-bit | `curl http://127.0.0.1:2020/api/v1/metrics` |

---

## 5. Verification (데이터 흐름 추적)

문제 생겼을 때 단계별 진단. 각 단계가 OK면 다음 단계로.

### 5.1 Fluent Bit (수집)

```bash
FB_IP=$(docker inspect nemonic-logging-fluent-bit \
  --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
curl -s http://$FB_IP:2020/api/v1/metrics | python3 -m json.tool
```

봐야 할 값:
- `input.tail.0.records`: 수집한 라인 수
- `output.kafka.0.proc_records`: Kafka로 보낸 수
- `output.kafka.0.errors`: 0이어야 정상
- `output.kafka.0.dropped_records`: 0이어야 정상

### 5.2 Kafka (버퍼)

```bash
# 토픽의 메시지 수
docker exec nemonic-logging-kafka kafka-get-offsets \
  --bootstrap-server 127.0.0.1:9092 --topic nemonic-logs

# 결과: nemonic-logs:0:N (N이 메시지 수)

# Consumer group lag
docker exec nemonic-logging-kafka kafka-consumer-groups \
  --bootstrap-server 127.0.0.1:9092 --describe --group logstash-nemonic-v4
```

봐야 할 값:
- `LOG-END-OFFSET` (Fluent Bit이 누적한 양)
- `CURRENT-OFFSET` (Logstash가 읽은 위치)
- `LAG` = 차이. 일정 수준 유지되면 정상, 계속 커지면 Logstash 처리 부족.

### 5.3 Logstash (파싱)

```bash
docker exec nemonic-logging-logstash \
  curl -s http://127.0.0.1:9600/_node/stats/pipelines/main \
  | grep -oE '"events":\{[^}]+\}'
```

결과 예: `"events":{"in":1234,"out":1230,"filtered":1230}`

- `in` > 0이면 Kafka에서 받는 중
- `out` ≈ `in`이면 OpenSearch까지 잘 흐름
- `filtered`는 grok 등 필터 처리량

### 5.4 OpenSearch (인덱싱)

```bash
# 인덱스 목록
docker exec nemonic-logging-opensearch \
  curl -s "http://127.0.0.1:9200/_cat/indices?v"

# 문서 수
docker exec nemonic-logging-opensearch \
  curl -s "http://127.0.0.1:9200/nemonic-app-logs-*/_count?pretty"

# 샘플 문서 (파싱 확인용)
docker exec nemonic-logging-opensearch \
  curl -s "http://127.0.0.1:9200/nemonic-app-logs-*/_search?size=1&pretty"
```

### 5.5 컨테이너별 로그 분포

```bash
docker exec nemonic-logging-opensearch \
  curl -s -X POST "http://127.0.0.1:9200/nemonic-app-logs-*/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "size": 0,
    "aggs": {
      "containers": {
        "terms": {"field": "container_id.keyword", "size": 20}
      }
    }
  }'
```

각 컨테이너에서 얼마나 로그가 들어오는지 한눈에.

---

## 6. Access (외부)

### 6.1 URL

```
https://k14s208.p.ssafy.io/_dashboards/
```

### 6.2 인증

HTTP BasicAuth (nginx 레벨). 비밀번호 파일은 git 추적 X.

```bash
# 새 사용자 추가
sudo htpasswd /opt/nemonic/infra/deploy/nginx/.htpasswd_dashboards <username>

# 첫 사용자 + 파일 생성
sudo htpasswd -c /opt/nemonic/infra/deploy/nginx/.htpasswd_dashboards admin
```

`.htpasswd_dashboards`는 nginx 컨테이너에 read-only 마운트됨
(`docker-compose.prod.yml`의 nginx volumes 참조).

### 6.3 첫 사용 (Index Pattern 등록)

Dashboards 첫 접속 시:

1. 좌측 메뉴 ☰ → **Stack Management** → **Index Patterns**
2. **Create index pattern**
3. Name: `nemonic-app-logs-*`
4. Time field: `@timestamp`
5. Create

이후 **Discover** 메뉴에서 로그 검색 가능.

---

## 7. Logged Fields (Spring Boot 예시)

Logstash grok이 Spring Boot 로그를 다음 필드로 분해:

| 필드 | 예시 | 출처 |
| --- | --- | --- |
| `@timestamp` | `2026-04-28T04:33:23.173Z` | grok이 Spring Boot 로그에서 추출 |
| `log_level` | `INFO`, `WARN`, `ERROR` | grok |
| `pid` | `7` | grok |
| `application` | `backend` | grok (`[backend]` 부분) |
| `thread` | `main`, `nio-8080-exec-1` | grok |
| `logger` | `com.nemonicworld.BackendApplication` | grok |
| `log_message` | `Started BackendApplication...` | grok |
| `tags` | `["spring_boot"]` | grok 성공 시 부착 |
| `container_id` | `f44537001a48e878` | Fluent Bit Lua 추출 |
| `log_tag` | `docker.var.lib.docker.containers...` | Fluent Bit Lua |
| `hostname` | `<fluent-bit 컨테이너 hostname>` | record_modifier |
| `stream` | `stdout` 또는 `stderr` | Docker JSON |
| `message` | 원본 로그 줄 | Fluent Bit |

비-Spring Boot 컨테이너 (nginx, postgres 등)는 grok 실패 →
`tags: ["_grokparsefailure_spring"]`. 이 자체는 정상이며, 나중에 컨테이너별
grok 패턴을 추가하면 분해 가능.

---

## 8. Search Examples

### Spring Boot 로그만

```
tags : "spring_boot"
```

### 특정 컨테이너 로그

```
container_id : "f44537001a48*"
```

### 에러 레벨

```
log_level : "ERROR"
```

### 복합

```
tags : "spring_boot" AND log_level : ("WARN" OR "ERROR")
```

---

## 9. Troubleshooting

문제 생기면 먼저 [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) 확인. 이 인프라
구축 과정에서 만난 15개 함정의 원인과 해결책 정리.

---

## 10. Snapshot Management (Phase 3)

장기 보관은 OpenSearch 클러스터가 아닌 **MinIO**로 분리. 인덱스는 일정 기간이
지나면 snapshot 후 클러스터에서 삭제, MinIO에는 더 오래 보관 (시점별 복원
가능).

### 10.1 구성 요소

| 요소 | 값 |
| --- | --- |
| MinIO 컨테이너 | `nemonic-prod-minio-1` (운영용 MinIO 공유) |
| 네트워크 | `nemonic-prod_cicd-net` (OpenSearch와 동일) |
| 전용 bucket | `nemonic-logs-snapshots` (운영 bucket과 분리) |
| Repository name | `nemonic-logs-repo` |
| 키 위치 | OpenSearch keystore (`s3.client.default.{access,secret}_key`) |

키는 환경변수가 아닌 keystore에 저장 — `docker inspect`로 노출 안 됨.
keystore는 `/opt/nemonic/data/opensearch-config/opensearch.keystore`로 영속화
(이유는 TROUBLESHOOTING #14 참조).

### 10.2 1회 셋업 (이미 완료, 새 환경 셋업 시 참고)

```bash
# (사전: bucket 생성)
set -a; source /opt/nemonic/shared/.env.prod; set +a
docker run --rm \
  --network nemonic-prod_cicd-net \
  -e MC_HOST_minio="http://${MINIO_ROOT_USER}:${MINIO_ROOT_PASSWORD}@minio:9000" \
  minio/mc \
  mb --ignore-existing minio/nemonic-logs-snapshots

# (keystore에 키 등록 — printf로 stdin 파이프, secret 채팅·history 노출 방지)
printf '%s' "$MINIO_ROOT_USER" | docker exec -i nemonic-logging-opensearch \
  /usr/share/opensearch/bin/opensearch-keystore add --stdin --force s3.client.default.access_key
printf '%s' "$MINIO_ROOT_PASSWORD" | docker exec -i nemonic-logging-opensearch \
  /usr/share/opensearch/bin/opensearch-keystore add --stdin --force s3.client.default.secret_key

# (reload — 재기동 없이 메모리에 새 키 로드)
docker exec nemonic-logging-opensearch \
  curl -s -X POST "http://127.0.0.1:9200/_nodes/reload_secure_settings"

# (repository 등록)
docker exec nemonic-logging-opensearch \
  curl -s -X PUT "http://127.0.0.1:9200/_snapshot/nemonic-logs-repo" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "s3",
    "settings": {
      "bucket": "nemonic-logs-snapshots",
      "endpoint": "minio:9000",
      "protocol": "http",
      "path_style_access": true
    }
  }'

# (verify)
docker exec nemonic-logging-opensearch \
  curl -s -X POST "http://127.0.0.1:9200/_snapshot/nemonic-logs-repo/_verify"
```

### 10.3 수동 snapshot

```bash
docker exec nemonic-logging-opensearch \
  curl -s -X PUT "http://127.0.0.1:9200/_snapshot/nemonic-logs-repo/<snapshot-name>?wait_for_completion=true" \
  -H 'Content-Type: application/json' \
  -d '{
    "indices": "nemonic-app-logs-*",
    "include_global_state": false
  }'
```

응답에서 `"state": "SUCCESS"` + `shards.successful` ≥ 1 이면 OK.

### 10.4 MinIO 객체 확인

```bash
set -a; source /opt/nemonic/shared/.env.prod; set +a
docker run --rm \
  --network nemonic-prod_cicd-net \
  -e MC_HOST_minio="http://${MINIO_ROOT_USER}:${MINIO_ROOT_PASSWORD}@minio:9000" \
  minio/mc \
  ls --recursive minio/nemonic-logs-snapshots/
```

---

## 11. Future Work

| Phase | 내용 |
| --- | --- |
| 3 (진행 중) | ✅ MinIO snapshot repository, ⏳ ISM policy (hot 7d → warm 30d → snapshot 90d → delete), ⏳ 인덱스 템플릿 + 인덱스 분리 (app/access/system), ⏳ 본격 대시보드 |
| 4 | 재사용 라이브러리 추출 (Java/JS/Python), 운영 문서 |
