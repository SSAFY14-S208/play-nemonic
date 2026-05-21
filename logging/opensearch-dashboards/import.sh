#!/bin/sh
# ============================================================
# OpenSearch Dashboards saved-objects 자동 import (oneshot).
#
# 사용 흐름:
#   - docker-compose.logging.yml의 dashboards-init 서비스가 매 기동 시 실행
#   - /saved-objects 디렉토리 안의 *.ndjson 파일을 파일명 순으로 import
#   - overwrite=true 로 멱등 (id 일치 시 그대로 덮어씀)
#
# 파일명 prefix(00-, 10-, 20-)로 import 순서 제어:
#   00-* : index-pattern (모든 visualization의 의존성)
#   10-* : 첫 도메인 대시보드
#   20-* : 두 번째 도메인 대시보드 ... (대시보드 단위로 1파일)
#
# 단일 ndjson 안에는 한 대시보드에 묶이는 visualization/search/dashboard 객체가
# 모두 들어가야 reference 해석이 끊기지 않는다 (OpenSearch Dashboards import API는
# 파일 단위로 처리하므로 cross-file reference는 별도 단계 import에 의존).
# ============================================================
set -eu

DASHBOARDS_URL="${DASHBOARDS_URL:-http://opensearch-dashboards:5601}"
BASEPATH="${BASEPATH:-/_dashboards}"
SAVED_OBJECTS_DIR="${SAVED_OBJECTS_DIR:-/saved-objects}"
API="${DASHBOARDS_URL}${BASEPATH}/api"

echo "[dashboards-init] target API: ${API}"
echo "[dashboards-init] saved-objects dir: ${SAVED_OBJECTS_DIR}"

# ---- 1) Dashboards ready 대기 ----
# depends_on: service_healthy 가 있더라도 첫 부팅에는 API가 잠시 503을 줄 수 있어서
# /api/status 의 overall.state가 green 이 될 때까지 한 번 더 polling.
WAIT_MAX=120
WAITED=0
while :; do
  STATUS=$(curl -sf "${API}/status" 2>/dev/null || echo "")
  case "$STATUS" in
    *'"state":"green"'*) break ;;
  esac
  if [ "$WAITED" -ge "$WAIT_MAX" ]; then
    echo "[dashboards-init] Dashboards not green after ${WAIT_MAX}s. last status:" >&2
    echo "  ${STATUS}" >&2
    exit 1
  fi
  printf "[dashboards-init] waiting for Dashboards green... (%ds/%ds)\r" "$WAITED" "$WAIT_MAX"
  sleep 5
  WAITED=$((WAITED + 5))
done
echo ""
echo "[dashboards-init] Dashboards is green."

# ---- 2) NDJSON 파일들 순서대로 import ----
shopt_failed=0  # POSIX sh이므로 일반 변수로 추적
FAILED=0
IMPORTED_FILES=0

# ls + sort로 파일명 prefix 순서 보장. *.ndjson 매칭 안 되면 글롭 패턴 그대로 들어와서
# -f 체크로 거른다.
for f in "${SAVED_OBJECTS_DIR}"/*.ndjson; do
  [ -f "$f" ] || continue
  name=$(basename "$f")
  size=$(wc -c < "$f" | tr -d ' ')
  if [ "$size" -lt 2 ]; then
    echo "[dashboards-init] SKIP ${name} (empty/placeholder)"
    continue
  fi

  echo "[dashboards-init] importing ${name} (${size}B)..."
  HTTP_CODE=$(curl -s -o /tmp/import-resp.json -w "%{http_code}" \
    -X POST \
    -H "osd-xsrf: true" \
    -F "file=@${f};type=application/ndjson" \
    "${API}/saved_objects/_import?overwrite=true")

  if [ "$HTTP_CODE" != "200" ]; then
    echo "[dashboards-init] FAIL ${name} HTTP ${HTTP_CODE}:" >&2
    cat /tmp/import-resp.json >&2 2>/dev/null || true
    echo "" >&2
    FAILED=$((FAILED + 1))
    continue
  fi

  # 응답에서 success/successCount/errors 추출 (jq 없는 환경 대비 grep로)
  SUCCESS=$(grep -o '"success":[[:space:]]*\(true\|false\)' /tmp/import-resp.json | head -1 | awk -F: '{print $2}' | tr -d ' ')
  SCOUNT=$(grep -o '"successCount":[[:space:]]*[0-9]\+' /tmp/import-resp.json | head -1 | awk -F: '{print $2}' | tr -d ' ')
  HAS_ERRORS=$(grep -c '"errors"[[:space:]]*:[[:space:]]*\[' /tmp/import-resp.json || true)

  if [ "${SUCCESS:-false}" = "true" ] && [ "${HAS_ERRORS:-0}" = "0" ]; then
    echo "[dashboards-init]   OK — imported ${SCOUNT:-?} object(s)"
  else
    echo "[dashboards-init]   WARN ${name} — success=${SUCCESS:-?} count=${SCOUNT:-?} errors=${HAS_ERRORS}" >&2
    echo "  response:" >&2
    cat /tmp/import-resp.json >&2 2>/dev/null || true
    echo "" >&2
    # success=true 라도 errors 배열이 있을 수 있음 (일부 객체만 실패). 전체 실패로 치지 않고 WARN으로만.
  fi

  IMPORTED_FILES=$((IMPORTED_FILES + 1))
done

if [ "$IMPORTED_FILES" = "0" ]; then
  echo "[dashboards-init] no ndjson files imported (${SAVED_OBJECTS_DIR} empty?). exit 0."
  exit 0
fi

if [ "$FAILED" != "0" ]; then
  echo "[dashboards-init] ${FAILED} file(s) failed." >&2
  exit 1
fi

echo "[dashboards-init] done. imported ${IMPORTED_FILES} file(s)."
