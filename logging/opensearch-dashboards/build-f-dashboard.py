#!/usr/bin/env python3
"""
F 대시보드 (에러/장애) NDJSON 생성기.

error-logs-* 중심 + 일부 패널은 biz-events-* 의 slow 신호 활용.
백오피스 spec §"알림 규칙" / §"통계 및 분석"의 에러 모니터링 측면.

운영 관점:
- 일별 에러 추이 (application 별 split)
- Application별 에러 비율
- Top Logger (어느 컴포넌트가 자주 에러)
- log_level 분포 (ERROR / FATAL 비율)
- Slow 신호 추이 (biz-events: *_slow / *_slow_request / *_moderation_slow)
- 카테고리별 카운터 (DB / Kafka / AI / 일반)

재실행 = 멱등.
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "saved-objects" / "40-dashboard-errors.ndjson"

# 두 개의 index-pattern 참조 — error-logs와 biz-events 둘 다 사용한다.
ERROR_LOGS_PATTERN_ID = "b443dc80-4cd4-11f1-93a4-814592ee4ccd"
BIZ_EVENTS_PATTERN_ID = "8528e1b0-4cd5-11f1-93a4-814592ee4ccd"

ERROR_PATTERN_REF = {
    "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
    "type": "index-pattern",
    "id": ERROR_LOGS_PATTERN_ID,
}
BIZ_PATTERN_REF = {
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


def viz(viz_id, title, vis_state, pattern_ref, query="", description=""):
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
        "references": [pattern_ref],
    }


# --- 공통 visualization params ---
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


def table_params(per_page=20):
    return {
        "perPage": per_page,
        "showPartialRows": False,
        "showMetricsAtAllLevels": False,
        "showTotal": True,
        "totalFunc": "sum",
        "percentageCol": "",
    }


def metric_params(threshold=False):
    """카운터(metric) viz의 params.

    threshold=True 면 SRE 스타일 임계 색상 (green / yellow / red) 자동 적용.
    구간은 7일 누적 기준 — 0~10 정상(green), 10~50 주의(yellow), 50+ 위험(red).
    """
    if threshold:
        # SRE / Grafana stat 패널 패턴 — 박스 배경은 다크 그대로,
        # 숫자(value) 색만 임계에 따라 변경. 시각적 노이즈 최소화.
        return {
            "addTooltip": True,
            "addLegend": False,
            "type": "metric",
            "metric": {
                "percentageMode": False,
                "useRanges": True,
                "colorSchema": "Green to Red",
                # "Labels" = value 글자에만 색. "Background"는 박스 전체 칠함.
                "metricColorMode": "Labels",
                "colorsRange": [
                    {"from": 0,   "to": 10},
                    {"from": 10,  "to": 50},
                    {"from": 50,  "to": 1000000},
                ],
                "labels": {"show": True},
                "invertColors": False,
                "style": {
                    "bgFill": "#000",
                    "bgColor": False,
                    "labelColor": False,
                    "subText": "",
                    "fontSize": 48,
                },
            },
        }
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
# F1: 에러 추이 (1d Stacked Bar, by application)
#   spring grok이 추출한 application 라벨로 split. 어느 서비스 인스턴스가
#   가장 많이 에러를 토했는지 시간 흐름과 함께 보임.
# ============================================================
F1 = viz(
    viz_id="vis-errors-timeline",
    title="[F1] 에러 추이 (1d)",
    description="error-logs 전체 — application 별 일별 stacked bar.",
    pattern_ref=ERROR_PATTERN_REF,
    query="",
    vis_state={
        "title": "[F1] 에러 추이 (1d)",
        "type": "histogram",
        "params": histogram_params(stacked=True),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "date_histogram", "schema": "segment", "params": {
                "field": "@timestamp", "useNormalizedEsInterval": True,
                "interval": "d", "drop_partials": False, "min_doc_count": 1, "extended_bounds": {},
            }},
            {"id": "3", "enabled": True, "type": "terms", "schema": "group", "params": {
                "field": "application", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": True, "otherBucketLabel": "그 외",
                "missingBucket": True, "missingBucketLabel": "(미식별)",
            }},
        ],
    },
)

# ============================================================
# F2: Application별 에러 분포 (Donut)
#   F1과 같은 데이터를 시간 무시하고 단순 비율로.
# ============================================================
F2 = viz(
    viz_id="vis-errors-application",
    title="[F2] Application별 에러 비율",
    description="application 라벨 비율. F1과 함께 보면 어느 인스턴스가 노이지한지 한눈에.",
    pattern_ref=ERROR_PATTERN_REF,
    query="",
    vis_state={
        "title": "[F2] Application별 에러 비율",
        "type": "pie",
        "params": pie_params(donut=True),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "segment", "params": {
                "field": "application", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(미식별)",
            }},
        ],
    },
)

# ============================================================
# F3: Top Logger (Table, Top 20)
#   어떤 Spring 클래스(Logger)에서 가장 많은 에러가 나는지.
#   장애 hot spot 식별. customLabel 로 컬럼 한글화.
# ============================================================
F3 = viz(
    viz_id="vis-errors-top-logger",
    title="[F3] Top Logger (Top 20)",
    description="logger 별 에러 카운트 desc — 장애 hot spot 식별.",
    pattern_ref=ERROR_PATTERN_REF,
    query="",
    vis_state={
        "title": "[F3] Top Logger (Top 20)",
        "type": "table",
        "params": table_params(per_page=20),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric",
             "params": {"customLabel": "에러 수"}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "logger", "orderBy": "1", "order": "desc",
                "size": 20, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(logger 없음)",
                "customLabel": "Logger",
            }},
        ],
    },
)

# ============================================================
# F4: log_level 분포 (Pie)
#   ERROR vs FATAL 비율. WARN 은 logstash 5b 분기에서 error-logs로 안 가지만
#   spring grok 추출한 log_level 에는 WARN 있을 수 있음.
# ============================================================
F4 = viz(
    viz_id="vis-errors-loglevel",
    title="[F4] log_level 분포",
    description="ERROR / FATAL / (있다면) WARN 비율.",
    pattern_ref=ERROR_PATTERN_REF,
    query="",
    vis_state={
        "title": "[F4] log_level 분포",
        "type": "pie",
        "params": pie_params(donut=False),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "segment", "params": {
                "field": "log_level", "orderBy": "1", "order": "desc",
                "size": 5, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(level 없음)",
            }},
        ],
    },
)

# ============================================================
# F5: Slow 신호 추이 (Stacked Bar, 1d) — biz-events 인덱스 사용
#   community_memo_moderation_slow / community_api_slow_request 등 백엔드가
#   명시적으로 찍은 slow 신호. 명세서 §"백엔드 비즈니스 이벤트"에 정의됨.
#   error-logs와 분리된 의미: "에러"는 아니지만 "장애 신호".
# ============================================================
F5 = viz(
    viz_id="vis-errors-slow-signals",
    title="[F5] Slow 신호 추이 (1d)",
    description="biz-events 의 *_slow / api_slow 이벤트 — 응답 지연 등 성능 신호.",
    pattern_ref=BIZ_PATTERN_REF,
    query=(
        "event_name:("
        "*_slow OR "
        "*_slow_request OR "
        "*_moderation_slow OR "
        "api_slow* OR "
        "*_finalization_attempt_*"
        ")"
    ),
    vis_state={
        "title": "[F5] Slow 신호 추이 (1d)",
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
# F6: 카테고리별 카운터 (Metric, 6 박스 — filters bucket)
#   error-logs 의 logger 패턴으로 카테고리 분류.
#   ERROR / FATAL — 단순 level
#   DB / Kafka / Network / AI — logger 패턴
# ============================================================
F6 = viz(
    viz_id="vis-errors-metrics",
    title="[F6] 카테고리별 카운터",
    description="logger / level 패턴으로 분류한 에러 카운트.",
    pattern_ref=ERROR_PATTERN_REF,
    query="",
    vis_state={
        "title": "[F6] 카테고리별 카운터",
        "type": "metric",
        "params": metric_params(threshold=True),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "filters", "schema": "group", "params": {
                "filters": [
                    {"input": {"query": "log_level:ERROR", "language": "lucene"},
                     "label": "ERROR"},
                    {"input": {"query": "log_level:FATAL", "language": "lucene"},
                     "label": "FATAL"},
                    {"input": {"query": "logger:(*jdbc* OR *Hikari* OR *postgres*)",
                               "language": "lucene"},
                     "label": "DB"},
                    {"input": {"query": "logger:*kafka*", "language": "lucene"},
                     "label": "Kafka"},
                    {"input": {"query": "logger:(*moderation* OR *gms*)",
                               "language": "lucene"},
                     "label": "AI/GMS"},
                    {"input": {"query": "logger:(*minio* OR *s3* OR *redis*)",
                               "language": "lucene"},
                     "label": "외부 의존성"},
                ],
            }},
        ],
    },
)


# ============================================================
# Dashboard — 6 viz 그리드
#   [F6 카운터 (full, 짧음)]
#   [F1 에러 추이 (full)]
#   [F2 Application][F4 log_level]
#   [F3 Top Logger (full)]
#   [F5 Slow 신호 (full)]
# ============================================================
PANELS = [
    {"vis_id": F6["id"], "panel_id": "1", "grid": {"x": 0,  "y": 0,  "w": 48, "h": 8}},
    {"vis_id": F1["id"], "panel_id": "2", "grid": {"x": 0,  "y": 8,  "w": 48, "h": 15}},
    {"vis_id": F2["id"], "panel_id": "3", "grid": {"x": 0,  "y": 23, "w": 24, "h": 15}},
    {"vis_id": F4["id"], "panel_id": "4", "grid": {"x": 24, "y": 23, "w": 24, "h": 15}},
    {"vis_id": F3["id"], "panel_id": "5", "grid": {"x": 0,  "y": 38, "w": 48, "h": 18}},
    {"vis_id": F5["id"], "panel_id": "6", "grid": {"x": 0,  "y": 56, "w": 48, "h": 15}},
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
        "id": "dashboard-errors",
        "type": "dashboard",
        "attributes": {
            "title": "[F] 에러/장애",
            "hits": 0,
            "description": (
                "error-logs 중심 — 에러 추이, application/logger 분포, log_level. "
                "biz-events 의 slow 신호도 함께."
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
    OBJECTS = [F1, F2, F3, F4, F5, F6, DASHBOARD]
    write_ndjson(OBJECTS, OUT)
    print(f"wrote {len(OBJECTS)} saved-objects -> {OUT}")
    for o in OBJECTS:
        print(f"  - {o['type']:14} {o['id']:32} {o['attributes']['title']}")
