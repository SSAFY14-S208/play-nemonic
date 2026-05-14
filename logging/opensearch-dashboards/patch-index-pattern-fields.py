#!/usr/bin/env python3
"""
00-index-patterns.ndjson 의 biz-events-* 패턴 fields 배열에 누락된 metadata 필드를 추가한다.

이 파일은 매번 자동 import 되므로 여기에 필드를 박아두지 않으면 dashboards-init 가 옛 fields
배열로 덮어쓴다 (UI 에서 수동 refresh 한 효과가 사라짐).

추가 대상:
    - metadata.funnel_name   (keyword)  — I2/I3 group by
    - metadata.entry_type    (keyword)  — H3 group by
    - metadata.step_name     (keyword)  — I3 단계 라벨링

biz-events-template 의 dynamic_template (strings_as_keyword) 에 의해 실제 index 매핑은
keyword 로 잡혀 있으므로, fields 배열에 등록만 하면 visualization 이 인식한다.

재실행 = 멱등. 이미 등록된 필드는 건드리지 않는다.
"""
import json
from pathlib import Path

NDJSON = Path(__file__).parent / "saved-objects" / "00-index-patterns.ndjson"
BIZ_EVENTS_TITLE = "biz-events-*"

ADDITIONS = [
    {
        "count": 0,
        "name": "metadata.funnel_name",
        "type": "string",
        "esTypes": ["keyword"],
        "scripted": False,
        "searchable": True,
        "aggregatable": True,
        "readFromDocValues": True,
    },
    {
        "count": 0,
        "name": "metadata.entry_type",
        "type": "string",
        "esTypes": ["keyword"],
        "scripted": False,
        "searchable": True,
        "aggregatable": True,
        "readFromDocValues": True,
    },
    {
        "count": 0,
        "name": "metadata.step_name",
        "type": "string",
        "esTypes": ["keyword"],
        "scripted": False,
        "searchable": True,
        "aggregatable": True,
        "readFromDocValues": True,
    },
]


def patch_line(raw_line: str) -> tuple[str, int]:
    """biz-events-* 패턴이면 fields 배열에 누락된 항목 추가. (new_line, added_count) 반환."""
    obj = json.loads(raw_line)
    if obj.get("type") != "index-pattern":
        return raw_line, 0
    if obj.get("attributes", {}).get("title") != BIZ_EVENTS_TITLE:
        return raw_line, 0

    fields_str = obj["attributes"]["fields"]
    fields = json.loads(fields_str)
    existing_names = {f["name"] for f in fields}

    added = 0
    for new_field in ADDITIONS:
        if new_field["name"] in existing_names:
            continue
        fields.append(new_field)
        added += 1

    if added == 0:
        return raw_line, 0

    # name 알파벳순 정렬 — 원본 패턴이 그렇게 보임 (필수는 아님)
    fields.sort(key=lambda f: f["name"])
    obj["attributes"]["fields"] = json.dumps(fields, ensure_ascii=False)
    return json.dumps(obj, ensure_ascii=False), added


def main() -> None:
    lines = NDJSON.read_text(encoding="utf-8").splitlines()
    out_lines: list[str] = []
    total_added = 0
    for raw in lines:
        if not raw.strip():
            out_lines.append(raw)
            continue
        new_raw, added = patch_line(raw)
        out_lines.append(new_raw)
        total_added += added

    NDJSON.write_text("\n".join(out_lines) + "\n", encoding="utf-8")
    print(f"patched {NDJSON} (+{total_added} fields)")


if __name__ == "__main__":
    main()
