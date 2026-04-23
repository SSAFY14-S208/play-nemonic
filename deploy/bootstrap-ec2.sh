#!/usr/bin/env bash
# ============================================================
# EC2 최초 1회 세팅 스크립트
# - Docker & Compose 설치
# - UFW 방화벽 설정 (22/80/443만 허용, 다른 포트 모두 차단)
# - 자체 서명 SSL 인증서 생성 (임시용)
# - 배포 디렉토리 생성
#
# 주의사항:
# 1. 실행 전 SSH 터미널을 2~3개 미리 띄워둘 것 (UFW 실수 대비)
# 2. SSH 키 파일(.pem)로 접속한 상태에서만 실행
# 3. /home 및 시스템 디렉토리 퍼미션 변경 금지
# ============================================================
set -euo pipefail

BASE_DIR="${BASE_DIR:-/opt/nemonic}"
APP_USER="${APP_USER:-${SUDO_USER:-$USER}}"
DOMAIN="${DOMAIN:-k14s208.p.ssafy.io}"

echo "=========================================="
echo "Bootstrap 시작"
echo "  BASE_DIR : $BASE_DIR"
echo "  APP_USER : $APP_USER"
echo "  DOMAIN   : $DOMAIN"
echo "=========================================="

# sudo 확인
if ! command -v sudo >/dev/null 2>&1; then
  echo "[ERROR] sudo가 필요합니다." >&2
  exit 1
fi

# ============================================================
# 1. 패키지 설치
# ============================================================
echo ""
echo "[1/5] 패키지 설치..."
sudo apt-get update
sudo apt-get install -y \
  ca-certificates \
  curl \
  gnupg \
  ufw \
  openssl

# Docker 공식 저장소 추가 (이미 있으면 스킵)
if [ ! -f /etc/apt/keyrings/docker.gpg ]; then
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
    sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg

  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
    sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

  sudo apt-get update
fi

sudo apt-get install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin

# Docker 서비스 활성화
sudo systemctl enable --now docker

# 현재 사용자를 docker 그룹에 추가
sudo usermod -aG docker "$APP_USER"

echo "  -> Docker 설치 완료"

# ============================================================
# 2. UFW 방화벽 설정
# 주의: EC2 기본은 UFW enabled + 22번만 허용 상태
# ============================================================
echo ""
echo "[2/5] UFW 방화벽 설정..."

# 먼저 필요한 포트 먼저 allow (enable 전에!)
sudo ufw allow 22/tcp comment 'SSH'
sudo ufw allow 80/tcp comment 'HTTP (redirect to HTTPS)'
sudo ufw allow 443/tcp comment 'HTTPS (nginx)'

# UFW 활성화 (이미 활성 상태여도 문제없음)
sudo ufw --force enable

echo ""
echo "  현재 UFW 상태:"
sudo ufw status verbose
echo ""
echo "  -> 22/80/443만 허용됨. PostgreSQL/Redis/Jenkins/MinIO 포트는 외부에서 접근 불가."

# ============================================================
# 3. 배포 디렉토리 생성
# ============================================================
echo ""
echo "[3/5] 배포 디렉토리 생성..."
sudo install -d -m 755 -o "$APP_USER" -g "$APP_USER" \
  "$BASE_DIR" \
  "$BASE_DIR/incoming" \
  "$BASE_DIR/releases" \
  "$BASE_DIR/shared" \
  "$BASE_DIR/shared/certs"

echo "  -> $BASE_DIR 이하 생성 완료"

# ============================================================
# 4. 임시 SSL 인증서 생성 (자체 서명)
# - 브라우저에서 경고 뜨지만 HTTPS는 동작함
# - 정식 인증서(Let's Encrypt 등)는 나중에 교체
# ============================================================
echo ""
echo "[4/5] 임시 SSL 인증서 생성..."

CERT_DIR="$BASE_DIR/shared/certs"
if [ ! -f "$CERT_DIR/fullchain.pem" ]; then
  sudo -u "$APP_USER" openssl req -x509 -nodes -days 365 \
    -newkey rsa:2048 \
    -keyout "$CERT_DIR/privkey.pem" \
    -out "$CERT_DIR/fullchain.pem" \
    -subj "/C=KR/ST=Seoul/L=Seoul/O=SSAFY/CN=$DOMAIN" \
    -addext "subjectAltName=DNS:$DOMAIN"
  chmod 600 "$CERT_DIR/privkey.pem"
  echo "  -> $CERT_DIR 에 자체 서명 인증서 생성"
else
  echo "  -> 인증서가 이미 있어 스킵"
fi

# ============================================================
# 5. .env.prod 안내
# ============================================================
echo ""
echo "[5/5] 환경변수 파일 체크..."
if [ ! -f "$BASE_DIR/shared/.env.prod" ]; then
  echo "  -> $BASE_DIR/shared/.env.prod 이 없습니다. 아래 절차대로 생성하세요."
else
  echo "  -> .env.prod 이미 존재"
fi

# ============================================================
# 완료 안내
# ============================================================
cat <<EOF

==========================================
Bootstrap 완료!
==========================================

다음 단계:

1. [필수] docker 그룹 적용을 위해 SSH 재접속
   $ exit
   $ ssh -i 키파일.pem $APP_USER@$DOMAIN

2. 환경변수 파일 작성
   $ cp 프로젝트/deploy/.env.prod.example $BASE_DIR/shared/.env.prod
   $ vi $BASE_DIR/shared/.env.prod
   # DB_PASSWORD, REDIS_PASSWORD, MINIO_ROOT_PASSWORD 강력한 값으로 변경
   $ chmod 600 $BASE_DIR/shared/.env.prod

3. SSL 인증서 링크 걸기 (bootstrap이 만든 자체 서명 인증서 사용)
   $ ln -sfn $BASE_DIR/shared/certs 프로젝트/deploy/nginx/certs

4. 첫 배포 실행
   $ cd 프로젝트
   $ ENV_FILE=$BASE_DIR/shared/.env.prod bash deploy/deploy.sh

5. Jenkins 초기 비밀번호 확인 (컨테이너 뜬 후)
   $ docker exec \$(docker ps -qf name=jenkins) cat /var/jenkins_home/secrets/initialAdminPassword

6. 브라우저에서 접속 (자체 서명 인증서라 경고 뜸 -> 고급 -> 이동)
   - Jenkins : https://$DOMAIN/jenkins/
   - MinIO   : https://$DOMAIN/minio/
   - App API : https://$DOMAIN/api/

보안 확인:
   $ sudo ufw status   # 22/80/443만 허용되어야 함
   $ docker ps         # 외부로 포트 노출된 건 nginx(80,443)만 있어야 함

EOF
