#!/usr/bin/env python3
"""
C 대시보드 (커뮤니티 운영) NDJSON 생성기.

백오피스 spec §"신고 관리" + §"커뮤니티 캔버스 관리" + §"AI 모더레이션 로그"
기반. 6개 visualization + 1개 dashboard.

운영 관점:
- 메모 라이프사이클 (작성/삭제/숨김/복원) 추이
- AI 모더레이션 결과 분포
- 신고 활동 추이
- 숨김 사유 분포 (자동 vs 운영자)
- abuse 시도 패턴 (duplicate report, ownership violation 등)
- 핵심 카운터 (6개 KPI)

재실행 = 멱등.
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "saved-objects" / "20-dashboard-community.ndjson"

BIZ_EVENTS_PATTERN_ID = "8528e1b0-4cd5-11f1-93a4-814592ee4ccd"
INDEX_PATTERN_REF = {
    "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
    "type": "index-pattern",
    "id": BIZ_EVENTS_PATTERN_ID,
}


def search_source(query=""):
    return json.dumps({
        "query": {"query": query, "language": "lucene"},
        "filter": [],
        "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index",
    })


def viz(viz_id, title, vis_state, query="", description=""):
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
    }


# ============================================================
# 공통 visualization 파라미터 (스타일 일관성)
# ============================================================
def histogram_params(stacked=True, title="Count"):
    """Vertical bar (stacked or grouped)."""
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


def table_params(per_page=20):
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
# C1: 메모 라이프사이클 추이 (1d 버킷, Stacked Bar)
#   created → user_deleted / auto_hidden_by_report / admin_hidden / admin_restored
#   백오피스 spec §"커뮤니티 캔버스 관리" + §"운영자 조치" 핵심 추이.
# ============================================================
C1 = viz(
    viz_id="vis-community-lifecycle",
    title="[C1] 메모 라이프사이클 추이 (1d)",
    description="created / user_deleted / auto_hidden / admin_hidden / admin_restored 일별 추이.",
    query=(
        "event_name:("
        "community_memo_created OR "
        "community_memo_user_deleted OR "
        "community_memo_auto_hidden_by_report OR "
        "admin_community_memo_hidden OR "
        "admin_community_memo_restored"
        ")"
    ),
    vis_state={
        "title": "[C1] 메모 라이프사이클 추이 (1d)",
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
                "size": 10, "otherBucket": False, "missingBucket": False,
            }},
        ],
    },
)

# ============================================================
# C2: AI 모더레이션 결과 분포 (Pie)
#   allowed / blocked / failed — slow는 별개라 C6 카운터에 둠.
#   백오피스 spec §"AI 모더레이션 로그" — 오탐 비율, 실패율 한눈에.
# ============================================================
C2 = viz(
    viz_id="vis-community-moderation",
    title="[C2] AI 모더레이션 결과 분포",
    description="moderation_allowed / blocked / failed 비율 (slow는 핵심 카운터에 별도).",
    query=(
        "event_name:("
        "community_memo_moderation_allowed OR "
        "community_memo_moderation_blocked OR "
        "community_memo_moderation_failed"
        ")"
    ),
    vis_state={
        "title": "[C2] AI 모더레이션 결과 분포",
        "type": "pie",
        "params": pie_params(donut=False),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "segment", "params": {
                "field": "event_name", "orderBy": "1", "order": "desc",
                "size": 5, "otherBucket": False, "missingBucket": False,
            }},
        ],
    },
)

# ============================================================
# C3: 신고 활동 추이 (1d Stacked Bar)
#   report_requested(시도) / created(접수) / rejected(거부, 예: 중복) /
#   threshold_reached(5회 도달 → 자동 숨김 트리거).
#   백오피스 spec §"신고 관리" 운영 동선 추적.
# ============================================================
C3 = viz(
    viz_id="vis-community-report-activity",
    title="[C3] 신고 활동 추이 (1d)",
    description="report_requested / created / rejected / threshold_reached 일별 분포.",
    query=(
        "event_name:("
        "community_memo_report_requested OR "
        "community_memo_report_created OR "
        "community_memo_report_rejected OR "
        "community_memo_report_threshold_reached"
        ")"
    ),
    vis_state={
        "title": "[C3] 신고 활동 추이 (1d)",
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
                "size": 10, "otherBucket": False, "missingBucket": False,
            }},
        ],
    },
)

# ============================================================
# C4: 숨김 사유 분포 (Donut)
#   자동(report_threshold + ai_moderation) vs 운영자(admin_hidden) 비율.
#   운영 부하 측면에서 자동 처리 비중이 얼마인지 모니터링.
# ============================================================
C4 = viz(
    viz_id="vis-community-hidden-reason",
    title="[C4] 숨김 사유 분포",
    description="자동 숨김(report_threshold) vs 운영자 숨김(admin_hidden) 비율.",
    query=(
        "event_name:("
        "community_memo_auto_hidden_by_report OR "
        "admin_community_memo_hidden"
        ")"
    ),
    vis_state={
        "title": "[C4] 숨김 사유 분포",
        "type": "pie",
        "params": pie_params(donut=True),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "segment", "params": {
                "field": "event_name", "orderBy": "1", "order": "desc",
                "size": 5, "otherBucket": False, "missingBucket": False,
            }},
        ],
    },
)

# ============================================================
# C5: Abuse 시도 패턴 (Data Table)
#   중복 신고, 권한 위반, 숨김된 메모 접근 등 보안 신호 카운트.
#   백오피스 spec §"신고 및 콘텐츠 관리"의 악성 패턴 모니터링.
# ============================================================
C5 = viz(
    viz_id="vis-community-abuse",
    title="[C5] Abuse 시도 패턴",
    description="중복 신고/권한 위반/숨김 메모 접근 등 abuse 신호 카운트.",
    query=(
        "event_name:("
        "community_duplicate_report_attempt OR "
        "community_hidden_memo_access_attempt OR "
        "community_deleted_memo_access_attempt OR "
        "community_ownership_violation OR "
        "community_file_ownership_violation OR "
        "community_invalid_uuid_repeated OR "
        "community_missing_user_header OR "
        "community_user_not_found"
        ")"
    ),
    vis_state={
        "title": "[C5] Abuse 시도 패턴",
        "type": "table",
        "params": table_params(per_page=15),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric",
             "params": {"customLabel": "발생 횟수"}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "event_name", "orderBy": "1", "order": "desc",
                "size": 15, "otherBucket": False, "missingBucket": False,
                "customLabel": "이벤트",
            }},
        ],
    },
)

# ============================================================
# C6: 핵심 카운터 (Metric, 6 박스 — filters bucket)
#   메모 작성 / 모더레이션 차단 / 신고 접수 / 자동 숨김 / 운영자 숨김 / 복원
# ============================================================
C6 = viz(
    viz_id="vis-community-metrics",
    title="[C6] 핵심 카운터",
    description="기간 내 작성/차단/신고/자동숨김/운영자숨김/복원 누적.",
    query="",
    vis_state={
        "title": "[C6] 핵심 카운터",
        "type": "metric",
        "params": metric_params(),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "filters", "schema": "group", "params": {
                "filters": [
                    {"input": {"query": "event_name:community_memo_created", "language": "lucene"},
                     "label": "메모 작성"},
                    {"input": {"query": "event_name:community_memo_moderation_blocked",
                               "language": "lucene"},
                     "label": "AI 차단"},
                    {"input": {"query": "event_name:community_memo_report_created",
                               "language": "lucene"},
                     "label": "신고 접수"},
                    {"input": {"query": "event_name:community_memo_auto_hidden_by_report",
                               "language": "lucene"},
                     "label": "자동 숨김"},
                    {"input": {"query": "event_name:admin_community_memo_hidden",
                               "language": "lucene"},
                     "label": "운영자 숨김"},
                    {"input": {"query": "event_name:admin_community_memo_restored",
                               "language": "lucene"},
                     "label": "복원"},
                ],
            }},
        ],
    },
)


# ============================================================
# Dashboard — 6개 viz 그리드 배치 (48 col 기준)
#   [C6 카운터 (full, 짧음)]
#   [C1 라이프사이클 추이 (full)]
#   [C2 모더레이션][C4 숨김 사유]
#   [C3 신고 추이 (full)]
#   [C5 Abuse 패턴 (full)]
# ============================================================
PANELS = [
    {"vis_id": C6["id"], "panel_id": "1", "grid": {"x": 0,  "y": 0,  "w": 48, "h": 8}},
    {"vis_id": C1["id"], "panel_id": "2", "grid": {"x": 0,  "y": 8,  "w": 48, "h": 15}},
    {"vis_id": C2["id"], "panel_id": "3", "grid": {"x": 0,  "y": 23, "w": 24, "h": 15}},
    {"vis_id": C4["id"], "panel_id": "4", "grid": {"x": 24, "y": 23, "w": 24, "h": 15}},
    {"vis_id": C3["id"], "panel_id": "5", "grid": {"x": 0,  "y": 38, "w": 48, "h": 15}},
    {"vis_id": C5["id"], "panel_id": "6", "grid": {"x": 0,  "y": 53, "w": 48, "h": 18}},
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
        "id": "dashboard-community",
        "type": "dashboard",
        "attributes": {
            "title": "[C] 커뮤니티 운영",
            "hits": 0,
            "description": (
                "백오피스 spec §신고 관리 / §커뮤니티 캔버스 관리 / §AI 모더레이션 — "
                "메모 라이프사이클, 모더레이션 결과, 신고 활동, 숨김 사유, abuse 패턴."
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
    OBJECTS = [C1, C2, C3, C4, C5, C6, DASHBOARD]
    write_ndjson(OBJECTS, OUT)
    print(f"wrote {len(OBJECTS)} saved-objects -> {OUT}")
    print("titles:")
    for o in OBJECTS:
        print(f"  - {o['type']:14} {o['id']:32} {o['attributes']['title']}")
