#!/usr/bin/env bash
# ============================================================
# 배포 실행 스크립트
# - 애플리케이션 서비스만 재배포 (app, postgres, redis, minio)
# - Jenkins, nginx는 건드리지 않음 (자기가 자기를 죽이는 문제 방지)
# - 최초 기동 시에는 first-up.sh 사용
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/deploy/.env.prod}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nemonic-prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"

# 배포 대상 서비스 (Jenkins, nginx 제외)
DEPLOY_SERVICES=(app postgres redis minio)

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
  echo "[WARN] $CERTS_DIR/fullchain.pem 이 없습니다." >&2
  echo "       ln -sfn /opt/nemonic/shared/certs $CERTS_DIR" >&2
  exit 1
fi

echo "=========================================="
echo "배포 시작 (Application 서비스만)"
echo "  프로젝트  : $COMPOSE_PROJECT_NAME"
echo "  대상      : ${DEPLOY_SERVICES[*]}"
echo "  제외      : jenkins, nginx (운영 중 유지)"
echo "=========================================="

cd "$ROOT_DIR"

# 이미지 pull
docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  pull --ignore-buildable "${DEPLOY_SERVICES[@]}" || true

# 명시된 서비스만 재생성 (--no-deps 로 jenkins/nginx 안건드림)
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
