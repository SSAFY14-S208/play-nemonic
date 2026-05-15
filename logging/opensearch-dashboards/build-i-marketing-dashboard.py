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


# ============================================================
# Design tokens — modern dark + violet accent.
#
# 모든 viz 가 여기에서 색을 끌어다 쓰도록 통일한다. viz 마다 임의의 hex 를 박지 않는다.
#   - ACCENT_VIOLET   : 마케팅 대시보드의 brand accent. neutral 차트(I9/I13)의 단일 색.
#   - STATUS_*        : 좋음/나쁨 의미가 분명한 지표(I2/I12)의 신호 색.
#   - FUNNEL_COLORS   : 5개 컨텐츠 funnel — I5/I6/I3 모두 동일 매핑 사용 (cross-viz 일관성).
#   - ENTRY_COLORS    : 7개 유입 채널 — I7/I10 동일 매핑.
#   - AXIS_*/GRID/BG  : 차트 chrome 톤. config 마다 복붙하지 않도록 dict 로 묶음.
# ============================================================
ACCENT_VIOLET = "#A78BFA"        # primary brand accent
ACCENT_VIOLET_DEEP = "#7C3AED"   # gradient stop / hover
ACCENT_VIOLET_SOFT = "#C4B5FD"   # highlight / label

STATUS_DANGER = "#F43F5E"        # 이탈/위험
STATUS_WARN = "#FB923C"          # 경계
STATUS_NEUTRAL = "#FBBF24"       # 중간
STATUS_GOOD = "#34D399"          # 양호
STATUS_GREAT = "#10B981"         # 좋음

NEUTRAL_MUTED = "#64748B"

# 컨텐츠(funnel) 색 — 보라 accent 중심으로 analogous(인접 색조)+ 한 가지 contrast.
# I5(시간대별 진입) / I6(공유율 도넛) / I3(단계별 깔때기 row) 가 모두 같은 funnel→색 매핑.
FUNNEL_COLORS = {
    "relay_room_creation":     "#A78BFA",   # violet (brand)
    "flipbook_room_creation":  "#F472B6",   # pink
    "community_memo_posting":  "#38BDF8",   # sky
    "fortune_creation":        "#FBBF24",   # amber
    "gallery_save_share":      "#34D399",   # emerald
}

# 유입 채널 색 — I7(분포)/I10(시간대 추이) 동일.
ENTRY_COLORS = {
    "direct":   "#94A3B8",   # slate
    "search":   "#38BDF8",   # sky
    "social":   "#A78BFA",   # violet
    "qr":       "#FBBF24",   # amber
    "share":    "#F472B6",   # pink
    "campaign": "#34D399",   # emerald
    "unknown":  "#475569",   # dark slate
}

# Vega config 공통 chrome — axis/grid/title 색을 viz 마다 복붙하지 않도록.
VEGA_CHROME = {
    "background": "transparent",
    "view": {"stroke": None},
    "axis": {
        "labelColor": "#CBD5E1",
        "titleColor": "#E2E8F0",
        "gridColor": "#1E293B",        # 기존 #334155 보다 어둡게 — 그리드 노이즈 ↓
        "domainColor": "#334155",
        "tickColor": "#334155",
        "labelFontSize": 11,
        "titleFontSize": 12,
        "titleFontWeight": 500,
        "titlePadding": 8,
    },
    "legend": {
        "labelColor": "#CBD5E1",
        "titleColor": "#E2E8F0",
        "labelFontSize": 11,
        "symbolStrokeWidth": 0,
    },
    "title": {
        "color": "#F1F5F9",
        "subtitleColor": "#94A3B8",
        "fontSize": 14,
        "subtitleFontSize": 11,
        "anchor": "start",
        "offset": 8,
    },
    "header": {"labelColor": "#E2E8F0", "titleColor": "#E2E8F0"},
}

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


def section_header_markdown(heading, subtitle, accent=ACCENT_VIOLET):
    """섹션 헤더용 markdown — h2 제목 + 부제 두 줄.

    OSD 2.x 의 markdown_vis 는 markdown-it 을 html:false 로 초기화하므로 inline HTML
    (style 속성, <div>, <span> 등) 은 escape 되어 텍스트로 그대로 표시된다.
    그라데이션 바·색상 등 시각 장식은 markdown vis 에서 불가 — plain markdown 만 사용.
    accent 인자는 호출부 시그니처 호환용으로 받지만 무시.
    """
    del accent
    return f"## {heading}\n\n{subtitle}"


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
                    # fontSize 는 markdown 본문(p) 기준 px. h2 는 약 1.5x 로 scale.
                    # 13 → 부제 13px / 제목 ~20px — 사이드바 디자인 의도와 근접.
                    "fontSize": 13,
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
    """single chart vega-lite spec 을 조건부로 vconcat 단일 항목으로 wrap.

    배경:
    - OSD Vega plugin 은 single-view spec 에 NUMERIC width/height 가 있으면 autosize:"fit"
      을 자동 주입하면서 "width/height ignored" warning 을 띄운다.
    - 그 자동 주입은 spec 이 multi-view(facet/vconcat/hconcat/repeat) 면 건너뛰므로,
      vconcat 1-item 으로 wrap 하면 warning 회피.

    핵심 trade-off (vega-lite 공식 docs):
    - autosize 는 multi-view 에서 ignored. wrap 하면 outer autosize:fit 가 죽는다.
    - panel container 크기에 맞춰 fit 하려면 spec 이 single-view 여야 함.

    그래서 sizing 방식에 따라 wrap 여부를 분기:
    - "container" sizing (width/height 가 "container"): wrap 안 함. single-view 로
      autosize:fit 가 정상 동작해 panel CSS 크기에 맞춰 그려짐. 컨테이너 사이즈는
      numeric 이 아니라 plugin 의 auto-injection 도 발동 안 함 (warning 없음).
    - numeric sizing: wrap 함. fit 보다 chart 가 고정 px 로 그려지길 원하는 경우.
    """
    # container sizing 이면 wrap 안 함 — autosize:fit 가 panel container fit 하도록.
    if spec.get("width") == "container" or spec.get("height") == "container":
        return spec

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
        # 의미: 활성=중립(slate), 진입=brand violet, 완료=success green, 이탈=danger rose.
        "활성 세션":   NEUTRAL_MUTED,
        "진입":        ACCENT_VIOLET,
        "완료":        STATUS_GREAT,
        "이탈":        STATUS_DANGER,
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
    # 내부 title 제거 — autosize:fit + contains:padding 은 padding 만 contain 하고
    # title/legend 는 SVG 위로 별도 높이 추가됨 → panel container 보다 커져 scroll 발생.
    # OSD panel header 가 이미 attributes.title 을 표시하므로 viz 내부 title 은 중복.
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
            "axis": {"title": "완주율 (%)", "tickCount": 5},
        },
    },
    # y축 label 자리 확보 — 좌측 padding 충분히.
    "padding": {"left": 170, "right": 60, "top": 20, "bottom": 40},
    "layer": [
        # background track — 100% 기준선 (subtle하게 panel 안의 max-rail 보여줌).
        {
            "mark": {"type": "bar", "cornerRadiusEnd": 4, "color": "#1E293B",
                     "opacity": 0.5, "tooltip": False},
            "encoding": {
                "x": {"datum": 100, "type": "quantitative"},
            },
        },
        {
            "mark": {"type": "bar", "cornerRadiusEnd": 4, "tooltip": True},
            "encoding": {
                "color": {
                    "field": "rate",
                    "type": "quantitative",
                    # 3-stop muted gradient (rose → amber → emerald). 기존 5-stop rainbow 보다
                    # 부드럽고 modern dark 와 어울림. "위험/중간/양호" 의미만 살림.
                    "scale": {
                        "domain": [0, 50, 100],
                        "range": [STATUS_DANGER, STATUS_NEUTRAL, STATUS_GREAT],
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
                     "color": "#F1F5F9"},
            "encoding": {
                "text": {"field": "rate", "type": "quantitative", "format": ".1f"},
            },
        },
    ],
    # container 로 두면 vega-lite 가 panel CSS 영역 크기 그대로 사용 — 고정 px 로 두면
    # panel 보다 클 때 horizontal scroll 발생. autosize:fit 과 함께 두 축 모두 container.
    "width": "container",
    "height": "container",
    "config": VEGA_CHROME,
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
    # 내부 title 제거 — facet spec 도 title 이 SVG 상단에 추가 높이로 붙어 panel 초과 시
    # scroll 발생. attributes.title 이 OSD panel header 에 표시됨.
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
                "labelFontWeight": 500,
            },
        },
    },
    "spec": {
        # facet inner spec — width:"container" 로 panel CSS 폭을 따라가게.
        # 모든 row 가 동일 container 폭을 공유하므로 facet 에서도 동작. height 는 row 당
        # 고정 px 로 — 컨텐츠 5 개 × 100px = 500px + axes ~60px ≈ 560px, panel grid h=24
        # (~600px) 안에 fit. title 제거로 추가 ~40px 도 절약.
        "width": "container",
        "height": 100,
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
                "axis": {"title": "세션 수"},
            },
            # 막대 색을 funnel(=row) 별로 지정 — 같은 row 안에서는 동일 색으로 잔존량 차이를
            # bar length 가 단독으로 표현 (모던 dark 톤). cross-viz 일관성: I5/I6 도 같은 매핑.
            "color": {
                "field": "funnel_label",
                "type": "nominal",
                "scale": {
                    "domain": ["릴레이드로잉", "플립북", "커뮤니티 메모", "오늘의 운세", "갤러리·공유"],
                    "range": [
                        FUNNEL_COLORS["relay_room_creation"],
                        FUNNEL_COLORS["flipbook_room_creation"],
                        FUNNEL_COLORS["community_memo_posting"],
                        FUNNEL_COLORS["fortune_creation"],
                        FUNNEL_COLORS["gallery_save_share"],
                    ],
                },
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
    "config": VEGA_CHROME,
}
I3 = viz_vega(
    viz_id="vis-marketing-funnel-stages",
    title="[I3] 단계별 이탈 깔때기",
    description="컨텐츠별 단계 진행에 따른 잔존 세션. 막대가 급격히 짧아지는 단계가 약점.",
    spec=I3_SPEC,
)


# ============================================================
# I4: 컨텐츠 간 이동 흐름 (Vega-Lite heatmap)
# page_view 의 prev_path × path 를 "컨텐츠 카테고리" 단위로 묶어 매트릭스로 시각화.
# 60 개 path 매트릭스는 셀이 잘게 쪼개져 가독성 ↓ — URL 첫 segment 로 ~8 개 카테고리에
# 합쳐 셀을 크고 굵게. 같은 카테고리 내 이동(예: /flipbook/lobby → /flipbook/drawing)은
# 제외해서 "컨텐츠 간 이동" 만 남김.
#
# 마케팅 활용:
#   - "릴레이드로잉 끝나고 어디로 가는가 → 갤러리(공유 의도) vs 홈(이탈 직전)"
#   - "오늘의 운세 → 플립북" 처럼 cross-content 재참여 패턴 발견.
# ============================================================
# path 첫 segment → 한글 컨텐츠 라벨. {src} 자리에 datum.from_raw / datum.to_raw 치환.
# indexof(str, sub) === 0 → 해당 prefix 로 시작. 첫 매칭이 우선.
PATH_TO_CONTENT_EXPR = (
    "indexof({src}, '/relay-drawing') === 0 ? '릴레이드로잉' : "
    "indexof({src}, '/flipbook') === 0 ? '플립북' : "
    "indexof({src}, '/community') === 0 ? '커뮤니티' : "
    "indexof({src}, '/fortune') === 0 ? '오늘의 운세' : "
    "indexof({src}, '/gallery') === 0 ? '갤러리' : "
    "indexof({src}, '/share') === 0 ? '공유' : "
    "({src} === '/' || {src} === '/main' || {src} === '/home') ? '홈' : "
    "'기타'"
)
CONTENT_ORDER = [
    "홈", "릴레이드로잉", "플립북", "커뮤니티",
    "오늘의 운세", "갤러리", "공유", "기타",
]

I4_SPEC = {
    "$schema": "https://vega.github.io/schema/vega-lite/v5.json",
    "autosize": {"type": "fit", "contains": "padding", "resize": True},
    # 내부 title 제거 — autosize:fit 가 title 을 contain 못 해 panel 초과 → scroll.
    # OSD panel header 의 "[I4] 컨텐츠 간 이동 흐름" 으로 충분.
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
                        # composite size 200 — raw path 가 다양해도 transform 단계에서
                        # 8 개 카테고리로 collapse 되므로 넉넉히 받아 누락 방지.
                        "aggs": {
                            "pairs": {
                                "composite": {
                                    "size": 200,
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
        {"calculate": "datum['key']['from_path']", "as": "from_raw"},
        {"calculate": "datum['key']['to_path']", "as": "to_raw"},
        {"calculate": "datum['doc_count']", "as": "count_raw"},
        # path → 컨텐츠 카테고리. URL 의 첫 segment 로 묶어서 ~8 개로 collapse.
        {"calculate": PATH_TO_CONTENT_EXPR.format(src="datum.from_raw"), "as": "from"},
        {"calculate": PATH_TO_CONTENT_EXPR.format(src="datum.to_raw"), "as": "to"},
        # 같은 컨텐츠 내 이동(릴레이 lobby→drawing 등) 제외 → "컨텐츠 간" 흐름만.
        {"filter": "datum.from != datum.to"},
        # 카테고리 매핑으로 합쳐진 페어 재집계.
        {"aggregate": [{"op": "sum", "field": "count_raw", "as": "count"}],
         "groupby": ["from", "to"]},
        {"filter": "datum.count > 0"},
        # 셀 안 텍스트 색 분기 기준 — 전체 max 대비 50% 이상이면 흰색, 아니면 어두운 색.
        {"joinaggregate": [{"op": "max", "field": "count", "as": "count_max"}]},
    ],
    "padding": {"top": 20, "right": 24, "bottom": 60, "left": 110},
    "width": "container",
    "height": "container",
    "layer": [
        {
            "mark": {"type": "rect", "tooltip": True, "stroke": "#0F172A", "strokeWidth": 2},
            "encoding": {
                "x": {
                    "field": "from",
                    "type": "nominal",
                    "sort": CONTENT_ORDER,
                    "axis": {
                        "title": "이전 컨텐츠",
                        "labelAngle": -15,
                        "labelFontSize": 13,
                        "titleFontSize": 13,
                        "titlePadding": 12,
                    },
                },
                "y": {
                    "field": "to",
                    "type": "nominal",
                    "sort": CONTENT_ORDER,
                    "axis": {
                        "title": "다음 컨텐츠",
                        "labelFontSize": 13,
                        "titleFontSize": 13,
                        "titlePadding": 12,
                    },
                },
                "color": {
                    "field": "count",
                    "type": "quantitative",
                    "scale": {"scheme": "purples"},
                    # legend 제거 — vega-lite autosize:fit 가 legend 를 contain 못 함 (right/
                    # bottom 어디든 SVG 영역 밖으로 추가됨 → scroll). 셀 안 숫자 + 색 진하기
                    # 의 자명한 의미(짙음 = 이동 많음)로 legend 없이도 해석 가능.
                    "legend": None,
                },
                "tooltip": [
                    {"field": "from", "type": "nominal", "title": "이전 컨텐츠"},
                    {"field": "to", "type": "nominal", "title": "다음 컨텐츠"},
                    {"field": "count", "type": "quantitative", "title": "이동 세션 수"},
                ],
            },
        },
        # 셀 안에 숫자 — 진한 셀에는 흰색, 옅은 셀에는 어두운 색으로 contrast 확보.
        {
            "mark": {"type": "text", "fontSize": 14, "fontWeight": 600},
            "encoding": {
                "x": {"field": "from", "type": "nominal", "sort": CONTENT_ORDER},
                "y": {"field": "to", "type": "nominal", "sort": CONTENT_ORDER},
                "text": {"field": "count", "type": "quantitative"},
                "color": {
                    "condition": {
                        "test": "datum.count > datum.count_max * 0.5",
                        "value": "#F1F5F9",
                    },
                    "value": "#1E293B",
                },
            },
        },
    ],
    "config": VEGA_CHROME,
}
I4 = viz_vega(
    viz_id="vis-marketing-flow-heatmap",
    title="[I4] 컨텐츠 간 이동 흐름",
    description="컨텐츠 카테고리 단위로 이전 → 다음 이동 빈도. 같은 컨텐츠 내 이동 제외, 셀 안 숫자 = 이동 세션 수.",
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
I5 = viz_classic(
    viz_id="vis-marketing-entry-timeline",
    title="[I5] 시간대별 진입 추이",
    description="funnel_started 를 컨텐츠별로 1시간 단위 누적. 피크 타임 + 컨텐츠 mix.",
    query="service:client-web AND event_name:funnel_started",
    # cross-viz 일관성: I3/I6 와 같은 funnel→색 매핑.
    colors=FUNNEL_COLORS,
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
        "subtitle": "컨텐츠별 결과 도달 대비 공유 없이 이탈하지 않은 비율 (근사치).",
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
    "mark": {"type": "arc", "innerRadius": 50, "outerRadius": 90, "tooltip": True,
             "stroke": "#0F172A", "strokeWidth": 2},
    "encoding": {
        "theta": {"field": "shared", "type": "quantitative"},
        "color": {
            "field": "funnel_label",
            "type": "nominal",
            # domain 명시 → I3/I5 와 동일한 funnel→색 매핑.
            "scale": {
                "domain": ["릴레이드로잉", "플립북", "커뮤니티 메모", "오늘의 운세", "갤러리·공유"],
                "range": [
                    FUNNEL_COLORS["relay_room_creation"],
                    FUNNEL_COLORS["flipbook_room_creation"],
                    FUNNEL_COLORS["community_memo_posting"],
                    FUNNEL_COLORS["fortune_creation"],
                    FUNNEL_COLORS["gallery_save_share"],
                ],
            },
            "legend": {
                "title": None,
                # vega-lite docs: fit autosize 는 legend space 부족 시 클리핑.
                # orient:"none" + ExprRef 로 view width 에 비례한 절대 좌표 — panel container
                # 크기가 어떻든 도넛(outerRadius 90) 우측에 안전하게 배치.
                "orient": "none",
                "legendX": {"expr": "(width / 2) + 110"},
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
    "config": VEGA_CHROME,
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
    "mark": {"type": "arc", "innerRadius": 50, "outerRadius": 90, "tooltip": True,
             "stroke": "#0F172A", "strokeWidth": 2},
    "encoding": {
        "theta": {"field": "count", "type": "quantitative"},
        "color": {
            "field": "entry_label",
            "type": "nominal",
            # I10 (시간대별 유입원) 과 동일 매핑 — ENTRY_COLORS 의 raw key 와 한글 라벨 매핑.
            "scale": {
                "domain": ["직접 접속", "검색", "SNS", "QR 코드",
                           "공유 링크", "캠페인", "알 수 없음"],
                "range": [
                    ENTRY_COLORS["direct"],
                    ENTRY_COLORS["search"],
                    ENTRY_COLORS["social"],
                    ENTRY_COLORS["qr"],
                    ENTRY_COLORS["share"],
                    ENTRY_COLORS["campaign"],
                    ENTRY_COLORS["unknown"],
                ],
            },
            "legend": {
                "title": None,
                # vega-lite docs: fit autosize 는 legend space 부족 시 클리핑.
                # orient:"none" + ExprRef 로 view width 에 비례한 절대 좌표 — panel container
                # 크기가 어떻든 도넛(outerRadius 90) 우측에 안전하게 배치.
                "orient": "none",
                "legendX": {"expr": "(width / 2) + 110"},
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
    "config": VEGA_CHROME,
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
    "mark": {"type": "arc", "innerRadius": 50, "outerRadius": 90, "tooltip": True,
             "stroke": "#0F172A", "strokeWidth": 2},
    "encoding": {
        "theta": {"field": "count", "type": "quantitative"},
        "color": {
            "field": "sns_label",
            "type": "nominal",
            # SNS 브랜드 색 유지 — 사용자 인식 ↑. 단 채도/명도 살짝 낮춰 다크 톤과 어울리게.
            "scale": {
                "domain": ["인스타그램", "트위터", "카카오톡", "페이스북", "링크드인", "기타"],
                "range": ["#E11D74", "#1DA1F2", "#FEE500", "#1877F2", "#0A66C2", NEUTRAL_MUTED],
            },
            "legend": {
                "title": None,
                # vega-lite docs: fit autosize 는 legend space 부족 시 클리핑.
                # orient:"none" + ExprRef 로 view width 에 비례한 절대 좌표 — panel container
                # 크기가 어떻든 도넛(outerRadius 90) 우측에 안전하게 배치.
                "orient": "none",
                "legendX": {"expr": "(width / 2) + 110"},
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
    "config": VEGA_CHROME,
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
    # 내부 title 제거 (panel header 와 중복, scroll 유발).
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
    # 막대 안에 가로 violet 그라데이션 — 왼쪽 deep → 오른쪽 soft. 긴 막대일수록 그라데이션이
    # 길게 펴져 시각적으로 강조됨.
    "mark": {
        "type": "bar",
        "cornerRadiusEnd": 4,
        "tooltip": True,
        "color": {
            "x1": 0, "y1": 0, "x2": 1, "y2": 0,
            "gradient": "linear",
            "stops": [
                {"offset": 0,   "color": ACCENT_VIOLET_DEEP},
                {"offset": 1,   "color": ACCENT_VIOLET_SOFT},
            ],
        },
    },
    "encoding": {
        "y": {
            "field": "path",
            "type": "nominal",
            "sort": "-x",
            "axis": {"title": None, "labelLimit": 200},
        },
        "x": {
            "field": "avg_sec",
            "type": "quantitative",
            "axis": {"title": "평균 체류 시간 (초)"},
        },
        "tooltip": [
            {"field": "path", "type": "nominal", "title": "화면"},
            {"field": "avg_sec", "type": "quantitative", "title": "평균 (초)", "format": ".1f"},
            {"field": "n", "type": "quantitative", "title": "샘플 수"},
        ],
    },
    "autosize": {"type": "fit", "contains": "padding", "resize": True},
    "width": "container",
    "height": "container",
    "config": VEGA_CHROME,
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
I10 = viz_classic(
    viz_id="vis-marketing-entry-by-channel-timeline",
    title="[I10] 시간대별 유입원 추이",
    description="landing_source_detected 의 metadata.entry_type 1h 단위 누적. 시간대별 채널 mix.",
    query="service:client-web AND event_name:landing_source_detected",
    # cross-viz 일관성: I7(유입 경로 비율 도넛) 과 같은 channel→색 매핑.
    colors=ENTRY_COLORS,
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
        # 방문자 = brand violet, 완주 = success green.
        "방문자":      ACCENT_VIOLET,
        "완주 방문자":  STATUS_GREAT,
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
    # 내부 title 제거 (panel header 와 중복, scroll 유발).
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
            "axis": {"title": "체류 시간 구간", "labelAngle": 0},
        },
        "y": {
            "field": "n",
            "type": "quantitative",
            "axis": {"title": "세션 수"},
        },
        "color": {
            "field": "bucket_label",
            "type": "nominal",
            # 3-stop muted (I2 와 동일 의미 체계) — 짧음(나쁨) → 보통 → 김(좋음).
            "scale": {
                "domain": ["0-5초", "5-10초", "10-30초", "30-60초", "60초+"],
                "range": [STATUS_DANGER, STATUS_WARN, STATUS_NEUTRAL, STATUS_GOOD, STATUS_GREAT],
            },
            "legend": None,
        },
        "tooltip": [
            {"field": "bucket_label", "type": "nominal", "title": "구간"},
            {"field": "n", "type": "quantitative", "title": "세션 수"},
        ],
    },
    "config": VEGA_CHROME,
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
    # 내부 title 제거 (panel header 와 중복, scroll 유발).
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
    # rose 그라데이션 — 이탈은 위험 신호. 단일 fill 보다 그라데이션이 막대 무게감을 줌.
    "mark": {
        "type": "bar",
        "cornerRadiusEnd": 4,
        "tooltip": True,
        "color": {
            "x1": 0, "y1": 0, "x2": 1, "y2": 0,
            "gradient": "linear",
            "stops": [
                {"offset": 0, "color": "#9F1239"},   # rose-900
                {"offset": 1, "color": STATUS_DANGER},
            ],
        },
    },
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
            "axis": {"title": "평균 (초)"},
        },
        "tooltip": [
            {"field": "label", "type": "nominal", "title": "이탈 유형"},
            {"field": "avg_sec", "type": "quantitative", "title": "평균 (초)", "format": ".1f"},
            {"field": "n", "type": "quantitative", "title": "이탈 건수"},
        ],
    },
    "config": VEGA_CHROME,
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
    markdown=section_header_markdown(
        heading="오늘의 KPI",
        subtitle="오늘 들어온 사용자와 핵심 행동의 완료·이탈 현황",
    ),
)

HEADER_CHANNEL = viz_markdown(
    viz_id="vis-marketing-header-channel",
    title="섹션 헤더 — 채널",
    markdown=section_header_markdown(
        heading="유입 채널",
        subtitle="어떤 경로로 들어오는지 — 직접·검색·SNS·공유·QR·캠페인",
    ),
)

HEADER_CONTENT = viz_markdown(
    viz_id="vis-marketing-header-content",
    title="섹션 헤더 — 컨텐츠",
    markdown=section_header_markdown(
        heading="컨텐츠",
        subtitle="컨텐츠별 완주율과 시간대별 진입 추이",
    ),
)

HEADER_FLOW = viz_markdown(
    viz_id="vis-marketing-header-flow",
    title="섹션 헤더 — 단계·흐름",
    markdown=section_header_markdown(
        heading="단계 · 화면 흐름",
        subtitle="어느 단계에서 사용자가 빠지고, 한 화면 다음에 어디로 가는가",
    ),
)

HEADER_RETENTION = viz_markdown(
    viz_id="vis-marketing-header-retention",
    title="섹션 헤더 — 만족도·이탈",
    markdown=section_header_markdown(
        heading="체류 · 이탈",
        subtitle="얼마나 오래 머물고, 어느 단계에서 얼마나 빨리 떠나는가",
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
    # 섹션 1: 오늘의 KPI
    {"vis_id": HEADER_OVERVIEW["id"], "panel_id": "h1",
     "grid": {"x": 0, "y": 0, "w": 48, "h": 4}},
    {"vis_id": I1["id"],  "panel_id": "1",
     "grid": {"x": 0,  "y": 4,  "w": 24, "h": 10}},
    {"vis_id": I11["id"], "panel_id": "2",
     "grid": {"x": 24, "y": 4,  "w": 24, "h": 10}},

    # 섹션 2: 유입 채널 (도넛 row 높이 ↑ — legend 자리 + 시각적 숨통)
    {"vis_id": HEADER_CHANNEL["id"], "panel_id": "h2",
     "grid": {"x": 0, "y": 14, "w": 48, "h": 4}},
    {"vis_id": I7["id"],  "panel_id": "3",
     "grid": {"x": 0,  "y": 18, "w": 24, "h": 18}},
    {"vis_id": I10["id"], "panel_id": "4",
     "grid": {"x": 24, "y": 18, "w": 24, "h": 18}},
    {"vis_id": I8["id"],  "panel_id": "5",
     "grid": {"x": 0,  "y": 36, "w": 24, "h": 18}},
    {"vis_id": I6["id"],  "panel_id": "6",
     "grid": {"x": 24, "y": 36, "w": 24, "h": 18}},

    # 섹션 3: 컨텐츠
    {"vis_id": HEADER_CONTENT["id"], "panel_id": "h3",
     "grid": {"x": 0, "y": 54, "w": 48, "h": 4}},
    {"vis_id": I2["id"],  "panel_id": "7",
     "grid": {"x": 0,  "y": 58, "w": 24, "h": 16}},
    {"vis_id": I5["id"],  "panel_id": "8",
     "grid": {"x": 24, "y": 58, "w": 24, "h": 16}},

    # 섹션 4: 단계·화면 흐름
    {"vis_id": HEADER_FLOW["id"], "panel_id": "h4",
     "grid": {"x": 0, "y": 74, "w": 48, "h": 4}},
    {"vis_id": I3["id"],  "panel_id": "9",
     "grid": {"x": 0,  "y": 78, "w": 48, "h": 24}},
    {"vis_id": I4["id"],  "panel_id": "10",
     "grid": {"x": 0,  "y": 102, "w": 48, "h": 22}},

    # 섹션 5: 체류·이탈
    {"vis_id": HEADER_RETENTION["id"], "panel_id": "h5",
     "grid": {"x": 0, "y": 124, "w": 48, "h": 4}},
    {"vis_id": I9["id"],  "panel_id": "11",
     "grid": {"x": 0,  "y": 128, "w": 24, "h": 18}},
    {"vis_id": I12["id"], "panel_id": "12",
     "grid": {"x": 24, "y": 128, "w": 24, "h": 18}},
    {"vis_id": I13["id"], "panel_id": "13",
     "grid": {"x": 0,  "y": 146, "w": 48, "h": 14}},
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
    import sys
    # Windows cp949 콘솔에서 em-dash / 한글 자모 출력 시 UnicodeEncodeError 회피.
    # 파일 출력(UTF-8)에는 영향 없음 — stdout 만 UTF-8 로 재설정.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

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
