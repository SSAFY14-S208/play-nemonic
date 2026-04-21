# Backend

## 로컬 실행 방법
1. `backend/.env.example`을 참고해 `backend/.env`를 작성합니다.
2. 레포 루트에서 `docker compose --env-file backend/.env -f docker-compose.local.yml up -d`를 실행합니다.
3. IntelliJ 실행 설정 또는 EnvFile 플러그인으로 `backend/.env`를 연결합니다.
4. `backend` 애플리케이션을 실행합니다.
