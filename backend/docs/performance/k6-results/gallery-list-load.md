# 갤러리 목록 조회 k6 부하 테스트

## 실행 조건

- base url: `http://localhost:8080/api/v1`
- vus: `20`
- duration: `30s`
- ramp up: `5s`
- ramp down: `5s`

## 결과

| 지표 | 값 |
| --- | ---: |
| 측정 endpoint tag | `gallery_list` |
| 요청 수 | 10745 |
| RPS | 268.54 |
| 실패율 | 0.00% |
| check 성공률 | 100.00% |
| 평균 latency | 14.87 ms |
| p50 latency | 14.27 ms |
| p95 latency | 19.96 ms |
| p99 latency | 26.69 ms |

> k6 결과는 실행 환경, seed 데이터, 외부 API stub 여부에 따라 달라집니다.
