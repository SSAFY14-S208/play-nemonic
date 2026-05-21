# 8B. 프론트엔드 로깅 가이드

이 문서는 [observability.md](./observability.md)(백엔드 + 인프라 spec)의 자매
문서. 같은 JSON 스키마를 공유하면서 **프론트엔드(Next.js SSR + 브라우저
클라이언트) 특화 이벤트**를 정의하고, 실시간 메트릭(접속자 수, 페이지별 활성
사용자 등) 수집에 필요한 heartbeat 메커니즘을 명세한다.

## 1. 목적과 배경

- 프론트엔드도 백엔드와 **동일한 표준 JSON 스키마**로 로그 발행 →
  단일 OpenSearch 대시보드에서 풀스택 가시성 확보.
- 클라이언트(브라우저)는 컨테이너 stdout이 없으므로 **백엔드 endpoint를
  거쳐 인프라로 흘려보낸다**.
- 실시간 접속자 수 같은 운영 지표는 클라이언트의 **heartbeat 이벤트**로 측정.

## 2. 공유 로그 스키마

[observability.md §로그 표준화 규약](./observability.md)을 그대로 사용. 프론트엔드
관점에서 각 필드의 채움 방식:

| 필드 | 프론트엔드에서 | 비고 |
| --- | --- | --- |
| `@timestamp` | `new Date().toISOString()` (UTC, ms) | 서버에서 다시 채우지 말 것 — 클라이언트 발생 시각 |
| `level` | `DEBUG` / `INFO` / `WARN` / `ERROR` | |
| `service` | `next-ssr` / `client-web` / `client-electron` | §3 참조 |
| `trace_id` | 백엔드 응답의 `X-Trace-Id` 헤더 또는 새로 생성한 UUID | 한 사용자 액션의 모든 이벤트에 같은 값 |
| `session_id` | 브라우저 탭 단위 UUID | `sessionStorage` (탭 닫으면 소실) |
| `uuid` | 익명 사용자 UUID | `localStorage` (영구) |
| `event_name` | snake_case | §5 카테고리별 정의 |
| `content_type` | `community` / `relay` / `flipbook` / `canvas` / `fortune` / `lobby` | 현재 페이지/콘텐츠 |
| `room_id` | room/canvas ID | 해당 화면일 때만 |
| `prev_zone` | 직전 콘텐츠 (zone 전환 시) | |
| `metadata` | 이벤트별 추가 (§5, §7) | 명시 매핑 필드 활용 |
| `error` | `{type, message, stack}` | 에러 이벤트일 때만 |

### 2.1 프론트엔드 추가 권장 metadata 필드

운영/UX 분석에 자주 쓰이는 클라이언트 컨텍스트:

```json
"metadata": {
  "viewport":   {"width": 1920, "height": 1080},
  "locale":     "ko-KR",
  "platform":   "desktop",        // desktop | mobile | tablet
  "network":    "4g",             // Network Info API: 4g | 3g | wifi | unknown
  "referrer":   "https://...",    // 진입 출처
  "path":       "/canvas/abc123"  // 현재 라우트
}
```

## 3. service 분류

| service | 위치 | 로그 수집 경로 |
| --- | --- | --- |
| `next-ssr` | Next.js 서버 컨테이너 (`nemonic-prod-frontend-1`) | 컨테이너 stdout → Fluent Bit이 자동 수집 |
| `client-web` | 사용자 브라우저 | `POST /api/logs/client` → 백엔드 정규화 → 컨테이너 stdout → Fluent Bit |
| `client-electron` | (선택) Electron 데스크톱 앱 | 동일 endpoint, service 값만 다르게 |

## 4. 클라이언트 → 백엔드 전송 메커니즘

### 4.1 endpoint 계약

```
POST /api/logs/client
Content-Type: application/json

{
  "events": [
    {<event 표준 JSON>},
    {<event 표준 JSON>},
    ...
  ]
}

→ 200 OK (body 빈)
```

- 백엔드는 받은 events를 그대로 한 줄씩 stdout으로 (logback JSON encoder).
- 즉 클라이언트의 표준 JSON이 그대로 OpenSearch `biz-events-*` 인덱스에 흘러감.

### 4.2 전송 시점 — batch + 트리거

| 트리거 | 동작 |
| --- | --- |
| **버퍼 50개 도달** | 즉시 flush |
| **5초 경과** | flush (interval) |
| **페이지 unload** | `navigator.sendBeacon`으로 잔여 flush (가장 안전) |
| **탭 비활성** | flush 후 heartbeat 빈도만 줄임 (전송은 계속) |
| **에러 이벤트** | 큐 우회하여 즉시 flush (loss 방지) |

> `fetch` 대신 unload 시점엔 **`navigator.sendBeacon`**을 써야 브라우저가
> 죽이기 전에 전송 보장.

### 4.3 sampling

| 종류 | sampling 비율 |
| --- | --- |
| `page_view`, `client_alive` 등 | **10%** (`Math.random() < 0.1`) |
| 비즈니스 이벤트 (`room_join`, `memo_create` 등) | **100%** |
| 에러 (`js_error`, `api_error` 등) | **100%** |

샘플링은 클라이언트 측에서. 100% 수집하면 트래픽 폭증.

## 5. 이벤트 카테고리

### 5.1 세션/페이지

| event_name | 발생 시점 | 필수 metadata |
| --- | --- | --- |
| `session_start` | 새 세션 ID 발급 시 (브라우저 탭 새로 열림) | `referrer`, `viewport`, `locale`, `platform` |
| `session_end` | unload (탭 닫기, 페이지 이동) | `duration_ms` |
| `page_view` | 라우트 변경 시 (Next.js router 이벤트) | `path`, `prev_path` |
| `page_leave` | 페이지 떠날 때 (다음 페이지 가기 직전) | `path`, `time_on_page_ms` |
| `visibility_change` | 탭 활성/비활성 전환 (`document.visibilitychange`) | `state: visible \| hidden`, `time_visible_ms` |

### 5.2 비즈니스 이벤트

[observability.md §비즈니스 이벤트 로그](./observability.md)와 동일. 프론트엔드는
**사용자 액션의 즉시성**을 잡는 데 강함:

| event_name | 발생 시점 | metadata |
| --- | --- | --- |
| `room_join` | 사용자가 참여 버튼 클릭 → WS 연결 시도 직전 | `room_id`, `content_type` |
| `room_leave` | 나가기 클릭 또는 브라우저 닫기 | `room_id`, `time_in_room_ms`, `reason: explicit \| close \| disconnect` |
| `canvas_join` | 무한 캔버스 진입 | `canvas_id` |
| `canvas_join_rejected` | 정원 초과 등 거부 사유 | `canvas_id`, `reason` |
| `memo_create` | 메모 작성 완료 (전송 직전 클라이언트 측) | `content_length`, `attached: bool` |
| `fortune_request` | 운세 요청 버튼 | `prompt_version` |
| `element_place` | 캔버스 요소 배치 | `canvas_id`, `element_type` |

> 같은 이벤트가 백엔드에서도 발생할 수 있다 (예: WS 서버가 `room_join`
> 처리 시). 그러면 같은 `trace_id`로 묶여 풀스택 추적 가능.

### 5.3 성능

| event_name | 발생 시점 | metadata |
| --- | --- | --- |
| `web_vitals` | 페이지 LCP/FID/CLS/TTFB 측정 시 | `metric_name`, `value`, `path` |
| `api_request` | 모든 fetch 호출 종료 시 | `method`, `path`, `status`, `latency_ms` |
| `resource_load_slow` | 큰 리소스 로드 (예: > 1MB or > 3s) | `url`, `size_bytes`, `duration_ms` |

### 5.4 에러

| event_name | trigger | 필수 필드 |
| --- | --- | --- |
| `js_error` | `window.onerror` | `error.type`, `error.message`, `error.stack`, `path` |
| `unhandled_rejection` | `window.onunhandledrejection` | 동일 |
| `api_error` | fetch 응답이 4xx/5xx | `path`, `status`, `error.message` |
| `network_error` | fetch 자체 fail (네트워크 끊김 등) | `path`, `error.type` |

### 5.5 실시간 heartbeat ⭐

**현재 접속자 수 등 실시간 메트릭의 source.**

| event_name | 주기 | metadata |
| --- | --- | --- |
| `client_alive` | **활성 탭: 30초**, **비활성 탭: 5분** | `path`, `content_type`, `room_id?`, `time_in_session_ms` |

heartbeat는 sampling 대상 X (모든 클라이언트가 보내야 의미 있음). 단,
ack는 받지 않고 fire-and-forget.

## 6. 실시간 접속자 수 측정

### 6.1 query 패턴 (OpenSearch)

**활성 세션 수** (최근 5분간 heartbeat 보낸 unique session):

```json
GET /biz-events-*/_search
{
  "size": 0,
  "query": {
    "bool": {
      "filter": [
        {"term": {"event_name": "client_alive"}},
        {"range": {"@timestamp": {"gte": "now-5m"}}}
      ]
    }
  },
  "aggs": {
    "active_sessions": {
      "cardinality": {"field": "session_id"}
    }
  }
}
```

**활성 사용자 수** (unique uuid):

```json
"aggs": {
  "active_users": {
    "cardinality": {"field": "uuid"}
  }
}
```

**콘텐츠 타입별 활성 사용자**:

```json
"aggs": {
  "by_content": {
    "terms": {"field": "content_type"},
    "aggs": {
      "active_users": {"cardinality": {"field": "uuid"}}
    }
  }
}
```

### 6.2 시각화 옵션

| 도구 | 방식 |
| --- | --- |
| **OpenSearch Dashboards** | Visualization → Metric/Bar chart에 위 query 박기 |
| **Grafana** | OpenSearch datasource plugin 설치 후 Prometheus처럼 query, 단일 dashboard로 통합 |
| **임베드** | 위 둘 중 하나의 panel을 우리 운영 페이지에 iframe 임베드 ([README.md](./README.md) §12.6) |

### 6.3 가공 메트릭 (옵션)

heartbeat가 너무 많으면 백엔드에서 1분 단위 집계 후 Prometheus 메트릭으로
노출하는 것도 방법:

```
nemonic_active_sessions{content_type="community"} 142
nemonic_active_sessions{content_type="canvas"} 38
nemonic_active_users 87
```

이러면 Grafana에서 Prometheus query로 직접 그래프. OpenSearch 부하 줄임.

## 7. 보안 / PII

### 7.1 절대 로그에 포함 금지

- 사용자 작성물: canvas content, memo body, fortune saju pillars의 개인정보 부분
- 인증: 패스워드, 토큰, JWT, refresh token, OAuth code
- 식별 정보: 이메일, 전화번호, 본명, 주소

### 7.2 허용 (익명/추적용)

- `uuid` (random)
- `session_id` (random)
- `path` (URL 경로) — 단 path에 token 박힌 경우 제거 (예: `/share/<token>`)
- IP는 백엔드 access log가 자동 수집

### 7.3 sanitization 권장

```typescript
// path 정규화 — 동적 ID는 placeholder로
"/canvas/abc-123-def"  →  "/canvas/:id"
"/share/secret-token"  →  "/share/:token"
```

이래야 cardinality 폭증 방지 + URL에 박힌 secret 노출 방지.

## 8. 구현 체크리스트

### 8.1 클라이언트 측 (브라우저)

- [ ] **Logger utility** (`lib/logger.ts`):
  - `uuid` 생성 + `localStorage` 저장 (이미 있으면 재사용)
  - `session_id` 생성 + `sessionStorage` 저장
  - `log(event_name, metadata)` 메서드
  - 내부 buffer + flush 로직 (50개 또는 5초)
  - sampling 적용
- [ ] **Heartbeat scheduler**:
  - 활성 탭: 30초 interval
  - `visibilitychange` 감지 → 비활성 시 5분으로 변경
- [ ] **Error global handlers**:
  - `window.onerror` → `js_error`
  - `window.onunhandledrejection` → `unhandled_rejection`
- [ ] **Web Vitals 수집** (`web-vitals` 패키지):
  - `onLCP`, `onFID`, `onCLS`, `onTTFB` → `web_vitals`
- [ ] **fetch wrapper**:
  - 모든 API 호출 latency 측정 → `api_request`
  - 4xx/5xx → `api_error`도 같이
  - 네트워크 fail → `network_error`
- [ ] **Page lifecycle**:
  - Next.js `router.events` → `page_view`, `page_leave`
  - `beforeunload` → `session_end` + sendBeacon flush

### 8.2 백엔드 endpoint (Spring Boot)

- [ ] `POST /api/logs/client` controller
- [ ] 요청 검증: events 배열 크기 제한 (예: 100개), 각 event 필드 검증
- [ ] 정규화: `@timestamp` 클라이언트 값 신뢰 (단 미래 시각은 reject), `service`, `level` 등 enum 검증
- [ ] 출력: 각 event를 logback의 JSON encoder로 stdout 한 줄씩
- [ ] PII filter: 이미 §7 위반 필드 제거
- [ ] rate limit: IP/uuid 단위 (예: 분당 1000 events)

### 8.3 인프라 측 (이미 완료)

- ✅ Logstash 라우팅: `event_name` 있는 메시지 → `biz-events-*` 인덱스
- ✅ 인덱스 템플릿 + ISM (90일 보관)
- ✅ MinIO snapshot (90일)
- 7차 마일스톤 후보: OpenSearch Dashboards에 visualization 만들기
- 8차 마일스톤 후보: Grafana OpenSearch datasource plugin

## 9. 트레이드오프 / 알려진 제약

### 9.1 Adblock / 광고 차단기

일부 adblock 룰이 `/api/logs/*` 같은 endpoint를 차단할 수 있음. 필요 시 endpoint
이름 변경 (예: `/api/events`, `/api/telemetry`).

### 9.2 GDPR / 개인정보

UUID는 익명이지만 충분한 메타데이터(IP + User-Agent + 행동 패턴)와 결합하면
개인 식별 가능. 운영 정책으로 90일 보관 후 자동 삭제 (이미 ISM 90d) + 신청 시
특정 uuid 데이터 삭제 API 별도 마련.

### 9.3 sampling 시 디버깅 어려움

10% sampling이면 특정 사용자 이슈 추적 불가. 운영 중 특정 uuid는 100% 수집하는
"debug uuid" 옵션 고려 (미래 작업).

### 9.4 시계열 정확도

heartbeat 30초 interval이라 활성 사용자 수의 정확도는 ±30초. 더 정밀한
실시간 (1초)은 WebSocket 연결 카운트 (백엔드)로 보완.

---

## 부록 A: 표준 logger 스켈레톤 (TypeScript)

```typescript
// lib/logger.ts
type LogEvent = {
  '@timestamp': string;
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';
  service: 'client-web';
  trace_id: string;
  session_id: string;
  uuid: string;
  event_name: string;
  content_type?: string;
  room_id?: string;
  metadata?: Record<string, unknown>;
  error?: { type: string; message: string; stack?: string };
};

class Logger {
  private buffer: LogEvent[] = [];
  private flushTimer: number | null = null;
  private uuid = this.getOrCreate('localStorage', 'nemonic_uuid');
  private session_id = this.getOrCreate('sessionStorage', 'nemonic_session_id');

  log(event_name: string, metadata?: Record<string, unknown>, level: LogEvent['level'] = 'INFO') {
    // sampling
    if (level === 'INFO' && !this.isCriticalEvent(event_name) && Math.random() > 0.1) {
      return;
    }
    this.buffer.push({
      '@timestamp': new Date().toISOString(),
      level, service: 'client-web',
      trace_id: this.currentTraceId(),
      session_id: this.session_id,
      uuid: this.uuid,
      event_name, metadata,
    });
    this.scheduleFlush();
  }

  error(event_name: string, error: Error, metadata?: Record<string, unknown>) {
    this.buffer.push({
      '@timestamp': new Date().toISOString(),
      level: 'ERROR', service: 'client-web',
      trace_id: this.currentTraceId(),
      session_id: this.session_id, uuid: this.uuid,
      event_name, metadata,
      error: { type: error.name, message: error.message, stack: error.stack },
    });
    this.flush(); // 에러는 즉시
  }

  private scheduleFlush() {
    if (this.buffer.length >= 50) return this.flush();
    if (this.flushTimer) return;
    this.flushTimer = window.setTimeout(() => this.flush(), 5000);
  }

  private flush(useBeacon = false) {
    if (this.buffer.length === 0) return;
    const payload = JSON.stringify({ events: this.buffer });
    this.buffer = [];
    if (this.flushTimer) { clearTimeout(this.flushTimer); this.flushTimer = null; }

    if (useBeacon && navigator.sendBeacon) {
      navigator.sendBeacon('/api/logs/client', new Blob([payload], { type: 'application/json' }));
    } else {
      fetch('/api/logs/client', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: payload, keepalive: true })
        .catch(() => { /* fire-and-forget */ });
    }
  }

  // 외부 라이프사이클이 호출
  startHeartbeat() {
    let interval = 30_000;
    const tick = () => this.log('client_alive', { path: location.pathname }, 'INFO');
    let timer = window.setInterval(tick, interval);
    document.addEventListener('visibilitychange', () => {
      clearInterval(timer);
      interval = document.hidden ? 300_000 : 30_000;
      timer = window.setInterval(tick, interval);
    });
    window.addEventListener('beforeunload', () => {
      this.log('session_end', {}, 'INFO');
      this.flush(true); // sendBeacon
    });
  }

  private getOrCreate(store: 'localStorage' | 'sessionStorage', key: string): string {
    const s = window[store];
    let v = s.getItem(key);
    if (!v) { v = crypto.randomUUID(); s.setItem(key, v); }
    return v;
  }
  private isCriticalEvent(name: string): boolean {
    return name.startsWith('room_') || name.startsWith('canvas_') ||
           name.startsWith('memo_') || name.startsWith('fortune_') ||
           name === 'client_alive';
  }
  private currentTraceId(): string {
    // 간단 구현. 진짜는 라우트 변경마다 새 trace_id 발급 또는 백엔드 응답에서 받기
    return crypto.randomUUID();
  }
}

export const logger = new Logger();
```

사용 예:

```typescript
// _app.tsx 같은 곳에서 한 번
import { logger } from '@/lib/logger';
logger.startHeartbeat();
logger.log('session_start', { referrer: document.referrer, viewport: { width: innerWidth, height: innerHeight } });

// 비즈니스 이벤트
logger.log('room_join', { room_id: 'ABC123', content_type: 'community' });

// 에러
try { /* ... */ } catch (e) { logger.error('canvas_save_failed', e as Error, { canvas_id: 'X' }); }
```
