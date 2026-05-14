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


def viz_markdown(viz_id, title, markdown):
    """섹션 헤더용 markdown panel — chart 그룹을 시각적으로 구분."""
    return {
        "id": viz_id,
        "type": "visualization",
        "attributes": {
            "title": title,
            "visState": json.dumps({
                "title": title,
                "type": "markdown",
                "aggs": [],
                "params": {
                    "fontSize": 14,
                    "openLinksInNewTab": False,
                    "markdown": markdown,
                },
            }, ensure_ascii=False),
            "uiStateJSON": "{}",
            "description": "",
            "version": 1,
            "kibanaSavedObjectMeta": {
                "searchSourceJSON": json.dumps({
                    "query": {"query": "", "language": "lucene"},
                    "filter": [],
                }),
            },
        },
        "references": [],
    }


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


def wrap_single_as_multiview(spec):
    """single chart vega-lite spec 을 vconcat 단일 항목으로 wrap.

    OS Dashboards Vega plugin 은 single chart spec 에 width/height 가 명시되면 autosize:"fit"
    을 자동 주입하면서 "width/height ignored" warning 을 출력한다. 이 자동 주입은 spec 이
    multi-view(facet/vconcat/hconcat/repeat) 일 때만 건너뛴다.

    single chart 를 vconcat 안에 1개짜리로 wrap 하면 plugin 입장에서 multi-view 가 되어
    width/height/autosize 가 inner spec 에서 그대로 적용되고 warning 도 사라진다.

    outer 에는 $schema 와 config 만 남기고, 나머지는 inner spec 으로 이동한다 — title 은
    inner 안에 있어야 multi-view 내부 chart 의 title 로 표시된다.
    """
    outer_keys = {"$schema", "config"}
    inner_spec = {k: v for k, v in spec.items() if k not in outer_keys}
    wrapped = {"vconcat": [inner_spec]}
    if "$schema" in spec:
        wrapped["$schema"] = spec["$schema"]
    if "config" in spec:
        wrapped["config"] = spec["config"]
    return wrapped


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
# funnel_name 별로 "참여 확정 후" 진입 대비 완료(funnel_goal_reached) 비율 백분율.
#
# 분모는 funnel_started 가 아니라 funnel_step_completed 의 첫 단계 — 사용자가 실제
# 참여를 확정한 시점:
#   - relay_room_creation / flipbook_room_creation: step_name="settings" (방 생성/입장 직후)
#   - fortune_creation: step_name="birth_info" (생년월일 정보 입력 직후)
# 단순 랜딩만 한 세션은 분모에서 제외해 "마음먹은 사용자 중 끝까지 간 비율" 측정.
#
# 색 = 위험도 (낮을수록 빨강, 높을수록 녹색).
# ============================================================
I2_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    # single chart spec 에 width/height 만 있으면 OS Dashboards Vega plugin 이 autosize:"fit"
    # 을 자동 주입하면서 "width/height ignored" warning 을 띄운다. 명시적으로 autosize 를
    # 두면 plugin 이 자동 주입을 건너뛰어 warning 사라짐. multi-view(facet) 인 I3 는
    # 자동 주입 대상이 아니라 명시 불필요.
    "autosize": {"type": "fit", "contains": "padding", "resize": True},
    "title": {
        "text": "컨텐츠별 완주율 (%)",
        "subtitle": "방 생성/참여 후 완료 비율. 단순 랜딩만 한 세션은 분모에서 제외. 막대 길이 = 비율, 색 = 위험도.",
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
                                # 분모(started)는 "방 생성/참여 직후" — funnel_step_completed 의 첫 단계.
                                # relay/flipbook: settings, fortune: birth_info. terms 로 묶어 funnel_name
                                # 별 분류 시 자기 funnel 에 해당하는 step 만 매칭됨.
                                "aggs": {
                                    "started": {
                                        "filter": {
                                            "bool": {
                                                "must": [
                                                    {"term": {"event_name":
                                                              "funnel_step_completed"}},
                                                    {"terms": {"metadata.step_name":
                                                               ["settings", "birth_info"]}}
                                                ]
                                            }
                                        }
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
            "axis": {
                "title": None,
                "labelFontSize": 13,
                "labelLimit": 240,
                "labelPadding": 8,
                "offset": 4,
            },
        },
        "x": {
            "field": "rate",
            "type": "quantitative",
            "scale": {"domain": [0, 100]},
            "axis": {"title": "완주율 (%)", "labelFontSize": 11, "tickCount": 5},
        },
    },
    # y축 label 자리 확보 — 좌측 padding 충분히.
    "padding": {"left": 170, "right": 60, "top": 50, "bottom": 40},
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
    # OS Dashboards Vega 가 spec.width 를 panel 폭으로 자동 inject 하지 않아서
    # 명시 안 하면 vega-lite default(200) 로 chart 가 작게 그려진다. 명시 숫자로 둠.
    "width": 700,
    "height": 240,
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
    spec=wrap_single_as_multiview(I2_SPEC),
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
                                    {"term": {"event_name": "funnel_step_completed"}}
                                ]
                            }
                        },
                        # composite aggregation 으로 funnel × step 조합을 평면 응답으로 받는다.
                        # nested terms 의 다단 buckets 를 vega-lite flatten 으로 풀면 vega-expression
                        # 의 chained property + ternary 조합이 안정적이지 않음.
                        "aggs": {
                            "pairs": {
                                "composite": {
                                    "size": 200,
                                    "sources": [
                                        {"funnel": {"terms": {
                                            "field": "metadata.funnel_name"
                                        }}},
                                        {"step": {"terms": {
                                            "field": "metadata.step_name"
                                        }}}
                                    ]
                                }
                            }
                        }
                    }
                }
            }
        },
        "format": {"property": "aggregations.filtered.pairs.buckets"}
    },
    "transform": [
        # composite agg 의 key 객체는 dotted property access 가 vega-expression 에서
        # 일관되지 않게 풀림. bracket notation 으로 안전하게 추출.
        {"calculate": "datum['key']['funnel']", "as": "funnel_name"},
        {"calculate": "datum['key']['step']", "as": "step_name"},
        {"calculate": "datum['doc_count']", "as": "count"},
        {"calculate":
            "{'relay_room_creation':'릴레이드로잉',"
            "'flipbook_room_creation':'플립북',"
            "'community_memo_posting':'커뮤니티 메모',"
            "'fortune_creation':'오늘의 운세',"
            "'gallery_save_share':'갤러리·공유'}[datum.funnel_name] || datum.funnel_name",
         "as": "funnel_label"},
        {"filter": "datum.count > 0"},
    ],
    # facet object + inner spec 정식 구조. row encoding shorthand 와 outer width/height
    # 동시 사용은 invalid spec 으로 떨어져서 vega view 가 mount 안 됨.
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
        # facet 의 inner spec — 명시 안 하면 default(200) 로 작게 그려짐.
        "width": 1100,
        "height": 140,
        "mark": {"type": "bar", "cornerRadiusEnd": 3, "tooltip": True},
        "encoding": {
            "y": {
                "field": "step_name",
                "type": "nominal",
                "axis": {"title": None, "labelFontSize": 11, "labelLimit": 140},
            },
            "x": {
                "field": "count",
                "type": "quantitative",
                "axis": {"title": "세션 수", "labelFontSize": 11},
            },
            "color": {
                "field": "step_name",
                "type": "nominal",
                "scale": {"scheme": "blues"},
                "legend": None,
            },
            "tooltip": [
                {"field": "funnel_label", "type": "nominal", "title": "컨텐츠"},
                {"field": "step_name", "type": "nominal", "title": "단계"},
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
    # I2 와 같은 이유로 autosize 명시 (plugin 자동 주입 + warning 회피).
    "autosize": {"type": "fit", "contains": "padding", "resize": True},
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
                                ],
                                # admin 백오피스 라우트 제외 — 마케팅 흐름 분석 노이즈.
                                "must_not": [
                                    {"prefix": {"path": "/admin"}},
                                    {"prefix": {"prev_path": "/admin"}}
                                ]
                            }
                        },
                        # composite aggregation 으로 prev_path × path 조합을 평면 응답으로 받음.
                        # 너무 많이 받으면 heatmap 이 잘게 쪼개져 가독성 떨어지므로 상위 60개만.
                        "aggs": {
                            "pairs": {
                                "composite": {
                                    "size": 60,
                                    "sources": [
                                        {"from_path": {"terms": {"field": "prev_path"}}},
                                        {"to_path": {"terms": {"field": "path"}}}
                                    ]
                                }
                            }
                        }
                    }
                }
            }
        },
        "format": {"property": "aggregations.filtered.pairs.buckets"}
    },
    "transform": [
        # composite key 객체를 bracket notation 으로 안전 추출.
        {"calculate": "datum['key']['from_path']", "as": "from_raw"},
        {"calculate": "datum['key']['to_path']", "as": "to_raw"},
        {"calculate": "datum['doc_count']", "as": "count_raw"},
        # 동적 segment 정규화 — relay 방코드, flipbook 라우트, share 토큰, admin 동적 ID 등을
        # 패턴으로 묶어서 같은 흐름으로 집계되도록.
        # vega-expression 의 replace 는 regexp() 정규식을 두 번째 인자로 받는다.
        {"calculate":
            "replace(replace(replace(replace(replace(datum.from_raw, "
            "regexp('/relay-drawing/[A-Z0-9]+'), '/relay-drawing/:room'), "
            "regexp('/flipbook/lobby/[A-Z0-9]+'), '/flipbook/lobby/:room'), "
            "regexp('/flipbook/drawing/[A-Z0-9]+'), '/flipbook/drawing/:room'), "
            "regexp('/flipbook/result/[A-Z0-9]+'), '/flipbook/result/:room'), "
            "regexp('/share/[A-Za-z0-9_-]+'), '/share/:token')",
         "as": "from"},
        {"calculate":
            "replace(replace(replace(replace(replace(datum.to_raw, "
            "regexp('/relay-drawing/[A-Z0-9]+'), '/relay-drawing/:room'), "
            "regexp('/flipbook/lobby/[A-Z0-9]+'), '/flipbook/lobby/:room'), "
            "regexp('/flipbook/drawing/[A-Z0-9]+'), '/flipbook/drawing/:room'), "
            "regexp('/flipbook/result/[A-Z0-9]+'), '/flipbook/result/:room'), "
            "regexp('/share/[A-Za-z0-9_-]+'), '/share/:token')",
         "as": "to"},
        {"filter": "datum.from != datum.to"},
        # 정규화로 합쳐진 같은 (from, to) 페어들을 sum 으로 다시 집계.
        {"aggregate": [{"op": "sum", "field": "count_raw", "as": "count"}],
         "groupby": ["from", "to"]},
    ],
    "mark": {"type": "rect", "tooltip": True},
    "encoding": {
        "x": {
            "field": "from",
            "type": "nominal",
            "axis": {
                "title": "이전 화면",
                "labelAngle": -25,
                "labelFontSize": 12,
                "labelLimit": 200,
                "titleFontSize": 13,
            },
        },
        "y": {
            "field": "to",
            "type": "nominal",
            "axis": {
                "title": "다음 화면",
                "labelFontSize": 12,
                "labelLimit": 220,
                "titleFontSize": 13,
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
    "width": 1100,
    "height": 420,
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
    spec=wrap_single_as_multiview(I4_SPEC),
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
# I6: 결과물 → 커뮤니티 게시 전환율 (Donut, 컨텐츠별)
# 결과 도달(funnel_goal_reached) 이후 공유 없이 떠난(result_share_abandoned) 비율의 보완.
# 공유 = goal_reached 카운트 - result_share_abandoned 카운트 (대략적 근사치).
# 정밀한 "커뮤니티 게시 전환" 은 community_memo_posting funnel 구현 후 측정 가능.
# ============================================================
I6_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    "title": {
        "text": "결과 도달 후 공유 비율",
        "subtitle": "컨텐츠별 결과 도달(funnel_goal_reached) 대비 공유 없이 이탈하지 않은 비율. "
                    "정밀한 커뮤니티 게시 전환은 community_memo_posting funnel 구현 시 측정.",
        "subtitleColor": "#94A3B8",
        "subtitleFontSize": 10,
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
                                "aggs": {
                                    "goal": {
                                        "filter": {"term":
                                                   {"event_name": "funnel_goal_reached"}}
                                    },
                                    "abandoned": {
                                        "filter": {"term":
                                                   {"event_name": "result_share_abandoned"}}
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
        {"calculate": "datum.goal ? datum.goal.doc_count : 0", "as": "goal"},
        {"calculate":
            "datum.abandoned ? datum.abandoned.doc_count : 0",
         "as": "abandoned"},
        {"calculate": "max(datum.goal - datum.abandoned, 0)", "as": "shared"},
        {"filter": "datum.goal > 0"},
        # FUNNEL_KOREAN_LABEL_EXPR 가 datum.funnel_name 을 참조하므로 별도 calculate.
        {"calculate": "datum.key", "as": "funnel_name"},
        {"calculate": FUNNEL_KOREAN_LABEL_EXPR + " || datum.funnel_name", "as": "funnel_label"},
    ],
    # padding 으로 chart 영역을 미리 좁힘 → panel container fit 시 chart 가 panel 다 안 차지하고
    # 남는 영역(right 160 px)에 legend 가 들어감. autosize fit contains padding 으로 panel
    # 안에 모두 fit 되도록.
    # autosize fit + container — Kibana/OS Dashboards 권장 default 패턴. legend 는
    # orient:"none" + 절대 좌표로 chart view 내부에 직접 배치해 panel 강제 fit 환경에서도
    # legend 가 view boundary 안에 항상 들어가도록 보장 (vega-lite docs 의 fit clipping 한계 회피).
    "autosize": {"type": "fit", "contains": "padding", "resize": True},
    "width": "container",
    "height": "container",
    "padding": {"top": 20, "right": 20, "bottom": 20, "left": 20},
    # 도넛 크기 고정 — view fit 으로 도넛이 panel 다 차지하면 legend 자리 안 남음.
    # 명시 innerRadius/outerRadius 로 도넛 크기 고정, 남은 공간에 legend 배치.
    "mark": {"type": "arc", "innerRadius": 35, "outerRadius": 80, "tooltip": True},
    "encoding": {
        "theta": {"field": "shared", "type": "quantitative"},
        "color": {
            "field": "funnel_label",
            "type": "nominal",
            "scale": {"range": ["#60A5FA", "#FB923C", "#F472B6", "#34D399", "#22D3EE"]},
            "legend": {
                "title": None,
                "labelFontSize": 11,
                "labelColor": "#E5E7EB",
                # vega-lite docs: fit autosize 는 legend space 부족 시 클리핑.
                # orient:"none" + ExprRef 로 view width 에 비례한 절대 좌표 — panel container
                # 크기가 어떻든 도넛(outerRadius 80) 우측에 안전하게 배치.
                "orient": "none",
                "legendX": {"expr": "(width / 2) + 100"},
                "legendY": {"expr": "20"},
                "direction": "vertical",
                "symbolSize": 100,
                "labelLimit": 140,
                "padding": 6,
            },
        },
        "tooltip": [
            {"field": "funnel_label", "type": "nominal", "title": "컨텐츠"},
            {"field": "goal", "type": "quantitative", "title": "결과 도달"},
            {"field": "shared", "type": "quantitative", "title": "공유 (추정)"},
            {"field": "abandoned", "type": "quantitative", "title": "공유 없이 이탈"},
        ],
    },
    # panel grid 16/48 (1/3 폭) 에 맞게 mark 영역 + bottom legend 공간 모두 확보.
    "width": 240,
    "height": 200,
    "config": {
        "background": "transparent",
        "view": {"stroke": None},
        "legend": {"labelColor": "#CBD5E1", "titleColor": "#CBD5E1"},
        "title": {"color": "#E5E7EB"},
    },
}
I6 = viz_vega(
    viz_id="vis-marketing-share-rate",
    title="[I6] 결과 도달 후 공유 비율",
    description="컨텐츠별 결과 도달 대비 공유 비율(근사치). 정밀 측정은 community_memo_posting funnel 구현 후.",
    spec=wrap_single_as_multiview(I6_SPEC),
)


# ============================================================
# I7: 유입 경로 비율 (Donut, entry_type 별)
# landing_source_detected 의 metadata.entry_type. 직접/검색/SNS/공유/QR/캠페인.
# 한글 라벨로 매핑.
# ============================================================
I7_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    "title": {
        "text": "유입 경로 비율",
        "subtitle": "랜딩 시점의 entry_type 분포 — 어디서 들어오는 사용자가 많은가.",
        "subtitleColor": "#94A3B8",
        "subtitleFontSize": 10,
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
                                    {"term": {"event_name": "landing_source_detected"}}
                                ]
                            }
                        },
                        "aggs": {
                            "by_type": {
                                "terms": {
                                    "field": "metadata.entry_type",
                                    "size": 10,
                                    "missing": "unknown"
                                }
                            }
                        }
                    }
                }
            }
        },
        "format": {"property": "aggregations.filtered.by_type.buckets"}
    },
    "transform": [
        {"calculate": "datum.key", "as": "entry_type"},
        {"calculate": "datum.doc_count", "as": "count"},
        {"calculate":
            "{'direct':'직접 접속','search':'검색','social':'SNS',"
            "'qr':'QR 코드','share':'공유 링크','campaign':'캠페인',"
            "'unknown':'알 수 없음'}[datum.entry_type] || datum.entry_type",
         "as": "entry_label"},
    ],
    # autosize fit + container — Kibana/OS Dashboards 권장 default 패턴. legend 는
    # orient:"none" + 절대 좌표로 chart view 내부에 직접 배치해 panel 강제 fit 환경에서도
    # legend 가 view boundary 안에 항상 들어가도록 보장 (vega-lite docs 의 fit clipping 한계 회피).
    "autosize": {"type": "fit", "contains": "padding", "resize": True},
    "width": "container",
    "height": "container",
    "padding": {"top": 20, "right": 20, "bottom": 20, "left": 20},
    # 도넛 크기 고정 — view fit 으로 도넛이 panel 다 차지하면 legend 자리 안 남음.
    # 명시 innerRadius/outerRadius 로 도넛 크기 고정, 남은 공간에 legend 배치.
    "mark": {"type": "arc", "innerRadius": 35, "outerRadius": 80, "tooltip": True},
    "encoding": {
        "theta": {"field": "count", "type": "quantitative"},
        "color": {
            "field": "entry_label",
            "type": "nominal",
            "scale": {
                "domain": ["직접 접속", "검색", "SNS", "QR 코드",
                           "공유 링크", "캠페인", "알 수 없음"],
                "range": ["#94A3B8", "#60A5FA", "#34D399", "#FBBF24",
                          "#A78BFA", "#F472B6", "#475569"],
            },
            "legend": {
                "title": None,
                "labelFontSize": 11,
                "labelColor": "#E5E7EB",
                # vega-lite docs: fit autosize 는 legend space 부족 시 클리핑.
                # orient:"none" + ExprRef 로 view width 에 비례한 절대 좌표 — panel container
                # 크기가 어떻든 도넛(outerRadius 80) 우측에 안전하게 배치.
                "orient": "none",
                "legendX": {"expr": "(width / 2) + 100"},
                "legendY": {"expr": "20"},
                "direction": "vertical",
                "symbolSize": 100,
                "labelLimit": 140,
                "padding": 6,
            },
        },
        "tooltip": [
            {"field": "entry_label", "type": "nominal", "title": "경로"},
            {"field": "count", "type": "quantitative", "title": "세션 수"},
        ],
    },
    # panel grid 16/48 (1/3 폭) 에 맞게 mark 영역 + bottom legend 공간 모두 확보.
    "width": 240,
    "height": 200,
    "config": {
        "background": "transparent",
        "view": {"stroke": None},
        "legend": {"labelColor": "#CBD5E1", "titleColor": "#CBD5E1"},
        "title": {"color": "#E5E7EB"},
    },
}
I7 = viz_vega(
    viz_id="vis-marketing-entry-types",
    title="[I7] 유입 경로 비율",
    description="entry_type 별 랜딩 세션 비율 (direct/search/social/share/qr/campaign).",
    spec=wrap_single_as_multiview(I7_SPEC),
)


# ============================================================
# I8: SNS 유입 비율 (Donut, referrer host 별)
# entry_type=social 인 세션의 referrer host 분포. 인스타/트위터/카카오톡 등.
# referrer 는 metadata.referrer (top-level path 아님) 에 들어있다.
# ============================================================
I8_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    "title": {
        "text": "SNS 유입 비율",
        "subtitle": "entry_type=social 세션의 referrer host 분포 — 어느 SNS 가 강한가.",
        "subtitleColor": "#94A3B8",
        "subtitleFontSize": 10,
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
                                    {"term": {"event_name": "landing_source_detected"}},
                                    {"term": {"metadata.entry_type": "social"}},
                                    {"exists": {"field": "metadata.referrer"}}
                                ]
                            }
                        },
                        "aggs": {
                            "by_ref": {
                                "terms": {"field": "metadata.referrer", "size": 30}
                            }
                        }
                    }
                }
            }
        },
        "format": {"property": "aggregations.filtered.by_ref.buckets"}
    },
    "transform": [
        {"calculate": "datum.key", "as": "referrer_raw"},
        {"calculate": "datum.doc_count", "as": "count"},
        # referrer host 추출 + SNS 별로 묶기.
        {"calculate":
            "indexof(datum.referrer_raw, 'instagram') >= 0 ? '인스타그램' "
            ": (indexof(datum.referrer_raw, 'twitter') >= 0 || indexof(datum.referrer_raw, 'x.com') >= 0 || indexof(datum.referrer_raw, 't.co') >= 0) ? '트위터' "
            ": (indexof(datum.referrer_raw, 'kakao') >= 0) ? '카카오톡' "
            ": (indexof(datum.referrer_raw, 'facebook') >= 0) ? '페이스북' "
            ": (indexof(datum.referrer_raw, 'linkedin') >= 0) ? '링크드인' "
            ": '기타'",
         "as": "sns_label"},
        {"aggregate": [{"op": "sum", "field": "count", "as": "count"}], "groupby": ["sns_label"]},
    ],
    # autosize fit + container — Kibana/OS Dashboards 권장 default 패턴. legend 는
    # orient:"none" + 절대 좌표로 chart view 내부에 직접 배치해 panel 강제 fit 환경에서도
    # legend 가 view boundary 안에 항상 들어가도록 보장 (vega-lite docs 의 fit clipping 한계 회피).
    "autosize": {"type": "fit", "contains": "padding", "resize": True},
    "width": "container",
    "height": "container",
    "padding": {"top": 20, "right": 20, "bottom": 20, "left": 20},
    # 도넛 크기 고정 — view fit 으로 도넛이 panel 다 차지하면 legend 자리 안 남음.
    # 명시 innerRadius/outerRadius 로 도넛 크기 고정, 남은 공간에 legend 배치.
    "mark": {"type": "arc", "innerRadius": 35, "outerRadius": 80, "tooltip": True},
    "encoding": {
        "theta": {"field": "count", "type": "quantitative"},
        "color": {
            "field": "sns_label",
            "type": "nominal",
            "scale": {
                "domain": ["인스타그램", "트위터", "카카오톡", "페이스북", "링크드인", "기타"],
                "range": ["#EC4899", "#60A5FA", "#FBBF24", "#3B82F6", "#0E76A8", "#94A3B8"],
            },
            "legend": {
                "title": None,
                "labelFontSize": 11,
                "labelColor": "#E5E7EB",
                # vega-lite docs: fit autosize 는 legend space 부족 시 클리핑.
                # orient:"none" + ExprRef 로 view width 에 비례한 절대 좌표 — panel container
                # 크기가 어떻든 도넛(outerRadius 80) 우측에 안전하게 배치.
                "orient": "none",
                "legendX": {"expr": "(width / 2) + 100"},
                "legendY": {"expr": "20"},
                "direction": "vertical",
                "symbolSize": 100,
                "labelLimit": 140,
                "padding": 6,
            },
        },
        "tooltip": [
            {"field": "sns_label", "type": "nominal", "title": "SNS"},
            {"field": "count", "type": "quantitative", "title": "세션 수"},
        ],
    },
    # panel grid 16/48 (1/3 폭) 에 맞게 mark 영역 + bottom legend 공간 모두 확보.
    "width": 240,
    "height": 200,
    "config": {
        "background": "transparent",
        "view": {"stroke": None},
        "legend": {"labelColor": "#CBD5E1", "titleColor": "#CBD5E1"},
        "title": {"color": "#E5E7EB"},
    },
}
I8 = viz_vega(
    viz_id="vis-marketing-sns-referrer",
    title="[I8] SNS 유입 비율",
    description="SNS 유입(entry_type=social) 의 referrer host 분포 — 인스타/트위터/카카오톡 등.",
    spec=wrap_single_as_multiview(I8_SPEC),
)


# ============================================================
# I9: 체험 공간 평균 체류 시간 (Bar, path 별)
# page_leave 의 metadata.time_on_page_ms 평균. 어느 화면이 사용자를 오래 붙드는가.
# 동적 segment(roomCode 등) 는 I4 와 같은 방식으로 정규화.
# ============================================================
I9_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    "title": {
        "text": "체험 공간 평균 체류 시간",
        "subtitle": "page_leave 의 time_on_page_ms 평균 (초). 막대가 길수록 더 오래 머무는 화면.",
        "subtitleColor": "#94A3B8",
        "subtitleFontSize": 10,
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
                                    {"term": {"event_name": "page_leave"}},
                                    {"exists": {"field": "path"}},
                                    {"exists": {"field": "metadata.time_on_page_ms"}}
                                ],
                                # admin 백오피스 라우트는 마케팅 분석에서 제외.
                                "must_not": [
                                    {"prefix": {"path": "/admin"}}
                                ]
                            }
                        },
                        "aggs": {
                            "by_path": {
                                "terms": {"field": "path", "size": 30},
                                "aggs": {
                                    "avg_ms": {"avg": {"field": "metadata.time_on_page_ms"}}
                                }
                            }
                        }
                    }
                }
            }
        },
        "format": {"property": "aggregations.filtered.by_path.buckets"}
    },
    "transform": [
        {"calculate": "datum.key", "as": "path_raw"},
        # 동적 segment 정규화 (I4 와 동일).
        {"calculate":
            "replace(replace(replace(replace(replace(datum.path_raw, "
            "regexp('/relay-drawing/[A-Z0-9]+'), '/relay-drawing/:room'), "
            "regexp('/flipbook/lobby/[A-Z0-9]+'), '/flipbook/lobby/:room'), "
            "regexp('/flipbook/drawing/[A-Z0-9]+'), '/flipbook/drawing/:room'), "
            "regexp('/flipbook/result/[A-Z0-9]+'), '/flipbook/result/:room'), "
            "regexp('/share/[A-Za-z0-9_-]+'), '/share/:token')",
         "as": "path"},
        {"calculate": "datum.avg_ms ? datum.avg_ms.value / 1000 : 0", "as": "avg_sec_raw"},
        {"calculate": "datum.doc_count", "as": "samples"},
        # 정규화로 같은 path 가 된 row 들을 다시 평균. 가중 평균을 위해 sum(avg*samples)/sum(samples).
        {"calculate": "datum.avg_sec_raw * datum.samples", "as": "weighted"},
        {"aggregate": [
            {"op": "sum", "field": "weighted", "as": "weighted_sum"},
            {"op": "sum", "field": "samples", "as": "n"}
         ], "groupby": ["path"]},
        {"calculate": "datum.n > 0 ? datum.weighted_sum / datum.n : 0", "as": "avg_sec"},
        {"filter": "datum.avg_sec > 0"},
    ],
    "mark": {"type": "bar", "cornerRadiusEnd": 3, "tooltip": True, "color": "#A78BFA"},
    "encoding": {
        "y": {
            "field": "path",
            "type": "nominal",
            "sort": "-x",
            "axis": {"title": None, "labelFontSize": 11, "labelLimit": 200},
        },
        "x": {
            "field": "avg_sec",
            "type": "quantitative",
            "axis": {"title": "평균 체류 시간 (초)", "labelFontSize": 11},
        },
        "tooltip": [
            {"field": "path", "type": "nominal", "title": "화면"},
            {"field": "avg_sec", "type": "quantitative", "title": "평균 (초)", "format": ".1f"},
            {"field": "n", "type": "quantitative", "title": "샘플 수"},
        ],
    },
    "width": 1100,
    "height": 420,
    "config": {
        "background": "transparent",
        "view": {"stroke": None},
        "axis": {"labelColor": "#CBD5E1", "titleColor": "#CBD5E1", "gridColor": "#334155"},
        "title": {"color": "#E5E7EB"},
    },
}
I9 = viz_vega(
    viz_id="vis-marketing-time-on-page",
    title="[I9] 체험 공간 평균 체류 시간",
    description="page_leave 의 time_on_page_ms 평균. 정규화된 path 별. 막대 길이 = 평균 초.",
    spec=wrap_single_as_multiview(I9_SPEC),
)


# ============================================================
# I10: 시간대별 유입원 추이 (Stacked Area, entry_type)
# landing_source_detected 이벤트를 entry_type 별로 1h 단위 누적. I5(funnel별)의
# 채널 버전. "어느 시간대에 어느 채널로 사용자가 들어오는가" — 광고 timing 분석.
# ============================================================
COLOR_ENTRY = {
    "direct":   "#94A3B8",
    "search":   "#60A5FA",
    "social":   "#34D399",
    "qr":       "#FBBF24",
    "share":    "#A78BFA",
    "campaign": "#F472B6",
    "unknown":  "#475569",
}
I10 = viz_classic(
    viz_id="vis-marketing-entry-by-channel-timeline",
    title="[I10] 시간대별 유입원 추이",
    description="landing_source_detected 의 metadata.entry_type 1h 단위 누적. 시간대별 채널 mix.",
    query="service:client-web AND event_name:landing_source_detected",
    colors=COLOR_ENTRY,
    vis_state={
        "title": "[I10] 시간대별 유입원 추이",
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
                "title": {"text": "랜딩 세션"},
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
                "field": "metadata.entry_type", "orderBy": "1", "order": "desc",
                "size": 10, "otherBucket": False,
                "missingBucket": True, "missingBucketLabel": "unknown",
            }},
        ],
    },
)


# ============================================================
# I11: 사용자 완주율 (Metric, distinct uuid 기반)
# 전체 distinct 방문자(uuid) vs 결과 도달한 distinct 방문자.
# session 기준이 아니라 uuid 기준이라 "1명이 여러 번 시도 후 완주" 케이스도 한 명으로 카운트.
# 단순 funnel_started 카운트보다 진짜 사용자 만족도에 가까운 지표.
# ============================================================
I11 = viz_classic(
    viz_id="vis-marketing-visitor-completion",
    title="[I11] 방문자 완주율",
    description="기간 내 distinct 방문자(uuid) 중 결과(funnel_goal_reached)에 도달한 비율.",
    query="service:client-web",
    colors={
        "방문자":     "#3B82F6",
        "완주 방문자": "#10B981",
    },
    vis_state={
        "title": "[I11] 방문자 완주율",
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
                    "fontSize": 42,
                },
            },
        },
        "aggs": [
            {"id": "1", "enabled": True, "type": "cardinality", "schema": "metric", "params": {
                "field": "uuid",
                "customLabel": "방문자",
            }},
            {"id": "2", "enabled": True, "type": "filters", "schema": "group", "params": {
                "filters": [
                    {"input": {"query": "*", "language": "lucene"}, "label": "방문자"},
                    {"input": {"query": "event_name:funnel_goal_reached", "language": "lucene"},
                     "label": "완주 방문자"},
                ],
            }},
        ],
    },
)


# ============================================================
# I12: 결과 화면 체류 시간 분포 (Vega-Lite Histogram)
# page_leave 이벤트 중 path 가 결과 화면인 것의 time_on_page_ms histogram.
# 결과 보고 바로 닫는 사용자 vs 오래 머무는 사용자 비율 — 컨텐츠 만족도 proxy.
#
# 결과 path 패턴: /flipbook/result, /share/:token, /relay-drawing/:room (FINISHED 상태 path)
# 단순화 — /result, /share, fortune /result 포함 path 만 필터.
# ============================================================
I12_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    "title": {
        "text": "결과 화면 체류 시간 분포",
        "subtitle": "결과 페이지 체류 시간 (초). 짧을수록 만족도 낮음(보고 바로 닫음).",
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
                                    {"term": {"event_name": "page_leave"}},
                                    {"exists": {"field": "metadata.time_on_page_ms"}}
                                ],
                                # 결과 화면 path 만. 동적 segment 포함.
                                "should": [
                                    {"wildcard": {"path": "*/result*"}},
                                    {"wildcard": {"path": "/share/*"}},
                                    {"prefix": {"path": "/fortune"}}
                                ],
                                "minimum_should_match": 1
                            }
                        },
                        "aggs": {
                            "duration_hist": {
                                "histogram": {
                                    "field": "metadata.time_on_page_ms",
                                    "interval": 5000,
                                    "min_doc_count": 1,
                                    "extended_bounds": {"min": 0, "max": 60000}
                                }
                            }
                        }
                    }
                }
            }
        },
        "format": {"property": "aggregations.filtered.duration_hist.buckets"}
    },
    "transform": [
        {"calculate": "datum.key / 1000", "as": "sec_bucket"},
        {"calculate": "datum.doc_count", "as": "count"},
        {"calculate":
            "datum.sec_bucket < 5 ? '0-5초' "
            ": datum.sec_bucket < 10 ? '5-10초' "
            ": datum.sec_bucket < 30 ? '10-30초' "
            ": datum.sec_bucket < 60 ? '30-60초' "
            ": '60초+'",
         "as": "bucket_label"},
        {"aggregate": [{"op": "sum", "field": "count", "as": "n"}], "groupby": ["bucket_label"]},
    ],
    "autosize": {"type": "fit", "contains": "padding", "resize": True},
    "width": "container",
    "height": "container",
    "padding": {"top": 30, "right": 30, "bottom": 50, "left": 50},
    "mark": {"type": "bar", "cornerRadiusEnd": 4, "tooltip": True},
    "encoding": {
        "x": {
            "field": "bucket_label",
            "type": "ordinal",
            "sort": ["0-5초", "5-10초", "10-30초", "30-60초", "60초+"],
            "axis": {"title": "체류 시간 구간", "labelFontSize": 11, "labelAngle": 0},
        },
        "y": {
            "field": "n",
            "type": "quantitative",
            "axis": {"title": "세션 수", "labelFontSize": 11},
        },
        "color": {
            "field": "bucket_label",
            "type": "nominal",
            "scale": {
                "domain": ["0-5초", "5-10초", "10-30초", "30-60초", "60초+"],
                "range": ["#EF4444", "#F97316", "#FBBF24", "#84CC16", "#10B981"],
            },
            "legend": None,
        },
        "tooltip": [
            {"field": "bucket_label", "type": "nominal", "title": "구간"},
            {"field": "n", "type": "quantitative", "title": "세션 수"},
        ],
    },
    "config": {
        "background": "transparent",
        "view": {"stroke": None},
        "axis": {"labelColor": "#CBD5E1", "titleColor": "#CBD5E1", "gridColor": "#334155"},
        "title": {"color": "#E5E7EB"},
    },
}
I12 = viz_vega(
    viz_id="vis-marketing-result-dwell-time",
    title="[I12] 결과 화면 체류 시간 분포",
    description="결과 화면(result) 체류 시간 histogram. 만족도 proxy.",
    spec=wrap_single_as_multiview(I12_SPEC),
)


# ============================================================
# I13: 이탈 직전 체류 시간 (Bar by phase)
# 각 이탈 이벤트의 시간 metadata 평균 — 사용자가 N초 안에 빠지는가.
# - room_lobby_abandoned.metadata.wait_time_ms (lobby 대기 시간)
# - creation_abandoned.metadata.elapsed_ms (그리기 진행 시간)
# - result_share_abandoned.metadata.time_on_result_ms (결과 보고 떠난 시간)
# ============================================================
I13_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    "title": {
        "text": "이탈 직전 평균 체류 시간",
        "subtitle": "이탈 유형별 평균 시간(초). 짧을수록 사용자가 빨리 지루해함.",
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
                            "lobby_avg": {
                                "filter": {"term": {"event_name": "room_lobby_abandoned"}},
                                "aggs": {
                                    "avg_ms": {"avg": {"field": "metadata.wait_time_ms"}},
                                    "n": {"value_count": {"field": "event_name"}}
                                }
                            },
                            "creation_avg": {
                                "filter": {"term": {"event_name": "creation_abandoned"}},
                                "aggs": {
                                    "avg_ms": {"avg": {"field": "metadata.elapsed_ms"}},
                                    "n": {"value_count": {"field": "event_name"}}
                                }
                            },
                            "result_avg": {
                                "filter": {"term": {"event_name": "result_share_abandoned"}},
                                "aggs": {
                                    "avg_ms": {"avg": {"field": "metadata.time_on_result_ms"}},
                                    "n": {"value_count": {"field": "event_name"}}
                                }
                            }
                        }
                    }
                }
            }
        },
        # 단일 객체 → array 로 풀기 위해 transform 으로 명시.
        "format": {"property": "aggregations.filtered"}
    },
    "transform": [
        # 세 개의 named filter aggregation 결과를 row 로 풀어내기.
        {"calculate": "[{ "
            "'label': '로비 대기 후 이탈', "
            "'avg_sec': datum.lobby_avg && datum.lobby_avg.avg_ms && datum.lobby_avg.avg_ms.value ? datum.lobby_avg.avg_ms.value / 1000 : 0, "
            "'n': datum.lobby_avg && datum.lobby_avg.doc_count ? datum.lobby_avg.doc_count : 0"
            "}, { "
            "'label': '그리는 도중 이탈', "
            "'avg_sec': datum.creation_avg && datum.creation_avg.avg_ms && datum.creation_avg.avg_ms.value ? datum.creation_avg.avg_ms.value / 1000 : 0, "
            "'n': datum.creation_avg && datum.creation_avg.doc_count ? datum.creation_avg.doc_count : 0"
            "}, { "
            "'label': '결과 보고 이탈', "
            "'avg_sec': datum.result_avg && datum.result_avg.avg_ms && datum.result_avg.avg_ms.value ? datum.result_avg.avg_ms.value / 1000 : 0, "
            "'n': datum.result_avg && datum.result_avg.doc_count ? datum.result_avg.doc_count : 0"
            "}]",
         "as": "rows"},
        {"flatten": ["rows"], "as": ["row"]},
        {"calculate": "datum.row.label", "as": "label"},
        {"calculate": "datum.row.avg_sec", "as": "avg_sec"},
        {"calculate": "datum.row.n", "as": "n"},
        {"filter": "datum.n > 0"},
    ],
    "autosize": {"type": "fit", "contains": "padding", "resize": True},
    "width": "container",
    "height": "container",
    "padding": {"top": 30, "right": 30, "bottom": 30, "left": 160},
    "mark": {"type": "bar", "cornerRadiusEnd": 4, "tooltip": True, "color": "#EF4444"},
    "encoding": {
        "y": {
            "field": "label",
            "type": "nominal",
            "sort": "-x",
            "axis": {"title": None, "labelFontSize": 12, "labelLimit": 200},
        },
        "x": {
            "field": "avg_sec",
            "type": "quantitative",
            "axis": {"title": "평균 (초)", "labelFontSize": 11},
        },
        "tooltip": [
            {"field": "label", "type": "nominal", "title": "이탈 유형"},
            {"field": "avg_sec", "type": "quantitative", "title": "평균 (초)", "format": ".1f"},
            {"field": "n", "type": "quantitative", "title": "이탈 건수"},
        ],
    },
    "config": {
        "background": "transparent",
        "view": {"stroke": None},
        "axis": {"labelColor": "#CBD5E1", "titleColor": "#CBD5E1", "gridColor": "#334155"},
        "title": {"color": "#E5E7EB"},
    },
}
I13 = viz_vega(
    viz_id="vis-marketing-abandon-elapsed",
    title="[I13] 이탈 직전 평균 체류 시간",
    description="이탈 유형별 직전 체류 시간 평균. 사용자가 N초 안에 지루해하는지 측정.",
    spec=wrap_single_as_multiview(I13_SPEC),
)


# ============================================================
# 섹션 헤더 (Markdown panels) — chart 그룹별 시각적 구분.
# ============================================================
HEADER_OVERVIEW = viz_markdown(
    viz_id="vis-marketing-header-overview",
    title="섹션 헤더 — 총량",
    markdown=(
        "## 🎯 오늘의 핵심 지표\n"
        "**얼마나 들어왔고**, **얼마나 끝까지 갔는가** — 일일 모니터링용 KPI."
    ),
)

HEADER_CHANNEL = viz_markdown(
    viz_id="vis-marketing-header-channel",
    title="섹션 헤더 — 채널",
    markdown=(
        "## 📡 유입 채널 분석\n"
        "**어디서 들어오는가** · **어느 시간대에 강한가** · **SNS / 공유 효과** "
        "— 홍보·광고 의사결정."
    ),
)

HEADER_CONTENT = viz_markdown(
    viz_id="vis-marketing-header-content",
    title="섹션 헤더 — 컨텐츠",
    markdown=(
        "## 🎨 컨텐츠 매력도\n"
        "**어느 컨텐츠가 사용자를 끝까지 잡나** · **시간대별 컨텐츠 mix** "
        "— 매력 포인트 식별."
    ),
)

HEADER_FLOW = viz_markdown(
    viz_id="vis-marketing-header-flow",
    title="섹션 헤더 — 단계·흐름",
    markdown=(
        "## 🔍 단계별 / 화면 흐름\n"
        "**어느 단계에서 사용자가 빠지나** · **어디로 이동하나** "
        "— 약점 / 다음 행동 패턴."
    ),
)

HEADER_RETENTION = viz_markdown(
    viz_id="vis-marketing-header-retention",
    title="섹션 헤더 — 만족도·이탈",
    markdown=(
        "## ⏱️ 만족도 & 이탈 분석\n"
        "**어느 화면이 사용자를 붙드나** · **결과를 진짜 즐기는가** · "
        "**언제 빨리 빠지는가** — 재미있는지 측정."
    ),
)


# ============================================================
# Dashboard — viz + 섹션 헤더 그리드 (48 column).
#
#   [I1 KPI (full, 짧음)]
#   [I7 유입경로 (16)][I8 SNS유입 (16)][I6 공유율 (16)]
#   [I2 완주율 (24)][I5 시간대별 진입 (24)]
#   [I3 단계별 깔때기 (full)]
#   [I4 화면 이동 (full)]
#   [I9 페이지별 체류 시간 (full)]
# ============================================================
# 섹션별 그룹화 — 각 섹션은 markdown header(h=3) + 본문 viz 들.
#
#   [§ 오늘의 핵심 지표]
#     [I1 KPI ─────────][I11 방문자 완주율 ─────────]
#
#   [§ 유입 채널 분석]
#     [I7 유입경로 ────][I10 시간대별 유입원 ────]
#     [I8 SNS ─────────][I6 공유율 ──────────────]
#
#   [§ 컨텐츠 매력도]
#     [I2 컨텐츠 완주율 ──────][I5 시간대별 진입 ──────]
#
#   [§ 단계별 / 화면 흐름]
#     [I3 깔때기 ────────────────────────────────]
#     [I4 화면 이동 ─────────────────────────────]
#
#   [§ 만족도 & 이탈]
#     [I9 체류시간 ──────────][I12 결과 체류 분포 ──]
#     [I13 이탈 직전 체류 ───────────────────────]
PANELS = [
    # 섹션 1: 오늘의 핵심 지표
    {"vis_id": HEADER_OVERVIEW["id"], "panel_id": "h1",
     "grid": {"x": 0, "y": 0, "w": 48, "h": 3}},
    {"vis_id": I1["id"],  "panel_id": "1",
     "grid": {"x": 0,  "y": 3,  "w": 24, "h": 10}},
    {"vis_id": I11["id"], "panel_id": "2",
     "grid": {"x": 24, "y": 3,  "w": 24, "h": 10}},

    # 섹션 2: 유입 채널 분석
    {"vis_id": HEADER_CHANNEL["id"], "panel_id": "h2",
     "grid": {"x": 0, "y": 13, "w": 48, "h": 3}},
    {"vis_id": I7["id"],  "panel_id": "3",
     "grid": {"x": 0,  "y": 16, "w": 24, "h": 16}},
    {"vis_id": I10["id"], "panel_id": "4",
     "grid": {"x": 24, "y": 16, "w": 24, "h": 16}},
    {"vis_id": I8["id"],  "panel_id": "5",
     "grid": {"x": 0,  "y": 32, "w": 24, "h": 16}},
    {"vis_id": I6["id"],  "panel_id": "6",
     "grid": {"x": 24, "y": 32, "w": 24, "h": 16}},

    # 섹션 3: 컨텐츠 매력도
    {"vis_id": HEADER_CONTENT["id"], "panel_id": "h3",
     "grid": {"x": 0, "y": 48, "w": 48, "h": 3}},
    {"vis_id": I2["id"],  "panel_id": "7",
     "grid": {"x": 0,  "y": 51, "w": 24, "h": 16}},
    {"vis_id": I5["id"],  "panel_id": "8",
     "grid": {"x": 24, "y": 51, "w": 24, "h": 16}},

    # 섹션 4: 단계별 / 화면 흐름
    {"vis_id": HEADER_FLOW["id"], "panel_id": "h4",
     "grid": {"x": 0, "y": 67, "w": 48, "h": 3}},
    {"vis_id": I3["id"],  "panel_id": "9",
     "grid": {"x": 0,  "y": 70, "w": 48, "h": 24}},
    {"vis_id": I4["id"],  "panel_id": "10",
     "grid": {"x": 0,  "y": 94, "w": 48, "h": 22}},

    # 섹션 5: 만족도 & 이탈
    {"vis_id": HEADER_RETENTION["id"], "panel_id": "h5",
     "grid": {"x": 0, "y": 116, "w": 48, "h": 3}},
    {"vis_id": I9["id"],  "panel_id": "11",
     "grid": {"x": 0,  "y": 119, "w": 24, "h": 18}},
    {"vis_id": I12["id"], "panel_id": "12",
     "grid": {"x": 24, "y": 119, "w": 24, "h": 18}},
    {"vis_id": I13["id"], "panel_id": "13",
     "grid": {"x": 0,  "y": 137, "w": 48, "h": 14}},
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
    OBJECTS = [
        I1, I2, I3, I4, I5, I6, I7, I8, I9, I10, I11, I12, I13,
        HEADER_OVERVIEW, HEADER_CHANNEL, HEADER_CONTENT, HEADER_FLOW, HEADER_RETENTION,
        DASHBOARD,
    ]
    write_ndjson(OBJECTS, OUT)
    print(f"wrote {len(OBJECTS)} saved-objects -> {OUT}")
    print("titles:")
    for o in OBJECTS:
        print(f"  - {o['type']:14} {o['id']:38} {o['attributes']['title']}")
