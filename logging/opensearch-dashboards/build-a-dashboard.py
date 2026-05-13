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
# Panel 정의 — 다른 대시보드에서 만든 viz id를 그대로 참조.
#
# id 규약(이미 적용):
#   B (room stats):  vis-roomstats-*
#   C (community):   vis-community-*
#   E (audit):       vis-audit-*
#   F (errors):      vis-errors-*
#
# 레이아웃 (48 col grid):
#   [B6 콘텐츠 카운터    ][C6 커뮤니티 카운터  ]
#   [E6 운영 카운터      ][F6 에러 카테고리   ]
#   [B1 콘텐츠 이벤트 추이 (full)              ]
#   [C1 메모 라이프사이클 (full)               ]
#   [F1 에러 추이 (full)                        ]
#   [E5 위험 액션 추이 (full)                   ]
# ============================================================
PANELS = [
    # 카운터 행 1: 콘텐츠 vs 커뮤니티
    {"vis_id": "vis-roomstats-meta-metrics",   "panel_id": "1",
     "grid": {"x": 0,  "y": 0,  "w": 24, "h": 10}},
    {"vis_id": "vis-community-metrics",        "panel_id": "2",
     "grid": {"x": 24, "y": 0,  "w": 24, "h": 10}},

    # 카운터 행 2: 운영 vs 에러
    {"vis_id": "vis-audit-metrics",            "panel_id": "3",
     "grid": {"x": 0,  "y": 10, "w": 24, "h": 10}},
    {"vis_id": "vis-errors-metrics",           "panel_id": "4",
     "grid": {"x": 24, "y": 10, "w": 24, "h": 10}},

    # 추이 행: 콘텐츠 → 커뮤니티 → 에러 → 위험 액션
    {"vis_id": "vis-roomstats-events-timeline","panel_id": "5",
     "grid": {"x": 0,  "y": 20, "w": 48, "h": 13}},
    {"vis_id": "vis-community-lifecycle",      "panel_id": "6",
     "grid": {"x": 0,  "y": 33, "w": 48, "h": 13}},
    {"vis_id": "vis-errors-timeline",          "panel_id": "7",
     "grid": {"x": 0,  "y": 46, "w": 48, "h": 13}},
    {"vis_id": "vis-audit-risk-actions",       "panel_id": "8",
     "grid": {"x": 0,  "y": 59, "w": 48, "h": 13}},
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
                "백오피스 spec §대시보드 지표 — 운영자 진입점. "
                "B(콘텐츠) / C(커뮤니티) / E(감사) / F(에러) 의 핵심 카운터와 "
                "추이를 한 화면에 모아 둠. 세부 분석은 각 대시보드로 이동."
            ),
            "panelsJSON": json.dumps(panels_json, ensure_ascii=False),
            "optionsJSON": json.dumps({
                "useMargins": True, "syncColors": False, "hidePanelTitles": False,
            }),
            "version": 1,
            "timeRestore": True,
            "timeTo": "now",
            "timeFrom": "now-7d",
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
