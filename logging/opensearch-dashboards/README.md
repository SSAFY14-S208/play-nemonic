# OpenSearch Dashboards Saved-Objects (코드 관리)

`https://k14s208.p.ssafy.io/_dashboards/` 의 index-pattern / visualization /
dashboard 정의를 NDJSON으로 git에 두고, **`dashboards-init` oneshot 컨테이너가
매 배포마다 자동으로 import** 한다.

UI에서 만든 작업물이 OpenSearch 데이터 디스크 (`/opt/nemonic/data/opensearch/`)
안의 `.kibana_*` 인덱스에 저장되는데, 디스크 손상/노드 교체/SM 복구 시 그
인덱스만 비면 모든 시각화가 증발하므로 — 같은 정의를 코드로도 보관해서
멱등 복구가 가능하게 만든다.

## 파일 구조

```
logging/opensearch-dashboards/
├── README.md
├── import.sh                       # dashboards-init 컨테이너 entrypoint
├── build-*-dashboard.py            # 각 dashboard NDJSON 생성기 (멱등)
└── saved-objects/
    ├── 00-index-patterns.ndjson    # 모든 visualization의 prerequisite
    ├── 10-dashboard-room-stats.ndjson         # [B] 릴레이/플립북 방 통계
    ├── 20-dashboard-community.ndjson          # [C] 커뮤니티 메모
    ├── 30-dashboard-audit.ndjson              # [E] 운영자 감사
    ├── 40-dashboard-errors.ndjson             # [F] 에러
    ├── 50-dashboard-overview.ndjson           # [A] 전체 개요
    ├── 60-dashboard-fortune.ndjson            # [G] 운세
    └── 70-dashboard-frontend.ndjson           # [H] 프론트엔드 사용자 행동
```

**파일명 prefix(`00-`, `10-`, `20-`...)는 import 순서를 결정한다.**
`import.sh`가 파일명 사전식 정렬로 차례대로 처리. `00-` 은 모든 visualization이
참조하는 index-pattern이라 반드시 먼저.

**한 대시보드 = 한 파일** 원칙.
한 NDJSON 파일 안에 dashboard 1개와 그가 참조하는 visualization / saved search가
모두 들어가야 reference가 끊기지 않는다. OpenSearch Dashboards의 import API는
파일 단위로 처리하므로 dashboard와 visualization을 다른 파일로 쪼개면
"missing reference" 오류로 import 실패한다.

## 신규 대시보드 추가 절차

1. **UI에서 작업**: `https://k14s208.p.ssafy.io/_dashboards/` 에서 visualization /
   dashboard 만든다.

2. **Export (NDJSON 받기)** — dashboard 단위로 묶어서:

   ```bash
   # 대시보드 ID는 UI > Dashboards > 해당 대시보드의 URL에서 확인
   docker exec nemonic-logging-dashboards \
     curl -s -X POST \
       -H "osd-xsrf: true" \
       -H "Content-Type: application/json" \
       -d '{
         "objects": [
           { "type": "dashboard", "id": "<DASHBOARD_ID>" }
         ],
         "includeReferencesDeep": true,
         "excludeExportDetails": true
       }' \
       "http://127.0.0.1:5601/_dashboards/api/saved_objects/_export" \
     > /tmp/<NN>-<name>.ndjson
   ```

   `includeReferencesDeep: true` 가 dashboard가 참조하는 visualization /
   saved search 까지 함께 export 한다. `excludeExportDetails: true` 는 마지막
   메타데이터 줄(`{"exportedCount":...}`)을 빼서 그대로 쓸 수 있게 한다.

3. **commit**: `saved-objects/<NN>-<name>.ndjson` 으로 저장 후 commit.

4. **Apply**:
   ```bash
   docker compose -p nemonic-logging \
     -f /opt/nemonic/infra/docker-compose.logging.yml \
     up -d --no-deps --force-recreate dashboards-init

   docker logs nemonic-logging-dashboards-init --tail=50
   ```

   `[dashboards-init] OK <name>.ndjson — imported N object(s)` 라인이 보이면 성공.

## index-pattern 추가/수정

`00-index-patterns.ndjson` 한 파일에 5개 패턴 (biz-events-*, error-logs-*,
access-logs-*, system-logs-*, audit-logs-*) 모두 모아둔다.

visualization이 index-pattern을 references로 참조하므로, **`id` 값은 절대 변경하지
않는다.** id를 바꾸면 모든 visualization이 깨진다. UI에서 패턴을 새로 만들 때
자동 생성된 random UUID를 그대로 보존.

## 멱등성

`import.sh` 는 `overwrite=true` 로 호출 — 같은 id 객체가 이미 있으면 덮어쓴다.
매 배포마다 dashboards-init 가 다시 돌아도 결과는 같다. 안전.

## 트러블슈팅

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| `[dashboards-init] FAIL ... HTTP 503` | Dashboards 아직 ready 아님 | start_period 늘리거나 healthcheck 안정될 때까지 대기 |
| `successCount: 0` + `errors: [...]` 에 `missing_references` | dashboard가 참조하는 viz/index-pattern이 같은 파일 또는 먼저 import된 파일에 없음 | dashboard와 그 의존성을 같은 NDJSON에 묶어두기 |
| `version_conflict` | 다른 사람이 UI에서 수정한 객체 위에 import 시도 | UI 변경분을 export로 다시 빼와서 NDJSON 갱신 |

## 운영 노트

- OpenSearch 데이터 SM 스냅샷에 `.kibana_*` 인덱스도 포함되지만, 복구 후에도
  이 NDJSON이 single source of truth — 스냅샷 ↔ git 사이에서 git을 우선한다.
- UI에서 즉흥 수정한 시각화는 시간이 지나면 사라질 수 있으니 (다음 dashboards-init
  실행에서 git 버전으로 덮어쓰기), 영구 변경은 반드시 export → commit 흐름으로.
