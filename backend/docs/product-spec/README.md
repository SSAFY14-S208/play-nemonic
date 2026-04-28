# Product Specification Index

이 폴더는 네모닉월드 제품 기획서를 AI와 개발자가 읽기 쉽게 기능별로 분할한 정규화 문서이다.

새로운 기능을 구현하거나 API/DB/이벤트 설계를 할 때는 `AGENTS.md`, `backend/docs/codex-current-state.md`와 함께
관련 제품 스펙을 먼저 읽는다.

## 문서 구성

| 파일 | 내용 |
| --- | --- |
| `01-community-canvas.md` | 커뮤니티 캔버스, 메모 생명주기, UUID 소유권, 신고, AI 모더레이션 |
| `02-relay-drawing.md` | 우당탕 릴레이 드로잉, 방/라운드/이탈/재접속/결과물 정책 |
| `03-fortune.md` | 오늘의 운세 뽑기, 만세력, GMS, 1일 1회 제한, 저장 구조 |
| `04-flipbook.md` | 플립북, 라운드 수, 프레임 compact 정책, GIF 결과물 |
| `05-infinite-canvas.md` | 무한 캔버스, Redis 활성 상태, 실시간 협업, 출력 포스트잇, AI 이모지 |
| `06-common-and-inquiry.md` | 공통 UUID, 인앱 브라우저, 멀티플레이 중복 접속, 고객 문의 |
| `07-backoffice.md` | 백오피스 기능, 권한, 대시보드, 신고/콘텐츠/파라미터/통계/알림/감사 로그 |
| `08-observability.md` | 로그 수집, 비즈니스 이벤트, 공통 로그 스키마, OpenSearch/Kafka/Fluent Bit |

## 읽는 방법

- API 구현: 해당 기능 문서 + `backend/docs/backend-architecture.md`
- DB 설계: 해당 기능의 저장 구조/생명주기 섹션 + 운영/로그 문서
- WebSocket 설계: 릴레이/플립북/무한 캔버스 문서 + 공통 중복 접속 정책
- 운영 기능 구현: `07-backoffice.md` + `08-observability.md`
- 이벤트 로깅 구현: `08-observability.md`

## 제품 공통 원칙

- 회원가입 없이 클라이언트 익명 UUID로 사용자를 식별한다.
- 결과물은 개인 인벤토리와 커뮤니티 캔버스 게시 흐름으로 연결된다.
- 커뮤니티 캔버스는 단일 공용 벽이며, 대부분의 결과물은 이 벽에 메모 형태로 게시된다.
- 커뮤니티 게시 복사본은 원본 결과물과 독립적으로 FIFO, 신고, 숨김, 삭제 정책을 적용받는다.
- 운영자는 사적 멀티플레이/협업 공간의 내부 콘텐츠를 열람하지 않는 것을 원칙으로 한다.
- 운영 조작과 설정 변경은 감사 로그에 기록한다.
