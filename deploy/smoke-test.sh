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
# 5. Nginx -> App 전체 경로 확인
# ============================================================
echo ""
echo "[5/5] Nginx 경유 App 헬스체크"
# 자체 서명 인증서라 -k 필요
# 호스트명은 Host 헤더로 넘기고 실제 접속은 localhost
curl -fsSk -H "Host: ${DOMAIN}" \
  "https://127.0.0.1/actuator/health" \
  | head -c 500

echo ""
echo ""
echo "Smoke test 통과"
