#!/usr/bin/env bash
# ============================================================
# 배포 직후 헬스체크 (smoke test)
# DEPLOY_TARGET에 따라 검증 범위 다름
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-/opt/nemonic/shared/.env.prod}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nemonic-prod}"
COMPOSE_FILE="$ROOT_DIR/docker-compose.prod.yml"
DEPLOY_TARGET="${DEPLOY_TARGET:-backend}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ERROR] 환경변수 파일 없음: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

PROFILE_ARGS=""
if [[ "$DEPLOY_TARGET" == "frontend" || "$DEPLOY_TARGET" == "all" ]]; then
  PROFILE_ARGS="--profile frontend"
fi

compose() {
  docker compose \
    -p "$COMPOSE_PROJECT_NAME" \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    $PROFILE_ARGS \
    "$@"
}

echo "=========================================="
echo "Smoke Test ($DEPLOY_TARGET)"
echo "=========================================="

echo ""
echo "[공통] 컨테이너 상태"
compose ps

# ============================================================
# Backend 검증
# ============================================================
if [[ "$DEPLOY_TARGET" == "backend" || "$DEPLOY_TARGET" == "all" ]]; then
  echo ""
  echo "[backend 1/4] PostgreSQL 연결 확인"
  compose exec -T postgres pg_isready -U "${DB_USERNAME}" -d "${DB_NAME}"

  echo ""
  echo "[backend 2/4] Redis 연결 확인"
  compose exec -T redis redis-cli -a "${REDIS_PASSWORD}" --no-auth-warning ping

  echo ""
  echo "[backend 3/4] MinIO 연결 확인"
  compose exec -T minio curl -fsS http://localhost:9000/minio/health/live

  echo ""
  echo "[backend 4/4] App healthy 대기 (최대 3분)"
  MAX_WAIT=180
  WAITED=0
  while [[ $WAITED -lt $MAX_WAIT ]]; do
    APP_CONTAINER=$(compose ps -q app 2>/dev/null | head -1)
    if [[ -z "$APP_CONTAINER" ]]; then
      echo "  app 컨테이너를 찾을 수 없습니다..."
      sleep 5; WAITED=$((WAITED + 5)); continue
    fi
    HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "$APP_CONTAINER" 2>/dev/null || echo "unknown")
    case "$HEALTH" in
      healthy)
        echo "  app healthy 확인 (대기: ${WAITED}초)"
        break
        ;;
      unhealthy)
        echo "  [ERROR] app unhealthy" >&2
        docker logs --tail=50 "$APP_CONTAINER" >&2 || true
        exit 1
        ;;
      *)
        printf "  기동 중... (${WAITED}s/${MAX_WAIT}s) status=${HEALTH}\r"
        sleep 5; WAITED=$((WAITED + 5))
        ;;
    esac
  done
  if [[ $WAITED -ge $MAX_WAIT ]]; then
    echo ""
    echo "  [ERROR] app 헬스체크 타임아웃" >&2
    docker logs --tail=50 "$APP_CONTAINER" >&2 || true
    exit 1
  fi

  echo ""
  echo "[backend] Nginx -> App 경로 확인 (최대 60초 retry)"
  # app 컨테이너 재생성 직후 nginx upstream DNS 캐시(TTL 10s)가 만료되기 전에는
  # stale IP로 503이 나올 수 있어서 단발 호출 대신 retry. resolver 패턴이 적용된
  # /actuator/ 블록과 함께 동작.
  MAX_WAIT=60
  WAITED=0
  until curl -fsSk -H "Host: ${DOMAIN}" \
              -o /tmp/smoke-actuator.out \
              "https://nginx/actuator/health"; do
    if [[ $WAITED -ge $MAX_WAIT ]]; then
      echo "  [ERROR] Nginx -> App 헬스체크 60초 내 미통과" >&2
      exit 1
    fi
    printf "  대기 중... (${WAITED}s/${MAX_WAIT}s)\r"
    sleep 5; WAITED=$((WAITED + 5))
  done
  echo ""
  head -c 300 /tmp/smoke-actuator.out
  rm -f /tmp/smoke-actuator.out
  echo ""
fi

# ============================================================
# Frontend 검증
# ============================================================
if [[ "$DEPLOY_TARGET" == "frontend" || "$DEPLOY_TARGET" == "all" ]]; then
  echo ""
  echo "[frontend 1/2] Frontend healthy 대기 (최대 90초)"
  MAX_WAIT=90
  WAITED=0
  while [[ $WAITED -lt $MAX_WAIT ]]; do
    FE_CONTAINER=$(compose ps -q frontend 2>/dev/null | head -1)
    if [[ -z "$FE_CONTAINER" ]]; then
      echo "  frontend 컨테이너를 찾을 수 없습니다..."
      sleep 5; WAITED=$((WAITED + 5)); continue
    fi
    HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "$FE_CONTAINER" 2>/dev/null || echo "unknown")
    case "$HEALTH" in
      healthy)
        echo "  frontend healthy 확인 (대기: ${WAITED}초)"
        break
        ;;
      unhealthy)
        echo "  [ERROR] frontend unhealthy" >&2
        docker logs --tail=50 "$FE_CONTAINER" >&2 || true
        exit 1
        ;;
      *)
        printf "  기동 중... (${WAITED}s/${MAX_WAIT}s) status=${HEALTH}\r"
        sleep 5; WAITED=$((WAITED + 5))
        ;;
    esac
  done
  if [[ $WAITED -ge $MAX_WAIT ]]; then
    echo ""
    echo "  [ERROR] frontend 헬스체크 타임아웃" >&2
    docker logs --tail=50 "$FE_CONTAINER" >&2 || true
    exit 1
  fi

  echo ""
  echo "[frontend 2/2] Nginx -> Frontend 경로 확인 (최대 60초 retry)"
  # frontend 컨테이너 재생성 직후 nginx upstream DNS 캐시 만료 대기.
  MAX_WAIT=60
  WAITED=0
  until curl -fsSk -H "Host: ${DOMAIN}" \
              -o /dev/null \
              "https://nginx/"; do
    if [[ $WAITED -ge $MAX_WAIT ]]; then
      echo "  [ERROR] Nginx -> Frontend 헬스체크 60초 내 미통과" >&2
      exit 1
    fi
    printf "  대기 중... (${WAITED}s/${MAX_WAIT}s)\r"
    sleep 5; WAITED=$((WAITED + 5))
  done
  echo "  HTTP 200"
fi

echo ""
echo "Smoke test 통과"
