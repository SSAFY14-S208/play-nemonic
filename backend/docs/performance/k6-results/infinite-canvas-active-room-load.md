# 무한캔버스 활성 방 목록 k6 부하 테스트

## 실행 조건

- base url: `http://localhost:8080/api/v1`
- vus: `20`
- duration: `30s`
- ramp up: `5s`
- ramp down: `5s`

## 결과

| 지표 | 값 |
| --- | ---: |
| 측정 endpoint tag | `infinite_canvas_active_rooms` |
| 요청 수 | 11000 |
| RPS | 274.57 |
| 실패율 | 0.00% |
| check 성공률 | 100.00% |
| 평균 latency | 13.02 ms |
| p50 latency | 11.76 ms |
| p95 latency | 20.54 ms |
| p99 latency | 49.64 ms |

> k6 결과는 실행 환경, seed 데이터, 외부 API stub 여부에 따라 달라집니다.
