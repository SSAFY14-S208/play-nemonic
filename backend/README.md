# Backend

## 로컬 실행 방법
1. `backend/.env.example`을 참고해 `backend/.env`를 작성합니다.
2. 레포 루트에서 `powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\local-up.ps1`를 실행합니다.
3. IntelliJ 실행 설정 또는 EnvFile 플러그인으로 `backend/.env`를 연결합니다.
4. `backend` 애플리케이션을 실행합니다.

로컬 의존성은 PostgreSQL, Redis, MinIO를 포함합니다.

## 검증

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
```

Flyway migration을 추가하거나 수정했다면 PostgreSQL 기반 migration 검증도 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify-migration.ps1
```
