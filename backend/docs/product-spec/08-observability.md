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

## 백엔드/프론트엔드 수집 책임 분리

두 로그 문서는 같은 표준 JSON 스키마와 OpenSearch 파이프라인을 공유하지만, 같은 사용자 행동을 양쪽에서 중복 이벤트로 남기지 않는다.

원칙:

- **백엔드 수집**: API 요청/응답, HTTP status, latency, 서버 예외, 서버가 성공/실패를 확정한 도메인 결과, WebSocket 연결/해제, 운영자 감사 로그, 외부 연동(GMS/MinIO/DB/Redis) 처리 결과.
- **프론트엔드 수집**: 유입 경로, 캠페인/공유 링크 attribution, 페이지 이동, CTA 클릭, 사용자가 시작한 플로우, 단계별 전환, 단계 이탈, 체류 시간, visibility, Web Vitals, 브라우저 JS 오류.
- 프론트엔드는 API 성공/실패, HTTP status, API latency를 별도 비즈니스 이벤트로 수집하지 않는다. 네트워크 단절, 브라우저 abort, CORS 등 서버에 도달하지 못한 클라이언트 전송 실패만 프론트엔드 오류로 남긴다.
- 동일한 흐름을 연결해야 할 때는 이벤트 이름을 중복하지 않고 `trace_id`, `session_id`, `uuid`, `room_id`로 상관 분석한다.
- 전환율, 이탈률, 유입 출처 분석은 프론트엔드 로깅 문서(`08B-frontend-logging.md`)의 funnel/acquisition 이벤트를 기준으로 한다.

## 수집 대상

OpenSearch에는 소스별 인덱스를 두지 않고, Kafka topic 단위로 수집한 뒤 Fluent Bit/Logstash 라우팅 단계에서 아래 5개 도메인 인덱스로 분류한다. 보존 기간은 “인덱스 분리 및 리텐션” 섹션이 단일 출처(single source of truth)다.

| 소스 | 로그 유형 | 포맷 | Kafka Topic | 라우팅 대상 인덱스 |
| --- | --- | --- | --- | --- |
| Nginx/Ingress | Access | JSON | `logs.nginx.access` | `access-logs-*` |
| Nginx/Ingress | Error | JSON | `logs.nginx.error` | `error-logs-*` |
| API Server | 요청/응답 access | JSON | `logs.api` | `access-logs-*` |
| API Server | 서버 확정 도메인 이벤트 | JSON | `logs.api` | `biz-events-*` |
| API Server | 서버 예외 (`level >= ERROR`) | JSON | `logs.api` | `error-logs-*` |
| WebSocket Server | 연결/해제, 서버 확정 방 이벤트 | JSON | `logs.websocket` | `biz-events-*` |
| WebSocket Server | 처리 오류 (`level >= ERROR`) | JSON | `logs.websocket` | `error-logs-*` |
| Next.js | SSR 렌더링/서버 컴포넌트 오류 | JSON | `logs.frontend` | `error-logs-*` |
| Client | 유입, 전환, 이탈, funnel, Web Vitals | JSON | `logs.client` | `biz-events-*` |
| Client | JS error, unhandled rejection, network failure | JSON | `logs.client` | `error-logs-*` |
| PostgreSQL | Slow query, error | Text parsing | `logs.db` | `error-logs-*` |
| Redis | 선별 커맨드 로그 | Text parsing | `logs.redis` | `system-logs-*` |
| MinIO | Audit | JSON | `logs.minio` | `audit-logs-*` |
| MinIO | Access | JSON | `logs.minio` | `access-logs-*` |
| Backoffice Audit | 운영자 조작 | JSON | `logs.audit` | `audit-logs-*` |
| System Metrics | CPU, Memory, Disk, Network | Fluent Bit metrics | `metrics.system` | `system-logs-*` |

### 라우팅 규칙

Fluent Bit 출력 단계에서 다음 우선순위로 도메인 인덱스를 결정한다. 위 항목이 먼저 매칭되면 아래는 평가하지 않는다.

1. `service == backoffice-api` 이고 `event_name`이 감사 로그 목록에 포함 → `audit-logs-*`
2. Kafka topic == `logs.audit` 또는 `logs.minio` 의 audit 분기 → `audit-logs-*`
3. `level in {ERROR, FATAL}` → `error-logs-*`
4. `event_name`이 비즈니스 이벤트 allow-list에 포함 (백엔드 도메인 이벤트, 프론트엔드 funnel/유입/이탈 이벤트) → `biz-events-*`
5. Nginx/MinIO access, API access 로그 → `access-logs-*`
6. 그 외 인프라/메트릭 → `system-logs-*`

allow-list는 본 문서의 “백엔드 비즈니스 이벤트 로그” 섹션과 `08B-frontend-logging.md`의 funnel/acquisition 이벤트 정의에서 자동 생성한다. 새 이벤트는 PR 머지 전 allow-list 갱신 없이는 `biz-events-*`로 적재되지 않고 `system-logs-*`로 떨어진다(누락 감지 가능).

## 백엔드 비즈니스 이벤트 로그

백엔드 이벤트는 “사용자가 의도했다”가 아니라 “서버가 처리 결과를 확정했다”는 사실만 기록한다. 버튼 클릭, 페이지 진입, 폼 입력 시작, 이탈은 프론트엔드 이벤트로 남긴다.

### API 처리 이벤트

- `api_request_completed`
- `api_request_failed`
- `api_validation_failed`
- `api_unauthorized`
- `api_forbidden`
- `api_rate_limited`

포함 데이터:

- `method`
- `path_template`
- `status`
- `latency_ms`
- `trace_id`
- `uuid` 또는 `admin_id`(존재하는 경우)
- `error_code`(실패 시)

분석 활용:

- API 응답 시간 P50/P95/P99
- status별 오류율
- 기능별 서버 실패율
- 프론트엔드 funnel 이탈 지점과 서버 실패의 상관 분석

### WebSocket/방 처리 이벤트

- `relay_room_created`
- `relay_room_settings_changed`
- `relay_participant_joined`
- `relay_participant_left`
- `relay_participant_kicked`
- `relay_host_changed`
- `relay_ws_connected`
- `relay_ws_reconnected`
- `relay_ws_disconnected`
- `relay_ws_connection_rejected`
- `relay_duplicate_session_closed`
- `relay_room_state_snapshot_sent`
- `relay_start_rejected`
- `relay_game_started`
- `relay_part_started`
- `relay_part_time_up`
- `relay_drawing_submitted`
- `relay_submission_rejected`
- `relay_part_auto_submitted`
- `relay_participant_dropped`
- `relay_all_parts_completed`
- `relay_result_created`
- `relay_room_closed`
- `relay_temp_cleanup_completed`
- `flipbook_round_started`
- `flipbook_frame_submitted`
- `flipbook_room_finished`
- `flipbook_room_closed`

포함 데이터:

- `room_id`
- `uuid`
- `content_type`
- `current_round`
- `participant_count`
- `is_host`
- `disconnect_reason`
- `reconnect_attempt`

분석 활용:

- 서버 기준 실제 연결/해제 수
- 라운드별 서버 처리 완료율
- 재접속 성공률
- 방장 이탈 빈도

### 커뮤니티/캔버스 서버 처리 이벤트

- `community_memo_presign_requested`
- `community_memo_upload_confirmed`
- `community_memo_upload_deleted`
- `community_repository_query_failed`
- `community_memo_list_viewed`
- `community_memo_detail_viewed`
- `community_memo_detail_not_found`
- `community_memo_create_requested`
- `community_memo_create_validation_failed`
- `community_memo_moderation_requested`
- `community_memo_moderation_allowed`
- `community_memo_moderation_blocked`
- `community_memo_moderation_failed`
- `community_memo_moderation_slow`
- `community_memo_created`
- `community_memo_fifo_checked`
- `community_memo_fifo_skipped`
- `community_memo_fifo_expired`
- `community_memo_layout_update_requested`
- `community_memo_layout_update_denied`
- `community_memo_layout_update_failed`
- `community_memo_layout_updated`
- `community_memo_delete_requested`
- `community_memo_delete_denied`
- `community_memo_delete_failed`
- `community_memo_user_deleted`
- `community_memo_report_requested`
- `community_memo_report_rejected`
- `community_memo_report_created`
- `community_memo_report_threshold_reached`
- `community_memo_auto_hidden_by_report`
- `community_api_slow_request`
- `community_admin_query_failed`
- `admin_community_memo_list_viewed`
- `admin_community_memo_detail_viewed`
- `admin_community_memo_reports_viewed`
- `admin_community_memo_search_failed`
- `admin_community_memo_hide_requested`
- `admin_community_memo_hidden`
- `admin_community_memo_hide_noop`
- `admin_community_memo_restore_requested`
- `admin_community_memo_restored`
- `admin_community_memo_restore_noop`
- `admin_access_denied`
- `admin_forbidden`
- `admin_token_invalid`
- `canvas_created`
- `canvas_joined`
- `canvas_join_rejected`
- `canvas_expired`
- `canvas_element_saved`

포함 데이터:

- `memo_id`
- `canvas_id`
- `uuid`
- `source_type`
- `artifact_id`
- `original_file_id`
- `thumbnail_file_id`
- `moderation_status`
- `hidden_reason`
- `deleted_reason`
- `report_count`
- `fifo_rank`
- `reject_reason`

### 오늘의 운세/GMS 처리 이벤트

- `fortune_created`
- `fortune_reissued`
- `fortune_gms_succeeded`
- `fortune_gms_retried`
- `fortune_gms_failed`

포함 데이터:

- `uuid`
- `gms_latency_ms`
- `retry_count`
- `prompt_version`
- `result`

분석 활용:

- GMS API 응답 지연
- 재시도율
- 최종 실패율

### 백엔드에서 수집하지 않는 이벤트

아래 항목은 프론트엔드 문서에서만 정의한다.

- `page_view`, `page_leave`, `session_start`, `session_end`
- `cta_clicked`, `funnel_step_viewed`, `funnel_step_completed`, `funnel_abandoned`
- `landing_source_detected`, `campaign_attributed`, `share_link_opened`
- `scroll_depth_reached`, `visibility_change`, `client_alive`
- 버튼 클릭, 입력 시작, 모달 노출, 클라이언트 라우트 이동, exit intent

## 감사 로그

운영자 조작 이력은 RDB에 저장하지 않고 로그 파이프라인으로만 적재한다. 백오피스 감사 로그 화면도
별도 테이블을 조회하지 않고 OpenSearch `audit-logs-*` 인덱스를 직접 질의한다.

### 저장 정책

- 백오피스 API 서버가 운영자 조작 핸들러에서 구조화 JSON 로그를 stdout으로 emit한다.
- Fluent Bit → Kafka `logs.audit` → OpenSearch `audit-logs-YYYY.MM` 경로로 흐른다.
- 감사 로그 전용 RDB 테이블은 두지 않는다.
- 단, 도메인 결과 자체를 표현하는 컬럼(`deleted_at`, `deleted_reason`, `is_hidden`, `reviewed_at`, `reviewed_by` 등)은
  업무 정합성을 위해 기존 테이블에 그대로 유지한다. 감사 로그는 그 변경 *행위*를 기록하는 별도 트레일이다.
- 영구 보존 대상이며 ILM delete phase를 두지 않는다.

### 기록 대상 이벤트

- `admin_login_success`, `admin_logout`, `admin_login_failed`
- `memo_soft_delete`, `memo_restore`, `memo_bulk_soft_delete`, `memo_bulk_restore`
- `report_review_decided`
- `relay_room_force_close`, `flipbook_room_force_close`
- `infinite_canvas_force_close`
- `ai_moderation_override`
- `param_change`
- `prompt_update`, `prompt_rollback`
- `inquiry_status_change`, `inquiry_reply_send`, `inquiry_internal_memo`
- `notification_send`
- `electron_channel_change`, `electron_release_publish`
- `admin_account_create`, `admin_account_delete` (슈퍼 관리자 전용)
- `audit_export`

### 스키마 매핑

공통 로그 스키마를 그대로 사용하며, 감사 전용 필드는 `metadata`에 명시 매핑한다.

| 위치 | 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| top | `service` | string | 필수 | `backoffice-api` 고정 |
| top | `level` | string | 필수 | 정상은 `INFO`, 실패는 `WARN` 또는 `ERROR` |
| top | `event_name` | string | 필수 | 위 목록의 snake_case |
| metadata | `actor_id` | string | 필수 | 관리자 계정 ID |
| metadata | `actor_role` | string | 필수 | `super_admin` 또는 `admin` |
| metadata | `actor_ip` | string | 필수 | 운영자 접속 IP |
| metadata | `target_type` | string | 필수 | `memo`, `room`, `canvas`, `param`, `prompt`, `inquiry`, `admin_account`, `notification` 등 |
| metadata | `target_id` | string | 필수 | 대상 식별자, 일괄 작업은 대표 ID 또는 `bulk:<count>` |
| metadata | `action` | string | 필수 | `create`, `update`, `delete`, `restore`, `force_close`, `login`, `logout`, `rollback`, `send` 등 |
| metadata | `reason` | string | 조건부 | 운영자 입력 사유 (파라미터 변경, 강제 종료, 삭제, 복원 시 필수) |
| metadata | `before` | object | 조건부 | 변경 전 값 (update, rollback) |
| metadata | `after` | object | 조건부 | 변경 후 값 (update, rollback) |
| metadata | `result` | string | 필수 | `success`, `failure` |

### 마스킹 규칙

- 비밀번호, 세션 토큰, JWT, API key는 어떤 필드에도 포함하지 않는다.
- Mattermost Webhook URL과 SMTP 자격 증명은 원문 대신 채널명/계정 식별자만 기록한다.
- CS 문의 회신 이메일 본문은 길이와 첨부 수만 기록하고 본문은 남기지 않는다.
- 사용자 UUID는 이미 익명 식별자이므로 마스킹하지 않는다.

### 무결성 보장

- 운영자 조작 핸들러는 비즈니스 트랜잭션 커밋 직후 emit한다. 트랜잭션 롤백 시에는 emit하지 않는다.
- Kafka `logs.audit`는 `acks=all`, replication 3, `min.insync.replicas: 2`로 단일 노드 장애에도 유실을 막는다.
- Fluent Bit 디스크 버퍼로 Kafka 일시 장애 시에도 큐잉한다.
- 로그 emit 실패는 `audit_log_emit_failure` 카운터로 노출하고, 임계 초과 시 `#nemonic-alerts-critical`로 알림한다.

### 조회 및 내보내기

- 백오피스 감사 로그 화면은 OpenSearch DSL로 직접 조회한다.
- 필터: `actor_id`, `event_name`, `action`, `target_type`, `result`, 날짜 범위
- CSV 다운로드는 OpenSearch scroll 또는 PIT API 결과를 변환해 제공한다.
- 다운로드 자체도 `event_name = audit_export`로 감사 로그에 기록한다.

### 예시

```json
{
  "@timestamp": "2026-05-04T14:22:31+09:00",
  "level": "INFO",
  "service": "backoffice-api",
  "trace_id": "5fa1c8a0-9e6b-4c9d-8b0d-9b0a7c6e5d4f",
  "event_name": "memo_soft_delete",
  "message": "admin removed reported memo",
  "metadata": {
    "actor_id": "admin.lee",
    "actor_role": "admin",
    "actor_ip": "10.10.20.31",
    "target_type": "memo",
    "target_id": "memo-7c8d9e",
    "action": "delete",
    "reason": "혐오 표현",
    "before": { "is_hidden": true, "deleted_at": null },
    "after": {
      "is_hidden": true,
      "deleted_at": "2026-05-04T14:22:31+09:00",
      "deleted_reason": "admin_removed"
    },
    "result": "success"
  }
}
```

## 로그 표준화 규약

인프라 로그와 비즈니스 이벤트 로그는 같은 단일 JSON 스키마를 공유한다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `@timestamp` | ISO-8601 string | 필수 | 이벤트 발생 시각, ECS 스타일. UTC offset 명시 필수, 프로젝트 표준은 KST(`+09:00`) |
| `level` | string | 필수 | `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL` |
| `service` | string | 필수 | `api-server`, `ws-server`, `next-ssr`, `client-web`, `client-electron`, `worker-fortune` 등 |
| `trace_id` | string | 필수 | 단일 HTTP 요청/WS 메시지 단위 UUID. 프론트엔드는 fetch마다 새로 발급해 `X-Trace-Id` 헤더로 전달, 백엔드는 동일 값을 그 요청의 모든 로그에 사용한다. |
| `flow_id` | string | 조건부 | 프론트엔드 funnel 단위 UUID. `funnel_started`에서 발급해 `funnel_goal_reached` 또는 `funnel_abandoned`까지 유지. 같은 funnel 안에서 발생한 fetch 요청은 헤더로 전달되어 백엔드 로그에도 보존된다. funnel을 가로지르는 분석의 join key. |
| `session_id` | string | 조건부 | 브라우저 탭 단위 세션 ID. 30분 idle 후 재발급. |
| `uuid` | string | 조건부 | 익명 사용자 UUID. localStorage 기반이므로 storage clear 시 단절될 수 있다. |
| `event_name` | string | 필수 | snake_case 이벤트 이름 |
| `content_type` | string | 조건부 | `community`, `relay`, `flipbook`, `canvas`, `fortune`, `lobby` |
| `room_id` | string | 조건부 | 방/캔버스 고유 ID |
| `prev_zone` | string | 조건부 | `zone_enter`에서 직전 방문 `content_type` (콘텐츠 단위) |
| `prev_path` | string | 조건부 | `page_view`/`page_leave`에서 직전 정규화된 route (route 단위) |
| `message` | string | 선택 | 사람이 읽을 수 있는 설명 |
| `metadata` | object | 선택 | 이벤트별 추가 데이터 |
| `error` | object | 조건부 | `type`, `message`, `stack`. stack은 PII sanitization 후 적재 |

규칙:

- 이벤트 이름은 snake_case만 사용한다.
- 점 표기와 camelCase 이벤트 이름은 사용하지 않는다.
- 이전 `timestamp`는 `@timestamp`로 통일한다.
- 이전 `traceId`는 `trace_id`로 통일한다.
- `trace_id`는 “요청 단위”, `flow_id`는 “funnel 단위”다. 두 키를 혼동하지 말 것.
- funnel과 백엔드 처리를 join할 때는 `flow_id`를 우선 사용하고, 누락된 경우에만 `(session_id, funnel_name, time window)`로 보완한다.
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

명시 매핑할 top-level/`metadata` 후보:

top-level (모든 인덱스 공통):

- `flow_id` — funnel join key, `keyword`
- `funnel_name`, `step_name`, `step_index` — funnel 분석 키
- `path` (정규화), `prev_path`, `entry_type` — 유입/이탈 분석

metadata:

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
- `actor_id`
- `actor_role`
- `actor_ip`
- `target_type`
- `target_id`
- `action`
- `reason`
- `result`

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
| `js_error` + `unhandled_rejection` 합계 > 분당 30건 | MM `#nemonic-alerts` | Warning |
| `client_network_failed` > 분당 50건 (서버 5xx 동반 아님) | MM `#nemonic-alerts` | Warning |
| LCP P75 > 2500ms (10분 평균, 표본 N >= 100) | MM `#nemonic-alerts` | Warning |
| INP P75 > 200ms (10분 평균, 표본 N >= 100) | MM `#nemonic-alerts` | Warning |
| 핵심 funnel `funnel_abandoned` 비율 > 직전 7일 baseline +20%p | MM `#nemonic-alerts` | Warning |
| Kafka Consumer Lag > 100K | MM `#nemonic-infra-alerts` | Warning |
| OpenSearch 상태 != Green | MM `#nemonic-infra-alerts` | Warning |
| 디스크 사용률 > 80% | MM `#nemonic-infra-alerts` | Warning |
| `audit_log_emit_failure` > 0 (5분 이상) | MM `#nemonic-alerts-critical` + 이메일 | Critical |
| CS 문의 미처리 > 24시간 | MM `#nemonic-cs` + 이메일 | Warning |

분포 지표(P75 등)는 표본 부족 시 변동성이 커지므로 알림 평가 윈도우 안에서 표본 N이 임계 미만이면 평가를 건너뛴다(false alert 방지).

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

## 백엔드 비즈니스 이벤트 적용

백엔드 비즈니스 이벤트도 공통 로그 스키마를 사용하되, 프론트엔드 여정 분석 필드를 직접 생산하지 않는다.

필수 성격:

- `@timestamp`
- `trace_id`
- `event_name`
- `content_type`
- 선택 `uuid`
- 조건부 `room_id`
- 선택 `metadata`

`session_id`, `flow_id`, `prev_zone`, `prev_path`, `referrer`, `utm_*`, `funnel_step`은 프론트엔드가 생산한 값이 요청 헤더(`X-Trace-Id`, `X-Flow-Id`) 또는 body로 전달된 경우 상관 분석용으로 그대로 보존한다. 서버가 자체 추정해서 채우지 않는다.

활용:

- 서버 확정 처리 결과 분석
- API/WS 장애와 프론트엔드 이탈 지점 상관 분석
- 서버 기준 도메인 성공률 계산

예시:

```json
{
  "@timestamp": "2026-04-21T17:30:00+09:00",
  "level": "INFO",
  "service": "ws-server",
  "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "trace_id": "8e1c4a2b-1f5d-4a90-9ab2-7c3d6e5f0011",
  "flow_id": "5f3a8e2c-9b71-4d4e-8aa1-12c34a55eeff",
  "session_id": "tab-7c8d9e",
  "event_name": "relay_drawing_submitted",
  "content_type": "relay",
  "room_id": "ROOM-ABC123",
  "metadata": {
    "round": 2,
    "part": "body",
    "participant_count": 4,
    "status": "success"
  }
}
```

## 인덱스 분리 및 리텐션

| 인덱스 패턴 | 대상 | 설명 |
| --- | --- | --- |
| `biz-events-YYYY.MM.DD` | 백엔드/프론트엔드 비즈니스 이벤트 | 서버 처리 결과, 프론트엔드 퍼널, 전환율, 이탈률 |
| `system-logs-YYYY.MM.DD` | 시스템/인프라 | CPU, Memory, WebSocket, Kafka, OpenSearch |
| `error-logs-YYYY.MM.DD` | 에러/예외 | 장애 추적 |
| `access-logs-YYYY.MM.DD` | 접근 로그 | HTTP/API 트래픽 |
| `audit-logs-YYYY.MM` | 운영자 조작 | 백오피스 감사 트레일, 영구 보존 |

| 인덱스 | Hot | Warm | 총 보관 |
| --- | --- | --- | --- |
| `biz-events-*` | 7일 | 83일 | 90일 |
| `system-logs-*` | 3일 | 27일 | 30일 |
| `error-logs-*` | 7일 | 83일 | 90일 |
| `access-logs-*` | 3일 | 11일 | 14일 |
| `audit-logs-*` | 30일 | 335일+ | 영구 (Cold archive) |

Hot-Warm 아키텍처:

- Hot 노드는 SSD와 최신 데이터용이다.
- Warm 노드는 HDD와 과거 데이터 조회용이다.
- ILM이 Hot, Warm, Delete를 자동 관리한다.
- 긴급 상황에서는 Curator CLI로 특정 인덱스를 삭제하거나 강제 이동한다.
