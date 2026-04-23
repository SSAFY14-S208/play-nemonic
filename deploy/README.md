# Nemonic 배포 가이드

SSAFY EC2 단일 서버에서 **Jenkins + Spring Boot + PostgreSQL + Redis + MinIO**를
Docker Compose로 운영하기 위한 설정입니다.

## 아키텍처

```
                        [k14s208.p.ssafy.io]
                               │
                          UFW (22/80/443만 허용)
                               │
                  ┌────────────▼────────────┐
                  │   Nginx (80, 443)        │
                  │   - 유일하게 외부 노출    │
                  └─┬────┬─────┬──────┬──────┘
                    │    │     │      │
          ┌─────────┘    │     │      └─────────┐
          │              │     │                 │
      /jenkins/      /minio/  /s3/            /api/
          │              │     │                 │
          ▼              ▼     ▼                 ▼
      ┌──────┐      ┌─────────────┐         ┌────────┐
      │Jenkins│     │    MinIO    │         │Spring  │
      └──┬───┘      │Console│ API │         │Boot App│
         │          └───────┴──┬──┘         └───┬────┘
         │  docker.sock        │                │
         │  host /opt/nemonic  │                │
         ▼                     │                │
    [호스트 docker]             │                │
                               │                │
                  ┌────────────▼────────────────▼──┐
                  │       내부 네트워크             │
                  │  ┌─────────┐   ┌─────────┐    │
                  │  │Postgres │   │  Redis  │    │
                  │  │ (비노출) │   │ (비노출) │    │
                  │  └─────────┘   └─────────┘    │
                  └─────────────────────────────────┘
```

핵심 설계:
- **Nginx가 유일한 외부 진입점** → UFW는 22/80/443만 허용
- PostgreSQL, Redis, MinIO API, Jenkins는 **포트 노출 없음** (내부 네트워크만)
- 모든 관리 UI(Jenkins, MinIO Console)는 HTTPS 뒤에서만 접근 가능
- Docker 소켓을 Jenkins에 마운트해서 Jenkins가 호스트 Docker를 제어

## 디렉토리 구조

```
프로젝트/
├── docker-compose.prod.yml
├── Jenkinsfile
├── backend/                      # Spring Boot 소스
│   └── Dockerfile
└── deploy/
    ├── README.md                 (이 파일)
    ├── JENKINS.md                (Jenkins 초기 세팅)
    ├── .env.prod.example
    ├── bootstrap-ec2.sh          (EC2 최초 1회 실행)
    ├── deploy.sh
    ├── smoke-test.sh
    ├── remote-deploy.sh
    └── nginx/
        ├── default.conf
        └── certs/                (→ /opt/nemonic/shared/certs 심볼릭 링크)
```

## 최초 세팅 (1회만)

### 1단계: EC2 접속

SSH 터미널을 **2~3개 미리 열어두세요.** UFW 설정 중 실수로 SSH가 끊어지면
복구 불가능합니다.

```bash
ssh -i k14s208.pem ubuntu@k14s208.p.ssafy.io
```

### 2단계: 프로젝트 클론

```bash
cd ~
git clone <your-gitlab-url> nemonic
cd nemonic
```

### 3단계: bootstrap 실행

```bash
chmod +x deploy/*.sh
./deploy/bootstrap-ec2.sh
```

완료 후 반드시 **SSH 재접속** (docker 그룹 권한 적용).

### 4단계: 환경변수 파일 작성

```bash
cp deploy/.env.prod.example /opt/nemonic/shared/.env.prod
vi /opt/nemonic/shared/.env.prod   # 비밀번호들 강력하게 변경
chmod 600 /opt/nemonic/shared/.env.prod
```

**필수 변경 항목:**
- `DB_PASSWORD` — 16자 이상, 대소문자/숫자/특수문자 혼합
- `REDIS_PASSWORD` — 16자 이상
- `MINIO_ROOT_PASSWORD` — 8자 이상 (MinIO 최소 요구사항)

### 5단계: SSL 인증서 링크

bootstrap이 자체 서명 인증서를 `/opt/nemonic/shared/certs`에 만들었습니다.
프로젝트의 nginx에서 사용하도록 심볼릭 링크를 겁니다.

```bash
cd ~/nemonic
ln -sfn /opt/nemonic/shared/certs deploy/nginx/certs
ls -la deploy/nginx/certs    # 파일 보이는지 확인
```

### 6단계: 첫 배포

```bash
ENV_FILE=/opt/nemonic/shared/.env.prod ./deploy/deploy.sh
```

컨테이너가 전부 뜨면:

```bash
docker ps
# nginx, jenkins, postgres, redis, minio, app 모두 running 상태여야 함
```

### 7단계: Jenkins 초기 비밀번호 확인

```bash
docker exec $(docker ps -qf name=jenkins) \
  cat /var/jenkins_home/secrets/initialAdminPassword
```

출력된 비밀번호를 메모해두세요.

### 8단계: 브라우저 접속

자체 서명 인증서라 브라우저에서 "안전하지 않음" 경고가 뜹니다.
**고급 → 이동(계속)** 을 눌러서 진행하세요.

- **Jenkins**: https://k14s208.p.ssafy.io/jenkins/
- **MinIO Console**: https://k14s208.p.ssafy.io/minio/
- **App API**: https://k14s208.p.ssafy.io/api/

Jenkins 초기 설정은 [JENKINS.md](./JENKINS.md) 참고.

## 일상 운영

### 수동 배포

```bash
cd ~/nemonic
git pull origin dev
ENV_FILE=/opt/nemonic/shared/.env.prod ./deploy/deploy.sh
ENV_FILE=/opt/nemonic/shared/.env.prod ./deploy/smoke-test.sh
```

### 로그 확인

```bash
docker compose -p nemonic-prod -f docker-compose.prod.yml --env-file /opt/nemonic/shared/.env.prod logs -f app
# app 자리에 jenkins, nginx, postgres, redis, minio 대체 가능
```

### 특정 서비스만 재시작

```bash
docker compose -p nemonic-prod -f docker-compose.prod.yml --env-file /opt/nemonic/shared/.env.prod restart app
```

### 전체 중지

```bash
docker compose -p nemonic-prod -f docker-compose.prod.yml --env-file /opt/nemonic/shared/.env.prod down
```

`down`은 볼륨은 유지합니다. 데이터(DB, Redis, MinIO, Jenkins 설정) 모두 보존됨.

## 보안 점검 (수시로)

```bash
# 1. UFW는 22/80/443만 열려있어야 함
sudo ufw status

# 2. 외부 포트가 열린 컨테이너는 nginx뿐이어야 함
docker ps --format "table {{.Names}}\t{{.Ports}}"

# 3. Jenkins/MinIO/Postgres/Redis는 PORTS 칸이 비어있거나 내부(8080/tcp 등)만 있어야 함
# 4. .env.prod 권한 확인
ls -la /opt/nemonic/shared/.env.prod   # -rw------- (600) 이어야 함
```

## 주의사항

- **UFW는 항상 `enable` 상태 유지.** `sudo ufw disable` 금지.
- `/home`, 시스템 디렉토리 퍼미션 변경 금지.
- `.env.prod`는 절대 Git에 커밋하지 말 것.
- `.ssh_bak` 백업 폴더가 있으니 SSH 키 문제 생기면 거기서 복구.
- 서비스 기본 포트(8080, 5432, 6379, 9000 등)를 외부로 노출하지 않음 → 이미 nginx 뒤에 숨김.

## 문제 해결

### nginx가 계속 재시작됨
→ `deploy/nginx/certs/` 안에 `fullchain.pem`, `privkey.pem`이 있는지 확인.
   없으면 심볼릭 링크를 다시 걸어주세요.

### Jenkins에서 `docker` 명령이 안 먹음
→ `/var/run/docker.sock`과 `/usr/bin/docker` 마운트 확인.
   호스트의 docker 버전과 다를 수 있으니 컨테이너 안에서 `docker version` 실행.

### MinIO Console 리디렉트 루프
→ `.env.prod`의 `DOMAIN` 값이 실제 도메인과 일치하는지 확인.
   `MINIO_BROWSER_REDIRECT_URL`이 `https://$DOMAIN/minio/`를 가리키는지.

### 배포 실패 후 컨테이너 꼬임
```bash
docker compose -p nemonic-prod -f docker-compose.prod.yml --env-file /opt/nemonic/shared/.env.prod down
docker compose -p nemonic-prod -f docker-compose.prod.yml --env-file /opt/nemonic/shared/.env.prod up -d
```
