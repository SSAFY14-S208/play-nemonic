#!/usr/bin/env bash
# ============================================================
# 원격 배포 스크립트 (EC2에서 실행)
# - Jenkins가 tar.gz를 /opt/nemonic/incoming에 업로드한 후 이 스크립트 호출
# - 릴리스 단위로 관리, smoke-test 실패 시 자동 롤백
# ============================================================
set -euo pipefail

BASE_DIR="/opt/nemonic"
ARCHIVE_PATH=""
RELEASE_NAME=""
ENV_FILE=""
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
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$ARCHIVE_PATH" || -z "$RELEASE_NAME" ]]; then
  echo "Usage: remote-deploy.sh --archive <tar.gz> --release <name> [--base-dir <dir>] [--env-file <path>]" >&2
  exit 1
fi

ENV_FILE="${ENV_FILE:-$BASE_DIR/shared/.env.prod}"

[[ -f "$ARCHIVE_PATH" ]] || { echo "Archive not found: $ARCHIVE_PATH" >&2; exit 1; }
[[ -f "$ENV_FILE"    ]] || { echo "Env file not found: $ENV_FILE" >&2; exit 1; }

RELEASES_DIR="$BASE_DIR/releases"
CURRENT_LINK="$BASE_DIR/current"
RELEASE_DIR="$RELEASES_DIR/$RELEASE_NAME"

# 이전 릴리스 계산 - 심볼릭 링크가 자기 자신이나 잘못된 곳을 가리키면 무시
PREVIOUS_RELEASE=""
if [[ -L "$CURRENT_LINK" ]]; then
  _resolved="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
  # releases 디렉토리 안의 유효한 경로만 이전 릴리스로 인정
  if [[ -n "$_resolved" && -d "$_resolved" && "$_resolved" == "$RELEASES_DIR"/* ]]; then
    PREVIOUS_RELEASE="$_resolved"
  fi
fi

echo "=========================================="
echo "원격 배포 시작"
echo "  릴리스     : $RELEASE_NAME"
echo "  이전 릴리스 : ${PREVIOUS_RELEASE:-없음}"
echo "=========================================="

# ============================================================
# 1. 릴리스 디렉토리에 압축 해제
# ============================================================
echo ""
echo "[1/4] 릴리스 압축 해제"
mkdir -p "$RELEASES_DIR"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"
tar -xzf "$ARCHIVE_PATH" -C "$RELEASE_DIR"

# ============================================================
# 2. nginx 인증서 심볼릭 링크
# (bootstrap이 만든 shared/certs를 릴리스 안으로 연결)
# ============================================================
echo ""
echo "[2/4] 인증서 링크"
ln -sfn "$BASE_DIR/shared/certs" "$RELEASE_DIR/deploy/nginx/certs"

# ============================================================
# 3. current 심볼릭 링크 교체 & 배포
# ============================================================
echo ""
echo "[3/4] 배포 실행"
ln -sfn "$RELEASE_DIR" "$CURRENT_LINK"

run_release() {
  ENV_FILE="$ENV_FILE" \
  COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" \
    bash "$CURRENT_LINK/deploy/deploy.sh"

  ENV_FILE="$ENV_FILE" \
  COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" \
    bash "$CURRENT_LINK/deploy/smoke-test.sh"
}

rollback() {
  if [[ -n "$PREVIOUS_RELEASE" && -d "$PREVIOUS_RELEASE" && "$PREVIOUS_RELEASE" != "$RELEASE_DIR" ]]; then
    echo ""
    echo "[!] 배포 실패. $PREVIOUS_RELEASE 로 롤백..." >&2
    ln -sfn "$PREVIOUS_RELEASE" "$CURRENT_LINK"
    ENV_FILE="$ENV_FILE" \
    COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME" \
      bash "$PREVIOUS_RELEASE/deploy/deploy.sh" || true
  else
    echo ""
    echo "[!] 롤백할 이전 릴리스가 없습니다. 컨테이너 상태를 수동 확인하세요." >&2
    # current 링크가 방금 실패한 릴리스를 가리키면 제거 (다음 배포 혼선 방지)
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
# 4. 오래된 릴리스 정리 (수정시간 기준으로 안전하게)
# ============================================================
echo ""
echo "[4/4] 오래된 릴리스 정리 (최근 $KEEP_RELEASES개 유지)"
# -dt : 디렉토리, 수정시간 내림차순
mapfile -t OLD_RELEASES < <(
  ls -1dt "$RELEASES_DIR"/*/ 2>/dev/null | tail -n +"$((KEEP_RELEASES + 1))"
)
for old in "${OLD_RELEASES[@]:-}"; do
  [[ -n "$old" ]] || continue
  # 현재 심볼릭 링크가 가리키는 건 건너뛰기 (이중 안전장치)
  if [[ "$(readlink -f "$old")" == "$(readlink -f "$CURRENT_LINK")" ]]; then
    continue
  fi
  echo "  삭제: $old"
  rm -rf "$old"
done

# 업로드된 tar.gz 삭제
rm -f "$ARCHIVE_PATH"

echo ""
echo "배포 성공: $RELEASE_NAME"
