#!/usr/bin/env bash
# ============================================================
# Host maintenance 자동화 설치 — idempotent.
# 여러 번 실행해도 안전. 변경된 파일만 갱신하고 timer를 enable한다.
#
# 사용:
#   sudo bash deploy/host-maintenance/install.sh
#
# 동작:
#   1. systemd unit 4개를 /etc/systemd/system/에 복사 (diff 있을 때만)
#   2. daemon-reload + 두 timer enable --now
#   3. /etc/docker/daemon.json을 본 레포 버전으로 교체 (diff 있을 때만 + 자동 백업)
#   4. daemon.json 변경 시 사용자에게 "운영 점검 시간에 docker 재시작 필요" 안내
#   5. 등록된 timer 출력해 검증
# ============================================================
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "[ERROR] sudo로 실행해주세요." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SYSTEMD_SRC="$SCRIPT_DIR/systemd"
DOCKER_SRC="$SCRIPT_DIR/docker"

SYSTEMD_DEST="/etc/systemd/system"
DOCKER_CONFIG_DEST="/etc/docker/daemon.json"

units=(
  "docker-prune.service"
  "docker-prune.timer"
  "registry-gc.service"
  "registry-gc.timer"
)

changed_units=()

# ============================================================
# Step 1. systemd unit 복사 (변경분만)
# ============================================================
for unit in "${units[@]}"; do
  src="$SYSTEMD_SRC/$unit"
  dest="$SYSTEMD_DEST/$unit"
  if [[ ! -f "$src" ]]; then
    echo "[ERROR] 소스 unit 파일이 없습니다: $src" >&2
    exit 1
  fi
  if [[ -f "$dest" ]] && cmp -s "$src" "$dest"; then
    echo "[skip] $unit (변경 없음)"
    continue
  fi
  install -m 0644 "$src" "$dest"
  echo "[install] $unit"
  changed_units+=("$unit")
done

# ============================================================
# Step 2. daemon-reload + timer enable
# ============================================================
if (( ${#changed_units[@]} > 0 )); then
  echo "[systemctl] daemon-reload"
  systemctl daemon-reload
fi

# enable --now는 이미 enabled여도 OK (멱등)
for timer in docker-prune.timer registry-gc.timer; do
  echo "[systemctl] enable --now $timer"
  systemctl enable --now "$timer"
done

# ============================================================
# Step 3. daemon.json 교체 (diff 있을 때만, 자동 백업)
# ============================================================
docker_restart_needed=0
mkdir -p "$(dirname "$DOCKER_CONFIG_DEST")"

if [[ -f "$DOCKER_CONFIG_DEST" ]] && cmp -s "$DOCKER_SRC/daemon.json" "$DOCKER_CONFIG_DEST"; then
  echo "[skip] daemon.json (변경 없음)"
else
  if [[ -f "$DOCKER_CONFIG_DEST" ]]; then
    backup="${DOCKER_CONFIG_DEST}.bak.$(date +%Y%m%d-%H%M%S)"
    cp -a "$DOCKER_CONFIG_DEST" "$backup"
    echo "[backup] $DOCKER_CONFIG_DEST → $backup"
  fi
  install -m 0644 "$DOCKER_SRC/daemon.json" "$DOCKER_CONFIG_DEST"
  echo "[install] $DOCKER_CONFIG_DEST"
  docker_restart_needed=1
fi

# ============================================================
# Step 4. 검증 출력
# ============================================================
echo
echo "==== 등록된 timer ===="
systemctl list-timers docker-prune.timer registry-gc.timer --no-pager || true

echo
echo "==== 최근 실행 결과 (있다면) ===="
journalctl -u docker-prune.service -u registry-gc.service -n 5 --no-pager 2>/dev/null || true

echo
echo "==== 설치 완료 ===="
if (( docker_restart_needed )); then
  echo "[NOTICE] /etc/docker/daemon.json이 갱신되었습니다."
  echo "         새 컨테이너부터 log 회전이 적용됩니다 (max-size=50m × 5)."
  echo "         기존 컨테이너에도 즉시 적용하려면 운영 점검 시간에 다음을 수동 실행:"
  echo "           sudo systemctl restart docker"
  echo "         또는 force recreate:"
  echo "           sudo docker compose -f <prod-compose.yml> up -d --force-recreate"
fi

if (( ${#changed_units[@]} > 0 )); then
  echo "[NOTICE] systemd unit ${#changed_units[@]}건 갱신: ${changed_units[*]}"
fi

echo "[OK] host-maintenance 설치 완료."
