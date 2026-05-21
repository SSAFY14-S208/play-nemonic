# 8B. 프론트엔드 로깅 가이드

이 문서는 `backend/docs/product-spec/08-observability.md`의 자매 문서다. 공통 JSON 스키마와 로그 파이프라인은 공유하지만, 프론트엔드는 백엔드와 같은 이벤트를 중복 수집하지 않고 **유입, 전환, 이탈, 화면 체류, 브라우저 오류**에 집중한다.

## 1. 목적과 책임 경계

프론트엔드 로그의 목적은 “사용자가 어디서 들어와서, 어떤 화면을 거쳐, 어느 단계에서 전환하거나 이탈했는지”를 설명하는 것이다. API 성공/실패, HTTP status, latency, 서버 도메인 결과는 백엔드 observability 문서의 책임이다.

| 구분 | 프론트엔드에서 수집 | 백엔드에서 수집 |
| --- | --- | --- |
| 유입 | referrer, UTM, 공유 링크, 랜딩 path | Nginx/API access log |
| 전환 | CTA 클릭, funnel step 진입/완료, 결과 화면 도달 | API 요청 성공/실패, 도메인 처리 결과 |
| 이탈 | page leave, funnel abandon, visibility hidden, exit intent | WebSocket disconnect, timeout, 서버 오류 |
| 성능 | Web Vitals, 느린 정적 리소스 | API latency, DB/GMS/MinIO latency |
| 오류 | JS error, unhandled rejection, 서버 미도달 network failure | 4xx/5xx, validation, auth, 서버 예외 |

중복 방지 규칙:

- 프론트엔드는 `api_request`, `api_error`, HTTP status, API latency 이벤트를 수집하지 않는다.
- 프론트엔드는 `room_joined`, `memo_created`, `fortune_created`처럼 서버가 성공을 확정하는 이벤트를 만들지 않는다.
- 프론트엔드는 `room_join_clicked`, `memo_editor_started`, `fortune_submit_clicked`처럼 사용자 의도와 화면 전환만 기록한다.
- 같은 사용자 흐름은 `trace_id`, `session_id`, `uuid`, `room_id`로 백엔드 이벤트와 연결한다.

## 2. 공유 로그 스키마

공통 필드는 백엔드 observability 문서의 `로그 표준화 규약`을 따른다. 프론트엔드는 아래 방식으로 채운다.

| 필드 | 프론트엔드 값 | 비고 |
| --- | --- | --- |
| `@timestamp` | `new Date().toISOString()` 후 KST(`+09:00`)로 normalize | UTC offset 명시 필수, 프로젝트 표준은 KST |
| `level` | `INFO`, `WARN`, `ERROR` | 일반 이벤트는 `INFO` |
| `service` | `client-web`, `next-ssr`, `client-electron` | 브라우저는 기본 `client-web` |
| `trace_id` | 단일 fetch/요청 단위 UUID | 매 fetch마다 새로 발급, `X-Trace-Id` 헤더로 전달. 라우트 또는 사용자 액션 단위로 “회전”하지 않는다. |
| `flow_id` | funnel 단위 UUID | `funnel_started`에서 발급, `funnel_goal_reached`/`funnel_abandoned`까지 유지. 같은 funnel 안의 모든 이벤트와 fetch 헤더(`X-Flow-Id`)에 동일 값 부여 |
| `session_id` | 브라우저 탭 단위 UUID | `sessionStorage`. 30분 idle 후 동일 탭에서도 새 ID로 회전 |
| `uuid` | 익명 사용자 UUID | `localStorage`. storage clear 시 신규 발급되어 동일 사용자 매칭이 단절될 수 있음을 분석에서 인지 |
| `event_name` | snake_case | 아래 정의된 이벤트만 사용 |
| `content_type` | `landing`, `hub`, `community`, `relay`, `flipbook`, `canvas`, `fortune`, `gallery`, `backoffice` | 현재 화면/기능 |
| `room_id` | room/canvas code | 해당 화면에서만 |
| `metadata` | 이벤트별 추가 값 | 명시 필드 우선 |
| `error` | `{type, message, stack}` | 오류 이벤트에서만. stack은 PII sanitization 후 |

`trace_id` vs `flow_id` 정리:

- `trace_id` — 단일 HTTP/WS 트랜잭션. 백엔드 access/error 로그와 1:1 join.
- `flow_id` — 사용자 funnel 한 번. 여러 `trace_id`를 묶는 상위 키. funnel 분석의 기본 join key.
- 둘 다 같은 fetch 호출에 헤더로 동시에 전달된다. 백엔드는 두 값을 그 요청의 모든 로그에 그대로 보존만 한다.

프론트엔드 공통 metadata:

| 필드 | 설명 |
| --- | --- |
| `path` | 정규화된 현재 route. 예: `/share/:token`, `/flipbook/rooms/:roomCode` |
| `prev_path` | 직전 route |
| `referrer` | 최초 진입 referrer |
| `utm_source`, `utm_medium`, `utm_campaign`, `utm_content`, `utm_term` | 캠페인 파라미터 |
| `entry_type` | `direct`, `search`, `social`, `qr`, `share`, `campaign`, `unknown` |
| `viewport` | `{width, height}` |
| `platform` | `desktop`, `mobile`, `tablet` |
| `locale` | 예: `ko-KR` |
| `network` | `4g`, `3g`, `wifi`, `unknown` |

## 3. 유입 분석 이벤트

유입 이벤트는 전환율 분석의 시작점이므로 100% 수집한다.

| event_name | 발생 시점 | 필수 metadata |
| --- | --- | --- |
| `landing_source_detected` | 첫 페이지 로드 직후 | `path`, `referrer`, `entry_type`, `utm_*` |
| `campaign_attributed` | UTM 또는 캠페인 키 확인 시 | `utm_source`, `utm_medium`, `utm_campaign` |
| `share_link_opened` | 공유 링크/QR 링크로 진입 | `share_type`, `source_content_type`, `source_id?` |
| `install_prompt_shown` | PWA/install 안내 노출 | `path` |
| `install_prompt_accepted` | 설치 안내 수락 | `path` |
| `install_prompt_dismissed` | 설치 안내 닫힘 | `path` |

`entry_type` 판정 예:

- `utm_*` 존재: `campaign`
- 공유 path 또는 invite/share token path: `share`
- QR 전용 parameter 존재: `qr`
- referrer가 검색 엔진: `search`
- referrer가 소셜 도메인: `social`
- referrer 없음: `direct`

## 4. 세션/페이지 이벤트

| event_name | 발생 시점 | 필수 metadata |
| --- | --- | --- |
| `session_start` | 새 `session_id` 발급 시 | `path`, `referrer`, `entry_type`, `viewport`, `platform` |
| `session_end` | `pagehide` 또는 탭 종료 | `duration_ms`, `last_path` |
| `page_view` | route 변경 시 | `path`, `prev_path`, `content_type` |
| `page_leave` | route 이탈 직전 | `path`, `time_on_page_ms`, `next_path?` |
| `visibility_change` | `document.visibilitychange` | `state`, `time_visible_ms`, `path` |
| `client_alive` | 활성 탭 30초, 비활성 탭 5분 | `path`, `content_type`, `time_in_session_ms` |

## 5. Funnel/전환 이벤트

모든 서비스는 공통 funnel 이벤트를 사용한다. 서비스별 세부 의미는 metadata의 `funnel_name`, `step_name`, `goal_name`으로 구분한다.

| event_name | 발생 시점 | 필수 metadata |
| --- | --- | --- |
| `funnel_started` | 전환 플로우 시작 | `funnel_name`, `entry_path`, `entry_type` |
| `funnel_step_viewed` | 단계 화면/모달 노출 | `funnel_name`, `step_name`, `step_index` |
| `funnel_step_completed` | 다음 단계로 UI 전환 완료 | `funnel_name`, `step_name`, `step_index` |
| `funnel_goal_reached` | 핵심 목표 화면 도달 | `funnel_name`, `goal_name` |
| `funnel_abandoned` | 완료 전 이탈 판단 | `funnel_name`, `last_step_name`, `reason` |
| `cta_clicked` | 주요 CTA 클릭 | `cta_id`, `path`, `funnel_name?` |

권장 funnel:

| funnel_name | 핵심 단계 | goal_name |
| --- | --- | --- |
| `relay_room_creation` | `landing`, `nickname`, `settings`, `lobby`, `drawing`, `result` | `result_viewed` |
| `flipbook_room_creation` | `landing`, `nickname`, `settings`, `lobby`, `drawing`, `result` | `result_viewed` |
| `community_memo_posting` | `canvas_view`, `editor_open`, `image_select`, `preview`, `posted` | `memo_post_screen_reached` |
| `fortune_creation` | `landing`, `birth_info`, `theme_select`, `loading`, `result` | `fortune_result_viewed` |
| `gallery_save_share` | `result_view`, `save_click`, `gallery_view`, `share_click` | `share_clicked` |

주의:

- `funnel_started`는 새 `flow_id`를 발급하고, 같은 funnel이 종료(`funnel_goal_reached` 또는 `funnel_abandoned`)될 때까지 후속 이벤트와 fetch 헤더에 동일 `flow_id`를 부여한다.
- `funnel_started` 직후 step 1 진입을 별도 `funnel_step_viewed`로 중복 발생시키지 않는다. `funnel_started`가 step 1 진입을 의미한다(분석 시 `step_index=0`로 취급).
- `funnel_step_completed`는 “프론트 화면이 다음 단계로 넘어갔다”는 의미다. 서버 저장 성공 자체는 백엔드 이벤트로 본다.
- API 응답 status나 latency를 metadata에 넣지 않는다. 같은 funnel의 백엔드 처리 결과는 `flow_id`로 join하고, 단일 요청 단위로 보고 싶을 때는 `trace_id`로 join한다.
- 이미 진행 중인 funnel을 사용자가 처음부터 다시 시작하면 새 `flow_id`를 발급한다. 이전 `flow_id`는 그 시점에 `funnel_abandoned(reason=route_change|back_navigation)`로 명시적으로 닫는다.

## 6. 이탈 이벤트

이탈은 “서버 연결이 끊겼다”보다 넓은 사용자 행동이다. 프론트엔드는 사용자가 플로우를 멈춘 화면과 이유 추정에 집중한다.

| event_name | 발생 시점 | 필수 metadata |
| --- | --- | --- |
| `page_exit_intent_detected` | 뒤로가기/닫기/외부 이동 의도 감지 | `path`, `funnel_name?`, `step_name?` |
| `room_lobby_abandoned` | lobby 진입 후 시작 전 이탈 | `content_type`, `room_id?`, `wait_time_ms`, `participant_count?` |
| `creation_abandoned` | 작성/그리기 중 제출 전 이탈 | `content_type`, `funnel_name`, `step_name`, `elapsed_ms` |
| `result_share_abandoned` | 결과 확인 후 저장/공유 없이 이탈 | `content_type`, `time_on_result_ms` |
| `funnel_abandoned` | 공통 funnel 이탈 | `funnel_name`, `last_step_name`, `reason` |

`reason` 후보:

- `route_change`
- `tab_close`
- `background_timeout`
- `back_navigation`
- `external_link`
- `idle_timeout`
- `unknown`

## 7. UI 참여 이벤트

아래 이벤트는 API 결과가 아니라 화면 내 행동을 설명할 때만 사용한다.

| event_name | 발생 시점 | 필수 metadata |
| --- | --- | --- |
| `scroll_depth_reached` | 25/50/75/100% 도달 | `path`, `depth_percent` |
| `modal_opened` | 모달 노출 | `modal_id`, `path` |
| `modal_closed` | 모달 닫힘 | `modal_id`, `path`, `reason` |
| `tool_selected` | 그리기/편집 도구 선택 | `tool_id`, `content_type` |
| `canvas_interaction_started` | 사용자가 캔버스 조작 시작 | `content_type`, `tool_id?` |
| `canvas_interaction_paused` | 일정 시간 조작 없음 | `content_type`, `elapsed_ms` |
| `phone_official_store_clicked` | 핸드폰 모달의 외부 이동 shortcut(공식몰 등) 클릭 | `shortcut_key`, `destination` |

`phone_official_store_clicked`는 일반 UI 참여 이벤트와 달리 외부 이탈 추이 분석에 직접 쓰이므로 §10에서 100% 샘플링한다. shortcut이 추가되면 같은 이벤트의 `shortcut_key` metadata로 분리 집계한다.

## 8. 성능/오류 이벤트

| event_name | 발생 시점 | 필수 metadata |
| --- | --- | --- |
| `web_vitals` | LCP/INP/CLS/TTFB 측정 | `metric_name`, `value`, `path` |
| `resource_load_slow` | 정적 리소스 로드가 느림 | `url`, `duration_ms`, `size_bytes?` |
| `js_error` | `window.onerror` | `path`, `error.type`, `error.message`, `error.stack?` |
| `unhandled_rejection` | `window.onunhandledrejection` | `path`, `error.type`, `error.message`, `error.stack?` |
| `client_network_failed` | 서버에 도달하지 못한 fetch 실패 | `path`, `request_path`, `error.type` |

수집하지 않는 것:

- `api_request`
- `api_error`
- HTTP status별 API 실패 이벤트
- API latency
- response body, request body

## 9. 전송 메커니즘

클라이언트 로그는 백엔드 ingest endpoint를 통해 stdout 로그 파이프라인으로 보낸다.

```http
POST /api/logs/client
Content-Type: application/json

{
  "events": [
    { "<standard-log-event>": "..." }
  ]
}
```

### 전송 정책

- 버퍼 50개 도달 시 flush
- 5초마다 flush
- 페이지 종료 시 `visibilitychange === 'hidden'`을 1차 트리거로, `pagehide`를 보조 트리거로 두고 `navigator.sendBeacon`으로 잔여 이벤트 전송. iOS Safari 등에서 `pagehide`가 누락되거나 늦는 경우를 보완한다.
- `sendBeacon` 페이로드는 64KB 이내. 초과 시 우선순위(`error_*` > funnel/이탈 > UI 참여 > heartbeat) 순으로 분할 전송하고, 가장 낮은 우선순위는 다음 flush로 미룬다.
- 오류 이벤트(`js_error`, `unhandled_rejection`, `client_network_failed`)는 즉시 flush.
- 로그 전송 실패는 재귀 로깅하지 않는다(무한 루프 방지).
- 같은 페이지 세션 내 동일 이벤트가 짧은 간격(예: 200ms)에 반복되면 dedup해 단일 이벤트로 합친다(특히 `js_error`).

### Ingest 엔드포인트 보안 (`/api/logs/client`)

비인증 엔드포인트이므로 abuse를 막기 위해 백엔드는 다음 정책을 적용한다.

- **Rate limit**: 동일 IP 기준 100 events/sec, 1,000 events/min. 초과분은 429.
- **Payload 한도**: 단일 요청 1MB, 한 요청 내 이벤트 200개.
- **Origin 검증**: `Origin`/`Referer`가 서비스 도메인 allow-list 외이면 drop.
- **이벤트 allow-list**: 본 문서에 정의된 `event_name`만 적재. 미정의 이벤트는 `system-logs-*`로 라우팅되어 누락 감지에만 사용한다.
- **스키마 검증**: 표준 스키마 필수 필드 누락 시 drop.
- **Bot drop**: 알려진 크롤러 UA 패턴은 drop.
- **PII 차단**: 서버 측에서도 path/referrer/error 본문에 대한 sanitization을 한 번 더 수행한다(클라이언트 sanitization은 신뢰 경계 밖이다).

## 10. Sampling

| 종류 | 비율 | 이유 |
| --- | --- | --- |
| 유입, 세션, 페이지, funnel, 이탈 | 100% | 전환/이탈 분석의 기준 |
| `client_alive` | 100% | 실시간 활성 사용자 추정 |
| `js_error`, `unhandled_rejection`, `client_network_failed` | 100% | 장애 분석 |
| `phone_official_store_clicked` | 100% | 외부 이탈(공식몰 등) 추이 분석 |
| `web_vitals` | 10%, session hash 기반 | 볼륨 제어 |
| `visibility_change` | 10%, session hash 기반 | 볼륨 제어 |
| `resource_load_slow` | 100% | 발생 빈도 낮음 |
| 세밀한 UI 참여 이벤트 | 10~30%, 기능별 조정 | 필요 시 확대 |

session hash 기반 샘플링을 사용해 선택된 세션의 흐름이 끊기지 않게 한다.

분포 지표 신뢰 가드:

- `web_vitals` 같이 10% 샘플링하는 분포 지표는 대시보드/알림 평가 윈도우 안에서 표본 N >= 100일 때만 P75 등을 표시한다. 표본이 부족하면 “데이터 부족”으로 표시하고 알림은 건너뛴다.
- `client_alive`로 추정한 active session 수는 100% 수집이지만, 인증/탭 전환 noise를 감안해 5분 이상 지속된 세션만 “지속 사용자”로 집계한다.

## 11. 대시보드 핵심 지표

### 유입

- entry_type별 session 수
- utm_campaign별 landing → service entry 전환율
- share_link_opened → funnel_started 전환율

### 전환

- funnel별 `funnel_started` → `funnel_goal_reached` 전환율
- step별 `funnel_step_viewed` 대비 `funnel_step_completed` 비율
- CTA별 클릭 후 다음 step 도달률

### 이탈

- funnel별 `funnel_abandoned` 비율
- step별 abandon heatmap
- `room_lobby_abandoned`, `creation_abandoned`, `result_share_abandoned` 추이

### 품질

- Web Vitals P75
- JS error rate
- client network failure rate
- active users/sessions by content_type

## 12. 보안/PII

절대 포함하지 않는다.

- 사용자 작성 본문, 그림 stroke 원본, 메모 본문
- 사주 원문 중 개인정보로 볼 수 있는 상세 입력값
- JWT, refresh token, OAuth code, password
- 이메일, 전화번호, 실명, 주소
- URL token 원문

허용:

- 익명 `uuid`
- `session_id`, `flow_id`, `trace_id`
- 정규화된 `path`
- 정규화된 `referrer` origin
- UTM 값(단, `utm_term`은 검색어가 들어올 수 있어 길이 cap 50자 + 알려진 PII 패턴 제거 후 적재)

path 정규화 예:

```text
/share/secret-token -> /share/:token
/flipbook/rooms/AB3K9Q -> /flipbook/rooms/:roomCode
/gallery/12345 -> /gallery/:galleryId
```

`error` 객체 sanitization:

- `error.message`, `error.stack`은 사용자 입력값(URL token, query string, 입력 본문)이 그대로 노출되는 경로다. logger 진입점에서 다음을 적용한다.
  - URL/Path 토큰 패턴(`/share/{token}`, `?token=…`, `?code=…`, `Authorization: …`)을 마스킹.
  - 길이 cap: `message` 1KB, `stack` 4KB. 초과 시 끝부분 truncate + `…[truncated]` 표시.
  - 알려진 시크릿 prefix(`sk-`, `Bearer `, `eyJ`로 시작하는 JWT 패턴)는 토큰 단위로 `***`로 치환.
- 백엔드 ingest에서도 동일 sanitization을 한 번 더 수행해 클라이언트 누락분을 보정한다.

## 13. 구현 체크리스트

- [ ] `shared/libs/logger.ts`에서 `uuid`, `session_id`, `trace_id`, `flow_id`를 관리한다.
- [ ] `session_id`는 `sessionStorage` 기반으로 발급하고, 30분 idle 후 회전한다.
- [ ] route 변경 시 `page_leave` → `page_view` 순서로 기록한다. `trace_id`는 fetch 단위이므로 route 변경만으로는 회전하지 않는다.
- [ ] 최초 진입 시 `landing_source_detected`, `session_start`를 기록한다.
- [ ] 주요 서비스 플로우에 `funnel_started`(여기서 `flow_id` 발급), `funnel_step_viewed`, `funnel_step_completed`, `funnel_goal_reached`, `funnel_abandoned`를 연결한다. funnel 종료 전 새 funnel 시작 시 이전 `flow_id`를 `funnel_abandoned`로 명시 종료한다.
- [ ] 페이지 종료 시 `visibilitychange === 'hidden'`을 1차 트리거로, `pagehide`를 보조로 두고 `session_end`와 잔여 flush를 `sendBeacon`으로 처리한다.
- [ ] `sendBeacon` 페이로드 64KB 한도를 우선순위 기반(`error_*` > funnel/이탈 > UI 참여 > heartbeat)으로 분할한다.
- [ ] `client_alive` heartbeat를 활성 30초, 비활성 5분 주기로 전송한다.
- [ ] `web-vitals` v3+로 LCP/INP/CLS/TTFB를 수집한다.
- [ ] fetch wrapper는 `X-Trace-Id`, `X-Flow-Id`(funnel 진행 중일 때) 헤더 주입만 담당하고 API 성공/실패 로그를 만들지 않는다.
- [ ] 서버 미도달 fetch 실패만 `client_network_failed`로 기록한다.
- [ ] path/referrer/token/PII sanitization과 `error.message`/`error.stack` 마스킹을 logger 진입점에서 수행한다.

## 14. 백엔드 이벤트와 연결하는 방법

프론트엔드와 백엔드는 두 단계의 ID를 공유한다.

- **`flow_id`** — funnel 단위 join key. funnel의 모든 프론트 이벤트와, 그 funnel 진행 중에 호출된 fetch가 트리거한 백엔드 처리 이벤트가 같은 값을 갖는다.
- **`trace_id`** — 단일 요청 단위 join key. fetch 한 번과 그 요청을 처리한 백엔드 access/error/biz 로그가 같은 값을 갖는다.

예 (`flipbook_room_creation` funnel):

1. 프론트엔드: `funnel_started` (`flow_id=F1`, 새로 발급)
2. 프론트엔드: `cta_clicked` (`flow_id=F1`, `trace_id=T1`로 fetch 직전 발급)
3. 백엔드: `api_request_completed` (`flow_id=F1`, `trace_id=T1`)
4. 백엔드: `flipbook_room_finished` (`flow_id=F1`, `trace_id=T1`)
5. 프론트엔드: `funnel_goal_reached` (`flow_id=F1`)

이벤트 이름은 양쪽이 겹치지 않지만, OpenSearch에서 `flow_id`로 join하면 funnel 전체 흐름을, `trace_id`로 join하면 단일 요청의 round-trip을 복원할 수 있다.
