#!/usr/bin/env python3
"""
인덱스 템플릿의 service / container_name 매핑을 모든 5개 도메인 인덱스
(access/audit/biz-events/error/system) 에 일관되게 박는다.

매핑 형태: text + .keyword multi-field
  - root는 text (analyzed search 가능)
  - .keyword sub는 keyword (aggregation/sort 가능)
  - OpenSearch 표준 multi-field 패턴

이러면 모든 인덱스에서 visualization은 `service.keyword`로 통일 사용 가능.
기존 plain keyword 인덱스에는 별도로 .keyword sub-field 추가 필요 (Step 2).

재실행 = 멱등 (덮어쓰기).
"""
import json
from pathlib import Path

MULTI_FIELD = {
    "type": "text",
    "fields": {
        "keyword": {"type": "keyword", "ignore_above": 256}
    }
}

TARGET_FIELDS = ["service", "container_name"]
TEMPLATES_DIR = Path(__file__).parent / "templates"


def patch_template(path):
    with open(path, encoding="utf-8") as fp:
        obj = json.load(fp)

    props = obj.get("template", {}).get("mappings", {}).get("properties", {})
    changed = []
    for fld in TARGET_FIELDS:
        old = props.get(fld)
        if old != MULTI_FIELD:
            props[fld] = MULTI_FIELD
            changed.append(fld)

    if changed:
        with open(path, "w", encoding="utf-8") as fp:
            json.dump(obj, fp, ensure_ascii=False, indent=2)
            fp.write("\n")
        print(f"  {path.name}: patched {changed}")
    else:
        print(f"  {path.name}: already up to date")


if __name__ == "__main__":
    for f in sorted(TEMPLATES_DIR.glob("*-template.json")):
        # nemonic-app-logs는 미사용 인덱스라 스킵 (혼동 방지)
        if "nemonic-app-logs" in f.name:
            print(f"  {f.name}: skipped (unused)")
            continue
        patch_template(f)
    print("done.")
