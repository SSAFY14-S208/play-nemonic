# 커뮤니티 메모 인덱스 최적화 synthetic benchmark 결과

운영 DB 실측이 아니라 V12 인덱스가 바꾸는 쿼리 shape를 재현한 CPU-side synthetic benchmark입니다.

| historical memo rows | Before admin reported p95 ms | After admin reported p95 ms | admin ratio | Before report p95 ms | After report p95 ms | report ratio | Before touched rows | After touched rows |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1,000 | 0.12 | 0.006 | 19.99 | 0.11 | 0.004 | 29.38 | 1,000 | 20 |
| 10,000 | 2.89 | 0.005 | 542.46 | 1.82 | 0.003 | 533.00 | 10,000 | 20 |
| 50,000 | 31.30 | 0.005 | 5777.50 | 20.03 | 0.005 | 3815.57 | 50,000 | 20 |
