# k6 부하 테스트 가이드

이 문서는 지금까지 진행한 백엔드 성능 최적화를 실제 HTTP 부하 테스트로 재현하기 위한 k6 실행 가이드입니다.

## k6가 하는 일

k6는 터미널에서 실행하는 부하 테스트 CLI입니다. JavaScript 파일에 요청 시나리오를 작성하고, k6가 여러 가상 사용자(VU)를 만들어 API를 반복 호출합니다. 실행 결과로 `http_req_duration`, p95, p99, RPS, 실패율, check 성공률을 확인할 수 있습니다.

## 대상 최적화

| 최적화 | k6 스크립트 | 주요 지표 |
| --- | --- | --- |
| 갤러리 목록 조회 쿼리 최적화 | `backend/scripts/k6/gallery-list-load.js` | `http_req_duration p95`, RPS |
| 무한캔버스 활성 방 목록 인덱스 최적화 | `backend/scripts/k6/infinite-canvas-active-room-load.js` | backoffice 목록 p95, 실패율 |
| 운세 생성 외부 I/O 트랜잭션 분리 | `backend/scripts/k6/fortune-create-load.js` | 운세 생성 p95, 실패율, Hikari pending connection |
| 문의 답변 SMTP 외부 I/O 트랜잭션 분리 | `backend/scripts/k6/admin-inquiry-reply-load.js` | 문의 답변 p95, 실패율, Hikari pending connection |

## 설치

macOS에서 로컬 실행:

```bash
brew install k6
```

Docker로 실행:

```bash
docker run --rm -v "$PWD:/work" -w /work grafana/k6 version
```

Docker에서 로컬 Spring Boot 서버를 호출할 때는 `BASE_URL=http://host.docker.internal:8080/api/v1`을 사용합니다.

## 사전 준비

로컬 인프라와 백엔드를 실행합니다.

```bash
cd backend
docker compose -f docker-compose.local.yml up -d
./gradlew bootRun
```

다른 터미널에서 k6를 실행합니다.

관리자 API를 측정하려면 관리자 로그인 후 access token을 `ADMIN_TOKEN`에 넣어야 합니다.

갤러리 목록 조회는 `USER_UUID`에 측정할 사용자의 UUID를 넣습니다. 갤러리 row가 충분히 많은 사용자일수록 쿼리 최적화 효과가 잘 보입니다.

외부 I/O 최적화 측정은 로컬 GMS, MinIO, SMTP 응답 시간을 고정해야 before/after 비교가 깨끗합니다. 실제 외부 서비스를 물리면 네트워크 상태가 결과에 섞입니다.

## 실행 명령

### 갤러리 목록 조회

```bash
k6 run \
  -e BASE_URL=http://localhost:8080/api/v1 \
  -e USER_UUID=<익명-사용자-UUID> \
  -e VUS=20 \
  -e DURATION=1m \
  backend/scripts/k6/gallery-list-load.js
```

Docker:

```bash
docker run --rm -v "$PWD:/work" -w /work grafana/k6 run \
  -e BASE_URL=http://host.docker.internal:8080/api/v1 \
  -e USER_UUID=<익명-사용자-UUID> \
  -e VUS=20 \
  -e DURATION=1m \
  backend/scripts/k6/gallery-list-load.js
```

### 무한캔버스 활성 방 목록

```bash
k6 run \
  -e BASE_URL=http://localhost:8080/api/v1 \
  -e ADMIN_TOKEN=<관리자-access-token> \
  -e VUS=20 \
  -e DURATION=1m \
  backend/scripts/k6/infinite-canvas-active-room-load.js
```

### 운세 생성

```bash
k6 run \
  -e BASE_URL=http://localhost:8080/api/v1 \
  -e VUS=10 \
  -e DURATION=1m \
  backend/scripts/k6/fortune-create-load.js
```

이 스크립트는 iteration마다 익명 사용자를 만든 뒤 운세 생성을 호출합니다. 운세는 사용자별 일 1회 제한이 있으므로 같은 UUID를 재사용하지 않습니다.

### 문의 답변

```bash
k6 run \
  -e BASE_URL=http://localhost:8080/api/v1 \
  -e ADMIN_TOKEN=<관리자-access-token> \
  -e VUS=10 \
  -e DURATION=1m \
  backend/scripts/k6/admin-inquiry-reply-load.js
```

이 스크립트는 iteration마다 익명 사용자와 문의를 만든 뒤 관리자 답변 API를 호출합니다. SMTP는 실제 메일 발송 대신 로컬 stub 또는 테스트 SMTP로 고정하는 것을 권장합니다.

## 결과 파일

각 스크립트는 실행 후 아래 경로에 JSON과 Markdown 요약을 생성합니다.

```text
backend/docs/performance/k6-results/*.json
backend/docs/performance/k6-results/*.md
```

터미널 캡처도 가능하지만, 포트폴리오에는 생성된 Markdown 요약의 p95/RPS 표와 기존 SVG 그래프를 함께 사용하는 편이 더 안정적입니다.

## before/after 비교 방식

1. 최적화 전 커밋으로 checkout합니다.
2. 동일한 seed 데이터와 동일한 k6 옵션으로 실행합니다.
3. 최적화 후 커밋으로 checkout합니다.
4. 동일한 명령을 다시 실행합니다.
5. `p95`, `RPS`, `failed rate`를 표로 비교합니다.

갤러리 쿼리 최적화처럼 DB query shape 개선은 k6 API p95와 synthetic benchmark 수치를 함께 제시하면 좋습니다. synthetic benchmark는 코드 경계의 비용 차이를 설명하고, k6는 실제 HTTP 요청 경로에서 체감 latency를 확인합니다.
