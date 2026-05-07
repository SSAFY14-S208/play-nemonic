# Backend

## 로컬 실행 방법

1. `backend/.env.example`을 참고해 `backend/.env`를 작성합니다.
2. 로컬 의존성(PostgreSQL, Redis, MinIO)을 띄웁니다.

   **macOS / Linux:**
   ```bash
   cd backend
   docker compose -f docker-compose.local.yml up -d
   ```

   **Windows (PowerShell):**
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\local-up.ps1
   ```
3. IntelliJ 실행 설정의 EnvFile 플러그인으로 `backend/.env`를 연결합니다 (또는 `./gradlew bootRun`으로 실행하면 자동으로 `.env`를 읽습니다).
4. `backend` 애플리케이션(`BackendApplication`)을 실행합니다.

`bootRun`에서 다른 env 파일을 사용하려면 `backend` 디렉터리에서 아래처럼 실행합니다.

```powershell
.\gradlew.bat bootRun -PenvFile=.env.server
```

또는 환경 변수로 선택할 수 있습니다.

```powershell
$env:ENV_FILE = ".env.server"
.\gradlew.bat bootRun
```

IntelliJ에서 직접 `BackendApplication`을 실행할 때도 Run Configuration의 Environment variables에
`ENV_FILE=.env.server`를 넣으면 Spring이 `backend/.env.server`를 읽습니다. EnvFile 플러그인을 쓰는 경우에는
기존처럼 `.env.server`를 직접 지정해도 됩니다.

> 서버 DB에 직접 접속해야 하는 경우(예: Swagger로 운영 DB 디버깅), `backend/.env.server`를 별도 작성하고 IntelliJ Run Configuration을 분리해 EnvFile에 `.env.server`를 지정합니다. SSH 터널이 필요하며, 서버 DB에 Flyway가 자동 실행되지 않도록 `.env.server`에는 보통 `FLYWAY_ENABLED=false`를 둡니다. 로컬 PostgreSQL 컨테이너가 `localhost:5432`를 사용 중이면 서버 터널은 `15432` 같은 다른 로컬 포트로 열고 `DB_URL`도 `jdbc:postgresql://localhost:15432/<db>`처럼 맞춰야 합니다.

### 의존성 내리기

**macOS / Linux:**
```bash
cd backend
docker compose -f docker-compose.local.yml down
```

**Windows (PowerShell):**
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\local-down.ps1
```

## 검증

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
```

Flyway migration을 추가하거나 수정했다면 PostgreSQL 기반 migration 검증도 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify-migration.ps1
```
