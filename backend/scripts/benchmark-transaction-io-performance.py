#!/usr/bin/env python3
"""Synthetic capacity model for transaction-scoped external I/O.

This script compares how long one HikariCP connection can be held when a
Spring transaction spans external calls versus when the transaction is narrowed
to database reads/writes only. It is intentionally deterministic: the DB and
external latency values are documented scenario inputs, not production traces.
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path


POOL_SIZE = 10
CHART_WIDTH = 960
CHART_HEIGHT = 540

SCENARIOS = (
    {
        "workflow": "fortune-create-success",
        "label_en": "Fortune create\n1.5s GMS",
        "label_ko": "운세 생성\nGMS 1.5초",
        "db_ms": 24.0,
        "external_ms": 1500.0,
    },
    {
        "workflow": "fortune-create-read-timeout",
        "label_en": "Fortune create\n30s timeout",
        "label_ko": "운세 생성\n30초 타임아웃",
        "db_ms": 24.0,
        "external_ms": 30000.0,
    },
    {
        "workflow": "fortune-create-max-retry-timeout",
        "label_en": "Fortune create\n3 retries",
        "label_ko": "운세 생성\n3회 재시도",
        "db_ms": 24.0,
        "external_ms": 90000.0,
    },
    {
        "workflow": "inquiry-reply-smtp",
        "label_en": "Inquiry reply\n1.2s SMTP",
        "label_ko": "문의 답변\nSMTP 1.2초",
        "db_ms": 12.0,
        "external_ms": 1200.0,
    },
)


def run_model(pool_size: int) -> list[dict]:
    rows = []
    for scenario in SCENARIOS:
        before_hold_ms = scenario["db_ms"] + scenario["external_ms"]
        after_hold_ms = scenario["db_ms"]
        before_max_rps = pool_size * 1000 / before_hold_ms
        after_max_rps = pool_size * 1000 / after_hold_ms
        rows.append(
            {
                **scenario,
                "before_hold_ms": before_hold_ms,
                "after_hold_ms": after_hold_ms,
                "reduction_percent": (1 - after_hold_ms / before_hold_ms) * 100,
                "before_conn_seconds_per_100": before_hold_ms * 100 / 1000,
                "after_conn_seconds_per_100": after_hold_ms * 100 / 1000,
                "before_max_rps": before_max_rps,
                "after_max_rps": after_max_rps,
                "throughput_ratio": after_max_rps / before_max_rps,
            }
        )
    return rows


def print_markdown_table(rows: list[dict]) -> None:
    columns = [
        "workflow",
        "before_hold_ms",
        "after_hold_ms",
        "reduction_percent",
        "before_conn_seconds_per_100",
        "after_conn_seconds_per_100",
        "before_max_rps",
        "after_max_rps",
        "throughput_ratio",
    ]
    print("| " + " | ".join(columns) + " |")
    print("| " + " | ".join(["---"] * len(columns)) + " |")
    for row in rows:
        values = []
        for column in columns:
            value = row[column]
            if isinstance(value, float):
                values.append(f"{value:.2f}")
            else:
                values.append(str(value))
        print("| " + " | ".join(values) + " |")


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


def fmt_number(value: float) -> str:
    if value >= 1000:
        return f"{value:,.0f}"
    if value >= 100:
        return f"{value:.0f}"
    if value >= 10:
        return f"{value:.1f}"
    return f"{value:.2f}"


def svg_text(
    x: float,
    y: float,
    text: str,
    size: int = 16,
    weight: int = 400,
    anchor: str = "middle",
    color: str = "#243142",
) -> str:
    lines = text.split("\n")
    if len(lines) == 1:
        return (
            f'<text x="{x:.1f}" y="{y:.1f}" text-anchor="{anchor}" font-size="{size}" '
            f'font-weight="{weight}" fill="{color}" font-family="Arial, sans-serif">{text}</text>'
        )
    tspans = []
    for index, line in enumerate(lines):
        dy = 0 if index == 0 else size + 2
        tspans.append(f'<tspan x="{x:.1f}" dy="{dy}">{line}</tspan>')
    return (
        f'<text x="{x:.1f}" y="{y:.1f}" text-anchor="{anchor}" font-size="{size}" '
        f'font-weight="{weight}" fill="{color}" font-family="Arial, sans-serif">'
        + "".join(tspans)
        + "</text>"
    )


def grouped_bar_chart_svg(
    title: str,
    labels: list[str],
    before_values: list[float],
    after_values: list[float],
    y_label: str,
    before_label: str,
    after_label: str,
) -> str:
    margin_left = 110
    margin_right = 52
    margin_top = 84
    margin_bottom = 112
    plot_width = CHART_WIDTH - margin_left - margin_right
    plot_height = CHART_HEIGHT - margin_top - margin_bottom
    y_max = nice_axis_max(max(before_values + after_values))
    group_width = plot_width / len(labels)
    bar_width = min(70, group_width * 0.28)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{CHART_WIDTH}" height="{CHART_HEIGHT}" viewBox="0 0 {CHART_WIDTH} {CHART_HEIGHT}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        svg_text(CHART_WIDTH / 2, 42, title, size=26, weight=700, color="#111827"),
        svg_text(30, margin_top + plot_height / 2, y_label, size=15, anchor="middle", color="#475569")
        .replace("<text ", '<text transform="rotate(-90 30 %.1f)" ' % (margin_top + plot_height / 2), 1),
    ]

    for tick in range(6):
        value = y_max * tick / 5
        y = margin_top + plot_height - plot_height * value / y_max
        parts.append(
            f'<line x1="{margin_left}" y1="{y:.1f}" x2="{CHART_WIDTH - margin_right}" y2="{y:.1f}" stroke="#e5e7eb" stroke-width="1"/>'
        )
        parts.append(svg_text(margin_left - 14, y + 5, fmt_number(value), size=13, anchor="end", color="#64748b"))

    parts.append(
        f'<line x1="{margin_left}" y1="{margin_top + plot_height}" x2="{CHART_WIDTH - margin_right}" y2="{margin_top + plot_height}" stroke="#334155" stroke-width="1.5"/>'
    )

    for index, label in enumerate(labels):
        center_x = margin_left + group_width * index + group_width / 2
        before_height = plot_height * before_values[index] / y_max
        after_height = plot_height * after_values[index] / y_max
        before_x = center_x - bar_width - 5
        after_x = center_x + 5
        before_y = margin_top + plot_height - before_height
        after_y = margin_top + plot_height - after_height
        parts.append(
            f'<rect x="{before_x:.1f}" y="{before_y:.1f}" width="{bar_width:.1f}" height="{before_height:.1f}" rx="4" fill="#ef4444"/>'
        )
        parts.append(
            f'<rect x="{after_x:.1f}" y="{after_y:.1f}" width="{bar_width:.1f}" height="{after_height:.1f}" rx="4" fill="#2563eb"/>'
        )
        parts.append(svg_text(before_x + bar_width / 2, max(before_y - 8, 72), fmt_number(before_values[index]), size=12,
            color="#991b1b"))
        parts.append(svg_text(after_x + bar_width / 2, max(after_y - 8, 72), fmt_number(after_values[index]), size=12,
            color="#1e3a8a"))
        parts.append(svg_text(center_x, margin_top + plot_height + 26, label, size=13, color="#334155"))

    legend_y = CHART_HEIGHT - 34
    parts.extend(
        [
            f'<rect x="{CHART_WIDTH / 2 - 142:.1f}" y="{legend_y - 13}" width="18" height="18" rx="4" fill="#ef4444"/>',
            svg_text(CHART_WIDTH / 2 - 112, legend_y + 2, before_label, size=14, anchor="start"),
            f'<rect x="{CHART_WIDTH / 2 + 44:.1f}" y="{legend_y - 13}" width="18" height="18" rx="4" fill="#2563eb"/>',
            svg_text(CHART_WIDTH / 2 + 74, legend_y + 2, after_label, size=14, anchor="start"),
            "</svg>",
        ]
    )
    return "\n".join(parts)


def write_charts(output_dir: Path, rows: list[dict], pool_size: int) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    labels_en = [row["label_en"] for row in rows]
    labels_ko = [row["label_ko"] for row in rows]

    (output_dir / "transaction-io-connection-hold.svg").write_text(
        grouped_bar_chart_svg(
            "Connection Hold Time Per Request",
            labels_en,
            [row["before_hold_ms"] for row in rows],
            [row["after_hold_ms"] for row in rows],
            "ms",
            "Before",
            "After",
        ),
        encoding="utf-8",
    )
    (output_dir / "transaction-io-connection-hold-ko.svg").write_text(
        grouped_bar_chart_svg(
            "요청 1건당 커넥션 점유 시간",
            labels_ko,
            [row["before_hold_ms"] for row in rows],
            [row["after_hold_ms"] for row in rows],
            "ms",
            "개선 전",
            "개선 후",
        ),
        encoding="utf-8",
    )
    (output_dir / "transaction-io-pool-capacity.svg").write_text(
        grouped_bar_chart_svg(
            f"Estimated Max RPS With Hikari Pool {pool_size}",
            labels_en,
            [row["before_max_rps"] for row in rows],
            [row["after_max_rps"] for row in rows],
            "requests/sec",
            "Before",
            "After",
        ),
        encoding="utf-8",
    )
    (output_dir / "transaction-io-pool-capacity-ko.svg").write_text(
        grouped_bar_chart_svg(
            f"Hikari pool {pool_size} 기준 처리 가능 RPS",
            labels_ko,
            [row["before_max_rps"] for row in rows],
            [row["after_max_rps"] for row in rows],
            "requests/sec",
            "개선 전",
            "개선 후",
        ),
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pool-size", type=int, default=POOL_SIZE)
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()

    rows = run_model(args.pool_size)
    if args.output_dir:
        write_charts(args.output_dir, rows, args.pool_size)

    print_markdown_table(rows)


if __name__ == "__main__":
    main()
