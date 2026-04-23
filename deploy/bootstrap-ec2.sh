#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="${BASE_DIR:-/opt/nemonic}"
APP_USER="${APP_USER:-${SUDO_USER:-$USER}}"
SSH_PORT="${SSH_PORT:-22}"

if ! command -v sudo >/dev/null 2>&1; then
  echo "This script requires sudo." >&2
  exit 1
fi

sudo apt-get update
if ! sudo apt-get install -y docker.io docker-compose-plugin ufw curl; then
  sudo apt-get install -y docker.io docker-compose-v2 ufw curl
fi
sudo systemctl enable --now docker
sudo usermod -aG docker "$APP_USER"

sudo install -d -m 755 -o "$APP_USER" -g "$APP_USER" \
  "$BASE_DIR" \
  "$BASE_DIR/incoming" \
  "$BASE_DIR/releases" \
  "$BASE_DIR/shared"

sudo ufw allow "${SSH_PORT}/tcp"
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status verbose

cat <<EOF
Bootstrap complete.

Next steps:
1. Copy deploy/.env.prod.example to ${BASE_DIR}/shared/.env.prod on the EC2 host.
2. Fill in production secrets in ${BASE_DIR}/shared/.env.prod.
3. Make sure the Jenkins SSH key can log in as ${APP_USER}.

Note:
- Jenkins is expected to run outside this EC2 host.
- This keeps UFW limited to 22/80/443 and avoids exposing the Jenkins web UI on a new port.
- Log out and back in once so the docker group takes effect for ${APP_USER}.
EOF
