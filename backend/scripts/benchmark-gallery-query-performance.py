#!/usr/bin/env python3
"""Synthetic benchmark for gallery list query-shape optimization.

The benchmark mirrors the old and new query shapes without requiring a live
PostgreSQL dataset. It measures the CPU-side work of joining subtype rows for
all active gallery items versus joining subtype rows only after the requested
page has been selected.
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path
import statistics
import time


PAGE_SIZE = 20
ROW_COUNTS = (1_000, 10_000, 50_000)
KINDS = ("fortune", "relay_drawing", "flipbook", "infinite_canvas", "phone", "community_memo")
CHART_WIDTH = 960
CHART_HEIGHT = 540


def build_dataset(row_count: int) -> tuple[list[dict], dict[str, dict[str, str]], dict[str, str]]:
    gallery_rows = []
    artifacts = {}
    subtype_tables = {kind: {} for kind in KINDS if kind != "community_memo"}
    for index in range(row_count):
        artifact_id = f"artifact-{index:06d}"
        kind = KINDS[index % len(KINDS)]
        gallery_rows.append({"gallery_id": f"gallery-{index:06d}", "artifact_id": artifact_id})
        artifacts[artifact_id] = {
            "artifact_id": artifact_id,
            "kind": kind,
            "thumbnail_url": f"{kind}/thumb-{index}.png",
            "source_room_id": f"ROOM-{index % 500}",
            "created_at": row_count - index,
        }
        if kind in subtype_tables:
            subtype_tables[kind][artifact_id] = f"{kind}/content-{index}.png"
    return gallery_rows, subtype_tables, artifacts


def before_count_active_items(gallery_rows: list[dict], subtype_tables: dict[str, dict[str, str]],
    artifacts: dict[str, dict]) -> int:
    count = 0
    for gallery_row in gallery_rows:
        artifact = artifacts.get(gallery_row["artifact_id"])
        if artifact is None:
            continue
        for subtype_table in subtype_tables.values():
            subtype_table.get(artifact["artifact_id"])
        count += 1
    return count


def after_count_active_items(gallery_rows: list[dict], artifacts: dict[str, dict]) -> int:
    count = 0
    for gallery_row in gallery_rows:
        if gallery_row["artifact_id"] in artifacts:
            count += 1
    return count


def before_find_active_items(gallery_rows: list[dict], subtype_tables: dict[str, dict[str, str]],
    artifacts: dict[str, dict]) -> list[dict]:
    joined_rows = []
    for gallery_row in gallery_rows:
        artifact = artifacts.get(gallery_row["artifact_id"])
        if artifact is None:
            continue
        content_url = resolve_content_url(artifact, subtype_tables)
        joined_rows.append(to_gallery_item(gallery_row, artifact, content_url))

    joined_rows.sort(key=lambda row: (row["created_at"], row["artifact_id"]), reverse=True)
    return joined_rows[:PAGE_SIZE]


def after_find_active_items(gallery_rows: list[dict], subtype_tables: dict[str, dict[str, str]],
    artifacts: dict[str, dict]) -> list[dict]:
    base_rows = []
    for gallery_row in gallery_rows:
        artifact = artifacts.get(gallery_row["artifact_id"])
        if artifact is None:
            continue
        base_rows.append((gallery_row, artifact))

    base_rows.sort(key=lambda value: (value[1]["created_at"], value[1]["artifact_id"]), reverse=True)
    page_rows = base_rows[:PAGE_SIZE]
    return [
        to_gallery_item(gallery_row, artifact, resolve_content_url(artifact, subtype_tables))
        for gallery_row, artifact in page_rows
    ]


def resolve_content_url(artifact: dict, subtype_tables: dict[str, dict[str, str]]) -> str:
    kind = artifact["kind"]
    if kind == "community_memo":
        return artifact["thumbnail_url"]
    return subtype_tables[kind].get(artifact["artifact_id"], artifact["thumbnail_url"])


def to_gallery_item(gallery_row: dict, artifact: dict, content_url: str) -> dict:
    return {
        "gallery_id": gallery_row["gallery_id"],
        "artifact_id": artifact["artifact_id"],
        "kind": artifact["kind"],
        "thumbnail_url": artifact["thumbnail_url"],
        "content_url": content_url,
        "source_room_id": artifact["source_room_id"],
        "created_at": artifact["created_at"],
    }


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


def run_benchmark(iterations: int) -> list[dict]:
    rows = []
    for row_count in ROW_COUNTS:
        gallery_rows, subtype_tables, artifacts = build_dataset(row_count)
        before_count_samples = measure_ms(before_count_active_items, gallery_rows, subtype_tables, artifacts,
            iterations=iterations)
        after_count_samples = measure_ms(after_count_active_items, gallery_rows, artifacts, iterations=iterations)
        before_list_samples = measure_ms(before_find_active_items, gallery_rows, subtype_tables, artifacts,
            iterations=iterations)
        after_list_samples = measure_ms(after_find_active_items, gallery_rows, subtype_tables, artifacts,
            iterations=iterations)
        before_count_avg, before_count_p95 = summarize(before_count_samples)
        after_count_avg, after_count_p95 = summarize(after_count_samples)
        before_list_avg, before_list_p95 = summarize(before_list_samples)
        after_list_avg, after_list_p95 = summarize(after_list_samples)
        rows.append(
            {
                "active_gallery_rows": row_count,
                "before_count_avg_ms": before_count_avg,
                "before_count_p95_ms": before_count_p95,
                "after_count_avg_ms": after_count_avg,
                "after_count_p95_ms": after_count_p95,
                "count_improvement_ratio": before_count_p95 / after_count_p95 if after_count_p95 else 0,
                "before_list_avg_ms": before_list_avg,
                "before_list_p95_ms": before_list_p95,
                "after_list_avg_ms": after_list_avg,
                "after_list_p95_ms": after_list_p95,
                "list_improvement_ratio": before_list_p95 / after_list_p95 if after_list_p95 else 0,
                "before_subtype_lookups": row_count * (len(KINDS) - 1),
                "after_subtype_lookups": PAGE_SIZE * (len(KINDS) - 1),
            }
        )
    return rows


def print_markdown_table(rows: list[dict]) -> None:
    columns = [
        "active_gallery_rows",
        "before_count_p95_ms",
        "after_count_p95_ms",
        "count_improvement_ratio",
        "before_list_p95_ms",
        "after_list_p95_ms",
        "list_improvement_ratio",
        "before_subtype_lookups",
        "after_subtype_lookups",
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
                values.append(f"{value:,}" if isinstance(value, int) else str(value))
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


def svg_text(x: float, y: float, text: str, size: int = 16, weight: int = 400, anchor: str = "middle",
    color: str = "#243142") -> str:
    return (
        f'<text x="{x:.1f}" y="{y:.1f}" text-anchor="{anchor}" font-size="{size}" '
        f'font-weight="{weight}" fill="{color}" font-family="Arial, sans-serif">{text}</text>'
    )


def grouped_bar_chart_svg(title: str, labels: list[str], before_values: list[float], after_values: list[float],
    y_label: str, before_label: str = "Before", after_label: str = "After") -> str:
    margin_left = 104
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
        parts.append(svg_text(before_x + bar_width / 2, max(before_y - 8, 74), fmt_number(before_values[index]),
            size=12, color="#991b1b"))
        parts.append(svg_text(after_x + bar_width / 2, max(after_y - 8, 74), fmt_number(after_values[index]), size=12,
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
    labels = [f"{row['active_gallery_rows']:,}" for row in rows]
    (output_dir / "gallery-query-count-p95-ko.svg").write_text(
        grouped_bar_chart_svg(
            "갤러리 count 쿼리 p95",
            labels,
            [row["before_count_p95_ms"] for row in rows],
            [row["after_count_p95_ms"] for row in rows],
            "p95 ms",
            before_label="개선 전",
            after_label="개선 후",
        ),
        encoding="utf-8",
    )
    (output_dir / "gallery-query-list-p95-ko.svg").write_text(
        grouped_bar_chart_svg(
            "갤러리 목록 조회 p95",
            labels,
            [row["before_list_p95_ms"] for row in rows],
            [row["after_list_p95_ms"] for row in rows],
            "p95 ms",
            before_label="개선 전",
            after_label="개선 후",
        ),
        encoding="utf-8",
    )
    (output_dir / "gallery-query-subtype-lookup-ko.svg").write_text(
        grouped_bar_chart_svg(
            "목록 조회 1회당 subtype lookup",
            labels,
            [row["before_subtype_lookups"] for row in rows],
            [row["after_subtype_lookups"] for row in rows],
            "lookup count",
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

    rows = run_benchmark(args.iterations)
    if args.output_dir:
        write_charts(args.output_dir, rows)
    print_markdown_table(rows)


if __name__ == "__main__":
    main()
