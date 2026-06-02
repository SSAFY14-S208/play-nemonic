#!/usr/bin/env python3
"""Synthetic benchmark for community memo index optimization.

The benchmark mirrors the query shapes covered by V12 community memo indexes:

- admin memo list: filter/order/page over historical community_memo rows
- memo report list: filter/order/page over community_memo_report rows
- public wall list: visible memo ordering, which is bounded by runtime setting

It does not require a live PostgreSQL dataset. The goal is to make the index
effect reproducible for documentation: old sequential filter + sort work versus
the indexed page-window shape.
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import random
import statistics
import time


PAGE_SIZE = 20
VISIBLE_MEMO_LIMIT = 50
ROW_COUNTS = (1_000, 10_000, 50_000)
REPORT_COUNTS = (1_000, 10_000, 50_000)
CHART_WIDTH = 960
CHART_HEIGHT = 540
STATUSES = ("pending", "allowed", "blocked")
REASONS = ("spam", "abuse", "sexual", "violence", "etc")


def build_memo_rows(row_count: int) -> list[dict]:
    rng = random.Random(row_count)
    rows = []
    for index in range(row_count):
        rows.append(
            {
                "id": f"memo-{index:06d}",
                "is_hidden": index % 7 == 0,
                "deleted": index % 31 == 0,
                "moderation_status": STATUSES[index % len(STATUSES)],
                "artifact_id": None if index % 4 == 0 else f"artifact-{index:06d}",
                "report_count": 0 if index % 5 else (index % 9) + 1,
                "z_index": index % 60,
                "attached_at": index,
                "created_at": index,
                "updated_at": row_count - index + (index % 11),
                "nickname": f"user-{index % 300}",
                "ocr_text": f"memo text {index % 500}",
            }
        )
    rng.shuffle(rows)
    return rows


def build_report_rows(report_count: int) -> list[dict]:
    rng = random.Random(report_count * 17)
    rows = []
    for index in range(report_count):
        rows.append(
            {
                "id": index,
                "memo_id": "memo-target",
                "reason": REASONS[index % len(REASONS)],
                "created_at": report_count - index,
                "reporter_nickname": f"reporter-{index % 200}",
            }
        )
    rng.shuffle(rows)
    return rows


def build_indexes(memo_rows: list[dict], report_rows: list[dict]) -> dict[str, list[dict]]:
    not_deleted = [row for row in memo_rows if not row["deleted"]]
    reported = [row for row in not_deleted if row["report_count"] > 0]
    visible = [row for row in not_deleted if not row["is_hidden"]][:VISIBLE_MEMO_LIMIT]
    reports_by_reason = [row for row in report_rows if row["reason"] == "spam"]
    return {
        "admin_updated": sorted(not_deleted, key=admin_sort_key),
        "admin_reported": sorted(reported, key=admin_sort_key),
        "visible_wall": sorted(visible, key=wall_sort_key),
        "reports_created": sorted(report_rows, key=report_sort_key),
        "reports_reason": sorted(reports_by_reason, key=report_sort_key),
    }


def admin_sort_key(row: dict) -> tuple[int, int, str]:
    return (-row["updated_at"], -row["created_at"], row["id"])


def wall_sort_key(row: dict) -> tuple[int, int, str]:
    return (row["z_index"], row["attached_at"], row["id"])


def report_sort_key(row: dict) -> tuple[int, int]:
    return (-row["created_at"], -row["id"])


def map_admin_row(row: dict) -> dict:
    source_type = "DIRECT" if row["artifact_id"] is None else "GALLERY"
    return {
        "memo_id": row["id"],
        "source_type": source_type,
        "reported": row["report_count"] > 0,
        "hidden": row["is_hidden"],
        "moderation_status": row["moderation_status"],
        "updated_at": row["updated_at"],
    }


def map_report_row(row: dict) -> dict:
    return {
        "report_id": row["id"],
        "reason": row["reason"],
        "reporter_nickname": row["reporter_nickname"],
        "created_at": row["created_at"],
    }


def before_admin_reported_list(memo_rows: list[dict]) -> list[dict]:
    filtered = [row for row in memo_rows if not row["deleted"] and row["report_count"] > 0]
    filtered.sort(key=admin_sort_key)
    return [map_admin_row(row) for row in filtered[:PAGE_SIZE]]


def after_admin_reported_list(indexes: dict[str, list[dict]]) -> list[dict]:
    return [map_admin_row(row) for row in indexes["admin_reported"][:PAGE_SIZE]]


def before_report_reason_list(report_rows: list[dict]) -> list[dict]:
    filtered = [row for row in report_rows if row["memo_id"] == "memo-target" and row["reason"] == "spam"]
    filtered.sort(key=report_sort_key)
    return [map_report_row(row) for row in filtered[:PAGE_SIZE]]


def after_report_reason_list(indexes: dict[str, list[dict]]) -> list[dict]:
    return [map_report_row(row) for row in indexes["reports_reason"][:PAGE_SIZE]]


def before_visible_wall_list(memo_rows: list[dict]) -> list[dict]:
    filtered = [row for row in memo_rows if not row["deleted"] and not row["is_hidden"]][:VISIBLE_MEMO_LIMIT]
    filtered.sort(key=wall_sort_key)
    return [map_admin_row(row) for row in filtered]


def after_visible_wall_list(indexes: dict[str, list[dict]]) -> list[dict]:
    return [map_admin_row(row) for row in indexes["visible_wall"]]


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


def ratio(before: float, after: float) -> float:
    if after <= 0:
        return 0
    return before / after


def run_benchmark(iterations: int) -> list[dict]:
    rows = []
    for row_count, report_count in zip(ROW_COUNTS, REPORT_COUNTS, strict=True):
        memo_rows = build_memo_rows(row_count)
        report_rows = build_report_rows(report_count)
        indexes = build_indexes(memo_rows, report_rows)

        before_admin_avg, before_admin_p95 = summarize(
            measure_ms(before_admin_reported_list, memo_rows, iterations=iterations)
        )
        after_admin_avg, after_admin_p95 = summarize(
            measure_ms(after_admin_reported_list, indexes, iterations=iterations)
        )
        before_report_avg, before_report_p95 = summarize(
            measure_ms(before_report_reason_list, report_rows, iterations=iterations)
        )
        after_report_avg, after_report_p95 = summarize(
            measure_ms(after_report_reason_list, indexes, iterations=iterations)
        )
        before_wall_avg, before_wall_p95 = summarize(
            measure_ms(before_visible_wall_list, memo_rows, iterations=iterations)
        )
        after_wall_avg, after_wall_p95 = summarize(
            measure_ms(after_visible_wall_list, indexes, iterations=iterations)
        )

        rows.append(
            {
                "historical_memo_rows": row_count,
                "report_rows_for_memo": report_count,
                "visible_memo_limit": VISIBLE_MEMO_LIMIT,
                "page_size": PAGE_SIZE,
                "before_admin_reported_avg_ms": before_admin_avg,
                "before_admin_reported_p95_ms": before_admin_p95,
                "after_admin_reported_avg_ms": after_admin_avg,
                "after_admin_reported_p95_ms": after_admin_p95,
                "admin_reported_improvement_ratio": ratio(before_admin_p95, after_admin_p95),
                "before_report_reason_avg_ms": before_report_avg,
                "before_report_reason_p95_ms": before_report_p95,
                "after_report_reason_avg_ms": after_report_avg,
                "after_report_reason_p95_ms": after_report_p95,
                "report_reason_improvement_ratio": ratio(before_report_p95, after_report_p95),
                "before_visible_wall_avg_ms": before_wall_avg,
                "before_visible_wall_p95_ms": before_wall_p95,
                "after_visible_wall_avg_ms": after_wall_avg,
                "after_visible_wall_p95_ms": after_wall_p95,
                "visible_wall_improvement_ratio": ratio(before_wall_p95, after_wall_p95),
                "before_admin_rows_touched": row_count,
                "after_admin_rows_touched": PAGE_SIZE,
                "before_report_rows_touched": report_count,
                "after_report_rows_touched": PAGE_SIZE,
            }
        )
    return rows


def fmt_float(value: float) -> str:
    if abs(value) < 0.1:
        return f"{value:.3f}"
    return f"{value:.2f}"


def fmt_int(value: int) -> str:
    return f"{value:,}"


def markdown_table(rows: list[dict]) -> str:
    columns = [
        ("historical_memo_rows", "historical memo rows"),
        ("before_admin_reported_p95_ms", "Before admin reported p95 ms"),
        ("after_admin_reported_p95_ms", "After admin reported p95 ms"),
        ("admin_reported_improvement_ratio", "admin ratio"),
        ("before_report_reason_p95_ms", "Before report p95 ms"),
        ("after_report_reason_p95_ms", "After report p95 ms"),
        ("report_reason_improvement_ratio", "report ratio"),
        ("before_admin_rows_touched", "Before touched rows"),
        ("after_admin_rows_touched", "After touched rows"),
    ]
    lines = [
        "| " + " | ".join(label for _, label in columns) + " |",
        "| " + " | ".join(["---:"] * len(columns)) + " |",
    ]
    for row in rows:
        values = []
        for key, _ in columns:
            value = row[key]
            if isinstance(value, float):
                values.append(fmt_float(value))
            else:
                values.append(fmt_int(value))
        lines.append("| " + " | ".join(values) + " |")
    return "\n".join(lines)


def write_results(prefix: Path, rows: list[dict]) -> None:
    prefix.parent.mkdir(parents=True, exist_ok=True)
    prefix.with_suffix(".json").write_text(json.dumps(rows, indent=2), encoding="utf-8")
    markdown = [
        "# 커뮤니티 메모 인덱스 최적화 synthetic benchmark 결과",
        "",
        "운영 DB 실측이 아니라 V12 인덱스가 바꾸는 쿼리 shape를 재현한 CPU-side synthetic benchmark입니다.",
        "",
        markdown_table(rows),
        "",
    ]
    prefix.with_suffix(".md").write_text("\n".join(markdown), encoding="utf-8")


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


def fmt_axis(value: float) -> str:
    if value >= 1000:
        return f"{value:,.0f}"
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
    y_label: str, before_label: str, after_label: str) -> str:
    margin_left = 112
    margin_right = 52
    margin_top = 84
    margin_bottom = 96
    plot_width = CHART_WIDTH - margin_left - margin_right
    plot_height = CHART_HEIGHT - margin_top - margin_bottom
    y_max = nice_axis_max(max(before_values + after_values))
    group_width = plot_width / len(labels)
    bar_width = min(72, group_width * 0.28)
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{CHART_WIDTH}" height="{CHART_HEIGHT}" viewBox="0 0 {CHART_WIDTH} {CHART_HEIGHT}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        svg_text(CHART_WIDTH / 2, 42, title, size=25, weight=700, color="#111827"),
        svg_text(34, margin_top + plot_height / 2, y_label, size=15, anchor="middle", color="#475569")
        .replace("<text ", '<text transform="rotate(-90 34 %.1f)" ' % (margin_top + plot_height / 2), 1),
    ]
    for tick in range(6):
        value = y_max * tick / 5
        y = margin_top + plot_height - plot_height * value / y_max
        parts.append(
            f'<line x1="{margin_left}" y1="{y:.1f}" x2="{CHART_WIDTH - margin_right}" y2="{y:.1f}" stroke="#e5e7eb" stroke-width="1"/>'
        )
        parts.append(svg_text(margin_left - 14, y + 5, fmt_axis(value), size=13, anchor="end", color="#64748b"))
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
        parts.append(svg_text(before_x + bar_width / 2, max(before_y - 8, 74), fmt_axis(before_values[index]),
            size=12, color="#991b1b"))
        parts.append(svg_text(after_x + bar_width / 2, max(after_y - 8, 74), fmt_axis(after_values[index]), size=12,
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


def write_charts(output_dir: Path, rows: list[dict]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    labels = [fmt_int(row["historical_memo_rows"]) for row in rows]

    chart_specs = [
        (
            "community-memo-admin-list-p95-ko.svg",
            "관리자 신고 메모 목록 p95",
            "p95 ms",
            [row["before_admin_reported_p95_ms"] for row in rows],
            [row["after_admin_reported_p95_ms"] for row in rows],
            "개선 전",
            "개선 후",
        ),
        (
            "community-memo-admin-list-p95.svg",
            "Admin Reported Memo List p95",
            "p95 ms",
            [row["before_admin_reported_p95_ms"] for row in rows],
            [row["after_admin_reported_p95_ms"] for row in rows],
            "Before",
            "After",
        ),
        (
            "community-memo-report-list-p95-ko.svg",
            "메모 신고 내역 조회 p95",
            "p95 ms",
            [row["before_report_reason_p95_ms"] for row in rows],
            [row["after_report_reason_p95_ms"] for row in rows],
            "개선 전",
            "개선 후",
        ),
        (
            "community-memo-report-list-p95.svg",
            "Memo Report List p95",
            "p95 ms",
            [row["before_report_reason_p95_ms"] for row in rows],
            [row["after_report_reason_p95_ms"] for row in rows],
            "Before",
            "After",
        ),
        (
            "community-memo-admin-rows-touched-ko.svg",
            "관리자 목록 1회당 후보 row 수",
            "rows",
            [row["before_admin_rows_touched"] for row in rows],
            [row["after_admin_rows_touched"] for row in rows],
            "개선 전",
            "개선 후",
        ),
        (
            "community-memo-admin-rows-touched.svg",
            "Admin List Candidate Rows per Request",
            "rows",
            [row["before_admin_rows_touched"] for row in rows],
            [row["after_admin_rows_touched"] for row in rows],
            "Before",
            "After",
        ),
    ]
    for filename, title, y_label, before_values, after_values, before_label, after_label in chart_specs:
        (output_dir / filename).write_text(
            grouped_bar_chart_svg(title, labels, before_values, after_values, y_label, before_label, after_label),
            encoding="utf-8",
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--iterations", type=int, default=20)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--results-prefix", type=Path)
    args = parser.parse_args()

    rows = run_benchmark(args.iterations)
    if args.output_dir:
        write_charts(args.output_dir, rows)
    if args.results_prefix:
        write_results(args.results_prefix, rows)
    print(markdown_table(rows))


if __name__ == "__main__":
    main()
