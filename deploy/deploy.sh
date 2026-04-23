#!/usr/bin/env bash
# ============================================================
# 배포 실행 스크립트
# - docker-compose.prod.yml 기준으로 전체 스택 up
# - Jenkins가 이 스크립트를 원격으로 호출하거나, 수동 실행
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/deploy/.env.prod}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nemonic-prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"

# ============================================================
# 사전 체크
# ============================================================
if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] 환경변수 파일 없음: $ENV_FILE" >&2
  echo "         deploy/.env.prod.example 을 복사해서 작성하세요." >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "[ERROR] Compose 파일 없음: $COMPOSE_FILE" >&2
  exit 1
fi

# nginx 인증서 심볼릭 링크 확인
CERTS_DIR="$ROOT_DIR/deploy/nginx/certs"
if [[ ! -e "$CERTS_DIR/fullchain.pem" ]]; then
  echo "[WARN] $CERTS_DIR/fullchain.pem 이 없습니다." >&2
  echo "       bootstrap-ec2.sh가 생성한 인증서를 링크하세요:" >&2
  echo "       ln -sfn /opt/nemonic/shared/certs $CERTS_DIR" >&2
  exit 1
fi

echo "=========================================="
echo "배포 시작"
echo "  프로젝트  : $COMPOSE_PROJECT_NAME"
echo "  Compose   : $COMPOSE_FILE"
echo "  Env       : $ENV_FILE"
echo "=========================================="

# ============================================================
# 빌드 & 기동
# ============================================================
cd "$ROOT_DIR"

docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  pull --ignore-buildable || true

docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  up -d --build --remove-orphans

echo ""
echo "현재 상태:"
docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$COMPOSE_FILE" \
  ps

# ============================================================
# 오래된 이미지 정리 (디스크 절약)
# ============================================================
echo ""
echo "오래된 이미지 정리..."
docker image prune -f >/dev/null || true

echo ""
echo "배포 완료"
