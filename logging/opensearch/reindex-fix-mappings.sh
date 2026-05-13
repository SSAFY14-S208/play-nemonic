#!/usr/bin/env bash
#
# 매핑 충돌이 굳어버린 옛 인덱스를 현재 템플릿 매핑으로 리매핑한다.
#
# 동작:
#   1) 원본 인덱스 doc count 기록
#   2) 원본 → <name>-reindex-tmp 로 reindex (템플릿 적용 → 매핑 정상화)
#   3) doc count 일치 확인 후 원본 삭제
#   4) tmp → 원래 이름으로 reindex (인덱스 이름 보존)
#   5) doc count 재확인 후 tmp 삭제
#
# 안전장치:
#   - 매 단계 doc count 검증, 불일치 시 즉시 abort (옛 데이터 보존)
#   - DRY_RUN=1 환경변수로 실제 변경 없이 시뮬레이션
#   - --check 플래그로 충돌 인덱스 목록만 출력 후 종료
#
# 사용법:
#   # 어떤 인덱스가 충돌인지 먼저 확인 (변경 없음)
#   bash reindex-fix-mappings.sh --check error-logs '*'
#
#   # 실제 reindex (옛 7개 인덱스)
#   bash reindex-fix-mappings.sh error-logs 2026.04.30 2026.05.02 2026.05.05 \
#       2026.05.07 2026.05.08 2026.05.09 2026.05.10
#
#   # dry-run
#   DRY_RUN=1 bash reindex-fix-mappings.sh error-logs 2026.04.30
#
# 환경변수:
#   OS_HOST       OpenSearch 엔드포인트 (default: http://127.0.0.1:9200)
#   OS_CONTAINER  curl을 실행할 컨테이너 (default: nemonic-logging-opensearch)
#                 빈 문자열이면 host에서 직접 curl
#   DRY_RUN       1이면 _reindex/DELETE 미실행
#
# 사전 조건:
#   - 현재 _index_template/<prefix>-template 이 정확한 매핑으로 등록돼 있어야 함
#   - 대상 인덱스가 read-only가 아니어야 함

set -euo pipefail

OS_HOST="${OS_HOST:-http://127.0.0.1:9200}"
OS_CONTAINER="${OS_CONTAINER:-nemonic-logging-opensearch}"
DRY_RUN="${DRY_RUN:-0}"

# ---------- helpers ----------

# curl을 컨테이너 내부 또는 호스트에서 실행. 호출자가 path만 넘기면 됨.
os_curl() {
  local method="$1"; shift
  local path="$1"; shift
  local url="${OS_HOST}${path}"

  if [[ -n "$OS_CONTAINER" ]]; then
    docker exec -i "$OS_CONTAINER" \
      curl -sS -X "$method" "$url" -H 'Content-Type: application/json' "$@"
  else
    curl -sS -X "$method" "$url" -H 'Content-Type: application/json' "$@"
  fi
}

# 인덱스 존재 여부 (HEAD 응답 코드).
# 주의: curl 은 '-X HEAD' 만 주면 헤더는 HEAD 로 보내지만 본문 read 를 기다려
#       hang 한다. '-I' (--head) 로 해야 본문을 안 기다림.
index_exists() {
  local name="$1"
  local code
  if [[ -n "$OS_CONTAINER" ]]; then
    code=$(docker exec -i "$OS_CONTAINER" \
      curl -sS -o /dev/null -w '%{http_code}' -I "${OS_HOST}/${name}")
  else
    code=$(curl -sS -o /dev/null -w '%{http_code}' -I "${OS_HOST}/${name}")
  fi
  [[ "$code" == "200" ]]
}

# 인덱스 doc count.
doc_count() {
  local name="$1"
  os_curl GET "/${name}/_count" \
    | grep -oE '"count":[[:space:]]*[0-9]+' \
    | grep -oE '[0-9]+'
}

# 충돌 필드 탐지 — 같은 필드 이름 아래 type 종류가 2개 이상이면 출력.
detect_conflicts() {
  local pattern="$1"
  os_curl GET "/${pattern}/_field_caps?fields=*" \
    | python3 -c '
import json, sys
data = json.load(sys.stdin)
fields = data.get("fields", {})
conflicts = []
for fname, types in fields.items():
    if fname.startswith("_"):
        continue
    real_types = [t for t in types.keys() if t != "unmapped"]
    if len(real_types) > 1:
        conflicts.append((fname, real_types))
if not conflicts:
    print("OK: no conflicts")
else:
    print("CONFLICTS:")
    for f, ts in conflicts:
        print(f"  {f}: {ts}")
    sys.exit(2)
'
}

reindex_one() {
  local src="$1" dst="$2"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [dry-run] would reindex ${src} → ${dst}"
    return 0
  fi
  # wait_for_completion=true → 동기 실행. 옛 일자 인덱스는 보통 작아 OK.
  # refresh=true → reindex 직후 count 검증이 정확하도록 강제 refresh.
  local resp
  resp=$(os_curl POST "/_reindex?wait_for_completion=true&refresh=true" \
    --data-binary "{\"source\":{\"index\":\"${src}\"},\"dest\":{\"index\":\"${dst}\"}}")
  if echo "$resp" | grep -q '"failures":\[\]'; then
    echo "  reindex ${src} → ${dst} done"
  else
    echo "  FAIL: ${resp}" >&2
    return 1
  fi
}

delete_one() {
  local name="$1"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [dry-run] would delete ${name}"
    return 0
  fi
  os_curl DELETE "/${name}" >/dev/null
  echo "  deleted ${name}"
}

verify_template_has_keyword_service() {
  local prefix="$1"
  local resp
  resp=$(os_curl GET "/_index_template/${prefix}-template" 2>/dev/null || true)
  if echo "$resp" | python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
    props = data["index_templates"][0]["index_template"]["template"]["mappings"]["properties"]
    sys.exit(0 if props.get("service", {}).get("type") == "keyword" else 1)
except Exception:
    sys.exit(1)
'; then
    return 0
  fi
  echo "ERROR: _index_template/${prefix}-template 이 service:keyword 로 등록돼 있지 않습니다." >&2
  echo "       먼저 logging/opensearch/templates/${prefix}-template.json 을 PUT 해주세요." >&2
  echo "       (README 의 _index_template 업로드 절차 참고)" >&2
  return 1
}

# ---------- main ----------

if [[ $# -lt 2 ]]; then
  sed -n '2,40p' "$0"
  exit 1
fi

# --check 모드: 충돌만 검사하고 종료.
if [[ "$1" == "--check" ]]; then
  shift
  prefix="$1"; shift
  pattern_suffix="${1:-*}"
  echo "Checking ${prefix}-${pattern_suffix} for mapping conflicts..."
  detect_conflicts "${prefix}-${pattern_suffix}"
  exit $?
fi

prefix="$1"; shift
dates=("$@")

echo "Target prefix: ${prefix}"
echo "Dates:         ${dates[*]}"
echo "DRY_RUN:       ${DRY_RUN}"
echo ""

# 0. 템플릿이 올바른 매핑으로 등록돼 있는지 사전 확인.
echo "[0/${#dates[@]}] verifying template..."
verify_template_has_keyword_service "$prefix"
echo "  template OK"
echo ""

# 1. 각 일자별 reindex 왕복.
i=0
for date in "${dates[@]}"; do
  i=$((i + 1))
  src="${prefix}-${date}"
  tmp="${src}-reindex-tmp"

  echo "[$i/${#dates[@]}] ${src}"

  if ! index_exists "$src"; then
    echo "  skip: ${src} 존재하지 않음"
    echo ""
    continue
  fi

  # tmp 인덱스가 이미 있으면 이전 실행이 실패한 흔적 — 안전을 위해 abort.
  if index_exists "$tmp"; then
    echo "  ABORT: ${tmp} 이 이미 존재합니다. 수동 정리 후 재실행하세요." >&2
    echo "         (이전 실행이 중간에 실패했을 가능성)" >&2
    exit 2
  fi

  src_count=$(doc_count "$src")
  echo "  doc count: ${src_count}"

  echo "  step 1/4: reindex ${src} → ${tmp}"
  reindex_one "$src" "$tmp"

  if [[ "$DRY_RUN" != "1" ]]; then
    tmp_count=$(doc_count "$tmp")
    if [[ "$src_count" != "$tmp_count" ]]; then
      echo "  ABORT: doc count 불일치 (src=${src_count}, tmp=${tmp_count})" >&2
      echo "         ${src} 는 보존됐고, ${tmp} 는 수동 확인 필요." >&2
      exit 3
    fi
    echo "  count match: ${tmp_count}"
  fi

  echo "  step 2/4: delete ${src}"
  delete_one "$src"

  echo "  step 3/4: reindex ${tmp} → ${src}"
  reindex_one "$tmp" "$src"

  if [[ "$DRY_RUN" != "1" ]]; then
    final_count=$(doc_count "$src")
    if [[ "$src_count" != "$final_count" ]]; then
      echo "  ABORT: 최종 doc count 불일치 (orig=${src_count}, final=${final_count})" >&2
      echo "         ${tmp} 는 보존됐습니다." >&2
      exit 4
    fi
    echo "  count match: ${final_count}"
  fi

  echo "  step 4/4: delete ${tmp}"
  delete_one "$tmp"

  echo "  done."
  echo ""
done

# 2. 최종 검증.
echo "Final check: detect remaining conflicts for ${prefix}-*"
if detect_conflicts "${prefix}-*"; then
  echo ""
  echo "✓ All done. ${prefix}-* 매핑이 통일됐습니다."
else
  echo ""
  echo "⚠ 아직 충돌이 남아있습니다. 누락된 일자를 인자로 다시 실행하세요."
  exit 5
fi
