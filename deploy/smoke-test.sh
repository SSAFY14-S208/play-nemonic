#!/usr/bin/env bash
# ============================================================
# 배포 직후 헬스체크 (smoke test)
# - 실패 시 exit 1 -> remote-deploy.sh가 롤백
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/deploy/.env.prod}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nemonic-prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] 환경변수 파일 없음: $ENV_FILE" >&2
  exit 1
fi

# .env.prod 로드 (DOMAIN 등 사용)
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

compose() {
  docker compose \
    -p "$COMPOSE_PROJECT_NAME" \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    "$@"
}

echo "=========================================="
echo "Smoke Test"
echo "=========================================="

# ============================================================
# 1. 컨테이너 상태 확인
# ============================================================
echo ""
echo "[1/5] 컨테이너 상태"
compose ps

# ============================================================
# 2. PostgreSQL
# ============================================================
echo ""
echo "[2/5] PostgreSQL 연결 확인"
compose exec -T postgres pg_isready -U "${DB_USERNAME}" -d "${DB_NAME}"

# ============================================================
# 3. Redis
# ============================================================
echo ""
echo "[3/5] Redis 연결 확인"
compose exec -T redis redis-cli -a "${REDIS_PASSWORD}" --no-auth-warning ping

# ============================================================
# 4. MinIO
# ============================================================
echo ""
echo "[4/5] MinIO 연결 확인"
# 컨테이너 내부에서 MinIO 헬스 엔드포인트 체크
compose exec -T minio \
  curl -fsS http://localhost:9000/minio/health/live

# ============================================================
# 5. App 컨테이너가 healthy 될 때까지 대기
# ============================================================
echo ""
echo "[5/6] App healthy 상태 대기 (최대 3분)"

MAX_WAIT=180   # 3분
WAITED=0
while [[ $WAITED -lt $MAX_WAIT ]]; do
  # app 컨테이너의 Health 상태 조회
  APP_CONTAINER=$(compose ps -q app 2>/dev/null | head -1)

  if [[ -z "$APP_CONTAINER" ]]; then
    echo "  app 컨테이너를 찾을 수 없습니다..."
    sleep 5
    WAITED=$((WAITED + 5))
    continue
  fi

  HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "$APP_CONTAINER" 2>/dev/null || echo "unknown")

  case "$HEALTH" in
    healthy)
      echo "  app healthy 확인 (대기 시간: ${WAITED}초)"
      break
      ;;
    unhealthy)
      echo "  [ERROR] app 컨테이너가 unhealthy 상태입니다." >&2
      echo "  로그 확인: docker logs $APP_CONTAINER" >&2
      docker logs --tail=50 "$APP_CONTAINER" >&2 || true
      exit 1
      ;;
    starting|unknown|*)
      printf "  기동 중... (${WAITED}s/${MAX_WAIT}s) status=${HEALTH}\r"
      sleep 5
      WAITED=$((WAITED + 5))
      ;;
  esac
done

if [[ $WAITED -ge $MAX_WAIT ]]; then
  echo ""
  echo "  [ERROR] app이 ${MAX_WAIT}초 내 healthy 상태가 되지 않음" >&2
  docker logs --tail=50 "$APP_CONTAINER" >&2 || true
  exit 1
fi

# ============================================================
# 6. Nginx -> App 전체 경로 확인
# ============================================================
echo ""
echo "[6/6] Nginx 경유 App 헬스체크"
# 자체 서명 인증서라 -k 필요
# 호스트명은 Host 헤더로 넘기고 실제 접속은 localhost
curl -fsSk -H "Host: ${DOMAIN}" \
  "https://127.0.0.1/actuator/health" \
  | head -c 500

echo ""
echo ""
echo "Smoke test 통과"
