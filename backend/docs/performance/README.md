# 백엔드 성능 최적화 트러블 슈팅 문서 모음

이 폴더는 백엔드 성능 최적화 자료를 트러블 슈팅 단위로 정리한 인덱스입니다.

각 트러블 슈팅 폴더 안에 문서, 그래프, k6 실행 파일, k6 결과 파일을 함께 둡니다. Finder에서 폴더만 열어도 해당 최적화에 필요한 자료를 한 번에 확인할 수 있도록 구성했습니다.

## 폴더 구조

```text
backend/docs/performance/
├── README.md
├── k6-실행-가이드.md
├── k6-상세-캡처-가이드.md
├── k6-common/
│   └── summary.js
├── 00-k6-공통-요약/
│   ├── README.md
│   └── graphs/
├── 01-운세-생성-외부-io-트랜잭션-분리/
│   ├── README.md
│   ├── graphs/
│   └── k6/
│       ├── 01-운세-생성-k6.js
│       └── results/
├── 02-문의-답변-메일-io-트랜잭션-분리/
│   ├── README.md
│   ├── graphs/
│   └── k6/
│       ├── 02-문의-답변-k6.js
│       └── results/
├── 03-갤러리-목록-조회-쿼리-최적화/
│   ├── README.md
│   ├── graphs/
│   └── k6/
│       ├── 03-갤러리-목록-조회-k6.js
│       └── results/
├── 04-무한캔버스-조회-payload-최적화/
│   ├── README.md
│   ├── graphs/
│   └── k6/
│       ├── 04-무한캔버스-활성-방-목록-k6.js
│       └── results/
└── 05-무한캔버스-요소-적용-최적화/
    ├── README.md
    ├── graphs/
    └── k6/
        └── README.md
```

## 한눈에 보기

| 구분 | 최적화 요약 | 상세 문서 | 그래프 | k6 |
| --- | --- | --- | --- | --- |
| 공통 | k6 API p95/RPS 요약 그래프 | `00-k6-공통-요약/README.md` | `00-k6-공통-요약/graphs/` | 각 트러블 슈팅 폴더 참고 |
| 트러블 슈팅 1 | GMS, MinIO를 긴 트랜잭션 밖으로 이동해 운세 생성 커넥션 점유 시간 축소 | `01-운세-생성-외부-io-트랜잭션-분리/README.md` | `01-운세-생성-외부-io-트랜잭션-분리/graphs/` | `01-운세-생성-외부-io-트랜잭션-분리/k6/` |
| 트러블 슈팅 2 | SMTP 메일 발송을 긴 트랜잭션 밖으로 이동해 문의 답변 커넥션 점유 시간 축소 | `02-문의-답변-메일-io-트랜잭션-분리/README.md` | `02-문의-답변-메일-io-트랜잭션-분리/graphs/` | `02-문의-답변-메일-io-트랜잭션-분리/k6/` |
| 트러블 슈팅 3 | count 쿼리의 subtype JOIN 제거, page row만 subtype JOIN | `03-갤러리-목록-조회-쿼리-최적화/README.md` | `03-갤러리-목록-조회-쿼리-최적화/graphs/` | `03-갤러리-목록-조회-쿼리-최적화/k6/` |
| 트러블 슈팅 4 | Redis Sorted Set 인덱스와 참여자 delta payload로 SCAN, 큰 JSON 전송 제거 | `04-무한캔버스-조회-payload-최적화/README.md` | `04-무한캔버스-조회-payload-최적화/graphs/` | `04-무한캔버스-조회-payload-최적화/k6/` |
| 트러블 슈팅 5 | 리스트 반복 탐색을 Map 기반 batch 적용으로 변경 | `05-무한캔버스-요소-적용-최적화/README.md` | `05-무한캔버스-요소-적용-최적화/graphs/` | 직접 k6 없음, `05-무한캔버스-요소-적용-최적화/k6/README.md` 참고 |

## k6 결과 파일

| 구분 | 실행 파일 | 결과 파일 |
| --- | --- | --- |
| 트러블 슈팅 1 운세 생성 | `01-운세-생성-외부-io-트랜잭션-분리/k6/01-운세-생성-k6.js` | `01-운세-생성-외부-io-트랜잭션-분리/k6/results/01-운세-생성-k6-결과.md` |
| 트러블 슈팅 2 문의 답변 | `02-문의-답변-메일-io-트랜잭션-분리/k6/02-문의-답변-k6.js` | `02-문의-답변-메일-io-트랜잭션-분리/k6/results/02-문의-답변-k6-결과.md` |
| 트러블 슈팅 3 갤러리 목록 조회 | `03-갤러리-목록-조회-쿼리-최적화/k6/03-갤러리-목록-조회-k6.js` | `03-갤러리-목록-조회-쿼리-최적화/k6/results/03-갤러리-목록-조회-k6-결과.md` |
| 트러블 슈팅 4 무한캔버스 활성 방 목록 | `04-무한캔버스-조회-payload-최적화/k6/04-무한캔버스-활성-방-목록-k6.js` | `04-무한캔버스-조회-payload-최적화/k6/results/04-무한캔버스-활성-방-목록-k6-결과.md` |

## 그래프 경로

| 구분 | 한국어 그래프 | English Graph |
| --- | --- | --- |
| 공통 k6 p95 | `00-k6-공통-요약/graphs/k6-p95-latency-ko.svg` | `00-k6-공통-요약/graphs/k6-p95-latency.svg` |
| 공통 k6 RPS | `00-k6-공통-요약/graphs/k6-rps-ko.svg` | `00-k6-공통-요약/graphs/k6-rps.svg` |
| 트러블 슈팅 1 커넥션 점유 | `01-운세-생성-외부-io-트랜잭션-분리/graphs/transaction-io-connection-hold-ko.svg` | `01-운세-생성-외부-io-트랜잭션-분리/graphs/transaction-io-connection-hold.svg` |
| 트러블 슈팅 1 pool capacity | `01-운세-생성-외부-io-트랜잭션-분리/graphs/transaction-io-pool-capacity-ko.svg` | `01-운세-생성-외부-io-트랜잭션-분리/graphs/transaction-io-pool-capacity.svg` |
| 트러블 슈팅 2 커넥션 점유 | `02-문의-답변-메일-io-트랜잭션-분리/graphs/transaction-io-connection-hold-ko.svg` | `02-문의-답변-메일-io-트랜잭션-분리/graphs/transaction-io-connection-hold.svg` |
| 트러블 슈팅 2 pool capacity | `02-문의-답변-메일-io-트랜잭션-분리/graphs/transaction-io-pool-capacity-ko.svg` | `02-문의-답변-메일-io-트랜잭션-분리/graphs/transaction-io-pool-capacity.svg` |
| 트러블 슈팅 3 count p95 | `03-갤러리-목록-조회-쿼리-최적화/graphs/gallery-query-count-p95-ko.svg` | `03-갤러리-목록-조회-쿼리-최적화/graphs/gallery-query-count-p95.svg` |
| 트러블 슈팅 3 list p95 | `03-갤러리-목록-조회-쿼리-최적화/graphs/gallery-query-list-p95-ko.svg` | `03-갤러리-목록-조회-쿼리-최적화/graphs/gallery-query-list-p95.svg` |
| 트러블 슈팅 4 활성 방 p95 | `04-무한캔버스-조회-payload-최적화/graphs/infinite-canvas-active-room-p95-ko.svg` | `04-무한캔버스-조회-payload-최적화/graphs/infinite-canvas-active-room-p95.svg` |
| 트러블 슈팅 4 payload | `04-무한캔버스-조회-payload-최적화/graphs/infinite-canvas-websocket-payload-ko.svg` | `04-무한캔버스-조회-payload-최적화/graphs/infinite-canvas-websocket-payload.svg` |
| 트러블 슈팅 5 operation apply p95 | `05-무한캔버스-요소-적용-최적화/graphs/infinite-canvas-operation-apply-p95-ko.svg` | `05-무한캔버스-요소-적용-최적화/graphs/infinite-canvas-operation-apply-p95.svg` |

## 실행 가이드

- k6 설치와 실행 명령: `k6-실행-가이드.md`
- 터미널 상세 캡처 기준: `k6-상세-캡처-가이드.md`
- 공통 k6 결과 그래프: `00-k6-공통-요약/README.md`

각 트러블 슈팅의 상세 설명, before / after 표, 그래프 이미지는 해당 번호 폴더의 `README.md`에서 확인합니다.
