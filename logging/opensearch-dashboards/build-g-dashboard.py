#!/usr/bin/env python3
"""
G 대시보드 (오늘의 운세 / GMS) NDJSON 생성기.

백오피스 spec §"오늘의 운세 관리" + §"오늘의 운세/GMS 처리 이벤트" 기반.
6 visualization + 1 dashboard.

운영 관점:
- 일별 발급량 (created/reissued)
- GMS API 최종 성공률 (성공 vs 실패)
- 응답시간 분포 P50/P95/P99 (alert 기준 P99 > 3000ms)
- 재시도 분포 (alert 기준 재시도율 > 40%)
- prompt_version별 성능 (롤백 의사결정 데이터)
- 핵심 카운터

biz-events-* 인덱스 사용. 재실행 = 멱등.
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "saved-objects" / "40-dashboard-fortune.ndjson"

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


def histogram_params(stacked=True, title="Count", chart_type="histogram"):
    """chart_type: histogram(bar) | line | area"""
    return {
        "type": chart_type,
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
            "show": True, "type": chart_type,
            "mode": "stacked" if stacked else "grouped",
            "data": {"label": "Count", "id": "1"},
            "valueAxis": "ValueAxis-1", "drawLinesBetweenPoints": True, "showCircles": True,
        }],
        "addTooltip": True, "addLegend": True, "legendPosition": "right",
        "times": [], "addTimeMarker": False,
    }


def pie_params(donut=False):
    return {
        "type": "pie",
        "addTooltip": True, "addLegend": True, "legendPosition": "right",
        "isDonut": donut,
        "labels": {"show": True, "values": True, "last_level": True, "truncate": 100},
    }


def table_params(per_page=15):
    return {
        "perPage": per_page,
        "showPartialRows": False, "showMetricsAtAllLevels": False,
        "showTotal": True, "totalFunc": "sum", "percentageCol": "",
    }


def metric_params():
    return {
        "addTooltip": True, "addLegend": False, "type": "metric",
        "metric": {
            "percentageMode": False, "useRanges": False,
            "colorSchema": "Green to Red", "metricColorMode": "None",
            "colorsRange": [{"from": 0, "to": 10000}],
            "labels": {"show": True}, "invertColors": False,
            "style": {"bgFill": "#000", "bgColor": False, "labelColor": False,
                      "subText": "", "fontSize": 36},
        },
    }


# ============================================================
# G1: 일별 발급 추이 (Stacked Bar, 1d)
#   fortune_created (신규) + fortune_reissued (재발급) 의 일별 분포.
#   백오피스 spec §"통계 및 분석" - 일별 발급 건수.
# ============================================================
G1 = viz(
    viz_id="vis-fortune-issue-timeline",
    title="[G1] 일별 발급 추이 (1d)",
    description="fortune_created (신규) + fortune_reissued (재발급) 일별 분포.",
    query="event_name:(fortune_created OR fortune_reissued)",
    vis_state={
        "title": "[G1] 일별 발급 추이 (1d)",
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
                "size": 5, "otherBucket": False, "missingBucket": False,
            }},
        ],
    },
)

# ============================================================
# G2: GMS 최종 결과 분포 (Donut)
#   fortune_gms_succeeded vs fortune_gms_failed. 재시도는 별개.
#   백오피스 spec §"오늘의 운세 관리" - GMS API 최종 성공률.
#   alert §"GMS 최종 실패율 > 20%" 임계 기준 시각화.
# ============================================================
G2 = viz(
    viz_id="vis-fortune-gms-result",
    title="[G2] GMS 최종 결과 분포",
    description="fortune_gms_succeeded vs failed — 최종 성공률 (재시도 제외).",
    query="event_name:(fortune_gms_succeeded OR fortune_gms_failed)",
    vis_state={
        "title": "[G2] GMS 최종 결과 분포",
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
# G3: GMS Latency P50/P95/P99 추이 (Line)
#   fortune_gms_succeeded/retried/failed 의 metadata.gms_latency_ms percentile.
#   alert §"API P99 > 3000ms" 모니터링 기준.
# ============================================================
G3 = viz(
    viz_id="vis-fortune-gms-latency",
    title="[G3] GMS Latency P50/P95/P99 (1h)",
    description="fortune_gms_* 의 gms_latency_ms percentile 시간 추이.",
    query="event_name:fortune_gms_*",
    vis_state={
        "title": "[G3] GMS Latency P50/P95/P99 (1h)",
        "type": "line",
        "params": histogram_params(stacked=False, title="Latency (ms)", chart_type="line"),
        "aggs": [
            {"id": "1", "enabled": True, "type": "percentiles", "schema": "metric", "params": {
                "field": "metadata.gms_latency_ms",
                "percents": [50, 95, 99],
                "keyed": False,
            }},
            {"id": "2", "enabled": True, "type": "date_histogram", "schema": "segment", "params": {
                "field": "@timestamp", "useNormalizedEsInterval": True,
                "interval": "h", "drop_partials": False, "min_doc_count": 1, "extended_bounds": {},
            }},
        ],
    },
)

# ============================================================
# G4: 재시도 횟수 분포 (Vertical Bar)
#   metadata.retry_count 분포 — 0이 정상, 1+가 재시도 발생.
#   alert §"GMS 재시도율 > 40%" 모니터링.
# ============================================================
G4 = viz(
    viz_id="vis-fortune-retry-distribution",
    title="[G4] GMS 재시도 횟수 분포",
    description="metadata.retry_count 값별 doc 분포 (0=정상, 1+=재시도 발생).",
    query="event_name:fortune_gms_*",
    vis_state={
        "title": "[G4] GMS 재시도 횟수 분포",
        "type": "histogram",
        "params": histogram_params(stacked=False, title="Count"),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "terms", "schema": "segment", "params": {
                "field": "metadata.retry_count",
                "orderBy": "_key", "order": "asc",
                "size": 15, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(미기록)",
                "customLabel": "재시도 횟수",
            }},
        ],
    },
)

# ============================================================
# G5: prompt_version별 성능 (Data Table)
#   metadata.prompt_version 별 호출 수 + 평균 latency.
#   백오피스 spec §"오늘의 운세 관리" - 프롬프트 버전별 성능, 롤백 의사결정.
#   주의: prompt_version은 인덱스 템플릿에 명시 매핑 없음 → dynamic mapping에 의존.
#         빈 결과면 fields refresh 한 번 또는 템플릿 패치 필요.
# ============================================================
G5 = viz(
    viz_id="vis-fortune-prompt-perf",
    title="[G5] Prompt 버전별 성능",
    description="prompt_version별 호출 수 + 평균 GMS latency. 롤백 의사결정 데이터.",
    query="event_name:fortune_gms_* AND _exists_:metadata.prompt_version",
    vis_state={
        "title": "[G5] Prompt 버전별 성능",
        "type": "table",
        "params": table_params(per_page=10),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric",
             "params": {"customLabel": "호출 수"}},
            {"id": "2", "enabled": True, "type": "avg", "schema": "metric", "params": {
                "field": "metadata.gms_latency_ms",
                "customLabel": "평균 latency (ms)",
            }},
            {"id": "3", "enabled": True, "type": "max", "schema": "metric", "params": {
                "field": "metadata.gms_latency_ms",
                "customLabel": "최대 latency (ms)",
            }},
            {"id": "4", "enabled": True, "type": "terms", "schema": "bucket", "params": {
                "field": "metadata.prompt_version", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False, "missingBucket": False,
                "customLabel": "Prompt 버전",
            }},
        ],
    },
)

# ============================================================
# G6: 핵심 카운터 (Metric, 6 박스 — filters bucket)
# ============================================================
G6 = viz(
    viz_id="vis-fortune-metrics",
    title="[G6] 핵심 카운터",
    description="발급/재발급/GMS 성공/실패/재시도 발생/평균 latency.",
    query="",
    vis_state={
        "title": "[G6] 핵심 카운터",
        "type": "metric",
        "params": metric_params(),
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "filters", "schema": "group", "params": {
                "filters": [
                    {"input": {"query": "event_name:fortune_created", "language": "lucene"},
                     "label": "발급"},
                    {"input": {"query": "event_name:fortune_reissued", "language": "lucene"},
                     "label": "재발급"},
                    {"input": {"query": "event_name:fortune_gms_succeeded", "language": "lucene"},
                     "label": "GMS 성공"},
                    {"input": {"query": "event_name:fortune_gms_failed", "language": "lucene"},
                     "label": "GMS 실패"},
                    {"input": {"query": "event_name:fortune_gms_retried", "language": "lucene"},
                     "label": "재시도 발생"},
                    {"input": {"query": "event_name:fortune_gms_* AND metadata.gms_latency_ms:>3000",
                               "language": "lucene"},
                     "label": "P99 임계 초과(>3s)"},
                ],
            }},
        ],
    },
)


# ============================================================
# Dashboard — 6 viz 그리드 (48 col)
#   [G6 카운터 (full, 짧음)]
#   [G1 일별 발급 추이 (full)]
#   [G2 GMS 결과 분포 ][G3 Latency 추이]
#   [G4 재시도 분포  ][G5 prompt 성능]
# ============================================================
PANELS = [
    {"vis_id": G6["id"], "panel_id": "1", "grid": {"x": 0,  "y": 0,  "w": 48, "h": 8}},
    {"vis_id": G1["id"], "panel_id": "2", "grid": {"x": 0,  "y": 8,  "w": 48, "h": 15}},
    {"vis_id": G2["id"], "panel_id": "3", "grid": {"x": 0,  "y": 23, "w": 24, "h": 15}},
    {"vis_id": G3["id"], "panel_id": "4", "grid": {"x": 24, "y": 23, "w": 24, "h": 15}},
    {"vis_id": G4["id"], "panel_id": "5", "grid": {"x": 0,  "y": 38, "w": 24, "h": 15}},
    {"vis_id": G5["id"], "panel_id": "6", "grid": {"x": 24, "y": 38, "w": 24, "h": 15}},
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
        "id": "dashboard-fortune",
        "type": "dashboard",
        "attributes": {
            "title": "[G] 오늘의 운세 / GMS",
            "hits": 0,
            "description": (
                "백오피스 spec §오늘의 운세 관리 — 발급량, GMS 최종 성공률, "
                "latency P50/P95/P99, 재시도 분포, prompt_version별 성능."
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
    OBJECTS = [G1, G2, G3, G4, G5, G6, DASHBOARD]
    write_ndjson(OBJECTS, OUT)
    print(f"wrote {len(OBJECTS)} saved-objects -> {OUT}")
    for o in OBJECTS:
        print(f"  - {o['type']:14} {o['id']:32} {o['attributes']['title']}")
