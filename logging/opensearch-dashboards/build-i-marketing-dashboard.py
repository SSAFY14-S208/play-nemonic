#!/usr/bin/env python3
"""
[I] 마케팅 인사이트 대시보드 NDJSON 생성기.

H 대시보드(분석가용 raw events)와 별개로, 마케팅 페르소나를 위한 "한눈에 보이는" 지표만 모은
대시보드. 핵심 비즈니스 질문 5개에 직접 답하도록 viz 선정·라벨링·색을 의도적으로 단순화한다.

비즈니스 질문 → viz 매핑:
  Q1. "오늘 얼마나 들어왔고 얼마나 핵심 행동을 완료했나?"          → I1 핵심 KPI (Metric 4 시리즈)
  Q2. "어느 컨텐츠가 완주율이 높고 어디서 무너지나?"                → I2 컨텐츠별 완주율 (Vega-Lite, %)
  Q3. "Funnel 어느 단계에서 사용자가 가장 많이 빠지는가?"            → I3 단계별 이탈 깔때기 (Vega-Lite faceted)
  Q4. "한 화면 다음에 사용자가 어디로 가장 많이 이동하는가?"          → I4 화면 이동 흐름 (Vega-Lite heatmap)
  Q5. "시간대별로 누가 어떤 컨텐츠에 들어오는가?"                    → I5 시간대별 진입 추이 (Stacked Area)

build-h-dashboard.py 와 동일 스키마/관용구 (viz_id, index pattern reference, ndjson 출력).

재실행 = 멱등 (output 파일 덮어쓰기).
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "saved-objects" / "80-dashboard-marketing.ndjson"

# index-pattern ID — saved-objects/00-index-patterns.ndjson 의 biz-events-* 항목.
BIZ_EVENTS_PATTERN_ID = "8528e1b0-4cd5-11f1-93a4-814592ee4ccd"

INDEX_PATTERN_REF = {
    "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
    "type": "index-pattern",
    "id": BIZ_EVENTS_PATTERN_ID,
}

# 컨텐츠 이름 한글화 — 마케팅 페르소나가 보기 편하도록.
# Vega-Lite expression 으로 도메인 키를 한글 라벨에 매핑한다.
FUNNEL_KOREAN_LABEL_EXPR = (
    "{'relay_room_creation':'릴레이드로잉',"
    "'flipbook_room_creation':'플립북',"
    "'community_memo_posting':'커뮤니티 메모',"
    "'fortune_creation':'오늘의 운세',"
    "'gallery_save_share':'갤러리·공유'}[datum.funnel_name]"
)


def search_source(query="", filters=None):
    return json.dumps({
        "query": {"query": query, "language": "lucene"},
        "filter": filters or [],
        "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index",
    })


def viz_classic(viz_id, title, vis_state, query="", description="", colors=None):
    """일반(non-Vega) visualization 래퍼 — search source 분리, index pattern reference 첨부."""
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


def viz_vega(viz_id, title, spec, description=""):
    """Vega/Vega-Lite visualization 래퍼.

    Vega viz 는 spec 안에서 직접 ES 쿼리를 발사하므로 search_source/references 가 필요 없다.

    OpenSearch Dashboards 의 Vega plugin 은 `params.spec` 을 **JSON 문자열** 로 기대한다
    (객체를 그대로 넣으면 escapeString 단계에서 `data.replace is not a function` 에러).
    그래서 spec dict 를 여기서 한 번 더 json.dumps 한다.
    """
    spec_str = json.dumps(spec, ensure_ascii=False)
    return {
        "id": viz_id,
        "type": "visualization",
        "attributes": {
            "title": title,
            "visState": json.dumps({
                "title": title,
                "type": "vega",
                "aggs": [],
                "params": {"spec": spec_str},
            }, ensure_ascii=False),
            "uiStateJSON": "{}",
            "description": description,
            "version": 1,
            "kibanaSavedObjectMeta": {
                "searchSourceJSON": json.dumps({
                    "query": {"query": "", "language": "lucene"},
                    "filter": [],
                }),
            },
        },
        # Vega 는 spec 내부의 data.url.index 로 인덱스를 직접 가리키므로 reference 없음.
        "references": [],
    }


# ============================================================
# I1: 핵심 KPI — 활성 세션 / 진입 / 완료 / 이탈
# 한 viz 안에 filters bucket 으로 4개 KPI 카운트.
# 마케팅이 가장 먼저 보는 "오늘 얼마나 들어왔고 얼마나 깨졌나" 신호.
# ============================================================
I1 = viz_classic(
    viz_id="vis-marketing-kpi",
    title="[I1] 오늘의 핵심 지표",
    description=(
        "활성 세션(client_alive heartbeat)·진입(funnel_started)·완료(funnel_goal_reached)·"
        "이탈(funnel_abandoned + room_lobby/creation/result_share_abandoned). "
        "활성 세션은 거의 변화 없는데 진입/완료 비율이 떨어지면 컨버전 이슈 신호."
    ),
    query="service:client-web",
    colors={
        "활성 세션":   "#3B82F6",   # 파랑 — 신경 안정
        "진입":        "#A78BFA",   # 보라
        "완료":        "#10B981",   # 녹색 — 좋음
        "이탈":        "#EF4444",   # 빨강 — 위험
    },
    vis_state={
        "title": "[I1] 오늘의 핵심 지표",
        "type": "metric",
        "params": {
            "addTooltip": True,
            "addLegend": False,
            "type": "metric",
            "metric": {
                "percentageMode": False,
                "useRanges": False,
                "colorSchema": "Green to Red",
                "metricColorMode": "Labels",
                "colorsRange": [{"from": 0, "to": 10000}],
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
        },
        "aggs": [
            {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}},
            {"id": "2", "enabled": True, "type": "filters", "schema": "group", "params": {
                "filters": [
                    {"input": {"query": "event_name:client_alive", "language": "lucene"},
                     "label": "활성 세션"},
                    {"input": {"query": "event_name:funnel_started", "language": "lucene"},
                     "label": "진입"},
                    {"input": {"query": "event_name:funnel_goal_reached", "language": "lucene"},
                     "label": "완료"},
                    {"input": {"query":
                               "event_name:(funnel_abandoned OR room_lobby_abandoned "
                               "OR creation_abandoned OR result_share_abandoned)",
                               "language": "lucene"},
                     "label": "이탈"},
                ],
            }},
        ],
    },
)


# ============================================================
# I2: 컨텐츠별 완주율 (Vega-Lite, %)
# funnel_name 별로 진입(funnel_started) 대비 완료(funnel_goal_reached) 비율 백분율.
# 색 = 위험도 (낮을수록 빨강, 높을수록 녹색).
#
# 두 번 ES 쿼리하지 않고 한 번에 funnel_name × event_name 으로 받은 뒤
# Vega-Lite transform 으로 비율 계산.
# ============================================================
I2_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    "title": {
        "text": "컨텐츠별 완주율 (%)",
        "subtitle": "진입(funnel_started) 대비 완료(funnel_goal_reached) 비율. 막대 길이 = 비율, 색 = 위험도.",
        "subtitleColor": "#94A3B8",
        "subtitleFontSize": 11,
        "fontSize": 14,
        "anchor": "start",
    },
    "data": {
        "url": {
            "%context%": True,
            "%timefield%": "@timestamp",
            "index": "biz-events-*",
            "body": {
                "size": 0,
                "aggs": {
                    "filtered": {
                        "filter": {
                            "bool": {
                                "filter": [{"term": {"service": "client-web"}}]
                            }
                        },
                        "aggs": {
                            "funnels": {
                                "terms": {"field": "metadata.funnel_name", "size": 10},
                                # started/completed 를 filter sub-agg 로 분리 — Vega-Lite
                                # transform 에서 reduce 같은 JS 메서드 의존 없이 평면 필드로 받음.
                                "aggs": {
                                    "started": {
                                        "filter": {"term": {"event_name": "funnel_started"}}
                                    },
                                    "completed": {
                                        "filter": {"term": {"event_name": "funnel_goal_reached"}}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        "format": {"property": "aggregations.filtered.funnels.buckets"}
    },
    "transform": [
        {"calculate": "datum.started ? datum.started.doc_count : 0", "as": "started"},
        {"calculate": "datum.completed ? datum.completed.doc_count : 0", "as": "completed"},
        {"calculate":
            "datum.started > 0 ? datum.completed / datum.started * 100 : 0",
         "as": "rate"},
        {"filter": "datum.started > 0"},
        {"calculate": "datum.key", "as": "funnel_name"},
        {"calculate": FUNNEL_KOREAN_LABEL_EXPR + " || datum.funnel_name",
         "as": "funnel_label"},
    ],
    "encoding": {
        "y": {
            "field": "funnel_label",
            "type": "nominal",
            "sort": "-x",
            "axis": {"title": None, "labelFontSize": 13, "labelLimit": 200},
        },
        "x": {
            "field": "rate",
            "type": "quantitative",
            "scale": {"domain": [0, 100]},
            "axis": {"title": "완주율 (%)", "labelFontSize": 11, "tickCount": 5},
        },
    },
    "layer": [
        {
            "mark": {"type": "bar", "cornerRadiusEnd": 4, "tooltip": True},
            "encoding": {
                "color": {
                    "field": "rate",
                    "type": "quantitative",
                    "scale": {
                        "domain": [0, 20, 50, 80, 100],
                        "range": ["#EF4444", "#F97316", "#FBBF24", "#84CC16", "#10B981"],
                    },
                    "legend": None,
                },
                "tooltip": [
                    {"field": "funnel_label", "type": "nominal", "title": "컨텐츠"},
                    {"field": "started", "type": "quantitative", "title": "진입"},
                    {"field": "completed", "type": "quantitative", "title": "완료"},
                    {"field": "rate", "type": "quantitative", "title": "완주율 %", "format": ".1f"},
                ],
            },
        },
        {
            "mark": {"type": "text", "align": "left", "dx": 6, "fontSize": 12, "fontWeight": "bold",
                     "color": "#E5E7EB"},
            "encoding": {
                "text": {"field": "rate", "type": "quantitative", "format": ".1f"},
            },
        },
    ],
    # height 를 step 기반(autosize fit 트리거) 대신 funnel 수에 맞춘 고정값으로 둔다.
    # autosize warning("width/height ignored")을 피하기 위함.
    "height": 240,
    "width": "container",
    "config": {
        "background": "transparent",
        "view": {"stroke": None},
        "axis": {"labelColor": "#CBD5E1", "titleColor": "#CBD5E1", "gridColor": "#334155"},
        "title": {"color": "#E5E7EB"},
    },
}
I2 = viz_vega(
    viz_id="vis-marketing-conversion-rate",
    title="[I2] 컨텐츠별 완주율",
    description="진입 → 완료 비율 (%). 색이 빨강일수록 이탈이 심한 컨텐츠.",
    spec=I2_SPEC,
)


# ============================================================
# I3: 단계별 이탈 깔때기 (Vega-Lite faceted)
# funnel_name 별로 단계 진행에 따른 잔존 카운트 가로 막대.
# funnel_started 와 funnel_step_completed(by step_index) 를 합쳐 시간 순서로 정렬.
#
# 단계 정의:
#   step 0  = funnel_started        (진입)
#   step 1+ = funnel_step_completed (각 step_index)
#   step N  = funnel_goal_reached   (완료)
#
# Vega-Lite transform 으로 step 라벨링 + faceting.
# ============================================================
I3_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    "title": {
        "text": "단계별 이탈 깔때기",
        "subtitle": "각 컨텐츠의 단계별 잔존 세션 수. 막대가 짧아질수록 그 단계에서 사용자가 빠진 것.",
        "subtitleColor": "#94A3B8",
        "subtitleFontSize": 11,
        "fontSize": 14,
        "anchor": "start",
    },
    "data": {
        "url": {
            "%context%": True,
            "%timefield%": "@timestamp",
            "index": "biz-events-*",
            "body": {
                "size": 0,
                "aggs": {
                    "filtered": {
                        "filter": {
                            "bool": {
                                "filter": [
                                    {"term": {"service": "client-web"}},
                                    {"terms": {
                                        "event_name": [
                                            "funnel_started",
                                            "funnel_step_completed",
                                            "funnel_goal_reached"
                                        ]
                                    }}
                                ]
                            }
                        },
                        "aggs": {
                            "funnels": {
                                "terms": {"field": "metadata.funnel_name", "size": 10},
                                "aggs": {
                                    # funnel_started/goal_reached 는 step_name 이 없으므로
                                    # filter sub-agg 로 단일 카운트만 받고,
                                    # funnel_step_completed 만 step_name 별로 분해한다.
                                    "started": {
                                        "filter": {"term": {"event_name": "funnel_started"}}
                                    },
                                    "completed_goal": {
                                        "filter": {"term": {"event_name": "funnel_goal_reached"}}
                                    },
                                    "steps_done": {
                                        "filter": {
                                            "term": {"event_name": "funnel_step_completed"}
                                        },
                                        "aggs": {
                                            "by_step": {
                                                "terms": {
                                                    "field": "metadata.step_name",
                                                    "size": 20
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        "format": {"property": "aggregations.filtered.funnels.buckets"}
    },
    "transform": [
        # funnel_step_completed 의 step buckets 를 평면화.
        {"flatten": ["steps_done.by_step.buckets"], "as": ["step_bucket"]},
        {"calculate": "datum.step_bucket.key", "as": "step_name"},
        {"calculate": "datum.step_bucket.doc_count", "as": "count"},
        {"calculate": "'1. ' + datum.step_name", "as": "step_label"},
        {"calculate": FUNNEL_KOREAN_LABEL_EXPR + " || datum.key",
         "as": "funnel_label"},
        {"filter": "datum.count > 0"},
    ],
    "facet": {
        "row": {
            "field": "funnel_label",
            "type": "nominal",
            "header": {
                "title": None,
                "labelFontSize": 13,
                "labelColor": "#E5E7EB",
                "labelAngle": 0,
                "labelAlign": "left",
                "labelOrient": "left",
            },
        },
    },
    "spec": {
        "width": "container",
        # I3 는 facet 안의 inner spec — step 단계 수에 비례한 고정 높이로 단순화.
        "height": 160,
        "mark": {"type": "bar", "cornerRadiusEnd": 3, "tooltip": True},
        "encoding": {
            "y": {
                "field": "step_label",
                "type": "ordinal",
                "sort": "ascending",
                "axis": {"title": None, "labelFontSize": 11, "labelLimit": 140},
            },
            "x": {
                "field": "count",
                "type": "quantitative",
                "axis": {"title": "세션 수", "labelFontSize": 11},
            },
            "color": {
                "field": "step_label",
                "type": "nominal",
                "scale": {
                    "scheme": "blues",
                },
                "legend": None,
            },
            "tooltip": [
                {"field": "funnel_label", "type": "nominal", "title": "컨텐츠"},
                {"field": "step_label", "type": "nominal", "title": "단계"},
                {"field": "count", "type": "quantitative", "title": "세션 수"},
            ],
        },
    },
    "resolve": {"scale": {"x": "independent"}},
    "config": {
        "background": "transparent",
        "view": {"stroke": None},
        "axis": {"labelColor": "#CBD5E1", "titleColor": "#CBD5E1", "gridColor": "#334155"},
        "title": {"color": "#E5E7EB"},
        "header": {"labelColor": "#E5E7EB", "titleColor": "#E5E7EB"},
    },
}
I3 = viz_vega(
    viz_id="vis-marketing-funnel-stages",
    title="[I3] 단계별 이탈 깔때기",
    description="컨텐츠별 단계 진행에 따른 잔존 세션. 막대가 급격히 짧아지는 단계가 약점.",
    spec=I3_SPEC,
)


# ============================================================
# I4: 컨텐츠 간 이동 흐름 (Vega-Lite heatmap)
# page_view 의 prev_path × path 매트릭스를 색 강도로 시각화.
# 진짜 sankey 는 Vega(full) 필요라 Vega-Lite 호환 heatmap 으로 흐름 표현.
#
# 마케팅 활용:
#   - "/relay-drawing 끝나고 가장 많이 가는 곳은? → /hub 가 짙으면 OK,
#     초기 페이지로 다시 가면 재참여 가능성"
# ============================================================
I4_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    "title": {
        "text": "화면 이동 흐름",
        "subtitle": "이전 화면(가로) → 다음 화면(세로) 이동 빈도. 색이 진할수록 이동량 많음.",
        "subtitleColor": "#94A3B8",
        "subtitleFontSize": 11,
        "fontSize": 14,
        "anchor": "start",
    },
    "data": {
        "url": {
            "%context%": True,
            "%timefield%": "@timestamp",
            "index": "biz-events-*",
            "body": {
                "size": 0,
                "aggs": {
                    "filtered": {
                        "filter": {
                            "bool": {
                                "filter": [
                                    {"term": {"service": "client-web"}},
                                    {"term": {"event_name": "page_view"}},
                                    {"exists": {"field": "prev_path"}}
                                ]
                            }
                        },
                        "aggs": {
                            "sources": {
                                "terms": {"field": "prev_path", "size": 12},
                                "aggs": {
                                    "targets": {
                                        "terms": {"field": "path", "size": 12}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        "format": {"property": "aggregations.filtered.sources.buckets"}
    },
    "transform": [
        {"flatten": ["targets.buckets"], "as": ["target"]},
        {"calculate": "datum.key", "as": "from"},
        {"calculate": "datum.target.key", "as": "to"},
        {"calculate": "datum.target.doc_count", "as": "count"},
        {"filter": "datum.from != datum.to"},
    ],
    "mark": {"type": "rect", "tooltip": True},
    "encoding": {
        "x": {
            "field": "from",
            "type": "nominal",
            "sort": "-color",
            "axis": {
                "title": "이전 화면",
                "labelAngle": -35,
                "labelFontSize": 11,
                "labelLimit": 140,
                "titleFontSize": 12,
            },
        },
        "y": {
            "field": "to",
            "type": "nominal",
            "sort": "-color",
            "axis": {
                "title": "다음 화면",
                "labelFontSize": 11,
                "labelLimit": 160,
                "titleFontSize": 12,
            },
        },
        "color": {
            "field": "count",
            "type": "quantitative",
            "scale": {"scheme": "tealblues"},
            "legend": {"title": "세션 수"},
        },
        "tooltip": [
            {"field": "from", "type": "nominal", "title": "이전"},
            {"field": "to", "type": "nominal", "title": "다음"},
            {"field": "count", "type": "quantitative", "title": "세션 수"},
        ],
    },
    "width": "container",
    "height": 380,
    "config": {
        "background": "transparent",
        "view": {"stroke": None},
        "axis": {"labelColor": "#CBD5E1", "titleColor": "#CBD5E1", "gridColor": "#334155"},
        "legend": {"labelColor": "#CBD5E1", "titleColor": "#CBD5E1"},
        "title": {"color": "#E5E7EB"},
    },
}
I4 = viz_vega(
    viz_id="vis-marketing-flow-heatmap",
    title="[I4] 화면 이동 흐름",
    description="이전 화면 → 다음 화면 이동 빈도 히트맵. 짙은 칸 = 자주 일어나는 흐름.",
    spec=I4_SPEC,
)


# ============================================================
# I5: 시간대별 진입 추이 (Stacked Area, 1h)
# funnel_started 이벤트를 funnel_name 별로 누적. 어느 시간대에 어느 컨텐츠가 강한지.
#
# 주의: content_type 으로 group by 하지 않는다 — startFunnel 헬퍼는 metadata.funnel_name 만
# 채우고 top-level content_type 은 비워서 보낸다. content_type 으로 group by 하면 전체가
# missing bucket 으로 빠져 차트가 비게 된다.
# ============================================================
COLOR_FUNNEL = {
    "relay_room_creation":     "#60A5FA",
    "flipbook_room_creation":  "#FB923C",
    "community_memo_posting":  "#34D399",
    "fortune_creation":        "#F472B6",
    "gallery_save_share":      "#22D3EE",
}
I5 = viz_classic(
    viz_id="vis-marketing-entry-timeline",
    title="[I5] 시간대별 진입 추이",
    description="funnel_started 를 컨텐츠별로 1시간 단위 누적. 피크 타임 + 컨텐츠 mix.",
    query="service:client-web AND event_name:funnel_started",
    colors=COLOR_FUNNEL,
    vis_state={
        "title": "[I5] 시간대별 진입 추이",
        "type": "area",
        "params": {
            "type": "area",
            "grid": {"categoryLines": False},
            "categoryAxes": [{
                "id": "CategoryAxis-1", "type": "category", "position": "bottom",
                "show": True, "style": {}, "scale": {"type": "linear"},
                "labels": {"show": True, "filter": True, "truncate": 100},
                "title": {},
            }],
            "valueAxes": [{
                "id": "ValueAxis-1", "name": "LeftAxis-1", "type": "value", "position": "left",
                "show": True, "style": {},
                "scale": {"type": "linear", "mode": "normal"},
                "labels": {"show": True, "rotate": 0, "filter": False, "truncate": 100},
                "title": {"text": "진입 세션"},
            }],
            "seriesParams": [{
                "show": True, "type": "area", "mode": "stacked",
                "data": {"label": "Count", "id": "1"},
                "drawLinesBetweenPoints": True,
                "showCircles": False,
                "interpolate": "cardinal",
                "valueAxis": "ValueAxis-1",
                "lineWidth": 1,
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
                "field": "metadata.funnel_name", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "(미지정)",
            }},
        ],
    },
)


# ============================================================
# Dashboard — 5개 viz 그리드 (48 column).
#
#   [I1 KPI (full width, 짧음)]
#   [I2 완주율 (절반)][I5 시간대별 진입 (절반)]
#   [I3 단계별 깔때기 (full width, 큼)]
#   [I4 화면 이동 흐름 (full width)]
# ============================================================
PANELS = [
    {"vis_id": I1["id"], "panel_id": "1", "grid": {"x": 0,  "y": 0,  "w": 48, "h": 10}},
    {"vis_id": I2["id"], "panel_id": "2", "grid": {"x": 0,  "y": 10, "w": 24, "h": 16}},
    {"vis_id": I5["id"], "panel_id": "3", "grid": {"x": 24, "y": 10, "w": 24, "h": 16}},
    {"vis_id": I3["id"], "panel_id": "4", "grid": {"x": 0,  "y": 26, "w": 48, "h": 24}},
    {"vis_id": I4["id"], "panel_id": "5", "grid": {"x": 0,  "y": 50, "w": 48, "h": 22}},
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
        "id": "dashboard-marketing",
        "type": "dashboard",
        "attributes": {
            "title": "[I] 마케팅 인사이트",
            "hits": 0,
            "description": (
                "마케팅 페르소나 — 비전문가도 한눈에 보는 핵심 지표. "
                "Q1 오늘의 KPI · Q2 컨텐츠 완주율 · Q3 단계별 이탈 · Q4 화면 이동 · Q5 진입 추이."
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
    OBJECTS = [I1, I2, I3, I4, I5, DASHBOARD]
    write_ndjson(OBJECTS, OUT)
    print(f"wrote {len(OBJECTS)} saved-objects -> {OUT}")
    print("titles:")
    for o in OBJECTS:
        print(f"  - {o['type']:14} {o['id']:38} {o['attributes']['title']}")
