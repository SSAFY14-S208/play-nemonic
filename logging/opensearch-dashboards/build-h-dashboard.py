#!/usr/bin/env python3
"""
H 대시보드 (프론트엔드 사용자 행동) NDJSON 생성기.

08B-frontend-logging.md spec 의 § 유입 / 세션·페이지 / Funnel·전환 / 이탈 /
성능 / 오류 카테고리를 한 대시보드에 모았다.

분류 기준:
  - service:client-web (브라우저에서 발생한 이벤트)
  - 백엔드와 중복되지 않는 FE 고유 이벤트만 (api_request/api_error 등 X)
  - 백엔드 funnel 이벤트와는 flow_id, trace_id 로 join 가능 (08B §14)

build-b-dashboard.py 와 동일 스키마/관용구. visualization 8개 + dashboard 1개.

재실행 = 멱등 (output 파일 덮어쓰기).
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "saved-objects" / "70-dashboard-frontend.ndjson"

# index-pattern ID — saved-objects/00-index-patterns.ndjson 의 biz-events-* 항목.
# 모든 viz 가 references 에서 이 id 로 biz-events-* 를 가리킨다.
BIZ_EVENTS_PATTERN_ID = "8528e1b0-4cd5-11f1-93a4-814592ee4ccd"

INDEX_PATTERN_REF = {
    "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
    "type": "index-pattern",
    "id": BIZ_EVENTS_PATTERN_ID,
}


def search_source(query="", filters=None):
    return json.dumps({
        "query": {"query": query, "language": "lucene"},
        "filter": filters or [],
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
# 컬러 매핑 — 시리즈 라벨별 색.
# B 대시보드와 같은 content_type 팔레트를 공유해서 풀스택 대시보드 간
# 시각적 일관성을 유지한다.
# ============================================================
COLOR_CONTENT_TYPE = {
    "landing":    "#FBBF24",
    "hub":        "#94A3B8",
    "community":  "#34D399",
    "relay":      "#60A5FA",
    "flipbook":   "#FB923C",
    "canvas":     "#A78BFA",
    "fortune":    "#F472B6",
    "gallery":    "#22D3EE",
    "backoffice": "#475569",
}

# entry_type 팔레트 — 유입 분석 색.
# direct/search/social/qr/share/campaign/unknown
COLOR_ENTRY_TYPE = {
    "direct":   "#94A3B8",
    "search":   "#60A5FA",
    "social":   "#34D399",
    "qr":       "#FBBF24",
    "share":    "#A78BFA",
    "campaign": "#F472B6",
    "unknown":  "#475569",
}

# 이탈 이벤트 팔레트.
COLOR_ABANDON = {
    "room_lobby_abandoned":   "#FB923C",
    "creation_abandoned":     "#EF4444",
    "result_share_abandoned": "#FBBF24",
    "funnel_abandoned":       "#A78BFA",
}

# H1 메트릭 라벨 팔레트.
COLOR_METRICS_H1 = {
    "활성 세션":       "#3B82F6",
    "페이지뷰":        "#34D399",
    "Funnel 시작":     "#A78BFA",
    "Funnel 완료":     "#10B981",
    "클라이언트 오류": "#EF4444",
}


# ============================================================
# H1: 핵심 카운터 (Metric, 5 시리즈)
# 한 viz 안에 filters bucket 으로 5개 KPI 카운트를 동시 표시.
# 백오피스에서 가장 먼저 보는 "오늘 사용자가 얼마나 들어왔고 얼마나 깨졌나" 신호.
# ============================================================
H1 = viz(
    viz_id="vis-frontend-meta-metrics",
    title="[H1] 핵심 카운터",
    description="기간 내 활성 세션 / 페이지뷰 / Funnel 시작 / Funnel 완료 / 클라이언트 오류 누적 카운트.",
    query="service:client-web",
    colors=COLOR_METRICS_H1,
    vis_state={
        "title": "[H1] 핵심 카운터",
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
                    {"input": {"query": "event_name:client_alive", "language": "lucene"},
                     "label": "활성 세션"},
                    {"input": {"query": "event_name:page_view", "language": "lucene"},
                     "label": "페이지뷰"},
                    {"input": {"query": "event_name:funnel_started", "language": "lucene"},
                     "label": "Funnel 시작"},
                    {"input": {"query": "event_name:funnel_goal_reached", "language": "lucene"},
                     "label": "Funnel 완료"},
                    {"input": {"query":
                               "event_name:(js_error OR unhandled_rejection OR client_network_failed)",
                               "language": "lucene"},
                     "label": "클라이언트 오류"},
                ],
            }},
        ],
    },
)


# ============================================================
# H2: 콘텐츠별 활성 사용자 추이 (Stacked Histogram, 1h)
# client_alive 이벤트를 content_type 으로 쌓아 시간대별 활성도 분포.
# 활성 사용자 정확 측정은 §6.1 cardinality(uuid) 쿼리지만, 대시보드 viz 는
# 시계열 누적이 직관적이라 count 로 표시한다.
# ============================================================
H2 = viz(
    viz_id="vis-frontend-active-timeline",
    title="[H2] 콘텐츠별 활성 추이 (1h)",
    description="client_alive heartbeat 를 content_type 별 시간 분포로 누적. content_type 미지정 doc 은 (missing).",
    query="service:client-web AND event_name:client_alive",
    colors=COLOR_CONTENT_TYPE,
    vis_state={
        "title": "[H2] 콘텐츠별 활성 추이 (1h)",
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
# H3: 유입 경로 분포 (Pie)
# landing_source_detected 이벤트의 entry_type 비율.
# direct/search/social/qr/share/campaign 비중으로 마케팅/공유 효과 가시화.
# ============================================================
H3 = viz(
    viz_id="vis-frontend-entry-types",
    title="[H3] 유입 경로 분포",
    description="landing_source_detected 의 entry_type 카테고리별 세션 비율.",
    query="service:client-web AND event_name:landing_source_detected",
    colors=COLOR_ENTRY_TYPE,
    vis_state={
        "title": "[H3] 유입 경로 분포",
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
                "field": "entry_type", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(unknown)",
            }},
        ],
    },
)


# ============================================================
# H4: 페이지뷰 Top 20 라우트 (Data Table)
# path 별 page_view 카운트. 어느 화면이 가장 많이 노출되는지 + path 정규화가
# 잘 되어 있는지(동적 ID 가 그대로 박혀 cardinality 폭증하지 않는지) 점검.
# ============================================================
H4 = viz(
    viz_id="vis-frontend-top-paths",
    title="[H4] 페이지뷰 Top 20 라우트",
    description="page_view 이벤트 path 별 카운트 상위 20개. 동적 ID 가 정규화돼 들어오는지 확인.",
    query="service:client-web AND event_name:page_view",
    vis_state={
        "title": "[H4] 페이지뷰 Top 20 라우트",
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
             "params": {"customLabel": "PV"}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "path", "orderBy": "1", "order": "desc",
                "size": 20, "otherBucket": False, "missingBucket": False,
                "customLabel": "Path",
            }},
        ],
    },
)


# ============================================================
# H5: Funnel 전환 분포 (Data Table)
# funnel_name × event_name 으로 split — 한눈에 funnel 별 시작/중간/완료/이탈
# 카운트를 비교. funnel_goal_reached/funnel_started 비율이 전환율.
# event_name 을 5개로 한정 (funnel 전 단계 + 이탈), step_index 는 별도 viz 가 필요하면 추가.
# ============================================================
H5 = viz(
    viz_id="vis-frontend-funnel-table",
    title="[H5] Funnel 전환 분포",
    description=(
        "funnel_name 별 funnel_started / funnel_step_viewed / funnel_step_completed / "
        "funnel_goal_reached / funnel_abandoned 카운트. 전환율 = goal_reached/started."
    ),
    query=(
        "service:client-web AND event_name:("
        "funnel_started OR funnel_step_viewed OR funnel_step_completed OR "
        "funnel_goal_reached OR funnel_abandoned"
        ")"
    ),
    vis_state={
        "title": "[H5] Funnel 전환 분포",
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
                "field": "funnel_name", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False, "missingBucket": False,
                "customLabel": "Funnel",
            }},
            {"id": "3", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "event_name", "orderBy": "1", "order": "desc",
                "size": 5, "otherBucket": False, "missingBucket": False,
                "customLabel": "단계",
            }},
        ],
    },
)


# ============================================================
# H6: 이탈 이벤트 추이 (Stacked Histogram, 1h)
# 4종 이탈 이벤트를 시간대별 누적해서 "어느 시점에 사용자가 빠지는지" 패턴 시각화.
# - room_lobby_abandoned: 대기실에서 이탈
# - creation_abandoned: 작성/그리기 중 이탈
# - result_share_abandoned: 결과 확인 후 공유 없이 이탈
# - funnel_abandoned: 공통 funnel 이탈
# ============================================================
H6 = viz(
    viz_id="vis-frontend-abandon-timeline",
    title="[H6] 이탈 이벤트 추이 (1h)",
    description="lobby/creation/result_share/funnel 이탈 이벤트의 시간대별 누적 분포.",
    query=(
        "service:client-web AND event_name:("
        "room_lobby_abandoned OR creation_abandoned OR "
        "result_share_abandoned OR funnel_abandoned"
        ")"
    ),
    colors=COLOR_ABANDON,
    vis_state={
        "title": "[H6] 이탈 이벤트 추이 (1h)",
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
                "field": "event_name", "orderBy": "1", "order": "desc",
                "size": 6, "otherBucket": False, "missingBucket": False,
            }},
        ],
    },
)


# ============================================================
# H7: Web Vitals P75 (Data Table)
# web_vitals 이벤트의 metadata.metric_name (LCP/INP/CLS/TTFB) 별
# P75 of metadata.value. Google 권고 임계값과 비교해 성능 회귀 감지.
# 08B §10 sampling 가드: web_vitals 는 10% sampling 이라 표본 N >= 100 일 때만 신뢰.
# 표 마지막 컬럼에 N(=count) 도 같이 표시해서 판단 근거를 보여준다.
# ============================================================
H7 = viz(
    viz_id="vis-frontend-web-vitals",
    title="[H7] Web Vitals P75",
    description=(
        "metric_name 별 metadata.value 의 P75 (10% session sampling). "
        "표본이 작을 때(예: N<100)는 분포 신뢰도가 낮으니 N 컬럼도 함께 본다."
    ),
    query="service:client-web AND event_name:web_vitals",
    vis_state={
        "title": "[H7] Web Vitals P75",
        "type": "table",
        "params": {
            "perPage": 10,
            "showPartialRows": False,
            "showMetricsAtAllLevels": False,
            "showTotal": False,
            "totalFunc": "sum",
            "percentageCol": "",
        },
        "aggs": [
            {"id": "1", "enabled": True, "type": "percentiles", "schema": "metric", "params": {
                "field": "metadata.value",
                "percents": [75],
                "customLabel": "P75",
            }},
            {"id": "2", "enabled": True, "type": "count", "schema": "metric",
             "params": {"customLabel": "N"}},
            {"id": "3", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "metadata.metric_name", "orderBy": "2", "order": "desc",
                "size": 10, "otherBucket": False, "missingBucket": False,
                "customLabel": "Metric",
            }},
        ],
    },
)


# ============================================================
# H8: 클라이언트 오류 Top (Data Table)
# js_error / unhandled_rejection / client_network_failed 이벤트를
# error.type × path 로 split — 어느 라우트에서 어떤 타입의 에러가 가장 많이 나는지.
# error.message 는 text 매핑이라 aggregatable 하지 않으므로 type 으로 묶는다.
# ============================================================
H8 = viz(
    viz_id="vis-frontend-error-top",
    title="[H8] 클라이언트 오류 Top",
    description=(
        "js_error / unhandled_rejection / client_network_failed 이벤트를 "
        "error.type × path 로 그룹화한 상위 행. message 본문은 Discover 에서 확인."
    ),
    query=(
        "service:(client-web OR next-ssr) AND event_name:("
        "js_error OR unhandled_rejection OR client_network_failed"
        ")"
    ),
    vis_state={
        "title": "[H8] 클라이언트 오류 Top",
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
             "params": {"customLabel": "건수"}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "event_name", "orderBy": "1", "order": "desc",
                "size": 5, "otherBucket": False, "missingBucket": False,
                "customLabel": "Event",
            }},
            {"id": "3", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "error.type", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(unknown type)",
                "customLabel": "Error Type",
            }},
            {"id": "4", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "path", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(no path)",
                "customLabel": "Path",
            }},
        ],
    },
)


# ============================================================
# Dashboard — 8개 viz 를 그리드로 배치 (48 column).
#
#   [H1 핵심 카운터 (full width, 짧음)]
#   [H2 활성 추이 (full width)]
#   [H3 유입 경로][H4 페이지뷰 Top]
#   [H5 Funnel 전환 (full width)]
#   [H6 이탈 추이 (full width)]
#   [H7 Web Vitals][H8 클라이언트 오류]
# ============================================================
PANELS = [
    {"vis_id": H1["id"], "panel_id": "1", "grid": {"x": 0,  "y": 0,  "w": 48, "h": 8}},
    {"vis_id": H2["id"], "panel_id": "2", "grid": {"x": 0,  "y": 8,  "w": 48, "h": 15}},
    {"vis_id": H3["id"], "panel_id": "3", "grid": {"x": 0,  "y": 23, "w": 24, "h": 15}},
    {"vis_id": H4["id"], "panel_id": "4", "grid": {"x": 24, "y": 23, "w": 24, "h": 15}},
    {"vis_id": H5["id"], "panel_id": "5", "grid": {"x": 0,  "y": 38, "w": 48, "h": 18}},
    {"vis_id": H6["id"], "panel_id": "6", "grid": {"x": 0,  "y": 56, "w": 48, "h": 15}},
    {"vis_id": H7["id"], "panel_id": "7", "grid": {"x": 0,  "y": 71, "w": 24, "h": 15}},
    {"vis_id": H8["id"], "panel_id": "8", "grid": {"x": 24, "y": 71, "w": 24, "h": 15}},
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
        "id": "dashboard-frontend",
        "type": "dashboard",
        "attributes": {
            "title": "[H] 프론트엔드 사용자 행동",
            "hits": 0,
            "description": (
                "08B-frontend-logging.md spec — 유입 / 세션·페이지 / Funnel·전환 / "
                "이탈 / Web Vitals / 클라이언트 오류. 백엔드와는 flow_id, trace_id 로 join."
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
    }


DASHBOARD = build_dashboard()


def write_ndjson(objects, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for obj in objects:
            f.write(json.dumps(obj, ensure_ascii=False))
            f.write("\n")


if __name__ == "__main__":
    OBJECTS = [H1, H2, H3, H4, H5, H6, H7, H8, DASHBOARD]
    write_ndjson(OBJECTS, OUT)
    print(f"wrote {len(OBJECTS)} saved-objects -> {OUT}")
    print("titles:")
    for o in OBJECTS:
        print(f"  - {o['type']:14} {o['id']:35} {o['attributes']['title']}")
