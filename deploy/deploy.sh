#!/usr/bin/env bash
# ============================================================
# 배포 실행 스크립트 (Registry 기반)
#
# 동작:
# - DEPLOY_TARGET: backend / frontend / ai / all
# - 이미지는 이미 Jenkins가 Registry에 push 완료된 상태
# - 여기서는 pull + 컨테이너 재시작만
# - Jenkins, nginx, registry는 절대 건드리지 않음
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-/opt/nemonic/shared/.env.prod}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nemonic-prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
DEPLOY_TARGET="${DEPLOY_TARGET:-backend}"

# 배포 대상 서비스 그룹
BACKEND_SERVICES=(app)
FRONTEND_SERVICES=(frontend)
AI_SERVICES=(moderation-server)

case "$DEPLOY_TARGET" in
  backend)
    DEPLOY_SERVICES=("${BACKEND_SERVICES[@]}")
    ;;
  frontend)
    DEPLOY_SERVICES=("${FRONTEND_SERVICES[@]}")
    ;;
  ai)
    DEPLOY_SERVICES=("${AI_SERVICES[@]}")
    ;;
  all)
    DEPLOY_SERVICES=("${AI_SERVICES[@]}" "${BACKEND_SERVICES[@]}" "${FRONTEND_SERVICES[@]}")
    ;;
  *)
    echo "[ERROR] DEPLOY_TARGET must be one of backend|frontend|ai|all" >&2
    exit 1
    ;;
esac

# ============================================================
# 사전 체크
# ============================================================
if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] 환경변수 파일 없음: $ENV_FILE" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "[ERROR] Compose 파일 없음: $COMPOSE_FILE" >&2
  exit 1
fi

CERTS_DIR="$ROOT_DIR/deploy/nginx/certs"
if [[ ! -e "$CERTS_DIR/fullchain.pem" ]]; then
  echo "[WARN] $CERTS_DIR/fullchain.pem 없음. 심볼릭 링크 생성:" >&2
  echo "       ln -sfn /opt/nemonic/shared/certs $CERTS_DIR" >&2
  exit 1
fi

echo "=========================================="
echo "배포 시작 ($DEPLOY_TARGET)"
echo "  프로젝트  : $COMPOSE_PROJECT_NAME"
echo "  대상      : ${DEPLOY_SERVICES[*]}"
echo "  Compose   : $COMPOSE_FILE"
echo "  제외      : jenkins, nginx, registry (운영 중 유지)"
echo "=========================================="

cd "$ROOT_DIR"

# frontend 서비스는 profile에 묶여있어서 활성화 필요
PROFILE_ARGS=""
if [[ " ${DEPLOY_SERVICES[*]} " == *" frontend "* ]]; then
  PROFILE_ARGS="--profile frontend"
fi

# 새 이미지 pull (이미 Jenkins가 Registry에 push했음)
echo ""
echo "[1/2] Registry에서 이미지 pull"
docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  $PROFILE_ARGS \
  pull "${DEPLOY_SERVICES[@]}"

# 컨테이너 재생성 (의존성 무시 = jenkins/registry/nginx 등 안 건드림)
echo ""
echo "[2/2] 컨테이너 재생성"
docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  $PROFILE_ARGS \
  up -d --no-deps "${DEPLOY_SERVICES[@]}"

echo ""
echo "현재 상태 (전체):"
docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  $PROFILE_ARGS \
  ps

echo ""
echo "오래된 이미지 정리..."
docker image prune -f >/dev/null || true

echo ""
echo "배포 완료"
