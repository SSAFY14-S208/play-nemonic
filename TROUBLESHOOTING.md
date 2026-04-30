# Troubleshooting Guide

nemonic 로깅 인프라 구축(Phase 1) 중 만난 모든 함정의 원인과 해결책. 같은
실수 반복하지 않도록, 그리고 Phase 4 라이브러리화 시 사용자에게 미리 알려줄
용도로 정리.

목차의 각 항목은 **증상 → 원인 → 해결**의 형식으로 정리.

---

## 1. Frontend Docker 빌드: `COPY failed: stat app/public: file does not exist`

**증상**

```
Step 24/27 : COPY --from=builder --chown=nextjs:nodejs /app/public ./public
COPY failed: stat app/public: file does not exist
```

**원인**

Next.js 프로젝트의 `public/` 폴더가 선택사항인데, 표준 Dockerfile 템플릿은
존재한다고 가정. 정적 파일이 없으면 폴더 자체가 git에 없음.

**해결**

빈 폴더라도 git이 추적하도록 `.gitkeep` 추가:

```bash
mkdir -p frontend/public
touch frontend/public/.gitkeep
git add frontend/public/.gitkeep
git commit -m "fix: add empty public folder for Docker build"
```

또는 Dockerfile builder 단계에서 `RUN mkdir -p public` 보장.

---

## 2. Frontend Healthcheck Timeout: IPv6 vs IPv4

**증상**

- 컨테이너 안에서 `wget http://0.0.0.0:3000/` → HTML 정상 반환
- 컨테이너 안에서 `wget http://localhost:3000/` → connection refused
- Docker healthcheck가 `localhost:3000`을 사용 → 실패 → 컨테이너 unhealthy

**원인**

Alpine 베이스 이미지의 musl libc resolver는 `localhost`를 IPv6 (`::1`)로
우선 해석. Next.js standalone server는 IPv4 (`0.0.0.0`)에만 listen.
→ IPv6로 연결 시도 → refused.

glibc는 이런 경우 fallback으로 IPv4 시도하지만, musl은 시도 후 멈춤.

**해결**

healthcheck의 `localhost`를 `127.0.0.1`로 변경:

```yaml
healthcheck:
  test: ["CMD", "wget", "-qO-", "http://127.0.0.1:3000/"]
```

이 패턴은 OpenSearch, Kafka 등 다른 healthcheck에도 동일 적용 필요.

**일반화된 교훈**

> **컨테이너 healthcheck에서 `localhost` 절대 사용 금지. 항상 `127.0.0.1`.**
> alpine 이미지가 기본인 컨테이너 시대에 musl libc IPv6 우선 해석은 흔한 함정.

---

## 3. Bitnami Kafka 이미지 사라짐

**증상**

```
docker pull bitnami/kafka:3.7
Error response from daemon: docker.io/bitnami/kafka:3.7: not found
```

**원인**

Broadcom의 VMware/Bitnami 인수 이후 정책 변경. 2025년 8월부로 마이너 버전
태그 (3.7, 3.8 등) 제거. `latest`만 무료, 버전 태그는 `bitnamilegacy/`나
유료 카탈로그로 이전.

**해결**

벤더 의존성 제거가 답. **`confluentinc/cp-kafka:7.5.4` 사용** (= Kafka 3.5,
Apache 2.0 라이선스). Confluent는 Kafka를 만든 LinkedIn 출신 회사가 운영,
오픈소스 정책 안정.

```yaml
image: confluentinc/cp-kafka:7.5.4
```

**환경변수 차이**: Bitnami는 `KAFKA_CFG_*`, Confluent/Apache는 `KAFKA_*`
(CFG_ 접두사 없음).

---

## 4. OpenSearch 부팅 실패: 환경변수 중복

**증상**

```
ERROR: setting [plugins.security.disabled] already set, saw [true] and [true]
```

**원인**

`DISABLE_SECURITY_PLUGIN: "true"` 환경변수가 내부적으로
`plugins.security.disabled`를 설정하는데, 우리가 그 변수를 또 명시 →
같은 설정 두 번 → 에러.

**해결**

둘 중 하나만 사용. 권장: `DISABLE_SECURITY_PLUGIN: "true"`.

```yaml
environment:
  DISABLE_SECURITY_PLUGIN: "true"
  DISABLE_INSTALL_DEMO_CONFIG: "true"
  # plugins.security.disabled: "true"   ← 제거
```

**일반화된 교훈**

> **환경변수 → 설정값 변환을 하는 컨테이너는 둘 다 명시 금지.**
> OpenSearch/Elasticsearch, Bitnami 이미지들에서 흔함.

---

## 5. Logstash 부팅 실패: `xpack` 설정 미지원

**증상**

```
Setting "xpack.monitoring.enabled" doesn't exist. Please check if you haven't made a typo.
[FATAL] Logstash stopped processing because of an error: (SystemExit) exit
```

**원인**

`xpack`은 Elastic 사의 상용/오픈코어 기능. OpenSearch는 Elastic에서 fork된
직후 X-Pack 자체를 빼고 만들어짐. `opensearchproject/logstash-oss-with-...`
이미지는 OSS 버전이라 xpack 설정을 인식 못 함.

**해결**

`logstash.yml`에서 `xpack.*` 라인 모두 제거:

```yaml
# 제거
# xpack.monitoring.enabled: false
```

**일반화된 교훈**

> **OpenSearch 스택에서는 Elastic 전용 설정 사용 금지.**
> `xpack.*`, `output { elasticsearch {...} }` 등.

---

## 6. Logstash 부팅 실패: `pipelines.yml` 못 찾음

**증상**

```
ERROR: Failed to read pipelines yaml file. 
Location: /usr/share/logstash/config/pipelines.yml
Exception: No such file or directory
```

**원인**

docker-compose에서 config 디렉토리 통째 마운트:

```yaml
- ./logging/logstash/config:/usr/share/logstash/config:ro
```

이게 컨테이너 안의 기본 `config/` 전체를 우리 디렉토리로 덮어씀. 결과적으로
이미지 내장 파일 (`pipelines.yml`, `log4j2.properties`, `jvm.options`)이
다 사라짐. 우리는 `logstash.yml`만 줬으니 나머지가 부족.

**해결**

디렉토리 통째가 아닌 **파일 단위**로 마운트:

```yaml
volumes:
  - ./logging/logstash/pipeline:/usr/share/logstash/pipeline:ro
  - ./logging/logstash/config/logstash.yml:/usr/share/logstash/config/logstash.yml:ro
```

**일반화된 교훈**

> **이미지 기본 설정 일부만 커스터마이즈할 때는 파일:파일 마운트.**
> 디렉토리:디렉토리는 이미지 안의 다른 파일을 다 덮어씀.

---

## 7. Fluent Bit 재시작 루프: sqlite DB 쓰기 권한

**증상**

```
[error] [sqldb] cannot open database /fluent-bit/etc/tail-db.sqlite
[error] [input:tail:tail.0] could not open/create database
```

**원인**

config 디렉토리를 `:ro`(read-only)로 마운트했는데, Fluent Bit이 같은
디렉토리에 tail offset 추적용 sqlite DB를 만들려 함. read-only라 실패.

**해결**

DB 파일을 **별도 호스트 디렉토리**에 보관, config는 ro 유지:

```yaml
volumes:
  - ./logging/fluent-bit:/fluent-bit/etc:ro          # 설정만 ro
  - /opt/nemonic/data/fluent-bit:/flb-state:rw       # DB만 rw
```

`fluent-bit.conf`의 `[INPUT]`에서 DB 경로 변경:

```ini
DB /flb-state/tail-db.sqlite
```

---

## 8. Fluent Bit 마운트 충돌: read-only 부모 안에 mount point 못 만듦

**증상**

```
failed to create shim task: ...
make mountpoint "/var/log/flb-state": read-only file system
```

**원인**

마운트 7번 해결 시 처음에 DB 경로를 `/var/log/flb-state`로 잡았음. 그런데
같은 docker-compose에 `/var/log:/var/log:ro`로 시스템 로그 디렉토리도 마운트.
부모(`/var/log`)가 read-only면 그 안에 자식 mount point 못 만듦.

**해결**

DB 경로를 `/var/log` 밖으로:

```yaml
- /opt/nemonic/data/fluent-bit:/flb-state:rw   # /var/log 밖
- /var/log:/var/log:ro                          # 그대로
```

**일반화된 교훈**

> **Docker volume 마운트는 부모/자식 관계 주의.**
> 부모가 ro면 자식 mount 못 만듦. 별개 경로로 분리하는 게 안전.

---

## 9. ⭐ Logstash가 Kafka에서 메시지 0건 fetch (가장 어려웠던 문제)

**증상**

- Fluent Bit metrics: `proc_records: 9471` (Kafka로 보냄 OK)
- Kafka: `nemonic-logs:0:9471` (메시지 누적됨)
- Logstash 로그:
  ```
  Subscribed to topic(s): nemonic-logs
  Resetting the last seen epoch of partition nemonic-logs-0
  ```
- 그 후 stuck. `events.in: 0`. OpenSearch에 인덱스 안 생김.
- `kafka-console-consumer`도 동일 timeout
- `kafka-consumer-groups --describe` → `TimeoutException: 
  describeGroups(api=FIND_COORDINATOR)`

**잘못된 가설들 (시간 낭비)**

1. ❌ "Logstash 8.9의 kafka-client 3.3과 Apache Kafka 3.7+ 호환성 문제"
   → Confluent 7.5(=Kafka 3.5)로 바꿔도 동일 증상
2. ❌ "Logstash 자체 stuck"
   → kafka-console-consumer까지 같은 증상이면 Kafka 자체 문제
3. ❌ "consumer group이 꼬임"
   → group_id 새로 만들어도 동일

**진짜 원인**

KRaft 단일 노드 Kafka에서 `__consumer_offsets` 토픽을 자동 생성해야 하는데,
**기본 replication factor가 3**. 단일 broker에서는 3개 replica 못 만들어서
토픽 생성 실패. 그래서:

- producer (Fluent Bit)는 동작 - offsets 토픽 안 씀
- consumer (Logstash, console-consumer)는 모두 stuck - offsets 토픽 필수

**핵심 진단 단서**

> **Producer는 OK인데 Consumer만 안 되면 `__consumer_offsets` 의심.**

**해결**

docker-compose에 환경변수 3개 추가:

```yaml
environment:
  ...
  KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: "1"
  KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: "1"
  KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: "1"
```

기존 잘못된 metadata가 남았을 수 있으니 **데이터 디렉토리도 비우기**:

```bash
docker compose -f docker-compose.logging.yml stop kafka
sudo rm -rf /opt/nemonic/data/kafka/*
docker compose -f docker-compose.logging.yml up -d kafka
```

**일반화된 교훈**

> **단일 노드 Kafka는 항상 OFFSETS_TOPIC_REPLICATION_FACTOR=1, TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1, TRANSACTION_STATE_LOG_MIN_ISR=1 명시.**
> 이건 KRaft든 Zookeeper 모드든 동일. Confluent 공식 문서에도 single-node
> 예제에 항상 포함됨.

---

## 10. Lua 인라인 코드 미적용

**증상**

Fluent Bit 설정에 Lua 필터 한 줄로 작성:

```ini
[FILTER]
    Name  lua
    Match docker.*
    call  add_container_id
    code  function add_container_id(tag, ts, record) local cid = string.match(tag, ...) ... end
```

→ 부팅은 성공하지만 record에 `container_id` 안 들어옴.

**원인**

INI 형식 conf의 한 줄에 Lua 함수 전체 넣기는 일부 환경에서 불안정.
Fluent Bit이 silently 무시하거나 파싱 오류.

**해결**

별도 `.lua` 파일로 분리:

`logging/fluent-bit/extract_container_id.lua`:

```lua
function extract_container_id(tag, timestamp, record)
    local cid = string.match(tag, "([a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9][a-f0-9])")
    if cid then
        record["container_id"] = cid
    end
    record["log_tag"] = tag
    return 1, timestamp, record
end
```

`fluent-bit.conf`:

```ini
[FILTER]
    Name   lua
    Match  docker.*
    script /fluent-bit/etc/extract_container_id.lua
    call   extract_container_id
```

---

## 11. Logstash 자기 로그 무한 재처리 (peudo-loop)

**증상**

OpenSearch 문서 수가 비정상적으로 빠르게 증가 (수십만, 수백만). 샘플
문서를 보면 대부분 Logstash 자체 로그 (`[Consumer]`, `Resetting the last
seen epoch`, `WARN Unrecognized @timestamp` 등).

**원인**

```
Logstash가 stdout으로 INFO/WARN 로그 출력
  → Docker stdout 캡처
  → Fluent Bit이 그 로그 파일을 watch (모든 컨테이너 자동 수집)
  → Kafka로 전송
  → Logstash가 다시 받아서 처리 (그러면서 또 stdout 로그 출력)
  → ...
```

진짜 무한 루프는 아니지만 (각 사이클마다 메시지가 약간 변하므로) 로그가
폭증하고 진짜 데이터를 묻어버림.

**해결**

Logstash filter에서 자기 로그 drop:

```
filter {
  if [log_tag] =~ /logstash|fluent-bit|fluentbit/ {
    drop { }
  }
  ...
}
```

`log_tag`는 Lua 스크립트가 추가한 필드 (Fluent Bit이 부여한 원본 tag,
컨테이너 path 포함).

**일반화된 교훈**

> **로그 수집기로 모든 컨테이너 로그 잡으면 자기 자신도 잡힘.**
> 명시적 drop 또는 Exclude_Path 필요. 안 그러면 로그 폭증.

---

## 12. Fluent Bit의 timestamp 형식 vs Logstash 기대 형식

**증상**

OpenSearch 문서에:

```json
"@timestamp": "2026-04-28T04:08:01.367Z",
"_@timestamp": 1.777348898453214E9,
"tags": ["_timestampparsefailure"]
```

Logstash 로그에:

```
[WARN] Unrecognized @timestamp value type=class org.jruby.RubyFloat
```

**원인**

Fluent Bit이 기본적으로 `@timestamp`를 epoch float (`seconds.microseconds`)로
직렬화. Logstash codec=json이 string ISO8601 또는 long을 기대 → 파싱 실패.

**해결**

Fluent Bit `[OUTPUT]`에 timestamp format 명시:

```ini
[OUTPUT]
    Name             kafka
    ...
    Timestamp_Key    @timestamp
    Timestamp_Format iso8601
```

이러면 Fluent Bit이 `"@timestamp": "2026-04-28T04:33:23.173Z"` 형식의 문자열로
보내고, Logstash가 정상 파싱.

---

## 13. Spring Boot가 idle해서 데이터 없음 (인프라 문제 아님)

**증상**

모든 인프라 healthy, Fluent Bit이 13개 컨테이너 watching 중. 그런데
OpenSearch에 Spring Boot 컨테이너 로그가 안 들어옴.

`docker stats nemonic-prod-app-1`은 살아있고, `/api/actuator/health`도 200
반환.

**원인**

Spring Boot의 logback은 기본적으로 startup 로그만 stdout에 찍고, 그 이후
요청 처리 access 로그는 비활성. actuator/health 같은 헬스체크 endpoint도
access 로그 안 남김 (반복 호출 노이즈 방지).

```bash
sudo stat /var/lib/docker/containers/<spring-boot>/<id>-json.log
# Modify: 어제 부팅 시간에서 안 변함 → stdout에 새 로그 안 찍힘
```

**해결 (단기)**

검증 위해 Spring Boot 재시작 → startup 로그가 다시 stdout으로:

```bash
docker restart nemonic-prod-app-1
```

30+ 줄의 Spring Boot startup 로그가 Fluent Bit에 잡혀 OpenSearch에 들어감.
grok이 정확히 매칭되어 `tags: ["spring_boot"]` + `log_level`, `application`
등 필드 분해 확인됨.

**해결 (장기, 백엔드 작업)**

Spring Boot의 access logging 활성화. `application.yml`:

```yaml
server:
  tomcat:
    accesslog:
      enabled: true
      directory: /dev
      prefix: stdout
      suffix: ""
      pattern: '%h %l %u %t "%r" %s %b "%{Referer}i" "%{User-Agent}i" %D'
```

이러면 모든 HTTP 요청이 stdout에 로그 출력 → Fluent Bit이 자동 수집.

**일반화된 교훈**

> **로깅 인프라가 동작한다 ≠ 데이터가 흐른다.**
> 앱이 실제로 stdout에 로그를 찍어야 의미가 있음. 검증 시 synthetic
> traffic이나 의도적 트리거 (재시작 등) 필요. production 가기 전 access
> log 활성화는 필수.

---

## 14. OpenSearch keystore 영속화: bind mount + atomic rename 충돌

**증상**

```
Exception in thread "main" java.nio.file.FileSystemException:
  /usr/share/opensearch/config/opensearch.keystore.tmp ->
  /usr/share/opensearch/config/opensearch.keystore: Device or resource busy
```

후속 시도에서:

```
java.nio.file.FileAlreadyExistsException:
  /usr/share/opensearch/config/opensearch.keystore.tmp
```

**원인**

`opensearch-keystore` CLI는 atomic 업데이트를 위해 2단계로 동작:

1. `opensearch.keystore.tmp`에 새 내용 쓰기
2. `mv tmp → keystore` (inode 교체)

호스트 파일을 컨테이너에 **파일 단위 bind mount** 한 경우, bind mount는
inode 단위로 묶여있어서 inode 교체가 차단됨 → `Device or resource busy`.
첫 시도가 실패하며 `.tmp` 잔재가 남고, 재시도는 `FileAlreadyExistsException`.

**잘못된 가설들**

1. ❌ "권한 문제" — chmod 600, owner UID 1000으로 맞췄지만 동일.
2. ❌ "`--force` 옵션 누락" — 추가해도 동일.
3. ❌ "stdin 입력 방식 문제" — add 명령 자체가 실패하는 게 아니라 atomic mv가 실패.

**해결**

keystore 단일 파일이 아니라 **config 디렉토리 전체**를 마운트. 단, 이미지
default config를 잃지 않도록 사전 추출 필수.

(`#6`의 진짜 원칙은 "default 잃지 말라"이지 "디렉토리 마운트 자체 금지"가
아님.)

```bash
# 임시 컨테이너에서 default config 추출 (실행 안 함, create만)
TEMP_ID=$(docker create nemonic/opensearch:2.15.0-s3)
sudo docker cp ${TEMP_ID}:/usr/share/opensearch/config/. \
  /opt/nemonic/data/opensearch-config/
docker rm ${TEMP_ID}
sudo chown -R 1000:1000 /opt/nemonic/data/opensearch-config
```

compose:

```yaml
volumes:
  - /opt/nemonic/data/opensearch:/usr/share/opensearch/data
  - /opt/nemonic/data/opensearch-config:/usr/share/opensearch/config
```

**일반화된 교훈**

> **컨테이너 안에서 atomic rename(`mv`)을 쓰는 도구는 파일 단위 bind mount와
> 호환 X.** 디렉토리 단위로 마운트하되, 이미지 기본값을 잃지 않도록 사전에
> default를 추출해서 호스트로 옮긴 뒤 마운트해야 함.

**부수 트레이드오프**

- 호스트에 OpenSearch default config가 통째로 노출됨. 실수 편집 시 컨테이너에
  즉시 반영.
- 이미지 업그레이드 시 새 default를 호스트에 수동 반영 필요.

---

## 15. 마운트 변경 시 `compose stop`으로는 새 정의 적용 안 됨

**증상**

compose에서 volume 정의를 바꾼 뒤 `compose stop` → `compose up -d` 시퀀스로
진행. 새 마운트 적용 실패하며 연쇄 에러:

```
Error response from daemon: mount /opt/.../opensearch.keystore:
  /var/lib/docker/rootfs/.../opensearch.keystore, flags: 0x5000:
  not a directory
```

```
Conflict. The container name "/nemonic-logging-opensearch"
  is already in use by container "<id>".
```

이후 모든 `docker exec`가 옛 stop 직전 컨테이너에 박혀버려 옛 함정(#14)에
다시 걸림.

**원인**

컨테이너의 mount metadata는 **컨테이너 생성 시 박힘**. `stop`/`start`로는
갱신 안 됨. `compose up`은 옛 컨테이너를 발견하고 이름 충돌 → 새 컨테이너
못 만듦. 그 사이 docker는 옛 mount source path가 없으면 자동으로 디렉토리를
생성하는데, 옛 컨테이너의 file-type mount는 dir과 type이 안 맞아 추가 충돌.

**잘못된 가설들**

1. ❌ "`stop` 후 `up`이면 새 정의 적용" — `stop`은 정지만 시킴.
2. ❌ "compose가 알아서 옛 컨테이너 교체" — 이름 충돌 발생, 새로 안 만듦.

**해결**

마운트 정의를 변경했다면 컨테이너를 **삭제하고 새로 만들어야** 함:

```bash
# 옵션 1 (compose 명령)
docker compose -f docker-compose.logging.yml down opensearch
# down은 컨테이너 제거(volume은 보존)

# 옵션 2 (개별 컨테이너)
docker rm -f nemonic-logging-opensearch
```

그 다음 `compose up -d`로 새 정의 적용. 호스트 잔재 디렉토리도 함께 정리해야
type 충돌이 안 남음.

**일반화된 교훈**

> **`compose stop`은 컨테이너를 정지만 시키고 mount metadata는 그대로 둔다.**
> volumes 정의를 변경했다면 `down` 또는 `docker rm -f`로 컨테이너를 삭제한 뒤
> 새로 만들어야 함. 호스트 잔재 디렉토리도 함께 정리하지 않으면 후속 마운트가
> type 충돌을 일으킨다.

---

## 16. ISM `_ism/explain` 응답 verbosity 함정

**증상**

ISM `add` API는 성공 응답:

```
{"updated_indices": 3, "failures": false}
```

그런데 직후 explain 호출하면 부착이 안 된 것처럼 보임:

```
{
  "nemonic-app-logs-2026.04.30": {
    "index.plugins.index_state_management.policy_id": null,
    "index.opendistro.index_state_management.policy_id": null,
    "enabled": null
  },
  "total_managed_indices": 0
}
```

**원인**

`_ism/explain`의 default 응답은 너무 짧다. ISM이 인덱스를 매니지드로 등록한
직후라도 default 출력에는 `policy_id: null`로 보일 수 있음. 진짜 부착 여부는
인덱스 settings에 박혀있는지로 판단해야 함.

**잘못된 가설들**

1. ❌ "`add` API가 silent fail" — 실제로는 settings에 이미 박혀있었음.
2. ❌ "ISM plugin이 비활성" — `_cluster/settings`에서 `enabled: true` 확인됨.

**해결**

진짜 진단은 두 가지 방법으로:

```bash
# (1) settings에 직접 박혔는지 (가장 확실)
curl "http://127.0.0.1:9200/<index>/_settings?flat_settings=true" \
  | grep policy_id
# → "index.plugins.index_state_management.policy_id" : "<policy>"

# (2) explain에 show_policy=true 옵션 추가
curl "http://127.0.0.1:9200/_plugins/_ism/explain/<index>?show_policy=true&pretty"
# → policy_id, enabled, state.name, action.name, step.* 까지 다 보임
```

**일반화된 교훈**

> **OpenSearch ISM의 진단은 settings를 source of truth로 본다.**
> explain의 짧은 응답은 metadata 초기화 전 단계라 빈 값이 나올 수 있음.
> 진짜 부착 여부는 `_settings?flat_settings=true | grep policy_id`로 확인.

---

## 17. `ism_template`은 비동기 — settings에 policy_id 명시가 즉시 보장

**증상**

ISM policy 안에 `ism_template` 정의:

```json
{
  "ism_template": [
    {"index_patterns": ["nemonic-app-logs-*"], "priority": 100}
  ]
}
```

새 인덱스를 만든 직후 explain → ISM 부착 안 됨:

```
{
  "nemonic-app-logs-2099.01.01": {
    "index.plugins.index_state_management.policy_id": null
  },
  "total_managed_indices": 0
}
```

**원인**

`ism_template`은 ISM coordinator의 **다음 sweeper cycle**(default 5분)에
인덱스를 발견하고 부착한다. 인덱스 생성 시점에 즉시 부착되는 게 아님.
그 동안 인덱스는 unmanaged 상태로 떠다님.

**해결**

인덱스 템플릿의 `settings`에 `policy_id`를 **명시**하면 OpenSearch가
인덱스 생성 시점에 settings로 직접 부여 → 즉시 ISM 인식:

```json
{
  "index_patterns": ["nemonic-app-logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0,
      "plugins.index_state_management.policy_id": "nemonic-app-logs-policy"
    },
    "mappings": { ... }
  }
}
```

`ism_template`은 fallback으로 그대로 두면 redundant하지만 안전.

**일반화된 교훈**

> **`ism_template`만 두면 인덱스 생성 ~ ISM 부착 사이 5분 gap이 생긴다.**
> 인덱스 템플릿 `settings`에 `policy_id`를 명시하여 동기 부착을 보장하라.
> 두 메커니즘 병행 = 즉시성 + fallback.

---

## 부록 A: 디버깅 도구 한 줄 요약

| 도구 | 용도 |
| --- | --- |
| `sudo stat <log-file>` | 로그 파일 Modify 시간 → 진짜 stdout 찍히는지 |
| `docker logs <container> --since 30s` | 최근 30초 로그만 (재시작 후 옛 로그 노이즈 제거) |
| `docker exec <container> ls /etc/...` | 마운트된 설정 파일 확인 |
| Fluent Bit `:2020/api/v1/metrics` | 수집/전송 통계 |
| Kafka `kafka-get-offsets` | 토픽 메시지 수 |
| Kafka `kafka-consumer-groups --describe` | Consumer lag |
| Logstash `:9600/_node/stats/pipelines/main` | events.in / out / filtered |
| Logstash `:9600/_node/hot_threads` | stuck thread 위치 (어디서 멈춰있나) |
| OpenSearch `_cat/indices?v` | 인덱스 + docs.count |
| OpenSearch `_search?size=1&pretty` | 샘플 문서 |

---

## 부록 B: alpine 이미지 셸 도구 부재

`fluent/fluent-bit:3.1`은 distroless 비슷한 minimal 이미지. `cat`, `wget`,
`curl` 모두 없음. 컨테이너 안 디버깅 시:

```bash
# ❌ 안 됨
docker exec nemonic-logging-fluent-bit cat /fluent-bit/etc/fluent-bit.conf

# ✅ 호스트에서 직접 (어차피 같은 파일이 ro 마운트됨)
cat /opt/nemonic/infra/logging/fluent-bit/fluent-bit.conf

# ✅ 임시 컨테이너로 보기
docker run --rm \
  -v /opt/nemonic/infra/logging/fluent-bit:/conf:ro \
  alpine cat /conf/fluent-bit.conf
```

---

## 부록 C: 시간 낭비를 부른 잘못된 가설들

이번 디버깅에서 한참 헛다리 짚은 것들. 같은 함정 피하려고 기록.

1. **"호환성 문제"로 단정짓기 (이슈 9)**
   - 진짜는 단일 노드 설정 누락 (1줄)
   - 호환성 의심 전에 같은 인프라의 다른 도구 (kafka-console-consumer)로
     검증했어야 함. 그게 같은 증상이면 client 문제 아님.

2. **이미지 버전 다운그레이드 시도**
   - apache/kafka 3.5 태그 없음 → 시간 낭비
   - 사용 가능 태그를 먼저 Docker Hub에서 확인했어야 함.

3. **"인프라가 동작하면 데이터도 흐른다"**
   - Spring Boot가 idle하면 데이터 0. 인프라 문제로 오해.
   - synthetic traffic으로 검증 필요.

---

## 부록 D: Phase 4 라이브러리화 시 사용자에게 미리 알릴 것

이 인프라를 다른 사람이 받아서 셋업할 때 README에 명시할 사전 경고:

1. EC2 사전 작업: `sudo sysctl -w vm.max_map_count=262144`
2. 데이터 디렉토리 권한: `sudo chown -R 1000:1000 /opt/nemonic/data/{opensearch,kafka}`
3. 단일 노드 Kafka: 환경변수 3개 (`*_REPLICATION_FACTOR=1`) 필수
4. Logstash 8.9는 Apache Kafka 3.7+ 비호환. Confluent 7.5.x 또는 Apache 3.5
   이하 사용. Apache 공식 이미지는 3.7+만 있으니 결과적으로 Confluent 권장.
5. healthcheck `localhost` 절대 금지, 항상 `127.0.0.1`.
6. Spring Boot access log 활성화 필요 (yaml 예제 첨부).
7. nginx에서 Dashboards는 BasicAuth 보호 필수 (외부 노출 시).
