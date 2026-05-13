#!/usr/bin/env python3
"""
E 대시보드 (운영 감사) NDJSON 생성기.

백오피스 spec §"감사 로그" 기반. audit-logs-* 인덱스 사용.
6개 visualization + 1개 dashboard.

운영 관점:
- 일별 전체 액션 추이
- 어떤 대상(target_type)을 가장 많이 조작했나
- 액션 성공/실패 분포
- 어떤 운영자(actor_id)가 가장 많이 작업했나
- 위험 액션(강제 종료/삭제/admin 계정 등) 추이
- 핵심 KPI 카운터

audit-logs-* 인덱스 ID: bfacb2e0-4cd4-11f1-93a4-814592ee4ccd
재실행 = 멱등.
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "saved-objects" / "30-dashboard-audit.ndjson"

AUDIT_LOGS_PATTERN_ID = "bfacb2e0-4cd4-11f1-93a4-814592ee4ccd"
INDEX_PATTERN_REF = {
    "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
    "type": "index-pattern",
    "id": AUDIT_LOGS_PATTERN_ID,
}


def search_source(query=""):
    return json.dumps({
        "query": {"query": query, "language": "lucene"},
        "filter": [],
        "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index",
    })


def viz(viz_id, title, vis_state, query="", description="", colors=None):
    ui_state = "{}"
    if colors:
        ui_state = json.dumps({"vis": {"colors": colors}}, ensure_ascii=False)
    return {
        "id": viz_id,
        "type": "visualization",
        "attributes": {
            "title": title,
            "visState": json.dumps(vis_state, ensure_ascii=False),
            "uiStateJSON": ui_state,
            "description": description,
            "version": 1,
            "kibanaSavedObjectMeta": {
                "searchSourceJSON": search_source(query=query),
            },
        },
        "references": [INDEX_PATTERN_REF],
    }


# ============================================================
# SRE 컬러 시스템 (E 도메인 — 감사)
# ============================================================
COLOR_AUDIT_RESULT = {
    "success": "#10B981",
    "failure": "#EF4444",
}

COLOR_RISK_ACTIONS = {
    "relay_room_force_close":      "#EF4444",
    "flipbook_room_force_close":   "#EF4444",
    "infinite_canvas_force_close": "#EF4444",
    "memo_soft_delete":            "#F59E0B",
    "memo_bulk_soft_delete":       "#F59E0B",
    "memo_restore":                "#3B82F6",
    "memo_bulk_restore":           "#3B82F6",
    "admin_account_create":        "#991B1B",
    "admin_account_delete":        "#991B1B",
    "prompt_update":               "#F59E0B",
    "prompt_rollback":             "#F59E0B",
    "param_change":                "#F59E0B",
    "ai_moderation_override":      "#F59E0B",
    "electron_channel_change":     "#94A3B8",
    "electron_release_publish":    "#94A3B8",
}

COLOR_E6_LABELS = {
    "로그인 성공":          "#10B981",
    "로그인 실패":          "#EF4444",
    "메모 삭제":            "#F59E0B",
    "메모 복원":            "#3B82F6",
    "방/캔버스 강제 종료":  "#EF4444",
    "파라미터 변경":        "#F59E0B",
}


# --- 공통 visualization params (B/C와 동일) ---
def histogram_params(stacked=True, title="Count"):
    return {
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
            "title": {"text": title},
        }],
        "seriesParams": [{
            "show": True, "type": "histogram", "mode": "stacked" if stacked else "grouped",
            "data": {"label": "Count", "id": "1"},
            "valueAxis": "ValueAxis-1", "drawLinesBetweenPoints": True, "showCircles": True,
        }],
        "addTooltip": True, "addLegend": True, "legendPosition": "right",
        "times": [], "addTimeMarker": False,
    }


def pie_params(donut=False):
    return {
        "type": "pie",
        "addTooltip": True,
        "addLegend": True,
        "legendPosition": "right",
        "isDonut": donut,
        "labels": {"show": True, "values": True, "last_level": True, "truncate": 100},
    }


def table_params(per_page=15):
    return {
        "perPage": per_page,
        "showPartialRows": False,
        "showMetricsAtAllLevels": False,
        "showTotal": True,
        "totalFunc": "sum",
        "percentageCol": "",
    }


def metric_params():
    return {
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
    }


# ============================================================
# E1: 액션 추이 (Stacked Bar, 1d)
#   audit-logs 전체 — 모든 감사 이벤트 일별 분포. event_name 별 split.
#   로그인/로그아웃 같은 자주 발생하는 이벤트와 가끔 발생하는 force_close
#   같은 운영 이벤트를 함께 시각화.
# ============================================================
E1 = viz(
    viz_id="vis-audit-action-timeline",
    title="[E1] 감사 액션 추이 (1d)",
    description="audit-logs 전체 — event_name 별 일별 stacked bar.",
    query="",
    vis_state={
        "title": "[E1] 감사 액션 추이 (1d)",
        "type": "histogram",
        "params": histogram_params(stacked=True),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "date_histogram", "schema": "segment", "params": {
                "field": "@timestamp", "useNormalizedEsInterval": True,
                "interval": "d", "drop_partials": False, "min_doc_count": 1, "extended_bounds": {},
            }},
            {"id": "3", "enabled": True, "type": "terms", "schema": "group", "params": {
                "field": "event_name", "orderBy": "1", "order": "desc",
                "size": 15, "otherBucket": True, "otherBucketLabel": "그 외",
                "missingBucket": False,
            }},
        ],
    },
)

# ============================================================
# E2: target_type 분포 (Donut)
#   memo / room / canvas / param / prompt / inquiry / admin_account / notification
#   어떤 대상을 가장 많이 조작했나.
# ============================================================
E2 = viz(
    viz_id="vis-audit-target-types",
    title="[E2] target_type 분포",
    description="조작 대상 카테고리 분포 (memo/room/canvas/param/prompt/...).",
    query="",
    vis_state={
        "title": "[E2] target_type 분포",
        "type": "pie",
        "params": pie_params(donut=True),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "segment", "params": {
                "field": "metadata.target_type", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(target 없음)",
            }},
        ],
    },
)

# ============================================================
# E3: 액션 결과 분포 (Pie)
#   metadata.result: success / failure.
#   운영자 실패 액션 비율 — 너무 높으면 권한/UX 문제 신호.
# ============================================================
E3 = viz(
    viz_id="vis-audit-results",
    title="[E3] 액션 결과 분포",
    description="metadata.result success vs failure — 운영 실패율 추적.",
    query="",
    colors=COLOR_AUDIT_RESULT,
    vis_state={
        "title": "[E3] 액션 결과 분포",
        "type": "pie",
        "params": pie_params(donut=False),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "segment", "params": {
                "field": "metadata.result", "orderBy": "1", "order": "desc",
                "size": 5, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(결과 미기록)",
            }},
        ],
    },
)

# ============================================================
# E4: Actor별 활동량 Top 10 (Data Table)
#   actor_id × actor_role 별 액션 카운트 desc.
#   누가 가장 많이 작업했는지 + super_admin vs admin 구분.
# ============================================================
E4 = viz(
    viz_id="vis-audit-actor-activity",
    title="[E4] Actor별 활동량 Top 10",
    description="actor_id (+actor_role)별 액션 카운트 — 운영자 활동 분포.",
    query="",
    vis_state={
        "title": "[E4] Actor별 활동량 Top 10",
        "type": "table",
        "params": table_params(per_page=10),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric",
             "params": {"customLabel": "액션 수"}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "metadata.actor_id", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(actor 없음)",
                "customLabel": "Actor ID",
            }},
            {"id": "3", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "metadata.actor_role", "orderBy": "1", "order": "desc",
                "size": 3, "otherBucket": False, "missingBucket": False,
                "customLabel": "Role",
            }},
        ],
    },
)

# ============================================================
# E5: 위험 액션 추이 (Stacked Bar, 1d)
#   강제 종료 / 메모 삭제·복원 / admin 계정 / 프롬프트 롤백 / 파라미터 변경.
#   백오피스 spec §"감사 로그" "기록 대상 이벤트" 중 영향력 큰 것만 filter.
# ============================================================
E5 = viz(
    viz_id="vis-audit-risk-actions",
    title="[E5] 위험 액션 추이 (1d)",
    description="강제 종료/삭제·복원/admin 계정/프롬프트 롤백/파라미터 변경.",
    colors=COLOR_RISK_ACTIONS,
    query=(
        "event_name:("
        "*_force_close OR "
        "memo_soft_delete OR memo_bulk_soft_delete OR "
        "memo_restore OR memo_bulk_restore OR "
        "admin_account_create OR admin_account_delete OR "
        "prompt_update OR prompt_rollback OR "
        "param_change OR "
        "ai_moderation_override OR "
        "electron_channel_change OR electron_release_publish"
        ")"
    ),
    vis_state={
        "title": "[E5] 위험 액션 추이 (1d)",
        "type": "histogram",
        "params": histogram_params(stacked=True),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "date_histogram", "schema": "segment", "params": {
                "field": "@timestamp", "useNormalizedEsInterval": True,
                "interval": "d", "drop_partials": False, "min_doc_count": 1, "extended_bounds": {},
            }},
            {"id": "3", "enabled": True, "type": "terms", "schema": "group", "params": {
                "field": "event_name", "orderBy": "1", "order": "desc",
                "size": 15, "otherBucket": False, "missingBucket": False,
            }},
        ],
    },
)

# ============================================================
# E6: 핵심 카운터 (Metric, 6 박스 — filters bucket)
#   로그인 성공/실패 / 메모 삭제 / 메모 복원 / 방 강제 종료 / 파라미터 변경
# ============================================================
E6 = viz(
    viz_id="vis-audit-metrics",
    title="[E6] 핵심 카운터",
    description="기간 내 로그인/삭제/복원/강제 종료/파라미터 변경 누적.",
    query="",
    colors=COLOR_E6_LABELS,
    vis_state={
        "title": "[E6] 핵심 카운터",
        "type": "metric",
        "params": metric_params(),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "filters", "schema": "group", "params": {
                "filters": [
                    {"input": {"query": "event_name:admin_login_success", "language": "lucene"},
                     "label": "로그인 성공"},
                    {"input": {"query": "event_name:admin_login_failed", "language": "lucene"},
                     "label": "로그인 실패"},
                    {"input": {"query": "event_name:(memo_soft_delete OR memo_bulk_soft_delete)",
                               "language": "lucene"},
                     "label": "메모 삭제"},
                    {"input": {"query": "event_name:(memo_restore OR memo_bulk_restore)",
                               "language": "lucene"},
                     "label": "메모 복원"},
                    {"input": {"query": "event_name:(*_force_close)", "language": "lucene"},
                     "label": "방/캔버스 강제 종료"},
                    {"input": {"query": "event_name:param_change", "language": "lucene"},
                     "label": "파라미터 변경"},
                ],
            }},
        ],
    },
)


# ============================================================
# Dashboard — 6 viz 그리드 (48 col 기준)
#   [E6 카운터 (full, 짧음)]
#   [E1 액션 추이 (full)]
#   [E2 target_type][E3 액션 결과]
#   [E4 Actor Top 10 (full)]
#   [E5 위험 액션 추이 (full)]
# ============================================================
PANELS = [
    {"vis_id": E6["id"], "panel_id": "1", "grid": {"x": 0,  "y": 0,  "w": 48, "h": 8}},
    {"vis_id": E1["id"], "panel_id": "2", "grid": {"x": 0,  "y": 8,  "w": 48, "h": 15}},
    {"vis_id": E2["id"], "panel_id": "3", "grid": {"x": 0,  "y": 23, "w": 24, "h": 15}},
    {"vis_id": E3["id"], "panel_id": "4", "grid": {"x": 24, "y": 23, "w": 24, "h": 15}},
    {"vis_id": E4["id"], "panel_id": "5", "grid": {"x": 0,  "y": 38, "w": 48, "h": 15}},
    {"vis_id": E5["id"], "panel_id": "6", "grid": {"x": 0,  "y": 53, "w": 48, "h": 15}},
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
        "id": "dashboard-audit",
        "type": "dashboard",
        "attributes": {
            "title": "[E] 운영 감사",
            "hits": 0,
            "description": (
                "백오피스 spec §감사 로그 — audit-logs 기반. 액션 추이, "
                "대상 분포, 성공률, actor 활동량, 위험 액션 추적."
            ),
            "panelsJSON": json.dumps(panels_json, ensure_ascii=False),
            "optionsJSON": json.dumps({
                "useMargins": True, "syncColors": False, "hidePanelTitles": False,
            }),
            "version": 1,
            "timeRestore": True,
            "timeTo": "now",
            "timeFrom": "now-30d",  # 감사 로그는 더 긴 기간 default
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
    OBJECTS = [E1, E2, E3, E4, E5, E6, DASHBOARD]
    write_ndjson(OBJECTS, OUT)
    print(f"wrote {len(OBJECTS)} saved-objects -> {OUT}")
    for o in OBJECTS:
        print(f"  - {o['type']:14} {o['id']:32} {o['attributes']['title']}")
