#!/usr/bin/env python3
"""
모든 인덱스 템플릿에 dynamic_templates 추가.

이유: template의 properties에 명시되지 않은 metadata.* 또는 다른 dynamic
string 필드들이 OS 기본 dynamic mapping에 의해 text + .keyword multi-field로
박힘. 그러면 visualization의 terms aggregation 이 `text` field 를 거부하고
사용자가 `.keyword` 를 명시해야 함. 인덱스마다 매핑이 달라지면 통일 깨짐.

해결: `dynamic_templates: [strings_as_keyword]` 를 모든 template 에 박아서
string 필드는 무조건 keyword 로 들어오도록 강제.

origin/infra/dev 가 error-logs-template.json 에 이미 추가한 패턴과 동일.

재실행 = 멱등.
"""
import json
from pathlib import Path

DYNAMIC_TEMPLATES = [
    {
        "strings_as_keyword": {
            "match_mapping_type": "string",
            "mapping": {"type": "keyword", "ignore_above": 1024}
        }
    }
]

TEMPLATES_DIR = Path(__file__).parent / "templates"


def patch_template(path):
    with open(path, encoding="utf-8") as fp:
        obj = json.load(fp)

    mappings = obj.get("template", {}).get("mappings", {})
    current = mappings.get("dynamic_templates")
    if current == DYNAMIC_TEMPLATES:
        print(f"  {path.name}: already up to date")
        return

    # properties 위에 두는 게 관습 — dict 재구성으로 순서 박기.
    new_mappings = {}
    for k, v in mappings.items():
        if k == "properties":
            new_mappings["dynamic_templates"] = DYNAMIC_TEMPLATES
        new_mappings[k] = v
    if "dynamic_templates" not in new_mappings:
        new_mappings["dynamic_templates"] = DYNAMIC_TEMPLATES

    obj["template"]["mappings"] = new_mappings

    with open(path, "w", encoding="utf-8") as fp:
        json.dump(obj, fp, ensure_ascii=False, indent=2)
        fp.write("\n")
    print(f"  {path.name}: patched")


if __name__ == "__main__":
    for f in sorted(TEMPLATES_DIR.glob("*-template.json")):
        if "nemonic-app-logs" in f.name:
            print(f"  {f.name}: skipped (unused)")
            continue
        patch_template(f)
    print("done.")
