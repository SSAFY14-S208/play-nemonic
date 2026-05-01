# 8. 로그 수집 및 모니터링

## 전체 아키텍처

중앙집중식 로그 파이프라인은 Fluent Bit, Kafka, OpenSearch, OpenSearch Dashboards로 구성한다.

```text
API Server / WS Server / Nginx / Next.js / MinIO / PostgreSQL / Redis
  -> Fluent Bit
  -> Kafka
  -> OpenSearch
  -> OpenSearch Dashboards
```

## 수집 대상

| 소스 | 로그 유형 | 포맷 | Kafka Topic | OpenSearch Index | 보존 |
| --- | --- | --- | --- | --- | --- |
| Nginx/Ingress | Access, Error | JSON | `logs.nginx.access`, `logs.nginx.error` | `nginx-access-YYYY.MM.DD` | 30일 |
| API Server | 요청/응답, 비즈니스 로직 | JSON | `logs.api` | `api-logs-YYYY.MM.DD` | 30일 |
| WebSocket Server | 연결/해제, 방 이벤트, 메시지 | JSON | `logs.websocket` | `ws-logs-YYYY.MM.DD` | 14일 |
| Next.js | SSR/CSR 렌더링, 에러 | JSON | `logs.frontend` | `frontend-logs-YYYY.MM.DD` | 14일 |
| Client | JS 에러, 성능, 행동 로그 | JSON | `logs.client` | `client-logs-YYYY.MM.DD` | 7일 |
| PostgreSQL | Slow query, error | Text parsing | `logs.db` | `db-logs-YYYY.MM.DD` | 30일 |
| Redis | 선별 커맨드 로그 | Text parsing | `logs.redis` | `redis-logs-YYYY.MM.DD` | 7일 |
| MinIO | Audit, access | JSON | `logs.minio` | `minio-logs-YYYY.MM.DD` | 30일 |
| Backoffice Audit | 운영자 조작 | JSON | `logs.audit` | `audit-logs-YYYY.MM` | 1년 이상 |
| System Metrics | CPU, Memory, Disk, Network | Fluent Bit metrics | `metrics.system` | `system-metrics-YYYY.MM.DD` | 7일 |

## 비즈니스 이벤트 로그

### WebSocket 이벤트

- `room_join`
- `room_leave`
- `round_start`
- `canvas_reassign`
- `drawing_submit`
- `connection_lost`
- `connection_reconnect`

포함 데이터:

- `roomId`
- `uuid`
- `contentType`
- `currentRound`
- `participantCount`
- `isHost`
- `reconnectAttempt`

분석 활용:

- 이탈률
- 라운드별 완주율
- 재접속 성공률
- 방장 이탈 빈도

### 커뮤니티 캔버스 이벤트

- `memo_create`
- `memo_attach`
- `memo_delete`
- `memo_expire_fifo`
- `memo_report`
- `memo_auto_hide`

포함 데이터:

- `memoId`
- `uuid`
- `reportCount`
- `fifoRank`

### 오늘의 운세 GMS 이벤트

- `fortune_request`
- `fortune_gms_success`
- `fortune_gms_retry`
- `fortune_gms_final_fail`

포함 데이터:

- `uuid`
- `gmsLatencyMs`
- `retryCount`
- `promptVersion`
- `sajuPillars`

분석 활용:

- GMS API 응답 지연
- 재시도율
- 최종 실패율

### 무한 캔버스 이벤트

- `canvas_create`
- `canvas_join`
- `canvas_join_rejected`
- `canvas_leave`
- `canvas_expire`
- `element_place`
- `postit_print`

포함 데이터:

- `canvasId`
- `uuid`
- `participantCount`

## 로그 표준화 규약

인프라 로그와 비즈니스 이벤트 로그는 같은 단일 JSON 스키마를 공유한다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `@timestamp` | ISO-8601 string | 필수 | 이벤트 발생 시각, ECS 스타일 |
| `level` | string | 필수 | `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL` |
| `service` | string | 필수 | `api-server`, `ws-server`, `next-ssr`, `client-web`, `client-electron`, `worker-fortune` 등 |
| `trace_id` | string | 필수 | 요청 단위 UUID |
| `session_id` | string | 조건부 | 브라우저 탭 단위 세션 ID |
| `uuid` | string | 조건부 | 익명 사용자 UUID |
| `event_name` | string | 필수 | snake_case 이벤트 이름 |
| `content_type` | string | 조건부 | `community`, `relay`, `flipbook`, `canvas`, `fortune`, `lobby` |
| `room_id` | string | 조건부 | 방/캔버스 고유 ID |
| `prev_zone` | string | 조건부 | `zone_enter`에서 직전 방문 콘텐츠 |
| `message` | string | 선택 | 사람이 읽을 수 있는 설명 |
| `metadata` | object | 선택 | 이벤트별 추가 데이터 |
| `error` | object | 조건부 | `type`, `message`, `stack` |

규칙:

- 이벤트 이름은 snake_case만 사용한다.
- 점 표기와 camelCase 이벤트 이름은 사용하지 않는다.
- 이전 `timestamp`는 `@timestamp`로 통일한다.
- 이전 `traceId`는 `trace_id`로 통일한다.
- 퍼널/분석에 필요한 `metadata` 서브필드는 명시 매핑한다.
- 그 외 임의 필드는 저장만 하고 runtime field로 임시 분석한다.

## Fluent Bit 최적화

- `Mem_Buf_Limit`와 `storage.path`로 메모리 초과 시 디스크 버퍼링
- Kafka 장애 시 로그 유실 방지
- `lz4` 압축으로 CPU와 네트워크 절감
- `Skip_Long_Lines On`
- Tag 기반 라우팅
- K8s labels/annotations 제거로 JSON 크기 절감
- `password`, `cookie`, `authorization` 필드 제거
- Java stacktrace 등 multiline 병합

## Kafka 설정

| Topic | Partition | Retention | Replication | 비고 |
| --- | --- | --- | --- | --- |
| `logs.api` | 6 | 3일 | 2 | 볼륨 큼 |
| `logs.websocket` | 4 | 3일 | 2 | 두 번째로 큼 |
| `logs.nginx.access` | 3 | 3일 | 2 |  |
| `logs.nginx.error` | 2 | 7일 | 2 |  |
| `logs.client` | 3 | 2일 | 2 |  |
| `logs.frontend` | 2 | 3일 | 2 |  |
| `logs.db` | 2 | 7일 | 2 |  |
| `logs.redis` | 1 | 2일 | 2 |  |
| `logs.minio` | 2 | 3일 | 2 |  |
| `logs.audit` | 2 | 400일 | 3 | 감사 로그, `min.insync.replicas: 2` |
| `metrics.system` | 3 | 2일 | 2 |  |

- Partition 수는 Consumer 병렬도와 연결된다.
- `logs.audit`는 유실 불가이므로 replication 3과 min ISR 2를 사용한다.
- Kafka retention은 OpenSearch 저장 전 버퍼 기간으로 짧게 둔다.
- Consumer lag는 Burrow 또는 Kafka Exporter로 모니터링한다.

## OpenSearch 최적화

| 설정 | 효과 |
| --- | --- |
| `refresh_interval: 10s` | 쓰기 처리량 향상 |
| `translog.durability: async` | 로그 특성상 성능 우선 |
| `message.index: false` | 전문 검색 불필요 필드 인덱싱 제외 |
| `metadata` 명시 매핑 + `dynamic: false` | mapping explosion 방지 |
| `codec: best_compression` | 디스크 절감 |
| 대부분 `keyword` | 정확 매칭 중심 |

명시 매핑할 `metadata` 후보:

- `round`
- `canvas_id`
- `frame_index`
- `report_count`
- `fifo_rank`
- `gms_latency_ms`
- `retry_count`
- `participant_count`
- `is_host`
- `reconnect_attempt`

## ILM 정책

| Phase | 조건 | 액션 |
| --- | --- | --- |
| Hot | 활성 인덱스 | 30GB 또는 1일 초과 시 rollover |
| Warm | 3일 경과 | force merge, shrink, read-only |
| Delete | 보존 기간 초과 | 인덱스 삭제 |

## 핵심 대시보드

1. 서비스 전체 현황
   - 접속자 수
   - 콘텐츠 활성 현황
   - 5xx 에러
   - API 응답 시간 P50/P95/P99
2. WebSocket 건강 상태
   - 현재 연결 수
   - 끊김 수 추이
   - 10초 내 재접속 성공률
   - 방별 평균 참여 시간
   - 이탈 이유 분포
3. 오늘의 운세 GMS 모니터링
   - GMS 응답 시간 P50/P95
   - 최종 성공률
   - 재시도율
   - 최종 실패율
   - 프롬프트 버전별 성능
4. 신고 및 제재 현황
   - 시간대별 신고 히트맵
   - 자동 숨김 처리 건수
5. 인프라 모니터링
   - CPU, Memory, Disk
   - Kafka Consumer Lag
   - OpenSearch cluster health

## 알림 규칙

| 조건 | 채널 | 심각도 |
| --- | --- | --- |
| 5xx 에러 > 분당 10건 | MM `#nemonic-alerts` | Warning |
| API P99 > 3000ms | MM `#nemonic-alerts` | Warning |
| WebSocket 끊김 > 분당 50건 | MM `#nemonic-alerts-critical` + 이메일 | Critical |
| GMS 최종 실패율 > 20% | MM `#nemonic-alerts-critical` + 이메일 | Critical |
| GMS 재시도율 > 40% | MM `#nemonic-alerts` | Warning |
| Kafka Consumer Lag > 100K | MM `#nemonic-infra-alerts` | Warning |
| OpenSearch 상태 != Green | MM `#nemonic-infra-alerts` | Warning |
| 디스크 사용률 > 80% | MM `#nemonic-infra-alerts` | Warning |
| CS 문의 미처리 > 24시간 | MM `#nemonic-cs` + 이메일 | Warning |

## 로그 볼륨 예측

| 소스 | 일 평균 | 월 OpenSearch 저장량, 압축 후 |
| --- | --- | --- |
| WebSocket 이벤트 | 500MB/일 | 10GB |
| API 요청 로그 | 300MB/일 | 6GB |
| Nginx Access | 200MB/일 | 4GB |
| 클라이언트 로그 | 100MB/일 | 1.5GB |
| 기타 | 100MB/일 | 2GB |
| 합계 | 1.2GB/일 | 25GB/월 |

비용 절감:

- 정상 200 응답 로그는 10% 샘플링
- 4xx/5xx는 100% 수집
- `request_body`, `response_body`는 에러 시에만 수집
- 30일 지난 인덱스는 S3 snapshot cold archive

## 파이프라인 장애 대응

| 장애 | 대응 |
| --- | --- |
| Fluent Bit -> Kafka 끊김 | 디스크 버퍼 저장 후 복구 시 재전송 |
| Kafka 브로커 다운 | replication 2 이상으로 남은 브로커 서비스 지속 |
| OpenSearch 다운 | Kafka retention 동안 로그 보존 후 복구 시 소비 |
| Consumer Lag 급증 | Consumer 수 scale-out |
| OpenSearch 디스크 풀 | ILM delete 또는 Curator 긴급 삭제 |

## 비즈니스 이벤트 적용

비즈니스 이벤트도 공통 로그 스키마를 사용한다.

필수 성격:

- `@timestamp`
- `uuid`
- `session_id`
- `trace_id`
- `event_name`
- `content_type`
- 조건부 `room_id`
- 조건부 `prev_zone`
- 선택 `metadata`

`session_id`는 브라우저 탭 단위 세션 ID이다.

활용:

- 세션 단위 퍼널 분석
- 체류 시간 계산
- 동일 UUID의 동시 세션 감지

예시:

```json
{
  "@timestamp": "2026-04-21T17:30:00+09:00",
  "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "session_id": "s9k8j7h6-g5f4-d3c2-b1a0-z9y8x7w6v5u4",
  "trace_id": "trace-uuid",
  "event_name": "round_complete",
  "content_type": "relay",
  "room_id": "ROOM-ABC123",
  "prev_zone": null,
  "metadata": {
    "round": 2,
    "part": "body",
    "time_spent_ms": 28500
  }
}
```

## 인덱스 분리 및 리텐션

| 인덱스 패턴 | 대상 | 설명 |
| --- | --- | --- |
| `biz-events-YYYY.MM.DD` | 비즈니스 이벤트 | 퍼널, 전환율, 이탈률 |
| `system-logs-YYYY.MM.DD` | 시스템/인프라 | CPU, Memory, WebSocket, Kafka, OpenSearch |
| `error-logs-YYYY.MM.DD` | 에러/예외 | 장애 추적 |
| `access-logs-YYYY.MM.DD` | 접근 로그 | HTTP/API 트래픽 |

| 인덱스 | Hot | Warm | 총 보관 |
| --- | --- | --- | --- |
| `biz-events-*` | 7일 | 83일 | 90일 |
| `system-logs-*` | 3일 | 27일 | 30일 |
| `error-logs-*` | 7일 | 83일 | 90일 |
| `access-logs-*` | 3일 | 11일 | 14일 |

Hot-Warm 아키텍처:

- Hot 노드는 SSD와 최신 데이터용이다.
- Warm 노드는 HDD와 과거 데이터 조회용이다.
- ILM이 Hot, Warm, Delete를 자동 관리한다.
- 긴급 상황에서는 Curator CLI로 특정 인덱스를 삭제하거나 강제 이동한다.
