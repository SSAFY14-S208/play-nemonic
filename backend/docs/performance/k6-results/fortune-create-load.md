# 운세 생성 외부 I/O 트랜잭션 분리 k6 부하 테스트

## 실행 조건

- base url: `http://localhost:8080/api/v1`
- vus: `5`
- duration: `20s`
- ramp up: `5s`
- ramp down: `5s`

## 결과

| 지표 | 값 |
| --- | ---: |
| 측정 endpoint tag | `fortune_create` |
| 요청 수 | 465 |
| RPS | 15.45 |
| 실패율 | 0.00% |
| check 성공률 | 100.00% |
| 평균 latency | 166.48 ms |
| p50 latency | 162.66 ms |
| p95 latency | 204.33 ms |
| p99 latency | 243.67 ms |

> k6 결과는 실행 환경, seed 데이터, 외부 API stub 여부에 따라 달라집니다.
