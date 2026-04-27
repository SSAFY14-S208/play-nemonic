#!/usr/bin/env bash
# ============================================================
# 원격 배포 스크립트 (EC2 호스트에서 실행)
#
# Jenkins가 호출하는 배포 진입점.
# 1. 코드 tar.gz를 받아서 /opt/nemonic/releases/{backend,frontend}/release-N/ 에 풀기
# 2. /opt/nemonic/current/{backend,frontend} 심볼릭 링크 갱신
# 3. /opt/nemonic/infra/deploy/deploy.sh 실행 (DEPLOY_TARGET 전달)
# 4. smoke-test 실행
# 5. 실패 시 심볼릭 링크만 이전 release로 되돌림 (deploy.sh 재실행 X)
# ============================================================
set -euo pipefail

BASE_DIR="/opt/nemonic"
INFRA_DIR="$BASE_DIR/infra"
ARCHIVE_PATH=""
RELEASE_NAME=""
ENV_FILE=""
DEPLOY_TARGET="${DEPLOY_TARGET:-backend}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
APP_NAME="${APP_NAME:-nemonic}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-${APP_NAME}-prod}"

# ============================================================
# 인자 파싱
# ============================================================
while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-dir)  BASE_DIR="$2"; shift 2 ;;
    --archive)   ARCHIVE_PATH="$2"; shift 2 ;;
    --release)   RELEASE_NAME="$2"; shift 2 ;;
    --env-file)  ENV_FILE="$2"; shift 2 ;;
    --target)    DEPLOY_TARGET="$2"; shift 2 ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$ARCHIVE_PATH" || -z "$RELEASE_NAME" ]]; then
  echo "Usage: remote-deploy.sh --archive <tar.gz> --release <n> --target backend|frontend [--base-dir <dir>] [--env-file <path>]" >&2
  exit 1
fi

if [[ "$DEPLOY_TARGET" != "backend" && "$DEPLOY_TARGET" != "frontend" ]]; then
  echo "[ERROR] --target은 backend 또는 frontend만 가능 (현재: $DEPLOY_TARGET)" >&2
  exit 1
fi

ENV_FILE="${ENV_FILE:-$BASE_DIR/shared/.env.prod}"
INFRA_DIR="$BASE_DIR/infra"

[[ -f "$ARCHIVE_PATH" ]] || { echo "Archive not found: $ARCHIVE_PATH" >&2; exit 1; }
[[ -f "$ENV_FILE"    ]] || { echo "Env file not found: $ENV_FILE" >&2; exit 1; }
[[ -d "$INFRA_DIR"   ]] || { echo "Infra dir not found: $INFRA_DIR" >&2; exit 1; }

RELEASES_DIR="$BASE_DIR/releases/$DEPLOY_TARGET"
CURRENT_LINK="$BASE_DIR/current/$DEPLOY_TARGET"
RELEASE_DIR="$RELEASES_DIR/$RELEASE_NAME"

# 이전 릴리스 계산 (방어 로직 포함)
PREVIOUS_RELEASE=""
if [[ -L "$CURRENT_LINK" ]]; then
  _resolved="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
  if [[ -n "$_resolved" && -d "$_resolved" && "$_resolved" == "$RELEASES_DIR"/* ]]; then
    PREVIOUS_RELEASE="$_resolved"
  fi
fi

echo "=========================================="
echo "원격 배포 시작 ($DEPLOY_TARGET)"
echo "  릴리스       : $RELEASE_NAME"
echo "  이전 릴리스  : ${PREVIOUS_RELEASE:-없음}"
echo "  Release Dir : $RELEASE_DIR"
echo "  Current Link: $CURRENT_LINK"
echo "=========================================="

# ============================================================
# 1. 릴리스 디렉토리에 압축 해제
# ============================================================
echo ""
echo "[1/4] 릴리스 압축 해제"
mkdir -p "$RELEASES_DIR"
mkdir -p "$BASE_DIR/current"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

# tar.gz 안에 backend/ 또는 frontend/ 가 들어있는 경우와
# 직접 코드가 들어있는 경우 모두 처리
tar -xzf "$ARCHIVE_PATH" -C "$RELEASE_DIR"

# tar 내용에 따라 실제 코드 위치 보정
# Jenkinsfile이 git archive로 만들면 backend/ frontend/ 폴더 채로 들어감
if [[ -d "$RELEASE_DIR/$DEPLOY_TARGET" ]]; then
  # backend 코드가 backend/ 폴더 안에 있으면 한 단계 올림
  echo "  $DEPLOY_TARGET/ 폴더 발견. release 루트로 이동."
  TMPDIR=$(mktemp -d)
  mv "$RELEASE_DIR/$DEPLOY_TARGET"/* "$RELEASE_DIR/$DEPLOY_TARGET"/.[!.]* "$TMPDIR/" 2>/dev/null || true
  rm -rf "$RELEASE_DIR/$DEPLOY_TARGET"
  mv "$TMPDIR"/* "$TMPDIR"/.[!.]* "$RELEASE_DIR/" 2>/dev/null || true
  rmdir "$TMPDIR"
fi

# Dockerfile 존재 확인
if [[ ! -f "$RELEASE_DIR/Dockerfile" ]]; then
  echo "[ERROR] $RELEASE_DIR/Dockerfile 없음" >&2
  echo "        tar.gz 내용 확인 필요" >&2
  ls -la "$RELEASE_DIR" >&2
  exit 1
fi

# ============================================================
# 2. current 심볼릭 링크 갱신
# ============================================================
echo ""
echo "[2/4] current 심볼릭 링크 갱신"
ln -sfn "$RELEASE_DIR" "$CURRENT_LINK"
ls -la "$CURRENT_LINK"

# ============================================================
# 3. 배포 실행 (infra의 deploy.sh)
# ============================================================
echo ""
echo "[3/4] 배포 실행"

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
  if [[ -n "$PREVIOUS_RELEASE" && -d "$PREVIOUS_RELEASE" && "$PREVIOUS_RELEASE" != "$RELEASE_DIR" ]]; then
    echo ""
    echo "[!] 배포 실패. current 링크만 $PREVIOUS_RELEASE 로 되돌림." >&2
    echo "    (이전 버전의 deploy.sh는 실행하지 않음 - Jenkins 자살 방지)" >&2
    ln -sfn "$PREVIOUS_RELEASE" "$CURRENT_LINK"
  else
    echo ""
    echo "[!] 롤백할 이전 릴리스가 없습니다. 컨테이너 상태를 수동 확인하세요." >&2
    if [[ -L "$CURRENT_LINK" ]] && [[ "$(readlink -f "$CURRENT_LINK")" == "$RELEASE_DIR" ]]; then
      rm -f "$CURRENT_LINK"
    fi
  fi
}

if ! run_release; then
  rollback
  exit 1
fi

# ============================================================
# 4. 오래된 릴리스 정리
# ============================================================
echo ""
echo "[4/4] 오래된 릴리스 정리 (최근 $KEEP_RELEASES개 유지)"
mapfile -t OLD_RELEASES < <(
  ls -1dt "$RELEASES_DIR"/*/ 2>/dev/null | tail -n +"$((KEEP_RELEASES + 1))"
)
for old in "${OLD_RELEASES[@]:-}"; do
  [[ -n "$old" ]] || continue
  if [[ "$(readlink -f "$old")" == "$(readlink -f "$CURRENT_LINK")" ]]; then
    continue
  fi
  echo "  삭제: $old"
  rm -rf "$old"
done

rm -f "$ARCHIVE_PATH"

echo ""
echo "배포 성공: $RELEASE_NAME ($DEPLOY_TARGET)"
