# 문의 답변 SMTP 외부 I/O 트랜잭션 분리 k6 부하 테스트

## 실행 조건

- base url: `http://localhost:8080/api/v1`
- vus: `5`
- duration: `20s`
- ramp up: `5s`
- ramp down: `5s`

## 결과

| 지표 | 값 |
| --- | ---: |
| 측정 endpoint tag | `admin_inquiry_reply` |
| 요청 수 | 967 |
| RPS | 32.09 |
| 실패율 | 0.00% |
| check 성공률 | 100.00% |
| 평균 latency | 14.00 ms |
| p50 latency | 13.18 ms |
| p95 latency | 19.53 ms |
| p99 latency | 35.20 ms |

> k6 결과는 실행 환경, seed 데이터, 외부 API stub 여부에 따라 달라집니다.
