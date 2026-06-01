# 무한 캔버스 성능 최적화 Before / After

## 요약

무한 캔버스의 성능 최적화 대상은 두 가지입니다.

- 백오피스 활성 방 목록 조회: Redis 전체 key `SCAN` 기반 조회를 Redis Sorted Set 인덱스 기반 페이지 조회로 변경
- WebSocket 참여자 이벤트: 캔버스 전체 상태성 payload를 참여자 변경 delta payload로 경량화

이번 최적화는 아래 성능 기법을 사용합니다.

| 사용 기법 | 적용 위치 | 설명 |
| --- | --- | --- |
| 캐싱/인덱싱 | Redis 활성 방 목록 | 활성 방을 Sorted Set에 보조 인덱스로 유지해 전체 key scan을 피함 |
| 페이지네이션 | 백오피스 활성 방 목록 | 필요한 페이지의 roomCode만 조회하고 page size만큼만 역직렬화 |
| 페이로드 크기 절감 | WebSocket 참여자 이벤트 | `elements`, `operations`, `locks`를 제외한 참여자 변경 정보만 전송 |
| Latency 측정 | Redis 조회, WebSocket JSON 처리 | p95 latency를 Before/After로 비교 |
| Throughput 개선 기반 | Redis 조회 비용 감소 | 같은 시간에 처리 가능한 목록 조회 수가 늘어나는 구조 |
| 네트워크 최적화 | WebSocket event body | 전송 byte를 줄여 클라이언트 수신/파싱 비용 감소 |

이번 범위에서 사용하지 않은 기법은 DB/HikariCP/JPA N+1/외부 API 병렬 호출입니다. 무한 캔버스의 병목 후보가 DB 쿼리가 아니라 Redis runtime state 조회와 WebSocket payload였기 때문입니다.

<br>

## 측정 조건

측정은 `backend/scripts/benchmark-infinite-canvas-performance.py`로 수행했습니다.

```bash
python3 backend/scripts/benchmark-infinite-canvas-performance.py --iterations 20 --output-dir backend/docs/performance/assets
```

측정 방식은 실제 Redis 서버의 순간 상태에 의존하지 않도록 synthetic benchmark로 구성했습니다.

- Redis room state JSON은 현재 `InfiniteCanvasState` 구조를 본떠 생성
- Before 활성 방 조회는 기존 코드의 `SCAN -> 전체 GET -> 전체 JSON deserialize -> 정렬 -> page slice` 흐름을 재현
- After 활성 방 조회는 변경 코드의 `Sorted Set page 조회 -> pageSize만 GET/deserialize` 흐름을 재현
- WebSocket Before payload는 참여자 이벤트에 `elements`, `operations`, `locks`가 포함된 상태성 이벤트로 측정
- WebSocket After payload는 참여자 변경 전용 DTO인 `InfiniteCanvasParticipantEventResponse` 기준으로 측정

이 측정은 로컬 재현용 수치입니다. 운영 환경에서는 Redis 네트워크 RTT, 서버 CPU, 실제 캔버스 요소 크기, 동시 접속자 수에 따라 절대값은 달라질 수 있습니다. 다만 Before/After의 비용 구조 차이는 코드 구조상 동일합니다.

<br>

## 1. Redis 활성 방 목록 조회

### Before

기존 백오피스 활성 무한 캔버스 목록 조회는 전체 Redis room key를 훑었습니다.

```text
SCAN infinite-canvas:room:*
-> 각 key GET
-> 전체 room state JSON 역직렬화
-> CLOSED 제외
-> createdAt 기준 정렬
-> page/size slice
```

문제는 page size가 20이어도 전체 방이 10,000개라면 10,000개 방 상태를 모두 읽고 역직렬화한다는 점입니다.

### After

변경 후에는 활성 방 인덱스를 Redis Sorted Set으로 유지합니다.

```text
infinite-canvas:room:{roomCode}
infinite-canvas:rooms:active:created-at
```

방 상태 저장/갱신 시:

```text
ACTIVE 상태 -> ZADD infinite-canvas:rooms:active:created-at createdAt roomCode
CLOSED 상태 -> ZREM infinite-canvas:rooms:active:created-at roomCode
DELETE      -> ZREM infinite-canvas:rooms:active:created-at roomCode
```

목록 조회 시:

```text
ZREVRANGE infinite-canvas:rooms:active:created-at pageOffset pageEnd
-> 해당 roomCode만 GET
-> pageSize만 JSON 역직렬화
```

관련 코드:

- `RedisInfiniteCanvasRepository.findActiveCanvases(...)`
- `BackofficeInfiniteCanvasQueryUseCase.getActiveCanvases(...)`

### 측정 결과

| 활성 방 수 | Before avg ms | Before p95 ms | After avg ms | After p95 ms | p95 개선 배율 | Before 역직렬화 | After 역직렬화 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 0.83 | 1.16 | 0.15 | 0.15 | 7.66x | 100 | 20 |
| 1,000 | 8.42 | 9.22 | 0.15 | 0.15 | 62.35x | 1,000 | 20 |
| 5,000 | 53.71 | 64.80 | 0.15 | 0.15 | 423.61x | 5,000 | 20 |
| 10,000 | 108.30 | 124.06 | 0.15 | 0.15 | 834.95x | 10,000 | 20 |

![활성 방 목록 조회 p95 지연 시간](./assets/infinite-canvas-active-room-p95-ko.svg)

![활성 방 목록 조회 시 JSON 역직렬화 개수](./assets/infinite-canvas-deserialize-count-ko.svg)

### 결과 해석

기존 구조는 20개 방을 보여주기 위해 전체 Redis room state를 모두 읽는 구조였습니다. 방 수가 증가할수록 Redis scan, JSON 역직렬화, Java 정렬 비용이 함께 증가했습니다.

변경 후에는 활성 방 roomCode를 Redis Sorted Set에 보조 인덱스로 유지하고, 백오피스 목록 조회 시 필요한 page 범위만 조회합니다. 이로 인해 활성 방 10,000개 기준 p95 latency가 `124.06ms`에서 `0.15ms`로 줄었고, 역직렬화 대상도 `10,000개`에서 `20개`로 줄었습니다.

<br>

## 2. WebSocket 참여자 이벤트 Payload 경량화

### Before

참여자 접속/해제 이벤트는 캔버스 요소와 직접 관련이 없습니다. 하지만 상태성 이벤트 payload에 `elements`, `operations`, `locks`가 포함되면 캔버스가 커질수록 참여자 이벤트도 함께 커집니다.

```text
PARTICIPANT_CONNECTED
-> participants
-> changedParticipant
-> elements
-> operations
-> locks
-> viewport
```

### After

참여자 변경 이벤트 전용 DTO를 추가했습니다.

```text
InfiniteCanvasParticipantEventResponse
-> roomCode
-> status
-> hostUserUuid
-> participants
-> changedParticipant
-> maxParticipants
-> revision
-> updatedAt
```

참여자 이벤트에서는 `elements`, `operations`, `locks`를 보내지 않습니다. 캔버스 내용 변경은 기존처럼 `OPS_APPLIED`, `SNAPSHOT_UPDATED`, lock/cursor 이벤트로 분리해서 처리합니다.

관련 코드:

- `InfiniteCanvasParticipantEventResponse`
- `InfiniteCanvasEventPublisher.publishParticipantConnected(...)`
- `InfiniteCanvasEventPublisher.publishParticipantDisconnected(...)`

### 측정 결과

| 캔버스 요소 수 | Before bytes | After bytes | payload 감소율 | Before parse p95 ms | After parse p95 ms | parse 개선 배율 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 67,811 | 682 | 98.99% | 1.55 | 0.06 | 24.04x |
| 1,000 | 262,211 | 682 | 99.74% | 7.31 | 0.01 | 1271.98x |
| 5,000 | 1,130,211 | 682 | 99.94% | 23.13 | 0.01 | 3405.65x |

![참여자 이벤트 페이로드 크기](./assets/infinite-canvas-websocket-payload-ko.svg)

![참여자 이벤트 JSON 파싱 p95](./assets/infinite-canvas-json-parse-p95-ko.svg)

### 결과 해석

무한 캔버스는 실시간 협업 기능이라 WebSocket message 크기가 사용자 체감에 직접 영향을 줍니다. 기존 구조에서는 참여자 접속 같은 단순 이벤트도 캔버스 요소 수에 따라 payload가 커질 수 있었습니다.

변경 후에는 참여자 이벤트를 delta payload로 분리했습니다. 요소 5,000개 기준 참여자 이벤트 크기는 `1,130,211 bytes`에서 `682 bytes`로 줄었고, payload 감소율은 `99.94%`입니다. 클라이언트 JSON parse p95도 `23.13ms`에서 `0.01ms`로 줄었습니다.

<br>

## 최종 개선 요약

| 영역 | 핵심 개선 | 대표 수치 |
| --- | --- | --- |
| Redis 활성 방 조회 | 전체 SCAN 제거, Sorted Set page 조회 적용 | 활성 방 10,000개 p95 `124.06ms -> 0.15ms` |
| Redis 처리량 | 전체 역직렬화 제거 | 역직렬화 `10,000개 -> 20개` |
| WebSocket 네트워크 | 참여자 이벤트 delta payload 적용 | 요소 5,000개 payload `1,130,211B -> 682B` |
| 클라이언트 처리 | 큰 JSON parse 제거 | parse p95 `23.13ms -> 0.01ms` |

## 결과 정리

무한 캔버스의 실시간 협업 성능 개선을 위해 Redis Sorted Set 기반 활성 방 인덱스를 추가하고, WebSocket 참여자 이벤트를 delta payload로 분리했습니다. 기존에는 백오피스 활성 방 20개를 조회하기 위해 전체 Redis room key를 scan하고 모든 room state를 역직렬화했지만, 변경 후에는 필요한 page 범위의 roomCode만 조회하도록 개선했습니다. 또한 참여자 접속/해제 이벤트에서 캔버스 전체 요소와 최근 operation 목록을 제거해, 캔버스 요소 수가 증가해도 참여자 이벤트 payload가 일정하게 유지되도록 했습니다.

## 검증

```bash
cd backend
./gradlew --no-daemon test --tests com.nemonicworld.infinitecanvas.repository.RedisInfiniteCanvasRepositoryTest --tests com.nemonicworld.infinitecanvas.websocket.InfiniteCanvasEventPublisherTest --tests com.nemonicworld.backoffice.infinitecanvas.controller.BackofficeInfiniteCanvasControllerIntegrationTest --tests com.nemonicworld.infinitecanvas.controller.InfiniteCanvasControllerIntegrationTest
```

결과: `BUILD SUCCESSFUL`
