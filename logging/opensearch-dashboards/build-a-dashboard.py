#!/usr/bin/env python3
"""
A 대시보드 (서비스 전체 현황 / Overview) NDJSON 생성기.

다른 대시보드(B~F)에 이미 만든 visualization 을 references로 임베드해서
"한눈에 보는 메타 대시보드"를 구성한다. 새 visualization 은 만들지 않는다.

핵심 4개 카운터(콘텐츠 / 커뮤니티 / 운영 / 에러) + 핵심 4개 추이.

import 순서: 50-* prefix 라 10/20/30/40 (각 도메인 dashboard) 가 먼저 import된 뒤
처리됨 → references 해석 성공.

재실행 = 멱등 (overwrite=true).
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "saved-objects" / "50-dashboard-overview.ndjson"


# ============================================================
# Panel 정의 — RED Method 패턴 (Rate / Errors / Duration) 으로 재배치.
#   Duration 은 Prometheus + Grafana 영역이라 여기 제외.
#
# 시각적 흐름 (운영자가 위에서 아래로 읽음):
#
#   ┌─ 상단: "지금 위험한 것" — F6 에러 카테고리 (threshold 색 적용)
#   │     └ Stat panel 패턴: 색만 보고 즉시 정상/비정상 판단
#   │
#   ├─ 중단 1: Rate(트래픽) vs Errors(에러) 가로 2분할
#   │     └ B1 콘텐츠 이벤트 추이 + F1 application별 에러 추이
#   │
#   ├─ 중단 2: Top Hot Spots — 원인 추적 단계
#   │     └ B5 활동 Top 방 + F3 Top Logger
#   │
#   ├─ 중단 3: E5 위험 액션 추이 — 운영자 조작 신호
#   │
#   └─ 하단: 활동 KPI (정상 활동 누적) — B6 / C6 / E6
#         └ 정상 활동도 알아둬야 비정상 비교 가능
# ============================================================
PANELS = [
    # ── 상단: "지금 위험한 것" ─────────────────────────────────
    {"vis_id": "vis-errors-metrics", "panel_id": "1",
     "grid": {"x": 0, "y": 0, "w": 48, "h": 10}},

    # ── 중단 1: Rate vs Errors ─────────────────────────────────
    {"vis_id": "vis-roomstats-events-timeline", "panel_id": "2",
     "grid": {"x": 0,  "y": 10, "w": 24, "h": 15}},
    {"vis_id": "vis-errors-timeline", "panel_id": "3",
     "grid": {"x": 24, "y": 10, "w": 24, "h": 15}},

    # ── 중단 2: Top Hot Spots (원인 추적) ──────────────────────
    {"vis_id": "vis-roomstats-top-rooms", "panel_id": "4",
     "grid": {"x": 0,  "y": 25, "w": 24, "h": 15}},
    {"vis_id": "vis-errors-top-logger", "panel_id": "5",
     "grid": {"x": 24, "y": 25, "w": 24, "h": 15}},

    # ── 중단 3: 위험 액션 ──────────────────────────────────────
    {"vis_id": "vis-audit-risk-actions", "panel_id": "6",
     "grid": {"x": 0, "y": 40, "w": 48, "h": 13}},

    # ── 하단: 활동 KPI 카운터 ──────────────────────────────────
    {"vis_id": "vis-roomstats-meta-metrics", "panel_id": "7",
     "grid": {"x": 0,  "y": 53, "w": 16, "h": 10}},
    {"vis_id": "vis-community-metrics", "panel_id": "8",
     "grid": {"x": 16, "y": 53, "w": 16, "h": 10}},
    {"vis_id": "vis-audit-metrics", "panel_id": "9",
     "grid": {"x": 32, "y": 53, "w": 16, "h": 10}},
]


def build_dashboard():
    panels_json = []
    references = []
    for p in PANELS:
        panel_ref_name = f"panel_{p['panel_id']}"
        panels_json.append({
            "version": "2.15.0",
            "type": "visualization",
            "gridData": {**p["grid"], "i": p["panel_id"]},
            "panelIndex": p["panel_id"],
            "embeddableConfig": {"enhancements": {}},
            "panelRefName": panel_ref_name,
        })
        references.append({
            "name": panel_ref_name,
            "type": "visualization",
            "id": p["vis_id"],
        })

    return {
        "id": "dashboard-overview",
        "type": "dashboard",
        "attributes": {
            "title": "[A] 서비스 전체 현황 (Overview)",
            "hits": 0,
            "description": (
                "백오피스 spec §대시보드 지표 — 운영자 진입점. RED Method "
                "(Rate/Errors) 흐름으로 배치: 상단 카테고리 카운터(색 신호) → "
                "Rate vs Errors 시계열 → Top hot spots → 위험 액션 → 활동 KPI. "
                "Duration(latency)은 Prometheus+Grafana 영역이라 제외. "
                "세부 분석은 각 대시보드로 drill-down."
            ),
            "panelsJSON": json.dumps(panels_json, ensure_ascii=False),
            "optionsJSON": json.dumps({
                "useMargins": True, "syncColors": False, "hidePanelTitles": False,
            }),
            "version": 1,
            "timeRestore": True,
            "timeTo": "now",
            "timeFrom": "now-24h",
            "refreshInterval": {"pause": True, "value": 0},
            "kibanaSavedObjectMeta": {
                "searchSourceJSON": json.dumps({
                    "query": {"query": "", "language": "lucene"},
                    "filter": [],
                }),
            },
        },
        "references": references,
    }


DASHBOARD = build_dashboard()


def write_ndjson(objects, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for obj in objects:
            f.write(json.dumps(obj, ensure_ascii=False))
            f.write("\n")


if __name__ == "__main__":
    OBJECTS = [DASHBOARD]
    write_ndjson(OBJECTS, OUT)
    print(f"wrote {len(OBJECTS)} saved-object -> {OUT}")
    print(f"  - {DASHBOARD['type']:14} {DASHBOARD['id']:25} {DASHBOARD['attributes']['title']}")
    print(f"  references {len(DASHBOARD['references'])} viz:")
    for r in DASHBOARD["references"]:
        print(f"    - {r['id']}")
