#!/usr/bin/env bash
# ============================================================
# 배포 실행 스크립트 (infra/main 브랜치용)
#
# 실행 위치: /opt/nemonic/infra/
# 동작:
# - DEPLOY_TARGET 환경변수로 backend / frontend / all 선택
# - Jenkins, nginx는 절대 건드리지 않음 (자기 자살 방지)
# - build context는 docker-compose의 /opt/nemonic/current/{backend,frontend}/
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-/opt/nemonic/shared/.env.prod}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nemonic-prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
DEPLOY_TARGET="${DEPLOY_TARGET:-backend}"

# 배포 대상 서비스 그룹
BACKEND_SERVICES=(app postgres redis minio)
FRONTEND_SERVICES=(frontend)

case "$DEPLOY_TARGET" in
  backend)
    DEPLOY_SERVICES=("${BACKEND_SERVICES[@]}")
    ;;
  frontend)
    DEPLOY_SERVICES=("${FRONTEND_SERVICES[@]}")
    ;;
  all)
    DEPLOY_SERVICES=("${BACKEND_SERVICES[@]}" "${FRONTEND_SERVICES[@]}")
    ;;
  *)
    echo "[ERROR] DEPLOY_TARGET은 backend|frontend|all 중 하나여야 합니다." >&2
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

# 배포 대상 코드가 실제로 존재하는지 확인
case "$DEPLOY_TARGET" in
  backend|all)
    if [[ ! -e "/opt/nemonic/current/backend/Dockerfile" ]]; then
      echo "[ERROR] /opt/nemonic/current/backend/Dockerfile 없음" >&2
      echo "        backend release가 아직 배포되지 않았습니다." >&2
      exit 1
    fi
    ;;
esac

case "$DEPLOY_TARGET" in
  frontend|all)
    if [[ ! -e "/opt/nemonic/current/frontend/Dockerfile" ]]; then
      echo "[ERROR] /opt/nemonic/current/frontend/Dockerfile 없음" >&2
      echo "        frontend release가 아직 배포되지 않았습니다." >&2
      exit 1
    fi
    ;;
esac

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
echo "  제외      : jenkins, nginx (운영 중 유지)"
echo "=========================================="

cd "$ROOT_DIR"

# 이미지 pull (build 가능한 서비스는 스킵)
docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  pull --ignore-buildable "${DEPLOY_SERVICES[@]}" || true

# 명시된 서비스만 재생성
docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  up -d --build --no-deps "${DEPLOY_SERVICES[@]}"

echo ""
echo "현재 상태 (전체):"
docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  ps

echo ""
echo "오래된 이미지 정리..."
docker image prune -f >/dev/null || true

echo ""
echo "배포 완료"
