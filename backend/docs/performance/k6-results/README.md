# k6 결과 파일 정리

이 폴더는 `backend/scripts/k6` 아래의 k6 스크립트를 실행했을 때 생성된 Markdown, JSON 결과를 보관합니다.

파일명은 k6 스크립트의 `handleSummary()`에서 생성하는 결과 prefix와 맞춰져 있습니다. 다음에 같은 스크립트를 다시 실행하면 같은 이름으로 덮어쓰기 쉬우므로, 파일명은 영문 prefix를 유지하고 이 README에서 트러블 슈팅별 의미를 정리합니다.

| 트러블 슈팅 | 측정 대상 | Markdown 결과 | JSON 결과 |
| --- | --- | --- | --- |
| 트러블 슈팅 1 | 운세 생성 외부 I/O 트랜잭션 분리 | `fortune-create-load.md` | `fortune-create-load.json` |
| 트러블 슈팅 1 | 문의 답변 SMTP 외부 I/O 트랜잭션 분리 | `admin-inquiry-reply-load.md` | `admin-inquiry-reply-load.json` |
| 트러블 슈팅 2 | 갤러리 목록 조회 쿼리 최적화 | `gallery-list-load.md` | `gallery-list-load.json` |
| 트러블 슈팅 3 | 무한캔버스 활성 방 목록 조회 최적화 | `infinite-canvas-active-room-load.md` | `infinite-canvas-active-room-load.json` |

## 읽는 순서

1. 전체 비교 그래프와 해석은 `backend/docs/performance/05-k6-부하-테스트-결과.md`를 먼저 봅니다.
2. 각 트러블 슈팅 상세 문서의 `사용한 k6` 섹션에서 어떤 스크립트가 어떤 최적화를 검증하는지 확인합니다.
3. 이 폴더의 개별 결과 파일에서 실행 조건, p95, p99, RPS, 실패율을 확인합니다.

## 주의

현재 결과는 2026-06-01 로컬 통제 환경 기준입니다. GMS와 SMTP는 로컬 stub으로 고정했고, 운영 서버 실측값이 아닙니다.
