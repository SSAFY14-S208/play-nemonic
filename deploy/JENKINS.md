# Jenkins 초기 세팅

`docker-compose.prod.yml`에 정의된 Jenkins 컨테이너를 처음 사용할 때 필요한 설정입니다.

## 접속

https://k14s208.p.ssafy.io/jenkins/

자체 서명 인증서 경고 → **고급 → 이동(계속)**

## 1. 초기 비밀번호 입력

EC2 호스트에서 실행:

```bash
docker exec $(docker ps -qf name=jenkins) \
  cat /var/jenkins_home/secrets/initialAdminPassword
```

출력된 문자열을 붙여넣기.

## 2. 플러그인 설치

**"Install suggested plugins"** 선택.

추가로 설치 권장:
- **GitLab**
- **Generic Webhook Trigger** (GitLab 웹훅 편의용)
- **Docker Pipeline**
- **Pipeline: Stage View**

설치 경로: *Manage Jenkins* → *Plugins* → *Available plugins*

## 3. 관리자 계정 생성

반드시 **강력한 비밀번호**로 생성. 이 계정이 유일한 방어선입니다.

## 4. Jenkins URL 설정

Manage Jenkins → System → **Jenkins URL**

```
https://k14s208.p.ssafy.io/jenkins/
```

끝에 `/` 포함 필수.

## 5. JDK Tool 등록

Manage Jenkins → Tools → **JDK installations**

- Name: `jdk21`
- Install automatically → **Install from adoptium.net** → Version 21 최신

Jenkinsfile에서 `tools { jdk 'jdk21' }`가 이걸 참조합니다.

## 6. GitLab 연동

### 6-1. GitLab에서 Personal Access Token 생성

GitLab → User Settings → Access Tokens
- Scope: `api`, `read_repository`
- 토큰 복사

### 6-2. Jenkins에 Credentials 등록

Manage Jenkins → Credentials → (global) → Add Credentials

| 항목 | 값 |
|------|-----|
| Kind | `GitLab API token` |
| ID | `gitlab-api-token` |
| API token | 위에서 복사한 토큰 |

추가로 Git 저장소 클론용 자격증명도:

| 항목 | 값 |
|------|-----|
| Kind | `Username with password` 또는 `SSH Username with private key` |
| ID | `gitlab-repo-creds` |

### 6-3. GitLab 서버 등록

Manage Jenkins → System → **GitLab**

- Connection name: `gitlab`
- GitLab host URL: GitLab 서버 주소
- Credentials: `gitlab-api-token`
- **Test Connection** → Success

## 7. Pipeline Job 생성

New Item → **Multibranch Pipeline** 선택 → 이름 `nemonic`

### Branch Sources
- **GitLab Project** 추가
- Credentials: `gitlab-repo-creds`
- Owner / Project path 입력

### Build Configuration
- Mode: **by Jenkinsfile**
- Script Path: `Jenkinsfile` (프로젝트 루트)

### Scan 주기
- Periodically if not otherwise run → 5분 정도로

**Save** → 자동으로 브랜치 스캔 → `dev`, `be/dev` 브랜치에서 파이프라인 실행됨.

## 8. GitLab Webhook 연동 (자동 트리거)

### 8-1. Jenkins Webhook URL 확인

프로젝트별 webhook URL:
```
https://k14s208.p.ssafy.io/jenkins/project/nemonic
```

### 8-2. GitLab Repository Settings

Settings → Webhooks
- URL: 위 Webhook URL
- Trigger: **Push events** (브랜치 필터 `dev` 또는 `be/dev`)
- SSL verification: **자체 서명 인증서라면 체크 해제**
- **Add webhook** → **Test** → `Hook executed successfully`

이제 dev 브랜치에 push하면 Jenkins가 자동으로 빌드/배포합니다.

## 9. 파이프라인 동작 확인

1. `dev` 브랜치에 커밋 push
2. Jenkins 대시보드에서 빌드 시작되는지 확인
3. Stages:
   - Checkout → Test → Package → Deploy
4. 실패 시 콘솔 로그에서 원인 파악
5. 배포 후 `/actuator/health` 응답 200 확인

## 주의사항

- Jenkins 컨테이너는 **root**로 실행됩니다 (`user: root`). `docker.sock` 제어를 위해 필요하지만 보안상 일반 관리 UI 접근은 반드시 강력한 비밀번호로 보호.
- Jenkins 포트는 **UFW에 열지 않음** — nginx를 통해서만 접근 가능. 실수로 `8080` 포트를 열지 마세요.
- Jenkins 백업: `docker volume inspect nemonic-prod_jenkins_home` 으로 경로 확인 후 주기적으로 스냅샷.
- 외부에 Jenkins URL 공유 금지 (관리자만 접근).

## 문제 해결

### Webhook이 와도 빌드 안 됨
- Manage Jenkins → System Log에서 `GitLabPushTrigger` 관련 로그 확인
- Jenkinsfile의 `when { }` 조건이 현재 브랜치와 맞는지 확인

### 빌드에서 `docker: command not found`
- `docker-compose.prod.yml`의 Jenkins 서비스 volumes에
  `/usr/bin/docker:/usr/bin/docker:ro` 마운트가 있는지 확인
- 컨테이너 재시작: `docker compose ... restart jenkins`

### 빌드에서 Docker socket permission denied
- Jenkins 컨테이너를 `user: root`로 실행 중인지 확인
- 호스트 `/var/run/docker.sock` 권한 확인 (`srw-rw----` with `docker` group)
