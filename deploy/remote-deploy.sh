#!/usr/bin/env bash
# ============================================================
# 원격 배포 스크립트 (Registry 기반)
#
# Jenkins가 호출하는 배포 진입점.
# 이전 버전의 tar.gz 풀기 / 심볼릭 링크 / 빌드는 모두 사라지고:
# 1. infra의 deploy.sh 호출 → docker pull + up -d
# 2. smoke-test.sh 실행
# 3. 실패 시 롤백 (이전 이미지 태그로 재배포)
# ============================================================
set -euo pipefail

BASE_DIR="/opt/nemonic"
INFRA_DIR="$BASE_DIR/infra"
ENV_FILE=""
DEPLOY_TARGET="${DEPLOY_TARGET:-backend}"
APP_NAME="${APP_NAME:-nemonic}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-${APP_NAME}-prod}"
RELEASE_NAME=""

# ============================================================
# 인자 파싱
# ============================================================
while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-dir)  BASE_DIR="$2"; shift 2 ;;
    --release)   RELEASE_NAME="$2"; shift 2 ;;
    --env-file)  ENV_FILE="$2"; shift 2 ;;
    --target)    DEPLOY_TARGET="$2"; shift 2 ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$RELEASE_NAME" ]]; then
  echo "Usage: remote-deploy.sh --release <name> --target backend|frontend [--base-dir <dir>] [--env-file <path>]" >&2
  exit 1
fi

if [[ "$DEPLOY_TARGET" != "backend" && "$DEPLOY_TARGET" != "frontend" ]]; then
  echo "[ERROR] --target은 backend 또는 frontend만 가능 (현재: $DEPLOY_TARGET)" >&2
  exit 1
fi

ENV_FILE="${ENV_FILE:-$BASE_DIR/shared/.env.prod}"
INFRA_DIR="$BASE_DIR/infra"

[[ -f "$ENV_FILE"  ]] || { echo "Env file not found: $ENV_FILE" >&2; exit 1; }
[[ -d "$INFRA_DIR" ]] || { echo "Infra dir not found: $INFRA_DIR" >&2; exit 1; }

# 이미지 이름과 서비스 이름.
# Jenkinsfile_backend는 nemonic/app으로 push하고 docker-compose도 nemonic/app:latest를
# pull하므로, DEPLOY_TARGET=backend일 때 IMAGE_REPO를 'app'으로 맞춰준다.
# 예전 버전은 nemonic/backend로 tag/push해서 롤백이 무효했음.
SERVICE_NAME="${DEPLOY_TARGET}"
IMAGE_REPO="${DEPLOY_TARGET}"
if [[ "$DEPLOY_TARGET" == "backend" ]]; then
  SERVICE_NAME="app"
  IMAGE_REPO="app"
fi
IMAGE_NAME="localhost:5000/nemonic/${IMAGE_REPO}"

echo "=========================================="
echo "원격 배포 시작 ($DEPLOY_TARGET)"
echo "  릴리스       : $RELEASE_NAME"
echo "  이미지       : $IMAGE_NAME:$RELEASE_NAME"
echo "  서비스       : $SERVICE_NAME"
echo "=========================================="

# ============================================================
# 이전 이미지 태그 기록 (롤백용)
# ============================================================
PREVIOUS_IMAGE=""
CURRENT_CONTAINER_IMAGE=$(docker compose \
  -p "$COMPOSE_PROJECT_NAME" \
  --env-file "$ENV_FILE" \
  -f "$INFRA_DIR/docker-compose.prod.yml" \
  ps -q "$SERVICE_NAME" 2>/dev/null | head -1)

if [[ -n "$CURRENT_CONTAINER_IMAGE" ]]; then
  PREVIOUS_IMAGE=$(docker inspect --format='{{.Image}}' "$CURRENT_CONTAINER_IMAGE" 2>/dev/null || true)
  echo "  이전 이미지 ID: ${PREVIOUS_IMAGE:0:12}"
fi

# ============================================================
# 1. 배포 실행 (infra의 deploy.sh)
# ============================================================
echo ""
echo "[1/2] 배포 실행"

run_release() {
  ENV_FILE="$ENV_FILE" \
  COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" \
  DEPLOY_TARGET="$DEPLOY_TARGET" \
    bash "$INFRA_DIR/deploy/deploy.sh"

  ENV_FILE="$ENV_FILE" \
  COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" \
  DEPLOY_TARGET="$DEPLOY_TARGET" \
    bash "$INFRA_DIR/deploy/smoke-test.sh"
}

rollback() {
  if [[ -n "$PREVIOUS_IMAGE" ]]; then
    echo ""
    echo "[!] 배포 실패. 이전 이미지($PREVIOUS_IMAGE)로 롤백 시도." >&2
    
    # 이전 이미지로 latest 태그 다시 붙임
    docker tag "$PREVIOUS_IMAGE" "$IMAGE_NAME:latest" || true
    docker push "$IMAGE_NAME:latest" || true
    
    # 컨테이너만 재시작
    docker compose \
      -p "$COMPOSE_PROJECT_NAME" \
      --env-file "$ENV_FILE" \
      -f "$INFRA_DIR/docker-compose.prod.yml" \
      up -d --no-deps "$SERVICE_NAME" || true
  else
    echo ""
    echo "[!] 롤백할 이전 이미지 없음. 컨테이너 상태 수동 확인 필요." >&2
  fi
}

if ! run_release; then
  rollback
  exit 1
fi

# ============================================================
# 2. Registry 정리 (선택사항, 누적 방지)
# ============================================================
echo ""
echo "[2/2] 호스트 이미지 정리"
docker image prune -f >/dev/null || true

# Registry 자체에서 옛날 이미지 삭제는 별도 cleanup job에서 처리
# (tag 별 보존 정책 필요 - 추후 구현)

echo ""
echo "배포 성공: $RELEASE_NAME ($DEPLOY_TARGET)"
