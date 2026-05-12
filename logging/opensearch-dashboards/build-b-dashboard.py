#!/usr/bin/env python3
"""
B 대시보드 (릴레이/플립북 방 통계) NDJSON 생성기.

OpenSearch Dashboards 2.15 saved-object 스키마로 6개 visualization +
1개 dashboard를 한 NDJSON 파일에 작성. visState/uiStateJSON 같은 nested-JSON
직렬화는 escape 오류가 흔해서 직접 NDJSON 손으로 쓰는 대신 dict → json.dumps로
빌드한 뒤 한 줄씩 출력한다.

재실행 = 멱등 (output 파일 덮어쓰기).
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "saved-objects" / "10-dashboard-room-stats.ndjson"

# index-pattern ID (이미 saved-objects/00-index-patterns.ndjson에 박혀 있는 값).
# 모든 viz가 references에 이 id를 통해 biz-events-* 패턴을 참조한다.
BIZ_EVENTS_PATTERN_ID = "8528e1b0-4cd5-11f1-93a4-814592ee4ccd"

# 모든 viz가 공유하는 reference 항목 — kibanaSavedObjectMeta.searchSourceJSON
# 안의 indexRefName이 이 이름을 가리킨다 (OpenSearch Dashboards 표준 패턴).
INDEX_PATTERN_REF = {
    "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
    "type": "index-pattern",
    "id": BIZ_EVENTS_PATTERN_ID,
}


def search_source(query="", filters=None):
    """visualization의 attributes.kibanaSavedObjectMeta.searchSourceJSON 본문.
    JSON 문자열로 직렬화돼서 저장된다."""
    return json.dumps({
        "query": {"query": query, "language": "lucene"},
        "filter": filters or [],
        "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index",
    })


def viz(viz_id, title, vis_state, query="", description=""):
    """visualization saved-object 1개."""
    return {
        "id": viz_id,
        "type": "visualization",
        "attributes": {
            "title": title,
            "visState": json.dumps(vis_state, ensure_ascii=False),
            "uiStateJSON": "{}",
            "description": description,
            "version": 1,
            "kibanaSavedObjectMeta": {
                "searchSourceJSON": search_source(query=query),
            },
        },
        "references": [INDEX_PATTERN_REF],
        "migrationVersion": {"visualization": "7.10.0"},
    }


# ============================================================
# V1: 콘텐츠별 이벤트 추이 (Stacked Vertical Bar)
# 시간 흐름에 따라 websocket-server 가 처리한 이벤트 카운트를 1시간 버킷,
# content_type 별로 쌓아서 표시. relay vs flipbook 활동 패턴 비교.
# ============================================================
V1 = viz(
    viz_id="vis-roomstats-events-timeline",
    title="[B1] 콘텐츠별 이벤트 추이 (1h)",
    description="websocket-server 이벤트를 content_type(relay/flipbook)별로 시간 분포 누적.",
    query="service:websocket-server",
    vis_state={
        "title": "[B1] 콘텐츠별 이벤트 추이 (1h)",
        "type": "histogram",
        "params": {
            "type": "histogram",
            "grid": {"categoryLines": False},
            "categoryAxes": [{
                "id": "CategoryAxis-1", "type": "category", "position": "bottom",
                "show": True, "style": {}, "scale": {"type": "linear"},
                "labels": {"show": True, "filter": True, "truncate": 100}, "title": {},
            }],
            "valueAxes": [{
                "id": "ValueAxis-1", "name": "LeftAxis-1", "type": "value", "position": "left",
                "show": True, "style": {}, "scale": {"type": "linear", "mode": "normal"},
                "labels": {"show": True, "rotate": 0, "filter": False, "truncate": 100},
                "title": {"text": "Count"},
            }],
            "seriesParams": [{
                "show": True, "type": "histogram", "mode": "stacked",
                "data": {"label": "Count", "id": "1"},
                "valueAxis": "ValueAxis-1", "drawLinesBetweenPoints": True, "showCircles": True,
            }],
            "addTooltip": True, "addLegend": True, "legendPosition": "right",
            "times": [], "addTimeMarker": False,
        },
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "date_histogram", "schema": "segment", "params": {
                "field": "@timestamp", "useNormalizedEsInterval": True,
                "interval": "h", "drop_partials": False, "min_doc_count": 1, "extended_bounds": {},
            }},
            {"id": "3", "enabled": True, "type": "terms", "schema": "group", "params": {
                "field": "content_type", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": True, "otherBucketLabel": "(missing)",
                "missingBucket": False,
            }},
        ],
    },
)

# ============================================================
# V2: WS 연결 거절 사유 분포 (Pie)
# 모든 *_ws_connection_rejected 이벤트의 metadata.reject_reason 분포.
# "이미 종료된 방", "존재하지 않는 방" 같은 운영 신호의 비율 파악.
# ============================================================
V2 = viz(
    viz_id="vis-roomstats-rejected-reasons",
    title="[B2] WS 연결 거절 사유 분포",
    description="*_ws_connection_rejected 이벤트의 reject_reason 카테고리별 비율.",
    query='event_name:(*_ws_connection_rejected)',
    vis_state={
        "title": "[B2] WS 연결 거절 사유 분포",
        "type": "pie",
        "params": {
            "type": "pie",
            "addTooltip": True,
            "addLegend": True,
            "legendPosition": "right",
            "isDonut": False,
            "labels": {"show": True, "values": True, "last_level": True, "truncate": 100},
        },
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "segment", "params": {
                "field": "metadata.reject_reason", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": True, "otherBucketLabel": "기타",
                "missingBucket": True, "missingBucketLabel": "(unspecified)",
            }},
        ],
    },
)

# ============================================================
# V3: 방 종료 분류 (Donut)
# 정상 종료(*_room_closed) vs 강제 종료(*_room_force_close) vs 정리(*_cleanup_*) 비율.
# 운영 측면에서 좀비방 정리 vs 자연 종료의 균형 파악.
# ============================================================
V3 = viz(
    viz_id="vis-roomstats-close-types",
    title="[B3] 방 종료 분류",
    description="room_closed / force_close / cleanup 이벤트 비율로 종료 사유 구분.",
    query=(
        'event_name:('
        '*_room_closed OR '
        '*_room_force_close OR '
        '*_orphan_cleanup_completed OR '
        '*_temp_cleanup_completed'
        ')'
    ),
    vis_state={
        "title": "[B3] 방 종료 분류",
        "type": "pie",
        "params": {
            "type": "pie",
            "addTooltip": True,
            "addLegend": True,
            "legendPosition": "right",
            "isDonut": True,
            "labels": {"show": True, "values": True, "last_level": True, "truncate": 100},
        },
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "segment", "params": {
                "field": "event_name", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False, "missingBucket": False,
            }},
        ],
    },
)

# ============================================================
# V4: 진행 단계 분포 (Data Table)
# 방 생성 → 게임 시작 → 제출 → 종료 등 핵심 진행 이벤트의 count.
# Funnel 비율은 사용자가 행끼리 직접 비교 (relay vs flipbook 각각).
# OpenSearch Dashboards에 정식 funnel viz는 없으므로 split rows table 사용.
# ============================================================
V4 = viz(
    viz_id="vis-roomstats-funnel-table",
    title="[B4] 콘텐츠별 진행 단계 분포",
    description="room_created → game_started → submitted → completed 단계별 카운트 (content_type별).",
    query=(
        'service:websocket-server AND event_name:('
        '*_room_created OR '
        '*_game_started OR '
        '*_round_started OR '
        '*_drawing_submitted OR '
        '*_frame_submitted OR '
        '*_part_started OR '
        '*_part_time_up OR '
        '*_all_parts_completed OR '
        '*_room_finished OR '
        '*_result_created'
        ')'
    ),
    vis_state={
        "title": "[B4] 콘텐츠별 진행 단계 분포",
        "type": "table",
        "params": {
            "perPage": 20,
            "showPartialRows": False,
            "showMetricsAtAllLevels": False,
            "showTotal": False,
            "totalFunc": "sum",
            "percentageCol": "",
        },
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "content_type", "orderBy": "1", "order": "desc",
                "size": 5, "otherBucket": False, "missingBucket": False,
                "customLabel": "콘텐츠",
            }},
            {"id": "3", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "event_name", "orderBy": "1", "order": "desc",
                "size": 30, "otherBucket": False, "missingBucket": False,
                "customLabel": "이벤트",
            }},
        ],
    },
)

# ============================================================
# V5: 활동성 Top 방 (Data Table)
# room_id별 ws 이벤트 카운트 desc top 20. 어느 방이 가장 활발했는지.
# 종료된 방도 포함 (감사 목적).
# ============================================================
V5 = viz(
    viz_id="vis-roomstats-top-rooms",
    title="[B5] 활동성 Top 20 방",
    description="websocket-server 이벤트 카운트 기준 room_id 상위 20개.",
    query="service:websocket-server AND _exists_:room_id",
    vis_state={
        "title": "[B5] 활동성 Top 20 방",
        "type": "table",
        "params": {
            "perPage": 20,
            "showPartialRows": False,
            "showMetricsAtAllLevels": False,
            "showTotal": True,
            "totalFunc": "sum",
            "percentageCol": "",
        },
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric",
             "params": {"customLabel": "이벤트 수"}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "room_id", "orderBy": "1", "order": "desc",
                "size": 20, "otherBucket": False, "missingBucket": False,
                "customLabel": "Room ID",
            }},
            {"id": "3", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "content_type", "orderBy": "1", "order": "desc",
                "size": 3, "otherBucket": False, "missingBucket": False,
                "customLabel": "콘텐츠",
            }},
        ],
    },
)

# ============================================================
# V6: 메타 카운터 (Metric, 4개 시리즈)
# filters bucket으로 한 viz 안에 4개 카운트 동시 표시 — 게임 시작 / 제출 /
# 강제 종료 / 정상 종료.
# ============================================================
V6 = viz(
    viz_id="vis-roomstats-meta-metrics",
    title="[B6] 핵심 카운터",
    description="기간 내 게임 시작 / 제출 / 강제 종료 / 정상 종료 누적 카운트.",
    query="",
    vis_state={
        "title": "[B6] 핵심 카운터",
        "type": "metric",
        "params": {
            "addTooltip": True,
            "addLegend": False,
            "type": "metric",
            "metric": {
                "percentageMode": False,
                "useRanges": False,
                "colorSchema": "Green to Red",
                "metricColorMode": "None",
                "colorsRange": [{"from": 0, "to": 10000}],
                "labels": {"show": True},
                "invertColors": False,
                "style": {
                    "bgFill": "#000",
                    "bgColor": False,
                    "labelColor": False,
                    "subText": "",
                    "fontSize": 36,
                },
            },
        },
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "filters", "schema": "group", "params": {
                "filters": [
                    {"input": {"query": "event_name:*_game_started", "language": "lucene"},
                     "label": "게임 시작"},
                    {"input": {"query": "event_name:(*_drawing_submitted OR *_frame_submitted)",
                               "language": "lucene"},
                     "label": "제출"},
                    {"input": {"query": "event_name:(*_room_force_close)",
                               "language": "lucene"},
                     "label": "강제 종료"},
                    {"input": {"query": "event_name:(*_room_closed OR *_room_finished)",
                               "language": "lucene"},
                     "label": "정상 종료"},
                ],
            }},
        ],
    },
)


# ============================================================
# Dashboard — 6개 viz를 그리드로 배치.
# OpenSearch Dashboards grid: 48 column width 기준 (실제로는 0~48 grid coords).
# 각 panel: x/y 좌표 + w(너비)/h(높이). 6 + 6 = 12 columns 의 절반 split,
# 8 columns 의 2/3 split 등 자유로움. 여기는 운영자가 한눈에 보기 좋은 배치:
#
#   [V6 메타카운터 (full width, 짧음)]
#   [V1 이벤트 추이 (full width)]
#   [V2 거절 사유    ][V3 종료 분류    ]
#   [V4 진행 단계 분포 (full width)]
#   [V5 Top 방 (full width)]
# ============================================================
PANELS = [
    # V6 메타 카운터 (높이 짧음, 가장 위)
    {"vis_id": V6["id"], "panel_id": "1",
     "grid": {"x": 0,  "y": 0,  "w": 48, "h": 8}},
    # V1 이벤트 추이 (시간 흐름)
    {"vis_id": V1["id"], "panel_id": "2",
     "grid": {"x": 0,  "y": 8,  "w": 48, "h": 15}},
    # V2 / V3 가로 2분할
    {"vis_id": V2["id"], "panel_id": "3",
     "grid": {"x": 0,  "y": 23, "w": 24, "h": 15}},
    {"vis_id": V3["id"], "panel_id": "4",
     "grid": {"x": 24, "y": 23, "w": 24, "h": 15}},
    # V4 진행 단계 분포
    {"vis_id": V4["id"], "panel_id": "5",
     "grid": {"x": 0,  "y": 38, "w": 48, "h": 18}},
    # V5 Top 방
    {"vis_id": V5["id"], "panel_id": "6",
     "grid": {"x": 0,  "y": 56, "w": 48, "h": 18}},
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
        "id": "dashboard-roomstats",
        "type": "dashboard",
        "attributes": {
            "title": "[B] 릴레이/플립북 방 통계",
            "hits": 0,
            "description": (
                "백오피스 spec §통계 및 분석 / §릴레이/플립북 관리 — "
                "방 통계(콘텐츠별 이벤트, 진행 단계, 종료 분류, 거절 사유, Top 방)."
            ),
            "panelsJSON": json.dumps(panels_json, ensure_ascii=False),
            "optionsJSON": json.dumps({
                "useMargins": True,
                "syncColors": False,
                "hidePanelTitles": False,
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
        "migrationVersion": {"dashboard": "7.10.0"},
    }


DASHBOARD = build_dashboard()


def write_ndjson(objects, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for obj in objects:
            f.write(json.dumps(obj, ensure_ascii=False))
            f.write("\n")


if __name__ == "__main__":
    OBJECTS = [V1, V2, V3, V4, V5, V6, DASHBOARD]
    write_ndjson(OBJECTS, OUT)
    print(f"wrote {len(OBJECTS)} saved-objects → {OUT}")
    print("titles:")
    for o in OBJECTS:
        print(f"  - {o['type']:14} {o['id']:35} {o['attributes']['title']}")
