#!/usr/bin/env python3
"""Synthetic benchmark for the infinite canvas performance optimization.

The benchmark mirrors the repository's Redis room-state shape and WebSocket
event payload shape so before/after numbers can be reproduced without relying
on a live Redis dataset.
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import statistics
import time
from datetime import datetime, timedelta


PAGE_SIZE = 20
ROOM_COUNTS = (100, 1_000, 5_000, 10_000)
ELEMENT_COUNTS = (100, 1_000, 5_000)
CHART_WIDTH = 960
CHART_HEIGHT = 540


def build_room_state(index: int, base_time: datetime) -> dict:
    created_at = base_time - timedelta(seconds=index)
    room_code = f"IC{index:06d}"
    user_uuid = f"550e8400-e29b-41d4-a716-{index:012d}"[-36:]
    return {
        "roomCode": room_code,
        "status": "CLOSED" if index % 10 == 9 else "ACTIVE",
        "hostUserUuid": user_uuid,
        "participants": [
            {
                "userUuid": user_uuid,
                "nickname": f"user-{index}",
                "color": "#72DDF7",
                "avatarUrl": None,
                "host": True,
                "connected": True,
                "joinedAt": created_at.isoformat(),
                "lastConnectedAt": created_at.isoformat(),
            }
        ],
        "elements": [
            {"id": f"element-{index}-{element_index}", "type": "line", "points": list(range(10))}
            for element_index in range(2)
        ],
        "operations": [
            {
                "operationId": f"op-{index}-{operation_index}",
                "clientOperationId": f"client-op-{index}-{operation_index}",
                "operationType": "CREATE_ELEMENT",
                "elementId": f"element-{index}-{operation_index}",
                "payload": None,
                "userUuid": user_uuid,
                "revision": operation_index,
                "occurredAt": created_at.isoformat(),
            }
            for operation_index in range(2)
        ],
        "locks": {},
        "cursors": {},
        "viewport": None,
        "maxParticipants": 6,
        "revision": 2,
        "createdAt": created_at.isoformat(),
        "updatedAt": created_at.isoformat(),
        "closedAt": created_at.isoformat() if index % 10 == 9 else None,
    }


def build_room_dataset(room_count: int) -> tuple[list[str], list[str]]:
    base_time = datetime(2026, 5, 17, 12, 0, 0)
    states = [build_room_state(index, base_time) for index in range(room_count)]
    serialized_by_scan_order = [json.dumps(state, separators=(",", ":")) for state in states]
    active_index = [
        json.dumps(state, separators=(",", ":"))
        for state in sorted(
            (state for state in states if state["status"] != "CLOSED"),
            key=lambda value: value["createdAt"],
            reverse=True,
        )
    ]
    return serialized_by_scan_order, active_index


def before_active_room_lookup(serialized_states: list[str]) -> list[dict]:
    states = []
    for serialized_state in serialized_states:
        state = json.loads(serialized_state)
        if state["status"] != "CLOSED":
            states.append(state)
    states.sort(key=lambda value: (value["createdAt"], value["roomCode"]), reverse=True)
    return states[:PAGE_SIZE]


def after_active_room_lookup(active_index: list[str]) -> list[dict]:
    return [json.loads(serialized_state) for serialized_state in active_index[:PAGE_SIZE]]


def build_state_event(element_count: int) -> tuple[str, str]:
    user_uuid = "550e8400-e29b-41d4-a716-446655440000"
    participant = {
        "userUuid": user_uuid,
        "nickname": "망고",
        "color": "#72DDF7",
        "avatarUrl": None,
        "host": True,
        "connected": True,
        "joinedAt": "2026-05-17T12:00:00",
        "lastConnectedAt": "2026-05-17T12:00:00",
    }
    elements = [
        {"id": f"element-{index}", "type": "line", "points": list(range(60))}
        for index in range(element_count)
    ]
    operations = [
        {
            "operationId": f"op-{index}",
            "clientOperationId": f"client-op-{index}",
            "operationType": "CREATE_ELEMENT",
            "elementId": f"element-{index}",
            "element": elements[index % element_count],
            "payload": None,
            "userUuid": user_uuid,
            "revision": index,
            "occurredAt": "2026-05-17T12:00:00",
        }
        for index in range(100)
    ]
    before_event = {
        "type": "PARTICIPANT_CONNECTED",
        "roomCode": "IC000001",
        "data": {
            "roomCode": "IC000001",
            "status": "ACTIVE",
            "hostUserUuid": user_uuid,
            "participants": [participant],
            "changedParticipant": participant,
            "elements": elements,
            "operations": operations,
            "locks": {},
            "viewport": None,
            "maxParticipants": 6,
            "revision": 100,
            "createdAt": "2026-05-17T12:00:00",
            "updatedAt": "2026-05-17T12:00:00",
        },
    }
    after_event = {
        "type": "PARTICIPANT_CONNECTED",
        "roomCode": "IC000001",
        "data": {
            "roomCode": "IC000001",
            "status": "ACTIVE",
            "hostUserUuid": user_uuid,
            "participants": [participant],
            "changedParticipant": participant,
            "maxParticipants": 6,
            "revision": 100,
            "updatedAt": "2026-05-17T12:00:00",
        },
    }
    return (
        json.dumps(before_event, ensure_ascii=False, separators=(",", ":")),
        json.dumps(after_event, ensure_ascii=False, separators=(",", ":")),
    )


def measure_ms(func, *args, iterations: int) -> list[float]:
    samples = []
    for _ in range(iterations):
        started = time.perf_counter()
        func(*args)
        samples.append((time.perf_counter() - started) * 1000)
    return samples


def percentile(samples: list[float], ratio: float) -> float:
    sorted_samples = sorted(samples)
    index = min(len(sorted_samples) - 1, round((len(sorted_samples) - 1) * ratio))
    return sorted_samples[index]


def summarize(samples: list[float]) -> tuple[float, float]:
    return statistics.mean(samples), percentile(samples, 0.95)


def run_lookup_benchmark(iterations: int) -> list[dict]:
    rows = []
    for room_count in ROOM_COUNTS:
        serialized_states, active_index = build_room_dataset(room_count)
        before_samples = measure_ms(before_active_room_lookup, serialized_states, iterations=iterations)
        after_samples = measure_ms(after_active_room_lookup, active_index, iterations=iterations)
        before_avg, before_p95 = summarize(before_samples)
        after_avg, after_p95 = summarize(after_samples)
        rows.append(
            {
                "room_count": room_count,
                "before_avg_ms": before_avg,
                "before_p95_ms": before_p95,
                "after_avg_ms": after_avg,
                "after_p95_ms": after_p95,
                "improvement_ratio": before_p95 / after_p95 if after_p95 else 0,
                "before_deserialized": room_count,
                "after_deserialized": PAGE_SIZE,
            }
        )
    return rows


def run_payload_benchmark(iterations: int) -> list[dict]:
    rows = []
    for element_count in ELEMENT_COUNTS:
        before_event, after_event = build_state_event(element_count)
        before_samples = measure_ms(json.loads, before_event, iterations=iterations)
        after_samples = measure_ms(json.loads, after_event, iterations=iterations)
        before_avg, before_p95 = summarize(before_samples)
        after_avg, after_p95 = summarize(after_samples)
        rows.append(
            {
                "element_count": element_count,
                "before_bytes": len(before_event.encode("utf-8")),
                "after_bytes": len(after_event.encode("utf-8")),
                "reduction_percent": (1 - len(after_event.encode("utf-8")) / len(before_event.encode("utf-8")))
                * 100,
                "before_parse_p95_ms": before_p95,
                "after_parse_p95_ms": after_p95,
                "parse_improvement_ratio": before_p95 / after_p95 if after_p95 else 0,
            }
        )
    return rows


def print_markdown_table(rows: list[dict], columns: list[str]) -> None:
    print("| " + " | ".join(columns) + " |")
    print("| " + " | ".join(["---"] * len(columns)) + " |")
    for row in rows:
        values = []
        for column in columns:
            value = row[column]
            if isinstance(value, float):
                values.append(f"{value:.2f}")
            else:
                values.append(f"{value:,}" if isinstance(value, int) else str(value))
        print("| " + " | ".join(values) + " |")


def fmt_number(value: float) -> str:
    if value >= 100:
        return f"{value:.0f}"
    if value >= 10:
        return f"{value:.1f}"
    return f"{value:.2f}"


def svg_text(x: float, y: float, text: str, size: int = 16, weight: int = 400, anchor: str = "middle",
    color: str = "#243142") -> str:
    return (
        f'<text x="{x:.1f}" y="{y:.1f}" text-anchor="{anchor}" font-size="{size}" '
        f'font-weight="{weight}" fill="{color}" font-family="Arial, sans-serif">{text}</text>'
    )


def grouped_bar_chart_svg(title: str, labels: list[str], before_values: list[float], after_values: list[float],
    y_label: str, before_label: str = "Before", after_label: str = "After") -> str:
    margin_left = 96
    margin_right = 52
    margin_top = 84
    margin_bottom = 96
    plot_width = CHART_WIDTH - margin_left - margin_right
    plot_height = CHART_HEIGHT - margin_top - margin_bottom
    max_value = max(before_values + after_values)
    y_max = nice_axis_max(max_value)
    group_width = plot_width / len(labels)
    bar_width = min(72, group_width * 0.28)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{CHART_WIDTH}" height="{CHART_HEIGHT}" viewBox="0 0 {CHART_WIDTH} {CHART_HEIGHT}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        svg_text(CHART_WIDTH / 2, 42, title, size=26, weight=700, color="#111827"),
        svg_text(28, margin_top + plot_height / 2, y_label, size=15, anchor="middle", color="#475569")
        .replace("<text ", '<text transform="rotate(-90 28 %.1f)" ' % (margin_top + plot_height / 2), 1),
    ]

    for tick in range(6):
        value = y_max * tick / 5
        y = margin_top + plot_height - plot_height * value / y_max
        parts.append(f'<line x1="{margin_left}" y1="{y:.1f}" x2="{CHART_WIDTH - margin_right}" y2="{y:.1f}" stroke="#e5e7eb" stroke-width="1"/>')
        parts.append(svg_text(margin_left - 14, y + 5, fmt_number(value), size=13, anchor="end", color="#64748b"))

    parts.append(f'<line x1="{margin_left}" y1="{margin_top + plot_height}" x2="{CHART_WIDTH - margin_right}" y2="{margin_top + plot_height}" stroke="#334155" stroke-width="1.5"/>')

    for index, label in enumerate(labels):
        center_x = margin_left + group_width * index + group_width / 2
        before_height = plot_height * before_values[index] / y_max
        after_height = plot_height * after_values[index] / y_max
        before_x = center_x - bar_width - 5
        after_x = center_x + 5
        before_y = margin_top + plot_height - before_height
        after_y = margin_top + plot_height - after_height
        parts.append(f'<rect x="{before_x:.1f}" y="{before_y:.1f}" width="{bar_width:.1f}" height="{before_height:.1f}" rx="4" fill="#ef4444"/>')
        parts.append(f'<rect x="{after_x:.1f}" y="{after_y:.1f}" width="{bar_width:.1f}" height="{after_height:.1f}" rx="4" fill="#2563eb"/>')
        parts.append(svg_text(before_x + bar_width / 2, before_y - 8, fmt_number(before_values[index]), size=12,
            color="#991b1b"))
        parts.append(svg_text(after_x + bar_width / 2, after_y - 8, fmt_number(after_values[index]), size=12,
            color="#1e3a8a"))
        parts.append(svg_text(center_x, margin_top + plot_height + 28, label, size=14, color="#334155"))

    legend_y = CHART_HEIGHT - 34
    parts.extend([
        f'<rect x="{CHART_WIDTH / 2 - 128:.1f}" y="{legend_y - 13}" width="18" height="18" rx="4" fill="#ef4444"/>',
        svg_text(CHART_WIDTH / 2 - 98, legend_y + 2, before_label, size=14, anchor="start"),
        f'<rect x="{CHART_WIDTH / 2 + 20:.1f}" y="{legend_y - 13}" width="18" height="18" rx="4" fill="#2563eb"/>',
        svg_text(CHART_WIDTH / 2 + 50, legend_y + 2, after_label, size=14, anchor="start"),
        "</svg>",
    ])
    return "\n".join(parts)


def nice_axis_max(value: float) -> float:
    if value <= 0:
        return 1
    exponent = math.floor(math.log10(value))
    fraction = value / 10**exponent
    if fraction <= 1:
        nice_fraction = 1
    elif fraction <= 2:
        nice_fraction = 2
    elif fraction <= 5:
        nice_fraction = 5
    else:
        nice_fraction = 10
    return nice_fraction * 10**exponent


def write_charts(output_dir: Path, lookup_rows: list[dict], payload_rows: list[dict]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    lookup_labels = [f"{row['room_count']:,}" for row in lookup_rows]
    payload_labels = [f"{row['element_count']:,}" for row in payload_rows]

    (output_dir / "infinite-canvas-active-room-p95.svg").write_text(
        grouped_bar_chart_svg(
            "Active Room Lookup p95 Latency",
            lookup_labels,
            [row["before_p95_ms"] for row in lookup_rows],
            [row["after_p95_ms"] for row in lookup_rows],
            "p95 ms",
        ),
        encoding="utf-8",
    )
    (output_dir / "infinite-canvas-active-room-p95-ko.svg").write_text(
        grouped_bar_chart_svg(
            "활성 방 목록 조회 p95 지연 시간",
            lookup_labels,
            [row["before_p95_ms"] for row in lookup_rows],
            [row["after_p95_ms"] for row in lookup_rows],
            "p95 ms",
            before_label="개선 전",
            after_label="개선 후",
        ),
        encoding="utf-8",
    )
    (output_dir / "infinite-canvas-deserialize-count.svg").write_text(
        grouped_bar_chart_svg(
            "JSON Deserialize Count Per Lookup",
            lookup_labels,
            [row["before_deserialized"] for row in lookup_rows],
            [row["after_deserialized"] for row in lookup_rows],
            "count",
        ),
        encoding="utf-8",
    )
    (output_dir / "infinite-canvas-deserialize-count-ko.svg").write_text(
        grouped_bar_chart_svg(
            "목록 조회 1회당 JSON 역직렬화 개수",
            lookup_labels,
            [row["before_deserialized"] for row in lookup_rows],
            [row["after_deserialized"] for row in lookup_rows],
            "개수",
            before_label="개선 전",
            after_label="개선 후",
        ),
        encoding="utf-8",
    )
    (output_dir / "infinite-canvas-websocket-payload.svg").write_text(
        grouped_bar_chart_svg(
            "Participant Event Payload Size",
            payload_labels,
            [row["before_bytes"] / 1024 for row in payload_rows],
            [row["after_bytes"] / 1024 for row in payload_rows],
            "KB",
        ),
        encoding="utf-8",
    )
    (output_dir / "infinite-canvas-websocket-payload-ko.svg").write_text(
        grouped_bar_chart_svg(
            "참여자 이벤트 페이로드 크기",
            payload_labels,
            [row["before_bytes"] / 1024 for row in payload_rows],
            [row["after_bytes"] / 1024 for row in payload_rows],
            "KB",
            before_label="개선 전",
            after_label="개선 후",
        ),
        encoding="utf-8",
    )
    (output_dir / "infinite-canvas-json-parse-p95.svg").write_text(
        grouped_bar_chart_svg(
            "Participant Event JSON Parse p95",
            payload_labels,
            [row["before_parse_p95_ms"] for row in payload_rows],
            [row["after_parse_p95_ms"] for row in payload_rows],
            "p95 ms",
        ),
        encoding="utf-8",
    )
    (output_dir / "infinite-canvas-json-parse-p95-ko.svg").write_text(
        grouped_bar_chart_svg(
            "참여자 이벤트 JSON 파싱 p95",
            payload_labels,
            [row["before_parse_p95_ms"] for row in payload_rows],
            [row["after_parse_p95_ms"] for row in payload_rows],
            "p95 ms",
            before_label="개선 전",
            after_label="개선 후",
        ),
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--iterations", type=int, default=10)
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()

    lookup_rows = run_lookup_benchmark(args.iterations)
    payload_rows = run_payload_benchmark(args.iterations)
    if args.output_dir:
        write_charts(args.output_dir, lookup_rows, payload_rows)

    print("## active-room-lookup")
    print_markdown_table(
        lookup_rows,
        [
            "room_count",
            "before_avg_ms",
            "before_p95_ms",
            "after_avg_ms",
            "after_p95_ms",
            "improvement_ratio",
            "before_deserialized",
            "after_deserialized",
        ],
    )
    print()
    print("## websocket-payload")
    print_markdown_table(
        payload_rows,
        [
            "element_count",
            "before_bytes",
            "after_bytes",
            "reduction_percent",
            "before_parse_p95_ms",
            "after_parse_p95_ms",
            "parse_improvement_ratio",
        ],
    )


if __name__ == "__main__":
    main()
