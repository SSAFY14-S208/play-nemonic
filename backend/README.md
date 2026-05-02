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

> 서버 DB에 직접 접속해야 하는 경우(예: Swagger로 운영 DB 디버깅), `backend/.env.server`를 별도 작성하고 IntelliJ Run Configuration을 분리해 EnvFile에 `.env.server`를 지정합니다. SSH 터널이 필요하며, 이때 Flyway 자동 실행을 막으려면 `application.yaml`이 `FLYWAY_ENABLED` 환경변수를 받도록 수정해야 합니다.

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
