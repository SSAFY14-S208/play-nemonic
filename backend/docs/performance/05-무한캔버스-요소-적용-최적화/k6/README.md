# k6 제외 사유

트러블 슈팅 5는 직접 k6 대상이 아닙니다.

무한캔버스 요소 적용 로직은 HTTP API 지연 시간보다 서버 내부 자료구조 적용 비용이 핵심 병목이었습니다. 그래서 Redis, 네트워크, WebSocket broadcast를 제외하고 `InfiniteCanvasOperationApplier`의 순수 적용 비용을 synthetic benchmark로 측정했습니다.

관련 HTTP k6 검증은 트러블 슈팅 4의 활성 방 목록 조회에서 수행했습니다.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080/api/v1 \
  -e ADMIN_TOKEN=$ADMIN_TOKEN \
  -e RAMP_UP=5s \
  -e DURATION=30s \
  -e RAMP_DOWN=5s \
  -e VUS=20 \
  backend/docs/performance/04-무한캔버스-조회-payload-최적화/k6/04-무한캔버스-활성-방-목록-k6.js
```

트러블 슈팅 5의 before / after 수치는 아래 명령으로 재생성합니다.

```bash
python3 backend/scripts/benchmark-infinite-canvas-performance.py --iterations 20 --output-dir backend/docs/performance/05-무한캔버스-요소-적용-최적화/graphs
```
